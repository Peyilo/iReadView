package org.peyilo.ireadview.spi.presentation

import android.graphics.RectF

import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.ReaderResult
import org.peyilo.ireadview.api.state.ReaderSelectionModel
import org.peyilo.ireadview.api.state.ReaderFeatureSupport
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
    fun continuousFragments(): ReaderResult<List<ContinuousFragment>> =
        ReaderResult.Rejected(ReaderRejection.Unsupported)

    fun rangesForText(range: TextRange): ReaderResult<List<BlockTextRange>> =
        ReaderResult.Rejected(ReaderRejection.Unsupported)
}

/** A positive finite row whose rounded height fits Android's integer layout dimensions. */
data class ContinuousFragment(val fragment: BlockLayoutFragment, val spacingAfterPx: Float = 0f) {
    init {
        require(spacingAfterPx.isFinite() && spacingAfterPx >= 0f)
        val rowHeight = fragment.heightPx + spacingAfterPx
        require(
            fragment.heightPx.isFinite() && fragment.heightPx > 0f &&
                    rowHeight.isFinite() && rowHeight.toDouble() <= Int.MAX_VALUE.toDouble()
        ) {
            "Continuous fragment height exceeds Android layout dimensions"
        }
    }

    val heightPx get() = fragment.heightPx + spacingAfterPx
}

interface BookPresentationHost : ReadContentHost<ReadingPosition> {
    val presentationComponents: ReaderPresentationComponents get() = ReaderPresentationComponents.DEFAULT
    val pageCacheMode: ReaderPageCacheMode get() = ReaderPageCacheMode.THREE_PAGE
    val inputOptions: org.peyilo.ireadview.api.input.ReaderInputOptions get() = org.peyilo.ireadview.api.input.ReaderInputOptions()

    /** Body-owned surfaces may transfer a drag to the Widget's existing page-gesture controller. */
    fun reportBodyTouch(event: android.view.MotionEvent): Boolean = false

    /** Document text selections use the same custom toolbar factory as native block selections. */
    fun reportDocumentSelection(
        selection: DocumentTextSelection?,
        actions: org.peyilo.ireadview.spi.selection.DocumentSelectionHandle?,
        bounds: RectF?,
        generation: Long
    ): Boolean = false

    val book: BookDescriptor?
    val style: ReaderStyle
    val pageChromeProvider: org.peyilo.ireadview.spi.page.ReaderPageChromeProvider? get() = null
    val pageFactory: ReaderPageFactory
    val placeholderFactory: ReaderPlaceholderFactory get() = org.peyilo.ireadview.internal.render.DefaultReaderPlaceholderFactory()
    suspend fun retryChapter(chapterId: String): ReaderResult<Unit> =
        ReaderResult.Rejected(ReaderRejection.Unsupported)

    fun requestReaderAction(
        action: org.peyilo.ireadview.api.event.ReaderAction,
        position: ReadingPosition,
        generation: Long
    ): Boolean = false

    val selection: TextSelection? get() = null
    val documentSelection: DocumentTextSelection? get() = null
    fun reportSelection(selection: TextSelection, generation: Long): ReaderResult<Unit> =
        ReaderResult.Rejected(ReaderRejection.Unsupported)

    fun clearSelection() {}
    fun clearAccessibilitySelection(generation: Long): Boolean = false
    fun reportSelectionDrag(active: Boolean) {}
    suspend fun requestSelectionPage(
        chapterId: String,
        pageNumber: Int,
        generation: Long
    ): NavigationResult<ReadingPosition> = NavigationResult.Rejected(ReaderRejection.Unsupported)

    suspend fun requestSelectionPage(
        chapterId: String,
        pageNumber: Int,
        generation: Long,
        isCurrent: () -> Boolean
    ): NavigationResult<ReadingPosition> =
        if (isCurrent()) requestSelectionPage(
            chapterId,
            pageNumber,
            generation
        ) else NavigationResult.Superseded

    /** Reader-initiated text traversal shares navigation commits. The Widget disables page animation;
     * the default delegates to an existing custom host's navigation implementation.
     */
    suspend fun requestAccessibilityNavigation(
        target: ReadNavigation<ReadingPosition>,
        generation: Long
    ): NavigationResult<ReadingPosition> =
        if (generation == this.generation) requestNavigation(target) else NavigationResult.Superseded

