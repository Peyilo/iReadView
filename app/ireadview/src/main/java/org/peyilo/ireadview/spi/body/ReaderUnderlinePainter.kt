package org.peyilo.ireadview.spi.body

import android.graphics.Canvas
import org.peyilo.ireadview.api.style.TextLineStyle
import org.peyilo.ireadview.spi.content.TextLineSegment

/** Shared line decoration geometry for native and document bodies. Main-thread, one instance per body. */
class ReaderUnderlinePainter {
    private val painter = org.peyilo.ireadview.internal.render.UnderlinePainter()
    fun draw(canvas: Canvas, segments: List<TextLineSegment>, style: TextLineStyle, density: Float) =
        painter.draw(canvas, segments, style, density)
}
