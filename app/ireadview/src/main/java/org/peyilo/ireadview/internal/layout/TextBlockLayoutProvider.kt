package org.peyilo.ireadview.internal.layout

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.style.TitleAlignment
import org.peyilo.ireadview.spi.content.*

internal class TextBlockLayoutProvider : BlockLayoutProvider {
    override val id = "iread.text"
    override val version = "2"
    override fun supports(block: ContentBlock): Boolean = block is TextContentBlock

    @Suppress("DEPRECATION")
    override fun measure(block: ContentBlock, context: BlockLayoutContext): BlockLayout {
        context.checkCancellation()
        val heading = block is HeadingBlock
        val text = (block as? TextContentBlock)?.text ?: error("Unsupported text block")
        val style = context.style
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = if (heading) context.titleTextSizePx else context.bodyTextSizePx
            color = if (heading) style.title.textColor else style.body.textColor
            typeface = if (heading) style.title.font ?: Typeface.DEFAULT_BOLD else style.body.font ?: Typeface.DEFAULT
            letterSpacing = if (heading) style.title.letterSpacingEm else style.body.letterSpacingEm
        }
        val source = if (!heading && text.isNotEmpty() && style.body.firstLineIndentEm > 0) {
            SpannableString(text).apply {
                setSpan(LeadingMarginSpan.Standard((style.body.firstLineIndentEm * paint.textSize).toInt(), 0), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else text
        val alignment = if (!heading) Layout.Alignment.ALIGN_NORMAL else when (style.title.alignment) {
            TitleAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            TitleAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TitleAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val spacing = if (heading) 1.2f else style.body.lineSpacingMultiplier
        val extra = if (heading) 0f else style.body.lineSpacingExtraDp * context.viewport.density
        val width = context.viewport.widthPx.coerceAtLeast(1)
        context.checkCancellation()
        val layout = if (Build.VERSION.SDK_INT >= 23) {
            StaticLayout.Builder.obtain(source, 0, source.length, paint, width)
                .setAlignment(alignment).setIncludePad(false).setLineSpacing(extra, spacing)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build()
        } else {
            StaticLayout(source, paint, width, alignment, spacing, extra, false)
        }
        context.checkCancellation()
        val gap = (if (heading) style.title.spacingAfterDp else style.body.paragraphSpacingDp) * context.viewport.density
        val units = ArrayList<BlockLayoutUnit>(layout.lineCount)
        for (line in 0 until layout.lineCount) {
            context.checkCancellation()
            units.add(BlockLayoutUnit((layout.getLineBottom(line) - layout.getLineTop(line)).toFloat().coerceAtLeast(1f),
                layout.getLineStart(line), layout.getLineEnd(line)))
        }
        val frozenUnits = immutableList(units)
        context.checkCancellation()
        return TextBlockLayout(block.blockId, layout, gap, heading, frozenUnits)
    }
}

private class TextBlockLayout(
    override val blockId: String,
    private val layout: StaticLayout,
    override val spacingAfterPx: Float,
    private val heading: Boolean,
    override val units: List<BlockLayoutUnit>
) : BlockLayout {
    override val estimatedRetainedBytes: Long = 1536L + layout.text.length.toLong() * 2 + layout.lineCount.toLong() * 128

    override fun fragment(firstUnitIndex: Int, endUnitExclusive: Int): BlockLayoutFragment {
        require(firstUnitIndex in units.indices && endUnitExclusive in (firstUnitIndex + 1)..units.size)
        val top = layout.getLineTop(firstUnitIndex)
        val bottom = layout.getLineBottom(endUnitExclusive - 1)
        return object : BlockLayoutFragment {
            override val accessibilityText: CharSequence
                get() = layout.text.subSequence(startOffset ?: 0, endOffset ?: 0).toString()
            override val accessibilityHeading get() = heading
            override fun accessibilityTextForVerticalRange(topPx: Float, bottomPx: Float): CharSequence? {
                val range = accessibilityRangeForVerticalRange(topPx, bottomPx) ?: return null
                return layout.text.subSequence(range.startOffset, range.endExclusive).toString()
            }
            override fun accessibilityRangeForVerticalRange(topPx: Float, bottomPx: Float): BlockTextRange? {
                val fromY = topPx.coerceIn(0f, heightPx)
                val toY = bottomPx.coerceIn(0f, heightPx)
                if (fromY >= toY) return null
                val first = layout.getLineForVertical((top + fromY).toInt()).coerceIn(firstUnitIndex, endUnitExclusive - 1)
                val last = layout.getLineForVertical(kotlin.math.ceil(top + toY).toInt() - 1).coerceIn(first, endUnitExclusive - 1)
                return BlockTextRange(blockId, layout.getLineStart(first), layout.getLineEnd(last))
            }
            override val blockId = this@TextBlockLayout.blockId
            override val startOffset = units[firstUnitIndex].startOffset
            override val endOffset = units[endUnitExclusive - 1].endOffset
            override val heightPx = (bottom - top).toFloat()
            override fun caretForOffset(offset: Int, endExclusive: Boolean): TextCaret? {
                val first = this.startOffset ?: return null
                val end = this.endOffset ?: return null
                if (offset < first || offset > end || offset == first && endExclusive && first != 0 || offset == end && !endExclusive && end != layout.text.length) return null
                var line = layout.getLineForOffset(offset)
                var x = layout.getPrimaryHorizontal(offset)
                if (endExclusive && offset > 0 && layout.getLineStart(line) == offset && line > 0) {
                    line--
                    x = if (layout.getParagraphDirection(line) > 0) layout.getLineRight(line) else layout.getLineLeft(line)
                }
                if (line !in firstUnitIndex until endUnitExclusive) return null
                return TextCaret(x, (layout.getLineTop(line) - top).toFloat(), (layout.getLineBottom(line) - top).toFloat())
            }
            override fun anchorAt(xPx: Float, yPx: Float): ContentAnchor? {
                if (yPx < 0 || yPx >= heightPx) return null
                val line = layout.getLineForVertical((top + yPx).toInt())
                if (xPx < minOf(layout.getLineLeft(line), layout.getLineRight(line)) || xPx > maxOf(layout.getLineLeft(line), layout.getLineRight(line))) return null
                return ContentAnchor(blockId, layout.getOffsetForHorizontal(line, xPx).coerceIn(this.startOffset ?: 0, this.endOffset ?: 0))
            }
            override fun nearestAnchorAt(xPx: Float, yPx: Float): ContentAnchor? {
                val y = yPx.coerceIn(0f, maxOf(0f, heightPx - 1f))
                val line = layout.getLineForVertical((top + y).toInt())
                val x = xPx.coerceIn(minOf(layout.getLineLeft(line), layout.getLineRight(line)), maxOf(layout.getLineLeft(line), layout.getLineRight(line)))
                return ContentAnchor(blockId, layout.getOffsetForHorizontal(line, x).coerceIn(this.startOffset ?: 0, this.endOffset ?: 0))
            }
            override fun anchorForVerticalOffset(yPx: Float): ContentAnchor {
                val line = layout.getLineForVertical((top + yPx.coerceIn(0f, maxOf(0f, heightPx - 1f))).toInt())
                return ContentAnchor(blockId, layout.getLineStart(line))
            }
            override fun selectionPath(startOffset: Int, endExclusive: Int): Path? {
                val from = maxOf(startOffset, this.startOffset ?: 0)
                val to = minOf(endExclusive, this.endOffset ?: 0)
                if (from >= to) return null
                return Path().apply {
                    layout.getSelectionPath(from, to, this)
                    offset(0f, -top.toFloat())
                }
            }
            override fun draw(canvas: Canvas) {
                val save = canvas.save()
                canvas.clipRect(0f, 0f, layout.width.toFloat(), heightPx)
                canvas.translate(0f, -top.toFloat())
                layout.draw(canvas)
                canvas.restoreToCount(save)
            }
        }
    }
}
