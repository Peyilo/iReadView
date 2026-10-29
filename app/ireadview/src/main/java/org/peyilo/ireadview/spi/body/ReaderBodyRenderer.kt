package org.peyilo.ireadview.spi.body

import android.content.Context
import android.graphics.Canvas
import org.peyilo.ireadview.spi.content.BlockLayoutFragment

/** Replaces/decorates content drawing, independently of native active-selection overlays.
 * drawDefault draws backend content into the supplied Canvas and is valid only during draw.
 * Keep layout/coordinate geometry unchanged here; a different layout/engine belongs in
 * BookPresentationFactory with its own ReaderBodyView. Main-thread, one instance per body.
 */
interface ReaderBodyRenderer : AutoCloseable {
    fun draw(canvas: Canvas, body: ReaderBodyView, drawDefault: (Canvas) -> Unit)
    override fun close() {}
}

fun interface ReaderBodyRendererFactory {
    fun create(context: Context): ReaderBodyRenderer
}

/** Optional native-block capability, without requiring document bodies to invent fragments. */
interface BlockReaderBodyContent {
    val fragments: List<BlockLayoutFragment>
}

object DefaultReaderBodyRenderer : ReaderBodyRenderer {
    override fun draw(canvas: Canvas, body: ReaderBodyView, drawDefault: (Canvas) -> Unit) =
        drawDefault(canvas)
}
