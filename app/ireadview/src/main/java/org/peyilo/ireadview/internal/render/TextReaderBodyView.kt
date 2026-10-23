package org.peyilo.ireadview.internal.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.graphics.PointF
import android.graphics.RectF
import android.view.View
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.graphics.Rect
import android.os.Bundle
import androidx.core.view.ViewCompat
import org.peyilo.ireadview.spi.content.BlockLayoutFragment
import org.peyilo.ireadview.spi.content.BlockTextRange
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.api.style.SelectionStyle

internal data class ColoredTextRange(val id: String, val range: BlockTextRange, val color: Int)
internal data class LineTextRange(val id: String, val range: BlockTextRange, val style: org.peyilo.ireadview.api.style.TextLineStyle)
internal data class AccessibilityTraversalAnchor(val anchor: ContentAnchor, val isValid: () -> Boolean)

internal class TextReaderBodyView(context: Context) : org.peyilo.ireadview.spi.body.ReaderBodyView(context), org.peyilo.ireadview.spi.body.BlockReaderBodyContent {
    /** Pure validation against the current binding; does not change selection or navigation. */
    var accessibilityMatchesText: ((BlockTextRange, String) -> Boolean)? = null
    var accessibilitySelectText: ((BlockTextRange) -> Boolean)? = null
    var accessibilitySelectAnchors: ((ContentAnchor, ContentAnchor) -> Boolean)? = null
    var accessibilityCompareAnchors: ((ContentAnchor, ContentAnchor) -> Int?)? = null
    var accessibilityMoveToAdjacent: ((Int, Bundle, AccessibilityTraversalAnchor?) -> Boolean)? = null
    var accessibilityPreserveAnchor: ((ContentAnchor) -> AccessibilityTraversalAnchor?)? = null
    /** Accepted asynchronous movement retains source focus until navigation succeeds. */
    var accessibilityMoveOffscreen: ((Int, Bundle, AccessibilityTraversalBoundary?, AccessibilityTraversalAnchor?, () -> Boolean) -> Boolean)? = null
    fun accessibilityMoveAtAnchor(action: Int, arguments: Bundle, anchor: ContentAnchor, fixed: AccessibilityTraversalAnchor? = null): Boolean =
        accessibility.traverseAtAnchor(action, arguments, anchor, fixed)
    fun accessibilityMoveAtBlock(action: Int, arguments: Bundle, blockId: String): Boolean =
        accessibility.traverseAtBlock(action, arguments, blockId)
    fun accessibilityMoveFromBoundary(action: Int, arguments: Bundle, fixed: AccessibilityTraversalAnchor?): Boolean =
        accessibility.traverseFromBoundary(action, arguments, fixed)
    var accessibilityClearSelection: (() -> Boolean)? = null
    var accessibilitySelectionVersion: Long = 0
        private set
    override var fragments: List<BlockLayoutFragment> = emptyList()
        set(value) { field = value.toList(); accessibility.replace(field); rebuildHighlights() }
    private val accessibility = BodyAccessibilityHelper(this)
    private var accessibilityRefreshQueued = false
    private var scrollObserver: ViewTreeObserver? = null
    private val accessibilityRefresh = Runnable { accessibilityRefreshQueued = false; accessibility.refresh() }
    private val ancestorScrollListener = ViewTreeObserver.OnScrollChangedListener {
        if (!accessibilityRefreshQueued && isShown) {
            accessibilityRefreshQueued = true
            postOnAnimation(accessibilityRefresh)
        }
    }
    init { setWillNotDraw(false); ViewCompat.setAccessibilityDelegate(this, accessibility) }
    override fun dispatchHoverEvent(event: MotionEvent): Boolean = accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildHighlights()
        if (isShown) accessibility.invalidateRoot()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scrollObserver = viewTreeObserver.also { it.addOnScrollChangedListener(ancestorScrollListener) }
        accessibility.refresh()
    }
    override fun onDetachedFromWindow() {
        scrollObserver?.takeIf { it.isAlive }?.removeOnScrollChangedListener(ancestorScrollListener)
        scrollObserver = null
        removeCallbacks(accessibilityRefresh); accessibilityRefreshQueued = false
        accessibility.clearFocus()
        super.onDetachedFromWindow()
    }
    private var highlights: List<ColoredTextRange> = emptyList()
    private var underlines: List<LineTextRange> = emptyList()
    private var underlinePaths: List<UnderlinePath> = emptyList()
    private var paths: List<HighlightPath> = emptyList()
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selectionRanges: List<ColoredTextRange> = emptyList()
    private var selectionPaths: List<HighlightPath> = emptyList()
    private var selectionStyle = SelectionStyle()
    /** Paged bodies expose off-page endpoints at the local selected slice boundary.
     * Scrolling rows keep real endpoints only, avoiding a proxy on every row. */
    var showOffPageSelectionHandles = false
        set(value) { if (field != value) { field = value; rebuildHighlights() } }
    private var startHandle: org.peyilo.ireadview.spi.selection.ReaderSelectionHandle? = null
    private var endHandle: org.peyilo.ireadview.spi.selection.ReaderSelectionHandle? = null

    /** End a content binding; a temporarily detached, reusable row stays bound. */
    override fun clearContentBinding() {
        accessibilityMatchesText = null
        accessibilitySelectText = null
        accessibilitySelectAnchors = null
        accessibilityCompareAnchors = null
        accessibilityMoveToAdjacent = null
        accessibilityPreserveAnchor = null
        accessibilityMoveOffscreen = null
        accessibilityClearSelection = null
        removeCallbacks(accessibilityRefresh)
        accessibilityRefreshQueued = false
        highlights = emptyList()
        underlines = emptyList()
        underlinePaths = emptyList()
        selectionRanges = emptyList()
        paths = emptyList()
        selectionPaths = emptyList()
        startHandle = null
        endHandle = null
        selectionStyle = SelectionStyle()
        accessibilitySelectionVersion++
        try {
            fragments = emptyList()
        } catch (error: Exception) {
            // State is already empty; a service notification must not stop sibling cleanup.
            android.util.Log.w("iReadView", "Clearing body content notification failed", error)
        }
    }

    fun setHighlights(value: List<ColoredTextRange>) { highlights = value.toList(); rebuildHighlights() }
    fun setUnderlines(value: List<LineTextRange>) { underlines = value.toList(); rebuildHighlights() }
    fun setSelection(ranges: List<BlockTextRange>, style: SelectionStyle) {
        val selectionChanged = selectionRanges.map { it.range } != ranges
        selectionStyle = style
        selectionRanges = ranges.map { ColoredTextRange("selection", it, style.fillColor) }; rebuildHighlights()
        if (selectionChanged) {
            accessibilitySelectionVersion++
            if (isShown) accessibility.invalidateRoot()
        }
    }
    fun accessibilitySelection(range: BlockTextRange): Pair<Int, Int>? = selectionRanges.firstOrNull {
        it.range.blockId == range.blockId && it.range.startOffset < range.endExclusive && it.range.endExclusive > range.startOffset
    }?.range?.let { (maxOf(it.startOffset, range.startOffset) - range.startOffset) to
        (minOf(it.endExclusive, range.endExclusive) - range.startOffset) }
    /** true=start, false=end; null outside either handle's enlarged touch target. */
    fun selectionHandleAt(xPx: Float, yPx: Float): Boolean? {
        return selectionPainter.handleAt(listOfNotNull(startHandle, endHandle), xPx, yPx, selectionStyle, resources.displayMetrics.density)
    }
    fun selectionDragOffset(start: Boolean, xPx: Float, yPx: Float): PointF? =
        (if (start) startHandle else endHandle)?.let { selectionPainter.dragOffset(it, xPx, yPx) }
    override fun selectionHandleBounds(start: Boolean): RectF? = (if (start) startHandle else endHandle)?.let {
        selectionPainter.handleBounds(it, selectionStyle, resources.displayMetrics.density)
    }
    override fun selectionBounds(): RectF? {
        var bounds: RectF? = null
        selectionPaths.forEach { selection ->
            val rect = RectF()
            selection.path.computeBounds(rect, true)
            if (rect.intersect(0f, 0f, (width - paddingLeft - paddingRight).toFloat(), selection.fragment.heightPx)) {
                rect.offset(paddingLeft + selection.fragment.leftPx, paddingTop + selection.fragment.topPx)
                if (bounds == null) bounds = RectF(rect) else bounds!!.union(rect)
            }
        }
        return bounds
    }
    fun anchorAt(xPx: Float, yPx: Float): ContentAnchor? {
        if (xPx < paddingLeft || xPx >= width - paddingRight || yPx < paddingTop || yPx >= height - paddingBottom) return null
        return fragments.firstNotNullOfOrNull { fragment -> fragment.anchorAt(xPx - paddingLeft - fragment.leftPx, yPx - paddingTop - fragment.topPx) }
    }
    fun linkAt(xPx: Float, yPx: Float): org.peyilo.ireadview.api.model.LinkTarget? {
        return fragments.firstNotNullOfOrNull { fragment ->
            fragment.linkAt(xPx - paddingLeft - fragment.leftPx, yPx - paddingTop - fragment.topPx)
        }
    }
    /** Dragging may enter a paragraph gap; select the nearest visible line without inventing text. */
    fun nearestAnchorAt(xPx: Float, yPx: Float): ContentAnchor? {
        anchorAt(xPx, yPx)?.let { return it }
        val x = xPx.coerceIn(paddingLeft.toFloat(), maxOf(paddingLeft.toFloat(), (width - paddingRight).toFloat()))
        val fragment = fragments.minByOrNull { fragment ->
            val top = paddingTop + fragment.topPx
            when { yPx < top -> top - yPx; yPx > top + fragment.heightPx -> yPx - top - fragment.heightPx; else -> 0f }
        } ?: return null
        val localY = (yPx - paddingTop - fragment.topPx).coerceIn(0f, maxOf(0f, fragment.heightPx - 1f))
        return fragment.nearestAnchorAt(x - paddingLeft - fragment.leftPx, localY)
    }

    fun highlightAt(xPx: Float, yPx: Float): String? {
        val bodyWidth = width - paddingLeft - paddingRight
        if (xPx < paddingLeft || xPx >= width - paddingRight || yPx < paddingTop || yPx >= height - paddingBottom) return null
        return paths.asReversed().firstOrNull { highlight ->
            val fragment = highlight.fragment
            val x = xPx - paddingLeft - fragment.leftPx
            val y = yPx - paddingTop - fragment.topPx
            val clip = Region(0, 0, bodyWidth, kotlin.math.ceil(fragment.heightPx).toInt())
            Region().apply { setPath(highlight.path, clip) }.contains(kotlin.math.floor(x).toInt(), kotlin.math.floor(y).toInt())
        }?.id
    }

    private fun rebuildHighlights() {
        fun build(ranges: List<ColoredTextRange>): List<HighlightPath> = ranges.flatMap { highlight ->
            fragments.filter { it.blockId == highlight.range.blockId }.mapNotNull { fragment ->
                fragment.selectionPath(highlight.range.startOffset, highlight.range.endExclusive)?.let { path ->
                    HighlightPath(highlight.id, path, highlight.color, fragment)
                }
            }
        }
        paths = build(highlights)
        underlinePaths = underlines.flatMap { underline ->
            fragments.filter { it.blockId == underline.range.blockId }.mapNotNull { fragment ->
                val segments = fragment.underlineSegments(underline.range.startOffset, underline.range.endExclusive)
                if (segments.isEmpty()) null else UnderlinePath(segments, fragment, underline.style)
            }
        }
        selectionPaths = build(selectionRanges)
        fun createHandle(fragment: BlockLayoutFragment, caret: org.peyilo.ireadview.spi.content.TextCaret, end: Boolean): org.peyilo.ireadview.spi.selection.ReaderSelectionHandle {
            val x = paddingLeft + fragment.leftPx + caret.xPx
            val top = paddingTop + fragment.topPx + caret.topPx
            val bottom = paddingTop + fragment.topPx + caret.bottomPx
            return selectionPainter.createHandle(x, top, bottom, end, width, height, selectionStyle,
                resources.displayMetrics.density, showOffPageSelectionHandles)
        }

        fun handle(range: BlockTextRange?, end: Boolean): org.peyilo.ireadview.spi.selection.ReaderSelectionHandle? {
            if (range == null) return null
            val offset = if (end) range.endExclusive else range.startOffset
            val real = fragments.filter { it.blockId == range.blockId }.firstNotNullOfOrNull { fragment ->
                fragment.caretForOffset(offset, end)?.let { createHandle(fragment, it, end) }
            }
            if (real != null || !showOffPageSelectionHandles) return real
            // Only the drawn, selected slice contributes a proxy. Do not move
            // the source endpoint or shrink a paragraph merely to show a handle.
            val visible = if (end) selectionPaths.asReversed() else selectionPaths
            return visible.firstNotNullOfOrNull { selected ->
                if (selected.path.isEmpty) return@firstNotNullOfOrNull null
                val fragment = selected.fragment
                val localRange = selectionRanges.firstOrNull { it.range.blockId == fragment.blockId }?.range
                    ?: return@firstNotNullOfOrNull null
                val from = maxOf(localRange.startOffset, fragment.startOffset ?: localRange.startOffset)
                val to = minOf(localRange.endExclusive, fragment.endOffset ?: localRange.endExclusive)
                if (from >= to) return@firstNotNullOfOrNull null
                fragment.caretForOffset(if (end) to else from, end)?.let { createHandle(fragment, it, end) }
            }
        }
        startHandle = handle(selectionRanges.firstOrNull()?.range, false)
        endHandle = handle(selectionRanges.lastOrNull()?.range, true)
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bodySave = canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        fun drawPaths(paths: List<HighlightPath>, selected: Boolean = false) = paths.forEach { highlight ->
            val fragment = highlight.fragment
            val save = canvas.save()
            canvas.translate(paddingLeft + fragment.leftPx, paddingTop + fragment.topPx)
            canvas.clipRect(0f, 0f, (width - paddingLeft - paddingRight).toFloat(), fragment.heightPx)
            if (selected) selectionPainter.drawFill(canvas, highlight.path, selectionStyle)
            else { highlightPaint.color = highlight.color; canvas.drawPath(highlight.path, highlightPaint) }
            canvas.restoreToCount(save)
        }
        drawPaths(paths)
        drawBodyContent(canvas) { target ->
            fragments.forEach { fragment ->
                val save = target.save()
                target.translate(paddingLeft + fragment.leftPx, paddingTop + fragment.topPx)
                fragment.draw(target)
                target.restoreToCount(save)
            }
        }
        // Active selection is the same Canvas overlay layer used by document bodies.
        drawPaths(selectionPaths, true)
        underlinePaths.forEach { underline ->
            val save = canvas.save()
            canvas.translate(paddingLeft + underline.fragment.leftPx, paddingTop + underline.fragment.topPx)
            canvas.clipRect(0f, 0f, (width - paddingLeft - paddingRight).toFloat(), underline.fragment.heightPx)
            drawUnderlines(canvas, underline.segments, underline.style, resources.displayMetrics.density)
            canvas.restoreToCount(save)
        }
        canvas.restoreToCount(bodySave)
        selectionPainter.drawHandles(canvas, listOfNotNull(startHandle, endHandle), selectionStyle, resources.displayMetrics.density, width)
    }
    private data class HighlightPath(val id: String, val path: Path, val color: Int, val fragment: BlockLayoutFragment)
    private data class UnderlinePath(val segments: List<org.peyilo.ireadview.spi.content.TextLineSegment>, val fragment: BlockLayoutFragment, val style: org.peyilo.ireadview.api.style.TextLineStyle)
}
