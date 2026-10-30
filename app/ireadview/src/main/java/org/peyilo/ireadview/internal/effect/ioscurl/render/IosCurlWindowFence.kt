package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi

/**
 * A one-pixel copy fences the restored parent buffer, not an entire page readback. Keep the final
 * GL image until the actual parent draw has reached its GPU surface.
 */
@RequiresApi(26)
internal class IosCurlWindowFence(private val view: View) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private var epoch = 0L
    private var cleanup: (() -> Unit)? = null
    val available
        get() = window() != null

    private fun window(): Window? {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) {
            val base = context.baseContext
            if (base === context) return null
            context = base
        }
        return (context as? Activity)?.window?.takeIf { it.decorView === view.rootView }
    }

    fun afterDraw(done: () -> Unit) {
        cancel()
        val owner = window()
        if (owner == null || !view.isAttachedToWindow) {
            done()
            return
        }
        val token = epoch
        val clock = Choreographer.getInstance()
        var frameTime = Long.MAX_VALUE
        val draw = Choreographer.FrameCallback { time ->
            frameTime = time
            view.rootView.invalidate()
        }
        val metrics = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            if (token == epoch && frame.getMetric(FrameMetrics.VSYNC_TIMESTAMP) >= frameTime) {
                if (frame.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION) <= 0L) {
                    view.rootView.postInvalidateOnAnimation()
                } else {
                    cleanup?.invoke()
                    cleanup = null
                    val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    try {
                        PixelCopy.request(
                            owner,
                            Rect(0, 0, 1, 1),
                            pixel,
                            { status ->
                                pixel.recycle()
                                if (token == epoch) {
                                    if (status == PixelCopy.SUCCESS || !view.isAttachedToWindow)
                                        done()
                                    else afterDraw(done)
                                }
                            },
                            main,
                        )
                    } catch (_: IllegalArgumentException) {
                        pixel.recycle()
                        if (token == epoch) done()
                    }
                }
            }
        }
        cleanup = {
            owner.removeOnFrameMetricsAvailableListener(metrics)
            clock.removeFrameCallback(draw)
        }
        owner.addOnFrameMetricsAvailableListener(metrics, main)
        clock.postFrameCallback(draw)
    }

    fun cancel() {
        epoch++
        cleanup?.invoke()
        cleanup = null
    }

    override fun close() = cancel()
}
