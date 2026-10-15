package org.peyilo.ireadview.spi.presentation

import android.graphics.RectF

import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.ReaderResult
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.spi.content.BlockLayoutFragment
import org.peyilo.ireadview.spi.content.BlockTextRange
import org.peyilo.ireadview.api.state.ReaderRejection
import org.peyilo.ireadview.api.state.NavigationResult
import org.peyilo.ireadview.spi.page.ReaderPageFactory
import org.peyilo.ireadview.spi.page.ReaderPlaceholderFactory

interface ChapterLayout {
    /** Conservative retained-size estimate for cache admission; null means unknown and uncached. */
    val estimatedRetainedBytes: Long? get() = null
    val chapterId: String
    val contentRevision: String?
    val layoutKey: String
    val pageCount: Int
    /** Immutable source snapshot borrowed by the active presentation for annotation validation. */
    val contentSnapshot: ChapterContent? get() = null
    fun pageForAnchor(anchor: ContentAnchor): ReaderResult<Int>
    fun anchorForPage(pageNumber: Int): ReaderResult<ContentAnchor?>
    fun fragmentsForPage(pageNumber: Int): ReaderResult<List<BlockLayoutFragment>>
    /** Immutable bounded chunks in source order; paragraph spacing survives virtual page breaks.
     * Returned fragment identities and dimensions must be stable and safe to read on layout workers, without Views.
     */
    fun continuousFragments(): ReaderResult<List<ContinuousFragment>> = ReaderResult.Rejected(ReaderRejection.Unsupported)
    fun rangesForText(range: TextRange): ReaderResult<List<BlockTextRange>> = ReaderResult.Rejected(ReaderRejection.Unsupported)
}

/** A positive finite row whose rounded height fits Android's integer layout dimensions. */
data class ContinuousFragment(val fragment: BlockLayoutFragment, val spacingAfterPx: Float = 0f) {
    init {
        require(spacingAfterPx.isFinite() && spacingAfterPx >= 0f)
        val rowHeight = fragment.heightPx + spacingAfterPx
        require(fragment.heightPx.isFinite() && fragment.heightPx > 0f &&
            rowHeight.isFinite() && rowHeight.toDouble() <= Int.MAX_VALUE.toDouble()) {
            "Continuous fragment height exceeds Android layout dimensions"
        }
    }
    val heightPx get() = fragment.heightPx + spacingAfterPx
}

interface BookPresentationHost : ReadContentHost<ReadingPosition> {
    val book: BookDescriptor?
    val style: ReaderStyle
    val pageChromeProvider: org.peyilo.ireadview.spi.page.ReaderPageChromeProvider? get() = null
    val pageFactory: ReaderPageFactory
    val placeholderFactory: ReaderPlaceholderFactory get() = org.peyilo.ireadview.internal.render.DefaultReaderPlaceholderFactory()
    suspend fun retryChapter(chapterId: String): ReaderResult<Unit> = ReaderResult.Rejected(ReaderRejection.Unsupported)
    fun requestReaderAction(action: org.peyilo.ireadview.api.event.ReaderAction, position: ReadingPosition, generation: Long): Boolean = false
    val selection: TextSelection? get() = null
    fun reportSelection(selection: TextSelection, generation: Long): ReaderResult<Unit> = ReaderResult.Rejected(ReaderRejection.Unsupported)
    fun clearSelection() {}
    fun clearAccessibilitySelection(generation: Long): Boolean = false
    fun reportSelectionDrag(active: Boolean) {}
    suspend fun requestSelectionPage(chapterId: String, pageNumber: Int, generation: Long): NavigationResult<ReadingPosition> = NavigationResult.Rejected(ReaderRejection.Unsupported)
    /** Reader-initiated text traversal shares navigation commits. The Widget disables page animation;
     * the default delegates to an existing custom host's navigation implementation.
     */
    suspend fun requestAccessibilityNavigation(target: ReadNavigation<ReadingPosition>, generation: Long): NavigationResult<ReadingPosition> =
        if (generation == this.generation) requestNavigation(target) else NavigationResult.Superseded
    /** Widget hosts recheck this main-thread predicate immediately before display. Custom hosts
     * inherit an entry check and can override to provide the same display boundary.
     */
    suspend fun requestAccessibilityNavigation(target: ReadNavigation<ReadingPosition>, generation: Long,
        isCurrent: () -> Boolean): NavigationResult<ReadingPosition> =
        if (isCurrent()) requestAccessibilityNavigation(target, generation) else NavigationResult.Superseded
    fun validHighlights(chapterId: String): List<Highlight> = emptyList()
    fun validUnderlines(chapterId: String): List<Underline> = emptyList()
    fun reportHighlightStyleFallback(id: String, styleId: String) {}
    fun reportHighlightTapped(id: String): Boolean = false
    /** Coordinates relative to the presentation root, for a child-owned touch surface. */
    fun reportTap(xPx: Float, yPx: Float) {}
    fun reportSelectionGeometryChanged() {}
    suspend fun getChapter(chapterId: String): ReaderResult<ChapterContent>
    suspend fun layoutChapter(chapterId: String, viewport: ReadViewport): ReaderResult<ChapterLayout>
}

abstract class BookPresentation : ReadContentAdapter<ReadingPosition> {
    open val needsTimeUpdates: Boolean get() = false
    open fun onTimeChanged(timeMillis: Long) {}
    open fun onPageChromeChanged() {}
    open fun onNavigationStateChanged(state: org.peyilo.ireadview.api.state.ReadNavigationState) {}
    /** Data/style decorations changed; retain text layout and the committed location. */
    open fun onDecorationsChanged() {}
    open fun onPlaceholdersChanged() {}
    open fun currentContent(): ChapterContent? = null
    /** Borrow a displayed chapter snapshot, including a neighboring chapter in a continuous viewport. */
    open fun contentForChapter(chapterId: String): ChapterContent? = currentContent()?.takeIf { it.chapterId == chapterId }
    /** Actual measured text viewport, excluding fixed chrome and body padding. */
    open fun layoutViewport(): ReadViewport? = null
    /** Coordinates relative to contentView. True consumes the tap before ordinary reader actions. */
    open fun onTap(xPx: Float, yPx: Float): Boolean = false
    open fun onLongPress(xPx: Float, yPx: Float): Boolean = false
    open fun onSelectionTouch(action: Int, xPx: Float, yPx: Float): Boolean = false
    /** Visible selection geometry relative to contentView, for toolbar placement. */
    open fun selectionBounds(): RectF? = null
}
