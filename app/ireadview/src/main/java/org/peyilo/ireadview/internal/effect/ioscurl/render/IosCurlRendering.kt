package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.View
import org.peyilo.ireadview.api.effect.BitmapLease
import org.peyilo.ireadview.api.effect.IosCurlOutput
import org.peyilo.ireadview.api.effect.PageTurnContext
import org.peyilo.ireadview.api.effect.PageTurnEffectHost
import org.peyilo.ireadview.api.effect.RecordedPageSurface
import org.peyilo.ireadview.internal.effect.EffectResourceUnavailable
import org.peyilo.ireadview.internal.effect.PageTurnSnapshots
import org.peyilo.ireadview.internal.effect.SnapshotBudgetHost
import org.peyilo.pagecurl.mesh.CurlMesh

/** Captures page pixels once; geometry and presentation run on the EGL owner. */
internal class IosCurlRendering(private val output: IosCurlOutput) : AutoCloseable {
    private var gpuLayer: IosCurlCompositor? = null
    private var bitmapRenderer: IosCurlRenderer? = null
    private val bitmapMesh = CurlMesh()
    private var bitmap: BitmapLease? = null
    private var gpu = false
    private var firstFrame: IosCurlFirstFrameTrace? = null

    fun beginPreparation() {
        firstFrame?.cancel()
        firstFrame = IosCurlFirstFrameTrace()
    }

    private val bounds = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    val usesGpu
        get() = gpu

    val hasFrame
        get() = if (gpu) gpuLayer?.hasFrame == true else bitmap != null

    val ready
        get() =
            if (gpu) gpuLayer?.hasFrame == true && gpuLayer?.replacesLiveContent == true
            else bitmap != null

    val canAccelerate
        get() = gpuLayer?.ready == true

    private fun renderer() = bitmapRenderer ?: IosCurlRenderer().also { bitmapRenderer = it }

    fun capture(host: PageTurnEffectHost, context: PageTurnContext): Boolean {
        val layer = gpuLayer ?: return false
        val budget = (host as? SnapshotBudgetHost)?.snapshotBudgetBytes ?: return false
        if (
            listOf(context.current, context.target).any {
                !it.isOpaque ||
                    (Build.VERSION.SDK_INT < 29 && (it as? RecordedPageSurface)?.isRecorded != true)
            }
        )
            return false
        val viewport = context.viewport
        val size =
            PageTurnSnapshots.sizeWithinBudget(viewport.widthPx, viewport.heightPx, budget)
                ?: return false
        if (!layer.prepareCapture(size.width, size.height)) return false
        gpu = layer.begin(IosCurlPageInput.Canvas(context, size.width, size.height, firstFrame))
        return gpu
    }

    fun createView(context: Context, captureBudget: () -> Long, invalidate: () -> Unit): View {
        val initialSize = { width: Int, height: Int ->
            if (Build.VERSION.SDK_INT >= 29)
                PageTurnSnapshots.sizeWithinBudget(width, height, captureBudget())
            else null
        }
        return (if (output == IosCurlOutput.Surface && Build.VERSION.SDK_INT >= 29)
                IosCurlSurfaceLayer(context, invalidate, initialSize)
            else IosCurlGpuLayer(context, invalidate, initialSize))
            .also { gpuLayer = it }
            .view
    }

    fun upload(
        current: Bitmap,
        target: Bitmap,
        retained: PageTurnSnapshots.Retained?,
        opaque: Boolean,
    ) {
        if (retained != null) {
            try {
                gpu = gpuLayer?.begin(IosCurlPageInput.Pixels(retained, opaque, firstFrame)) == true
            } finally {
                if (!gpu) retained.close()
            }
            if (gpu) return
        }
        renderer().upload(current, target)
    }

    fun bindOutput(
        host: PageTurnEffectHost,
        width: Int,
        height: Int,
        viewWidth: Int,
        viewHeight: Int,
    ) {
        bounds.set(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        if (gpu) return
        var w = width
        var h = height
        while (bitmap == null && w > 0 && h > 0) {
            bitmap = host.borrowBitmap(w, h)
            if (bitmap == null) {
                w /= 2
                h /= 2
            }
        }
        renderer().bindOutput(bitmap?.bitmap ?: throw EffectResourceUnavailable())
    }

    fun render(scene: IosCurlScene) {
        if (gpu) gpuLayer?.submit(scene)
        else {
            scene.draw(bitmapMesh, renderer())
            firstFrame?.submitted()
        }
    }

    fun draw(canvas: Canvas) {
        if (gpu) {
            gpuLayer?.checkFailure()
            return
        }
        val bitmap = bitmap?.bitmap ?: return
        val saved = canvas.save()
        try {
            canvas.translate(0f, bounds.height())
            canvas.scale(1f, -1f)
            canvas.drawBitmap(bitmap, null, bounds, paint)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    fun end() {
        firstFrame?.cancel()
        firstFrame = null
        gpuLayer?.end()
        try {
            bitmapRenderer?.releasePages()
        } finally {
            gpu = false
            bitmap?.close()
            bitmap = null
        }
    }

    override fun close() {
        try {
            end()
        } finally {
            gpuLayer?.close()
            gpuLayer = null
            bitmapRenderer?.close()
            bitmapRenderer = null
        }
    }
}
