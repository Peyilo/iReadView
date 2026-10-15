package org.peyilo.iread.demo.content

import android.graphics.Canvas
import android.graphics.Paint
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.api.model.ContentBlock
import org.peyilo.ireadview.spi.content.*

/** A worker-safe provider using only the public content SPI; no Views or Activity references. */
internal class DividerLayoutProvider : BlockLayoutProvider {
    override val id = "demo.divider"
    override val version = "1"
    override fun supports(block: ContentBlock) = block is DividerBlock

    override fun measure(block: ContentBlock, context: BlockLayoutContext): BlockLayout {
        context.checkCancellation()
        val divider = block as DividerBlock
        val density = context.viewport.density
        val width = context.viewport.widthPx.toFloat()
        val height = 24f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.style.body.textColor
            strokeWidth = density
            alpha = 100
        }
        return object : BlockLayout {
            override val blockId = divider.blockId
            override val estimatedRetainedBytes = 1280L
            override val units = listOf(BlockLayoutUnit(height, null, null))
            override val spacingAfterPx = 0f
            override fun fragment(firstUnitIndex: Int, endUnitExclusive: Int): BlockLayoutFragment {
                require(firstUnitIndex == 0 && endUnitExclusive == 1)
                return object : BlockLayoutFragment {
                    override val blockId = divider.blockId
                    override val startOffset: Int? = null
                    override val endOffset: Int? = null
                    override val heightPx = height
                    override val accessibilityText = divider.description
                    override val accessibilityDescribesWholeBlock = true
                    override fun anchorForVerticalOffset(yPx: Float) = ContentAnchor(blockId, 0)
                    override fun draw(canvas: Canvas) {
                        val center = width / 2f
                        val gap = minOf(8f * density, width / 8f)
                        val inset = width / 8f
                        val y = height / 2f
                        canvas.drawLine(inset, y, center - gap, y, paint)
                        canvas.drawCircle(center, y, minOf(2f * density, gap / 2f), paint)
                        canvas.drawLine(center + gap, y, width - inset, y, paint)
                    }
                }
            }
        }
    }
}
