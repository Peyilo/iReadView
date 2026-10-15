package org.peyilo.ireadview.internal.render

import android.graphics.*
import org.peyilo.ireadview.api.style.*
import org.peyilo.ireadview.spi.content.TextLineSegment
import kotlin.math.*

/** Draw line marks at each selected line's bottom, never as a background fill. */
internal class UnderlinePainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    fun draw(canvas: Canvas, segments: List<TextLineSegment>, style: TextLineStyle, density: Float) {
        val stroke = style.thicknessDp * density
        paint.color = style.color; paint.strokeWidth = stroke
        paint.strokeCap = if (style.pattern == TextLinePattern.DOTTED) Paint.Cap.ROUND else Paint.Cap.BUTT
        paint.pathEffect = when (style.pattern) {
            TextLinePattern.DASHED -> DashPathEffect(floatArrayOf(style.dashLengthDp * density, style.gapDp * density), 0f)
            TextLinePattern.DOTTED -> DashPathEffect(floatArrayOf(maxOf(0.1f, stroke * 0.01f), (style.gapDp + style.thicknessDp) * density), 0f)
            else -> null
        }
        segments.forEach { segment ->
            val rect = RectF(segment.startXPx, 0f, segment.endXPx, segment.bottomYPx)
            val bottom = rect.bottom - stroke / 2f
            when (style.pattern) {
                TextLinePattern.WAVY -> {
                    val amplitude = style.waveAmplitudeDp * density
                    val y = bottom - amplitude
                    val wavelength = style.waveLengthDp * density
                    val count = ceil(rect.width() / maxOf(density, wavelength / 8f)).toInt().coerceIn(1, 4096)
                    path.reset()
                    for (step in 0..count) {
                        val x = rect.left + rect.width() * step / count
                        val waveY = y + sin((x - rect.left) * 2 * PI / wavelength).toFloat() * amplitude
                        if (step == 0) path.moveTo(x, waveY) else path.lineTo(x, waveY)
                    }
                    canvas.drawPath(path, paint)
                }
                TextLinePattern.DOUBLE -> {
                    canvas.drawLine(rect.left, bottom, rect.right, bottom, paint)
                    canvas.drawLine(rect.left, bottom - stroke - style.gapDp * density, rect.right, bottom - stroke - style.gapDp * density, paint)
                }
                else -> canvas.drawLine(rect.left, bottom, rect.right, bottom, paint)
            }
        }
        paint.pathEffect = null
    }
}
