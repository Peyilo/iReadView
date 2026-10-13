package org.peyilo.ireadview.internal.layout

import android.graphics.Canvas
import android.graphics.Path
import org.peyilo.ireadview.api.model.ContentBlock
import org.peyilo.ireadview.api.model.HeadingBlock
import org.peyilo.ireadview.api.model.immutableList
import org.peyilo.ireadview.spi.content.*

/** One shared geometry transform for pagination, continuous flow, selection and accessibility. */
internal fun measurePaddedBlock(provider: BlockLayoutProvider, block: ContentBlock, context: BlockLayoutContext): BlockLayout {
    val padding = if (block is HeadingBlock) context.style.title.padding else context.style.body.blockPadding
    if (padding.startDp == 0f && padding.topDp == 0f && padding.endDp == 0f && padding.bottomDp == 0f)
        return provider.measure(block, context)
    val density = context.viewport.density
    val left = (if (context.viewport.rightToLeft) padding.endDp else padding.startDp) * density
    val right = (if (context.viewport.rightToLeft) padding.startDp else padding.endDp) * density
    val top = padding.topDp * density
    val bottom = padding.bottomDp * density
    val width = context.viewport.widthPx.toDouble() - left - right
    require(listOf(left, right, top, bottom).all { it.isFinite() } && width >= 1.0) { "Block padding leaves no text width" }
    val wrapped = provider.measure(block, context.copy(viewport = context.viewport.copy(widthPx = width.toInt())))
    context.checkCancellation()
    val sourceUnits = wrapped.units
    return object : BlockLayout by wrapped {
        override val estimatedRetainedBytes = wrapped.estimatedRetainedBytes?.takeIf { it >= 0 }?.let {
            org.peyilo.ireadview.internal.cache.RetainedSize.add(it, 128L + sourceUnits.size.toLong() * 40)
        }
        override val units = immutableList(sourceUnits.mapIndexed { index, unit ->
            context.checkCancellation()
            unit.copy(heightPx = unit.heightPx + (if (index == 0) top else 0f) + (if (index == sourceUnits.lastIndex) bottom else 0f))
        })
        override fun fragment(firstUnitIndex: Int, endUnitExclusive: Int): BlockLayoutFragment {
            val inner = wrapped.fragment(firstUnitIndex, endUnitExclusive)
            val insetTop = if (firstUnitIndex == 0) top else 0f
            val insetBottom = if (endUnitExclusive == units.size) bottom else 0f
            return object : BlockLayoutFragment by inner {
                override val heightPx = inner.heightPx + insetTop + insetBottom
                override fun draw(canvas: Canvas) {
                    val save = canvas.save()
                    try { canvas.translate(left, insetTop); inner.draw(canvas) } finally { canvas.restoreToCount(save) }
                }
                override fun selectionPath(startOffset: Int, endExclusive: Int): Path? =
                    inner.selectionPath(startOffset, endExclusive)?.let { Path(it).apply { offset(left, insetTop) } }
                override fun caretForOffset(offset: Int, endExclusive: Boolean): TextCaret? =
                    inner.caretForOffset(offset, endExclusive)?.let { TextCaret(it.xPx + left, it.topPx + insetTop, it.bottomPx + insetTop) }
                override fun anchorAt(xPx: Float, yPx: Float) =
                    if (xPx < left || xPx >= left + width || yPx < insetTop || yPx >= insetTop + inner.heightPx) null
                    else inner.anchorAt(xPx - left, yPx - insetTop)
                override fun nearestAnchorAt(xPx: Float, yPx: Float) = inner.nearestAnchorAt(
                    (xPx - left).coerceIn(0f, maxOf(0f, width.toFloat() - 1f)), (yPx - insetTop).coerceIn(0f, maxOf(0f, inner.heightPx - 1f)))
                override fun anchorForVerticalOffset(yPx: Float) = inner.anchorForVerticalOffset(
                    (yPx - insetTop).coerceIn(0f, maxOf(0f, inner.heightPx - 1f)))
                override fun accessibilityTextForVerticalRange(topPx: Float, bottomPx: Float): CharSequence? =
                    if (bottomPx <= insetTop || topPx >= insetTop + inner.heightPx) null
                    else inner.accessibilityTextForVerticalRange((topPx - insetTop).coerceAtLeast(0f), (bottomPx - insetTop).coerceAtMost(inner.heightPx))
                override fun accessibilityRangeForVerticalRange(topPx: Float, bottomPx: Float): BlockTextRange? =
                    if (bottomPx <= insetTop || topPx >= insetTop + inner.heightPx) null
                    else inner.accessibilityRangeForVerticalRange((topPx - insetTop).coerceAtLeast(0f), (bottomPx - insetTop).coerceAtMost(inner.heightPx))
            }
        }
    }
}
