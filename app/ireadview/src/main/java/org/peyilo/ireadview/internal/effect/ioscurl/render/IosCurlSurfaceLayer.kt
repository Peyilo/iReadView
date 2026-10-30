package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.content.Context
import android.graphics.PixelFormat
import android.os.Parcel
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import org.peyilo.ireadview.internal.effect.EffectResourceUnavailable

/**
 * Direct output for fixed reader rectangles. The live page stays beneath the opaque surface, so
 * buffer presentation/clearing cannot expose an empty parent window.
 */
@RequiresApi(26)
internal class IosCurlSurfaceLayer(
    context: Context,
    private val invalidate: () -> Unit,
) : IosCurlCompositor, SurfaceHolder.Callback {
    private var pending: IosCurlPageInput? = null
    private var scene: IosCurlScene? = null
    private var worker: IosCurlGpuWorker? = null
    private var active = false
    private var closed = false
    private var width = 0
    private var height = 0
    private var failure: RuntimeException? = null
    private var epoch = 0L
    override val replacesLiveContent = false
    override val view =
        SurfaceView(context).apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(this@IosCurlSurfaceLayer)
        }
    private val fence = IosCurlWindowFence(view)
    override val ready
        get() =
            view.isHardwareAccelerated &&
                fence.available &&
                !closed &&
                failure == null &&
                worker?.failure == null

    override val hasFrame
        get() = active && worker?.hasFrame == true

    override fun prepareCapture() = ready && worker?.prepareCapture() == true

    override fun begin(input: IosCurlPageInput): Boolean {
        if (!ready || !input.opaque) return false
        epoch++
        fence.cancel()
        pending?.close()
        pending = input
        active = true
        scene = null
        view.holder.setFixedSize(input.width, input.height)
        startPending()
        return true
    }

    private fun startPending() {
        val capture = pending ?: return
        val current = worker ?: return
        if (!current.ready || capture.width != width || capture.height != height) return
        if (!current.begin(capture)) return
        pending = null
        scene?.let(current::submit)
    }

    override fun submit(scene: IosCurlScene) {
        if (!active) return
        this.scene = scene
        startPending()
        if (pending == null) worker?.submit(scene)
    }

    override fun checkFailure() {
        (failure ?: worker?.failure)?.let { throw it }
    }

    override fun end() {
        if (!active && pending == null) return
        val finalScene = scene
        active = false
        scene = null
        pending?.close()
        pending = null
        val token = ++epoch
        worker?.end(finalScene)
        fence.afterDraw { if (token == epoch && !active && !closed) worker?.clearSurface() }
    }

    private fun onWorkerEvent() {
        val error = worker?.failure ?: return
        failure = error
        view.visibility = android.view.View.INVISIBLE
        invalidate()
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        this.width = width
        this.height = height
        if (worker == null && !closed) {
            val parcel = Parcel.obtain()
            val owned =
                try {
                    holder.surface.writeToParcel(parcel, 0)
                    parcel.setDataPosition(0)
                    Surface.CREATOR.createFromParcel(parcel)
                } finally {
                    parcel.recycle()
                }
            try {
                worker =
                    IosCurlGpuWorker(
                        owned,
                        width,
                        height,
                        ::onWorkerEvent,
                        onReady = { if (!closed) startPending() },
                    )
            } catch (error: RuntimeException) {
                owned.release()
                failure = error
                invalidate()
            } catch (_: OutOfMemoryError) {
                owned.release()
                failure = EffectResourceUnavailable()
                invalidate()
            }
        }
        startPending()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        epoch++
        fence.cancel()
        worker?.close()
        worker = null
        width = 0
        height = 0
        if (active) {
            failure = EffectResourceUnavailable()
            invalidate()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        active = false
        epoch++
        fence.close()
        pending?.close()
        pending = null
        scene = null
        view.holder.removeCallback(this)
        worker?.close()
        worker = null
    }
}
