package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import org.peyilo.ireadview.internal.effect.EffectResourceUnavailable
import org.peyilo.ireadview.internal.effect.PageTurnSnapshots

/** Keeps the consumer draining while parked; only current-turn pixels become visible. */
internal class IosCurlGpuLayer(
    context: Context,
    private val invalidate: () -> Unit,
    private val initialCaptureSize: (Int, Int) -> PageTurnSnapshots.Size?,
) : TextureView.SurfaceTextureListener, IosCurlCompositor {
    private var active = false
    private var presented = false
    private var beganAt = 0L
    override val view =
        object : FrameLayout(context) {
            override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
                if (!active || !presented) {
                    // ViewGroup skips children in an empty layer. Invoke TextureView directly
                    // so its consumer can prepare/acquire buffers while its pixels stay clipped.
                    val saved = canvas.save()
                    try {
                        canvas.clipRect(0, 0, 0, 0)
                        child.draw(canvas)
                    } finally {
                        canvas.restoreToCount(saved)
                    }
                    acceptFrame()
                }
                return if (active && presented) super.drawChild(canvas, child, drawingTime)
                else true
            }
        }
    private val texture =
        TextureView(context).apply {
            isOpaque = false
            surfaceTextureListener = this@IosCurlGpuLayer
        }
    private var worker: IosCurlGpuWorker? = null
    private var surfaceFailure: RuntimeException? = null
    override val ready
        get() = worker?.ready == true && texture.isAvailable

    override val hasFrame
        get() = active && presented

    override fun prepareCapture(width: Int, height: Int) =
        ready && worker?.prepareCapture(width, height) == true

    init {
        view.addView(texture, FrameLayout.LayoutParams(-1, -1))
    }

    override fun begin(input: IosCurlPageInput): Boolean {
        texture.isOpaque = input.opaque
        beganAt = System.nanoTime()
        texture.surfaceTexture?.setDefaultBufferSize(input.width, input.height)
        active = true
        presented = false
        view.invalidate()
        val accepted = worker?.begin(input) == true
        if (!accepted) active = false
        return accepted
    }

    override fun submit(scene: IosCurlScene) {
        worker?.submit(scene)
    }

    override fun checkFailure() {
        (surfaceFailure ?: worker?.failure)?.let { throw it }
    }

    override fun end() {
        active = false
        presented = false
        view.invalidate()
        worker?.end()
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val surface = Surface(texture)
        try {
            surfaceFailure = null
            worker =
                IosCurlGpuWorker(
                    surface,
                    width,
                    height,
                    invalidate,
                    initialCaptureSize = initialCaptureSize(width, height),
                )
        } catch (error: RuntimeException) {
            surface.release()
            Log.w("iReadView", "GPU curl compositor unavailable; using bitmap output", error)
        } catch (_: OutOfMemoryError) {
            surface.release()
        }
    }

    private fun acceptFrame() {
        if (!active || presented || worker?.hasFrame != true) return
        if ((texture.surfaceTexture?.timestamp ?: 0L) >= beganAt) {
            presented = true
            view.invalidate()
            invalidate()
        } else
            view.postOnAnimation {
                if (active && !presented) {
                    view.invalidate()
                    invalidate()
                }
            }
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        acceptFrame()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) =
        Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        worker?.close()
        worker = null
        presented = false
        if (active) {
            surfaceFailure = EffectResourceUnavailable()
            invalidate()
        }
        return true
    }

    override fun close() {
        active = false
        presented = false
        texture.surfaceTextureListener = null
        worker?.close()
        worker = null
    }
}
