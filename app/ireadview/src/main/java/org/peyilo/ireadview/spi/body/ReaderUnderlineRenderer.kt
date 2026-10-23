package org.peyilo.ireadview.spi.body

import android.content.Context
import android.graphics.Canvas
import org.peyilo.ireadview.api.style.TextLineStyle
import org.peyilo.ireadview.spi.content.TextLineSegment

/** Body-owned line decoration renderer, shared by native text and document bodies.
 * Main-thread callbacks. Segments are borrowed, body-local physical pixels; do not mutate
 * or retain them. Density converts style dp to pixels. Keep the supplied geometry so
 * decorations continue to agree with selection and hit testing.
 * close is final disposal, never a temporary detach or content rebind; it must not throw.
 */
interface ReaderUnderlineRenderer : AutoCloseable {
    fun draw(canvas: Canvas, segments: List<TextLineSegment>, style: TextLineStyle, density: Float)
    override fun close() {}
}

/** Return a fresh instance for each body; cached pages have separate ownership. */
fun interface ReaderUnderlineRendererFactory {
    fun create(context: Context): ReaderUnderlineRenderer
}
