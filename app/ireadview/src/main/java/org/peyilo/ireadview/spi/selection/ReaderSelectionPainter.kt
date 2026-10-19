package org.peyilo.ireadview.spi.selection

import android.graphics.*
import org.peyilo.ireadview.api.style.SelectionStyle

data class ReaderSelectionHandle(val x: Float, val y: Float, val caretX: Float,
    val caretTop: Float, val caretBottom: Float, val end: Boolean)

/** The same Canvas fill, stem, dot, hit target and grab offset for every body renderer.
 * Bodies draw active fill after text and before persistent lines; handles draw outside the text clip.
 */
open class ReaderSelectionPainter : ReaderSelectionRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun createHandle(x: Float, top: Float, bottom: Float, end: Boolean, width: Int, height: Int,
        style: SelectionStyle, density: Float, clampDots: Boolean): ReaderSelectionHandle {
        val radius = style.handleRadiusDp * density
        fun fit(value: Float, extent: Int) = if (clampDots && extent > radius * 2) value.coerceIn(radius, extent - radius) else value
        return ReaderSelectionHandle(fit(x, width), fit(if (end) bottom + radius else top - radius, height), x, top, bottom, end)
    }
    override fun drawFill(canvas: Canvas, path: Path, style: SelectionStyle) {
        paint.color = style.fillColor; canvas.drawPath(path, paint)
    }
    override fun drawHandles(canvas: Canvas, handles: List<ReaderSelectionHandle>, style: SelectionStyle, density: Float, width: Int) {
        paint.color = style.handleColor
        val radius = style.handleRadiusDp * density
        handles.forEach { handle ->
            val stroke = density * 1.2f
            val outsideLeft = if (handle.end) handle.caretX else handle.caretX - stroke
            val left = outsideLeft.coerceIn(0f, maxOf(0f, width - stroke))
            canvas.drawRect(left, minOf(handle.caretTop, handle.y), left + stroke, maxOf(handle.caretBottom, handle.y), paint)
            if (handle.x != handle.caretX) canvas.drawRect(minOf(handle.x, handle.caretX), handle.y - stroke / 2f,
                maxOf(handle.x, handle.caretX), handle.y + stroke / 2f, paint)
            canvas.drawCircle(handle.x, handle.y, radius, paint)
        }
    }
    override fun handleAt(handles: List<ReaderSelectionHandle>, x: Float, y: Float, style: SelectionStyle, density: Float): Boolean? {
        val radius = maxOf(24f, style.handleRadiusDp * 2f) * density
        return handles.map { it to kotlin.math.hypot(x - it.x, y - it.y) }
            .filter { it.second <= radius }.minByOrNull { it.second }?.first?.let { !it.end }
    }
    override fun dragOffset(handle: ReaderSelectionHandle, x: Float, y: Float) =
        PointF(handle.caretX - x, (handle.caretTop + handle.caretBottom) / 2f - y)
    override fun handleBounds(handle: ReaderSelectionHandle, style: SelectionStyle, density: Float): RectF {
        val radius = maxOf(24f, style.handleRadiusDp * 2f) * density
        return RectF(handle.x - radius, handle.y - radius, handle.x + radius, handle.y + radius)
    }
}
