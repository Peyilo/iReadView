package org.peyilo.ireadview.internal.effect

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.spi.presentation.ReadViewport
import kotlin.math.abs

/** One GPU material map for paper, text and shadows. The Widget owns navigation and time. */
internal class BoundPageCurlEffect(private val options: PageCurlOptions) : SnapshotPageTurnEffect {
    override val usesPageSnapshots get() = output != null || runtimeActive
    private var host: PageTurnEffectHost? = null
    private var turn: PageTurnContext? = null
    private var renderer: BoundPageCurlRenderer? = null
    private var runtime: BoundPageCurlRuntime? = null
    private var runtimeActive = false
    private var output: BitmapLease? = null
    private val snapshots = PageTurnSnapshots()
    private val rect = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var backTint = android.graphics.Color.WHITE
    override fun attach(host: PageTurnEffectHost) {
        this.host = host
        if (android.os.Build.VERSION.SDK_INT >= 33 && (host as? HardwarePageTurnEffectHost)?.hardwareAccelerated == true) {
            runtime = try { BoundPageCurlRuntime() } catch (failure: Exception) {
                android.util.Log.w("iReadPageCurl", "Runtime shader unavailable; using GLES", failure); null
            }
        }
    }
    override fun supports(direction: TurnDirection, viewport: ReadViewport) = viewport.isValid
    override fun start(context: PageTurnContext) {
        clear()
        turn = context
        val owner = host ?: throw EffectResourceUnavailable()
        rect.set(0f,0f,context.viewport.widthPx.toFloat(),context.viewport.heightPx.toFloat())
        try {
            if (!snapshots.capture(owner,context)) throw EffectResourceUnavailable()
            val sheet = if (context.direction == TurnDirection.Forward) context.current else context.target
            val sheetBitmap = if (context.direction == TurnDirection.Forward) snapshots.current else snapshots.target
            backTint = options.backTintColor ?: PageBackTint.resolve(sheet, requireNotNull(sheetBitmap))
            runtime?.let { gpu ->
                gpu.bind(if(context.direction==TurnDirection.Forward) requireNotNull(snapshots.current) else requireNotNull(snapshots.target),
                    if(context.direction==TurnDirection.Forward) requireNotNull(snapshots.target) else requireNotNull(snapshots.current),
                    context.viewport.widthPx,context.viewport.heightPx)
                runtimeActive = true
                return
            }
            renderer = BoundPageCurlRenderer()
            requireNotNull(renderer).upload(requireNotNull(snapshots.current),requireNotNull(snapshots.target))
            // There is no extra detail to recover above the captured texture resolution.
            // Avoid a larger per-frame GPU render/readback followed by another CPU upload.
            var width = requireNotNull(snapshots.current).width
            var height = requireNotNull(snapshots.current).height
            // GPU snapshots are immutable. Return CPU leases before borrowing the output:
            // the pool can reuse a full-size page without three simultaneous CPU bitmaps.
            snapshots.close()
            while (output == null && width > 0 && height > 0) {
                output = owner.borrowBitmap(width,height)
                if (output == null) { width /= 2; height /= 2 }
            }
            requireNotNull(renderer).bindOutput(output?.bitmap ?: throw EffectResourceUnavailable())
        } catch (_: OutOfMemoryError) { clear(); throw EffectResourceUnavailable() }
        catch (error: Throwable) { clear(); throw error }
    }
    override fun update(frame: PageTurnFrame) {
        val context = turn ?: return
        val origin = context.touchOrigin
        val touch = frame.touch
        val tilt = if (origin != null && touch != null)
            (options.tilt+(touch.yPx-origin.yPx)/(abs(touch.xPx-origin.xPx)+.2f*rect.width())).coerceIn(-.8f,.8f)
            else options.tilt
        val forward = context.direction == TurnDirection.Forward
        if (runtimeActive) {
            runtime?.update(if(forward)frame.progress else 1f-frame.progress,rect.width()/rect.height(),tilt,
                context.viewport.rightToLeft,options,backTint)
            return
        }
        renderer?.render(if(forward)frame.progress else 1f-frame.progress,
            rect.width()/rect.height(),tilt,context.viewport.rightToLeft,forward,options,backTint)
    }
    override fun draw(canvas: Canvas, frame: PageTurnFrame) {
        if (frame.progress <= 0f) { turn?.current?.draw(canvas); return }
        if (frame.progress >= 1f) { turn?.target?.draw(canvas); return }
        if (runtimeActive) { runtime?.draw(canvas); return }
        val bitmap = output?.bitmap ?: return
        val save = canvas.save()
        try {
            canvas.translate(0f,rect.height()); canvas.scale(1f,-1f)
            canvas.drawBitmap(bitmap,null,rect,paint)
        } finally { canvas.restoreToCount(save) }
    }
    private fun clear() {
        runtime?.clearPages(); runtimeActive = false
        try { renderer?.close() } finally {
            renderer=null
            try { output?.close() } finally { output=null; snapshots.close(); turn=null }
        }
    }
    override fun finish(outcome: TurnOutcome) { clear() }
    override fun abort(reason: TurnAbortReason) { clear() }
    override fun detach() { try { clear() } finally { runtime=null; host=null } }
}
