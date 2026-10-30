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
import org.peyilo.ireadview.internal.effect.PageTurnSnapshots
import org.peyilo.pagecurl.mesh.CurlMesh

/** Owns its EGL context and a single latest-frame mailbox, never a queue of stale poses. */
internal class IosCurlGpuWorker(
    private val surface: Surface,
    width: Int,
    height: Int,
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
    private var lastScene: IosCurlScene? = null
    private var activeGeneration = -1L
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
            try {
                val created = IosCurlRenderer(retainContext = true)
                renderer = created
                created.bindSurface(surface, width, height)
                created.clearSurface()
                ready = true
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

    /** A successful post transfers the read pins; every queued outcome closes them. */
    fun begin(capture: PageTurnSnapshots.Retained, width: Int, height: Int): Boolean {
        if (!ready || closed.get()) return false
        val token = generation.incrementAndGet()
        pending.set(null)
        hasFrame = false
        return handler.post {
            try {
                val renderer = renderer ?: return@post
                if (token != generation.get() || closed.get()) return@post
                renderer.releasePages()
                trace("iRead.curl.upload") {
                    renderer.upload(capture.current.bitmap, capture.target.bitmap)
                }
                if (token != generation.get() || closed.get()) return@post
                renderer.bindSurface(surface, width, height)
                lastScene = null
                activeGeneration = token
                if (pending.get() != null) schedule()
            } catch (error: RuntimeException) {
                fail(error)
            } catch (_: OutOfMemoryError) {
                fail(EffectResourceUnavailable())
            } finally {
                capture.close()
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
                renderer?.releasePages()
                lastScene = null
                activeGeneration = -1L
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
        handler.post {
            try {
                runCatching { renderer?.close() }
                renderer = null
            } finally {
                surface.release()
                thread.quitSafely()
            }
        }
    }
}