    /** Widget hosts recheck this main-thread predicate immediately before display. Custom hosts
     * inherit an entry check and can override to provide the same display boundary.
     */
    suspend fun requestAccessibilityNavigation(
        target: ReadNavigation<ReadingPosition>, generation: Long,
        isCurrent: () -> Boolean
    ): NavigationResult<ReadingPosition> =
        if (isCurrent()) requestAccessibilityNavigation(
            target,
            generation
        ) else NavigationResult.Superseded

    fun validHighlights(chapterId: String): List<Highlight> = emptyList()
    fun validUnderlines(chapterId: String): List<Underline> = emptyList()
    fun documentAnnotations(chapterId: String): List<DocumentRangedAnnotation> = emptyList()

    /** Renderer must supply the exact annotation object it validated; replacements reject stale results. */
    fun reportDocumentAnnotationStatus(
        annotation: DocumentRangedAnnotation,
        status: AnnotationStatus,
        generation: Long
    ) {
    }

    fun reportHighlightStyleFallback(id: String, styleId: String) {}
    fun reportHighlightTapped(id: String): Boolean = false
    fun reportLinkTapped(target: LinkTarget, chapterId: String, generation: Long): Boolean = false

    /** Coordinates relative to the presentation root, for a child-owned touch surface. */
    fun reportTap(xPx: Float, yPx: Float) {}
    fun reportSelectionGeometryChanged() {}
    suspend fun getChapter(chapterId: String): ReaderResult<ChapterContent>
    suspend fun layoutChapter(
        chapterId: String,
        viewport: ReadViewport
    ): ReaderResult<ChapterLayout>
}

/** Engine-declared features only. Index/effect support uses the separate existing flags. */
data class BookPresentationFeatures @JvmOverloads constructor(
    val selectionModel: ReaderSelectionModel = ReaderSelectionModel.Unknown,
    val links: ReaderFeatureSupport = ReaderFeatureSupport.Unknown,
    val rangedAnnotations: ReaderFeatureSupport = ReaderFeatureSupport.Unknown
)

abstract class BookPresentation : ReadContentAdapter<ReadingPosition>,
    ReadVisualHandoff<ReadingPosition> {
    /** Defaults are Unknown for third-party engines. Keep declarations stable for this
     * presentation's lifetime; replace the engine to change them. Must be fast and pure.
     */
    open val contentFeatures: BookPresentationFeatures get() = BookPresentationFeatures()

    /** Opt in only when native ChapterLayout pagination describes this engine's pages.
     * Source block availability alone does not prove that a replacement engine uses it.
     */
    open val supportsBlockLayout: Boolean get() = false
    override suspend fun prepareVisualHandoff(candidate: PreparedRead<ReadingPosition>) {}

    /** Synchronous range containment query against a document renderer's locator grammar. */
    open fun documentSelectionCoveredBy(
        selected: DocumentTextSelection,
        outer: DocumentTextSelection
    ): Boolean = selected == outer

    open fun positionForDocumentSelection(selection: DocumentTextSelection): ReadingPosition? = null
    open fun positionForSelectionPage(chapterId: String, pageNumber: Int): ReadingPosition? = null
    open val currentPageCount: Int? get() = null

    /** False only when a backend cannot provide prepared surfaces for Widget-owned effects. */
    open val supportsPageTurnEffects: Boolean get() = true
    open val needsTimeUpdates: Boolean get() = false
    open fun onTimeChanged(timeMillis: Long) {}
    open fun onPageChromeChanged() {}
    open fun onNavigationStateChanged(state: org.peyilo.ireadview.api.state.ReadNavigationState) {}
    open fun onPageCacheModeChanged() {}

    /** Data/style decorations changed; retain text layout and the committed location. */
    open fun onDecorationsChanged() {}
    open fun onPlaceholdersChanged() {}
    open fun currentContent(): ChapterContent? = null

    /** Borrow a displayed chapter snapshot, including a neighboring chapter in a continuous viewport. */
    open fun contentForChapter(chapterId: String): ChapterContent? =
        currentContent()?.takeIf { it.chapterId == chapterId }

    /** Actual measured text viewport, excluding fixed chrome and body padding. */
    open fun layoutViewport(): ReadViewport? = null

    /** Coordinates relative to contentView. True consumes the tap before ordinary reader actions. */
    open fun onTap(xPx: Float, yPx: Float): Boolean = false
    open fun onLongPress(xPx: Float, yPx: Float): Boolean = false
    open fun onSelectionTouch(action: Int, xPx: Float, yPx: Float): Boolean = false

    /** Visible selection geometry relative to contentView, for toolbar placement. */
    open fun selectionBounds(): RectF? = null
    open fun selectionHandleBounds(start: Boolean): RectF? = null
}
