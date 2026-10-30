package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.Surface
import androidx.core.os.trace
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.peyilo.ireadview.internal.effect.EffectResourceUnavailable
import org.peyilo.pagecurl.mesh.CurlMesh

/**
 * Owns its EGL context, two reusable texture allocations and one latest-frame mailbox. Every begin
 * replaces both textures' pixels; page content is never reused by object identity.
 */
internal class IosCurlGpuWorker(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    private val invalidate: () -> Unit,
    private val onReady: () -> Unit = {},
) : AutoCloseable {
    private val thread =
        HandlerThread("iRead-curl", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean()
    private val generation = AtomicLong()
    private val scheduled = AtomicBoolean()

    private data class Frame(val generation: Long, val scene: IosCurlScene)

    private val pending = AtomicReference<Frame?>()
    private val mesh = CurlMesh()
    private var renderer: IosCurlRenderer? = null
    private var canvasCapture: IosCurlCanvasCapture? = null
    private val captureRequested = AtomicBoolean()
    @Volatile private var captureReady = false

    private var lastScene: IosCurlScene? = null
    private var activeGeneration = -1L
    private var firstFrame: IosCurlFirstFrameTrace? = null
    @Volatile
    var ready = false
        private set

    @Volatile
    var hasFrame = false
        private set

    @Volatile
    var failure: RuntimeException? = null
        private set

    init {
        handler.post {
            if (closed.get()) return@post
            try {
                val created = IosCurlRenderer(retainContext = true)
                renderer = created
                created.bindSurface(surface, width, height)
                created.clearSurface()
                ready = !closed.get()
                main.post(onReady)
            } catch (error: RuntimeException) {
                renderer?.close()
                renderer = null
                fail(error)
            } catch (_: OutOfMemoryError) {
                renderer?.close()
                renderer = null
                fail(EffectResourceUnavailable())
            }
        }
    }

    fun prepareCapture(): Boolean {
        if (!ready || closed.get()) return false
        if (captureRequested.compareAndSet(false, true))
            handler.post {
                if (closed.get()) return@post
                try {
                    canvasCapture =
                        renderer?.createCanvasCapture(
                            handler,
                            width,
                            height,
                            { !closed.get() },
                            {
                                captureReady = true
                                main.post(onReady)
                            },
                            ::fail,
                        )
                    canvasCapture?.warm()
                } catch (error: RuntimeException) {
                    fail(error)
                } catch (_: OutOfMemoryError) {
                    fail(EffectResourceUnavailable())
                }
            }
        return captureReady
    }

    /** A successful post transfers the read pins; every queued outcome closes them. */
    fun begin(input: IosCurlPageInput): Boolean {
        if (!ready || closed.get()) return false
        val token = generation.incrementAndGet()
        pending.set(null)
        hasFrame = false
        return handler.post {
            try {
                val renderer = renderer ?: return@post
                if (token != generation.get() || closed.get()) return@post
                activeGeneration = -1L
                firstFrame = input.firstFrame
                val width = input.width
                val height = input.height
                renderer.bindSurface(surface, width, height)
                lastScene = null
                fun current() = token == generation.get() && !closed.get() && failure == null
                fun prepared() {
                    if (current()) {
                        activeGeneration = token
                        if (pending.get() != null) schedule()
                    }
                }
                when (input) {
                    is IosCurlPageInput.Pixels -> {
                        canvasCapture?.cancel()
                        trace("iRead.curl.upload") {
                            renderer.upload(input.pages.current.bitmap, input.pages.target.bitmap)
                        }
                        prepared()
                    }
                    is IosCurlPageInput.Canvas ->
                        requireNotNull(canvasCapture).begin(input, ::current, ::prepared)
                }
            } catch (error: RuntimeException) {
                fail(error)
            } catch (_: OutOfMemoryError) {
                fail(EffectResourceUnavailable())
            } finally {
                input.close()
            }
        }
    }

    fun submit(scene: IosCurlScene) {
        if (closed.get()) return
        pending.set(Frame(generation.get(), scene))
        schedule()
    }

    private fun schedule() {
        if (!closed.get() && scheduled.compareAndSet(false, true)) handler.post(drain)
    }

    private val drain =
        object : Runnable {
            override fun run() {
                // A previously scheduled drain cannot render a new turn before its textures bind.
                if (activeGeneration != generation.get()) {
                    scheduled.set(false)
                    return
                }
                val frame = pending.getAndSet(null)
                if (frame != null && !closed.get() && frame.generation == generation.get()) {
                    try {
                        if (lastScene != frame.scene) {
                            requireNotNull(renderer).presentationTimeNanos = System.nanoTime()
                            frame.scene.draw(mesh, requireNotNull(renderer))
                            lastScene = frame.scene
                        }
                        if (frame.generation == generation.get() && !hasFrame) {
                            firstFrame?.submitted()
                            firstFrame = null
                            hasFrame = true
                            main.post(invalidate)
                        }
                    } catch (error: RuntimeException) {
                        fail(error)
                    } catch (_: OutOfMemoryError) {
                        fail(EffectResourceUnavailable())
                    }
                }
                scheduled.set(false)
                if (pending.get() != null) schedule()
            }
        }

    private fun fail(error: RuntimeException) {
        firstFrame?.cancel()
        firstFrame = null
        ready = false
        failure = error
        main.post(invalidate)
    }

    fun end(finalScene: IosCurlScene? = null) {
        val token = generation.incrementAndGet()
        pending.set(null)
        hasFrame = false
        handler.post {
            if (token != generation.get() || closed.get()) return@post
            try {
                if (finalScene != null && finalScene != lastScene && activeGeneration != -1L) {
                    requireNotNull(renderer).presentationTimeNanos = System.nanoTime()
                    finalScene.draw(mesh, requireNotNull(renderer))
                }
                lastScene = null
                activeGeneration = -1L
                canvasCapture?.cancel()
            } catch (error: RuntimeException) {
                fail(error)
            }
        }
    }

    fun clearSurface() {
        handler.post {
            try {
                renderer?.clearSurface()
            } catch (error: RuntimeException) {
                fail(error)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ready = false
        generation.incrementAndGet()
        pending.set(null)
        fun release() {
            try {
                runCatching { canvasCapture?.close() }
                canvasCapture = null
                runCatching { renderer?.close() }
                renderer = null
            } finally {
                surface.release()
                thread.quitSafely()
            }
        }
        handler.post {
            val capture = canvasCapture
            if (capture == null) release() else capture.closeProducers(::release)
        }
    }
}
