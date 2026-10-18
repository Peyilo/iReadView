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
            val fixedSpacing = if (heading) style.title.letterSpacingDp else style.body.letterSpacingDp
            if (fixedSpacing != null) letterSpacing = fixedSpacing * context.viewport.density / textSize
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
        val spacing = if (heading) style.title.lineSpacingMultiplier else style.body.lineSpacingMultiplier
        val extra = (if (heading) style.title.lineSpacingExtraDp else style.body.lineSpacingExtraDp) * context.viewport.density
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

    private fun offsetForHorizontal(line: Int, x: Float): Int {
        val offset = layout.getOffsetForHorizontal(line, x)
        val end = layout.getLineVisibleEnd(line)
        val start = layout.getLineStart(line)
        if (end <= start) return offset
        val rtl = layout.getParagraphDirection(line) < 0
        // A mixed-direction trailing run has its own visual boundary; leave its
        // hit testing to Layout rather than assuming the paragraph's outer edge.
        if (layout.isRtlCharAt(end - 1) != rtl) return offset
        val edge = if (rtl) layout.getLineLeft(line) else layout.getLineRight(line)
        // Layout may exclude the soft-wrap offset, since it is also the next
        // line's start. Explicitly expose that exclusive endpoint on this line.
        val last = layout.getOffsetForHorizontal(line, edge)
        if (last !in start until end) return offset
        val lastX = layout.getPrimaryHorizontal(last)
        val midpoint = (lastX + edge) / 2f
        return if (if (rtl) x <= midpoint else x >= midpoint) end else offset
    }

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
                val metrics = layout.paint.fontMetrics
                val baseline = layout.getLineBaseline(line)
                return TextCaret(x, baseline + metrics.ascent - top, baseline + metrics.descent - top)
            }
            override fun anchorAt(xPx: Float, yPx: Float): ContentAnchor? {
                if (yPx < 0 || yPx >= heightPx) return null
                val line = layout.getLineForVertical((top + yPx).toInt())
                if (xPx < minOf(layout.getLineLeft(line), layout.getLineRight(line)) || xPx > maxOf(layout.getLineLeft(line), layout.getLineRight(line))) return null
                return ContentAnchor(blockId, offsetForHorizontal(line, xPx).coerceIn(this.startOffset ?: 0, this.endOffset ?: 0))
            }
            override fun nearestAnchorAt(xPx: Float, yPx: Float): ContentAnchor? {
                val y = yPx.coerceIn(0f, maxOf(0f, heightPx - 1f))
                val line = layout.getLineForVertical((top + y).toInt())
                val x = xPx.coerceIn(minOf(layout.getLineLeft(line), layout.getLineRight(line)), maxOf(layout.getLineLeft(line), layout.getLineRight(line)))
                return ContentAnchor(blockId, offsetForHorizontal(line, x).coerceIn(this.startOffset ?: 0, this.endOffset ?: 0))
            }
            override fun anchorForVerticalOffset(yPx: Float): ContentAnchor {
                val line = layout.getLineForVertical((top + yPx.coerceIn(0f, maxOf(0f, heightPx - 1f))).toInt())
                return ContentAnchor(blockId, layout.getLineStart(line))
            }
            override fun selectionPath(startOffset: Int, endExclusive: Int): Path? {
                val from = maxOf(startOffset, this.startOffset ?: 0)
                val to = minOf(endExclusive, this.endOffset ?: 0)
                if (from >= to) return null
                val result = Path()
                val metrics = layout.paint.fontMetrics
                for (line in firstUnitIndex until endUnitExclusive) {
                    val lineFrom = maxOf(from, layout.getLineStart(line))
                    var lineTo = minOf(to, layout.getLineEnd(line))
                    while (lineTo > lineFrom && (layout.text[lineTo - 1] == '\n' || layout.text[lineTo - 1] == '\r')) lineTo--
                    if (lineFrom >= lineTo) continue
                    val segment = Path()
                    layout.getSelectionPath(lineFrom, lineTo, segment)
                    val baseline = layout.getLineBaseline(line)
                    val clip = Path().apply {
                        addRect(minOf(layout.getLineLeft(line), layout.getLineRight(line)), baseline + metrics.ascent,
                            maxOf(layout.getLineLeft(line), layout.getLineRight(line)), baseline + metrics.descent, Path.Direction.CW)
                    }
                    segment.op(clip, Path.Op.INTERSECT)
                    result.addPath(segment)
                }
                result.offset(0f, -top.toFloat())
                return result
            }
            override fun underlineSegments(startOffset: Int, endExclusive: Int): List<TextLineSegment> {
                val result = ArrayList<TextLineSegment>()
                for (line in firstUnitIndex until endUnitExclusive) {
                    val from = maxOf(startOffset, layout.getLineStart(line))
                    var to = minOf(endExclusive, layout.getLineVisibleEnd(line))
                    while (to > from && (layout.text[to - 1] == '\r' || layout.text[to - 1] == '\n')) to--
                    if (from >= to) continue
                    val path = Path(); layout.getSelectionPath(from, to, path)
                    val left = minOf(layout.getLineLeft(line), layout.getLineRight(line))
                    val right = maxOf(layout.getLineLeft(line), layout.getLineRight(line))
                    val clip = android.graphics.Region(kotlin.math.floor(left).toInt(), layout.getLineTop(line), kotlin.math.ceil(right).toInt(), layout.getLineBottom(line))
                    val region = android.graphics.Region().apply { setPath(path, clip) }
                    val iterator = android.graphics.RegionIterator(region)
                    val rect = android.graphics.Rect()
                    val y = layout.getLineBaseline(line) + layout.paint.fontMetrics.descent - top
                    while (iterator.next(rect)) {
                        val start = maxOf(left, rect.left.toFloat())
                        val end = minOf(right, rect.right.toFloat())
                        if (end > start) result.add(TextLineSegment(start, end, y))
                    }
                }
                return result
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
