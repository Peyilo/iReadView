package org.peyilo.ireadview.widget

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import kotlinx.coroutines.*
import org.peyilo.ireadview.api.event.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.source.PresentationBookSource
import org.peyilo.ireadview.api.source.BookNavigationSource
import org.peyilo.ireadview.api.source.BookNavigationEntry
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.internal.style.ReaderStyleStateCodec
import org.peyilo.ireadview.internal.style.ReaderFontResolver
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.internal.book.ReaderCoordinator
import org.peyilo.ireadview.internal.annotation.AnnotationStore
import org.peyilo.ireadview.internal.selection.SelectionToolbarController
import org.peyilo.ireadview.internal.selection.TextSelectionResolver
import org.peyilo.ireadview.spi.selection.*
import java.util.UUID
import org.peyilo.ireadview.internal.render.DefaultReaderPageFactory
import org.peyilo.ireadview.spi.content.BlockLayoutProvider
import org.peyilo.ireadview.spi.page.*
import org.peyilo.ireadview.spi.presentation.*
import org.peyilo.ireadview.R

/** Host-facing book operations; presentation subclasses still use the generic container's commits. */
abstract class BookReadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : BaseReadView<ReadingPosition>(context, attrs, defStyleAttr, defStyleRes) {
    private val listeners = LinkedHashSet<ReaderListener>()
    private var phase = ReaderPhase.Closed
    private var lastError: ReaderError? = null
    private var statePublicationSequence = 0L
    private var openSequence = 0L
    private var relayout: Job? = null
    private var relayoutSequence = 0L
    private var restoredPosition: ReadingPosition? = null
    private var pageFactory: ReaderPageFactory = DefaultReaderPageFactory()
    private var pageChromeProvider: ReaderPageChromeProvider? = null
    private val pageChromeBridge = ReaderPageChromeProvider { page ->
        pageChromeProvider?.provide(page) ?: ReaderPageChrome()
    }
    private var chromeRefreshPending = false
    private var refreshingChrome = false
    private var presentation: BookPresentation? = null
    private var presentationSource: PresentationBookSource? = null
    private var pageClock: org.peyilo.ireadview.internal.render.ReaderPageClock? = null
    init {
        pageClock = org.peyilo.ireadview.internal.render.ReaderPageClock(this, { presentation }, { contentGeneration },
            { !isReleased && currentPosition != null })
    }
    var readingMode: ReadingMode = ReadingMode.PAGED
        private set
    private var requestedMode: ReadingMode? = null
    private var modeExplicitlySet = false
    private var requestedModeGeneration = -1L
    private var globalIndexNavigation: Job? = null
    private var linkHandler: ReaderLinkHandler? = null
    private var linkRequest: Job? = null
    private var linkSequence = 0L
    private var configuredPageCacheMode = ReaderPageCacheMode.THREE_PAGE
    val pageCacheMode: ReaderPageCacheMode get() = configuredPageCacheMode
    private var configuredComponents = ReaderPresentationComponents.DEFAULT
    val presentationComponents: ReaderPresentationComponents get() = configuredComponents
    private var configuredPresentationFactory: BookPresentationFactory? = null
    private var presentationConfigurationVersion = 0L

    /** Installs independent content drawing, selection rendering and gesture factories.
     * Main thread. Rebuilds cached bodies at the current source anchor; does not reopen the source.
     * Success installs configuration; async restoration is observed through readerState/events.
     */
    fun setPresentationComponents(components: ReaderPresentationComponents): ReaderResult<Unit> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        if (navigationState != ReadNavigationState.Idle || draggingSelection || requestedMode != null || phase == ReaderPhase.LoadingBook)
            return ReaderResult.Rejected(ReaderRejection.Busy)
        if (configuredComponents != components) { configuredComponents = components; presentationConfigurationVersion++; refreshPresentationConfiguration(invalidateLayouts = false) }
        return if (isReleased || configuredComponents != components) ReaderResult.Superseded else ReaderResult.Success(Unit)
    }
    /** Null restores source/default backend selection. Full engine/layout replacement
     * belongs here; page drawing and selection-only changes use setPresentationComponents.
     */
    fun setBookPresentationFactory(factory: BookPresentationFactory?): ReaderResult<Unit> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        if (navigationState != ReadNavigationState.Idle || draggingSelection || requestedMode != null || phase == ReaderPhase.LoadingBook)
            return ReaderResult.Rejected(ReaderRejection.Busy)
        if (configuredPresentationFactory !== factory) { configuredPresentationFactory = factory; presentationConfigurationVersion++; refreshLayoutConfiguration() }
        return if (isReleased || configuredPresentationFactory !== factory) ReaderResult.Superseded else ReaderResult.Success(Unit)
    }

    /** Main thread; preserves the committed page. Busy changes are rejected rather than
     * disposing a surface while a gesture or navigation transaction still owns it.
     */
    fun setPageCacheMode(mode: ReaderPageCacheMode): ReaderResult<Unit> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        if (navigationState != ReadNavigationState.Idle) return ReaderResult.Rejected(ReaderRejection.Busy)
        if (pageCacheMode != mode) {
            configuredPageCacheMode = mode
            presentation?.onPageCacheModeChanged()
        }
        return ReaderResult.Success(Unit)
    }

    /** Intercepts links before built-in book navigation. External actions belong to the host. */
    fun setReaderLinkHandler(handler: ReaderLinkHandler?) {
        checkMainThread()
        if (!isReleased) linkHandler = handler
    }

    private fun activateLink(target: LinkTarget, chapterId: String, generation: Long): Boolean {
        checkMainThread()
        if (isReleased || !isEnabled || !isAttachedToWindow || generation != contentGeneration || navigationState != ReadNavigationState.Idle) return false
        val source = coordinator.source ?: return false
        val origin = currentPosition
        val revision = currentBook?.revision
        val sequence = ++linkSequence
        linkRequest?.cancel()
        linkRequest = null
        fun current() = !isReleased && isEnabled && isAttachedToWindow && generation == contentGeneration &&
            sequence == linkSequence && coordinator.source === source && currentBook?.revision == revision && currentPosition == origin &&
            navigationState == ReadNavigationState.Idle
        if (linkHandler?.onLink(this, chapterId, target) == true || !current()) return true
        if (target is LinkTarget.Internal) {
            val resolver = source as? org.peyilo.ireadview.api.source.BookLinkSource
            linkRequest = readerScope.launch {
                try {
                    val position = resolver?.let { withContext(Dispatchers.IO) { it.resolveLink(target, revision) } }
                    if (!current()) return@launch
                    if (position == null) reportReaderError(ReaderError(ReaderStage.NAVIGATION, "UnresolvedLink", "Book link target is unavailable", chapterId, retryable = false))
                    else goToPosition(position)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (current()) reportReaderError(ReaderError(ReaderStage.NAVIGATION, "LinkResolutionFailed", failure.message ?: "Book link failed", chapterId, cause = failure))
                }
            }
        }
        // A real link consumes the tap even when no external-link handler is installed.
        return true
    }
    private class GlobalIndexInvalidated : CancellationException("Global page index unavailable")
    private var placeholderController: org.peyilo.ireadview.internal.render.ReaderPlaceholderController? = null
    private var placeholderFactory: ReaderPlaceholderFactory = org.peyilo.ireadview.internal.render.DefaultReaderPlaceholderFactory()
    private var lastOpenRequest = OpenBookRequest()
    private val annotationStore = AnnotationStore()
    private var selection: TextSelection? = null
    private class DocumentSelectionBinding(val selected: DocumentTextSelection, var delegate: org.peyilo.ireadview.spi.selection.DocumentSelectionHandle,
        var bounds: Rect?, val generation: Long) { lateinit var actions: SelectionActions }
    private var documentSelection: DocumentSelectionBinding? = null
    val currentSelection: ReaderSelection? get() = documentSelection?.selected ?: selection
    private var selectionRevision = 0L
    private var accessibleSelectionActions: SelectionActions? = null
    private var draggingSelection = false
    private val selectionToolbar = SelectionToolbarController(this)
    private var annotationIds: AnnotationIdFactory = object : AnnotationIdFactory {
        override fun newBookmarkId() = UUID.randomUUID().toString()
        override fun newHighlightId() = UUID.randomUUID().toString()
    }
    var style: ReaderStyle = ReaderStyle.default()
        private set
    private var styleExplicitlySet = false
    private val coordinator: ReaderCoordinator = ReaderCoordinator(
        { readerScope },
        { book ->
            clearSelection()
            annotationStore.bind(book)
            if (book != null) publishAnnotations()
            if (currentBook === book) dispatch { it.onBookChanged(this, book) }
        },
        { id, state ->
            val generation = contentGeneration
            if (state == ChapterState.Unloaded) {
                if (selection?.range?.chapterId == id) clearSelection()
                annotationStore.markPending(id)
                publishAnnotationStatuses()
            }
            if (generation == contentGeneration) dispatch { it.onChapterStateChanged(this, id, state) }
        },
        { id, state -> dispatch { it.onPaginationChanged(this, id, state) }; publishState() },
        { value -> phase = value; publishState() },
        { content ->
            val generation = contentGeneration
            val changes = annotationStore.validate(content)
            presentation?.onDecorationsChanged()
            val revision = annotationStore.revision
            changes.forEach { (id, status) ->
                if (generation == contentGeneration && revision == annotationStore.revision) dispatch {
                    if (revision == annotationStore.revision) it.onAnnotationStatusChanged(this, id, status)
                }
            }
        },
        { state ->
            if (state !is BookPageIndexState.Ready) globalIndexNavigation?.cancel(GlobalIndexInvalidated())
            dispatch { it.onBookPageIndexChanged(this, state) }; publishState()
        },
        ReaderFontResolver(context)::resolve
    )

    val currentPosition get() = currentLocation
    val currentBook get() = coordinator.book
    val chapterCount get() = currentBook?.chapters?.size ?: 0
    val currentChapter get() = currentPosition?.let { position -> currentBook?.chapterForId(position.chapterId) }
    /** Unified navigation tree. Format-specific fragments remain opaque ReadingPosition locators. */
    suspend fun getNavigation(): ReaderResult<List<BookNavigationEntry>> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        val book = currentBook ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        val source = coordinator.source
        val generation = contentGeneration
        return try {
            val entries = if (source is BookNavigationSource) withContext(Dispatchers.IO) { source.loadNavigation() }
            else {
                fun convert(items: List<TocEntry>): List<BookNavigationEntry> = items.map { item -> when (item) {
                    is TocEntry.Chapter -> BookNavigationEntry(item.descriptor.title, ReadingPosition(book.bookId, item.descriptor.chapterId))
                    is TocEntry.Group -> BookNavigationEntry(item.title, null, convert(item.children))
                } }
                convert(book.tableOfContents)
            }
            if (generation != contentGeneration || currentBook !== book || coordinator.source !== source) ReaderResult.Superseded
            else ReaderResult.Success(java.util.Collections.unmodifiableList(ArrayList(entries)))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (generation != contentGeneration || currentBook !== book) ReaderResult.Superseded
            else ReaderResult.Failure(ReaderError(ReaderStage.BOOK, "NavigationLoadFailed", error.message ?: "Table of contents failed", cause = error))
        }
    }
    fun getNavigation(callback: ReaderCallback<ReaderResult<List<BookNavigationEntry>>>): ReaderRequest = submit(callback) { getNavigation() }
    val bookPageIndex: BookPageIndex? get() = coordinator.pageIndex
    /** Main-thread feature snapshot of the current engine. Closed/released readers return
     * Unknown, not stale features from a previously opened book. Does not load or lay out data.
     */
    val contentCapabilities: ReaderContentCapabilities get() {
        checkMainThread()
        val engine = presentation?.takeIf { !isReleased && currentBook != null } ?: return ReaderContentCapabilities()
        val features = engine.contentFeatures
        return ReaderContentCapabilities(features.selectionModel, features.links, features.rangedAnnotations,
            wholeBookPageIndex = if (presentationSource?.supportsBlockLayout != false && engine.supportsBlockLayout)
                ReaderFeatureSupport.Supported else ReaderFeatureSupport.Unsupported,
            pageTurnEffects = if (isPageTurnEffectActive()) ReaderFeatureSupport.Supported else ReaderFeatureSupport.Unsupported
        )
    }
    val readerState: ReaderState get() = ReaderState(phase, currentBook?.bookId, currentPosition, presentation?.currentPageCount ?: currentPosition?.let { (coordinator.paginationState(it.chapterId) as? ChapterPaginationState.Ready)?.pageCount }, chapterCount, lastError, readingMode,
        coordinator.pageIndexState, globalPageNumber(), coordinator.pageIndex?.totalPageCount, navigationState, navigationCapabilities, contentCapabilities)
    override fun onNavigationCapabilitiesChanged(capabilities: ReadNavigationCapabilities) { publishState() }
    private fun globalPageNumber(): Long? {
        val position = currentPosition ?: return null
        val displayed = presentation?.contentForChapter(position.chapterId) ?: return null
        coordinator.pageIndex?.let { index ->
            val entry = index.chapterForId(position.chapterId) ?: return null
            if (entry.contentRevision != displayed.revision || entry.contentFingerprint != displayed.contentFingerprint) return null
            return index.pageForPosition(position)
        }
        val chapters = currentBook?.chapters ?: return null
        val index = chapters.indexOfFirst { it.chapterId == position.chapterId }
        if (index < 0) return null
        var prefix = 0L
        for (chapterIndex in 0 until index) {
            val state = coordinator.paginationState(chapters[chapterIndex].chapterId) as? ChapterPaginationState.Ready ?: return null
            prefix += state.pageCount
        }
        return position.pageNumberHint?.let { prefix + it }
    }

    suspend fun buildBookPageIndex(): ReaderResult<BookPageIndex> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        if (currentBook == null) return ReaderResult.Rejected(ReaderRejection.NoBook)
        if (presentationSource?.supportsBlockLayout == false || presentation?.supportsBlockLayout != true) return ReaderResult.Rejected(ReaderRejection.Unsupported)
        val viewport = presentation?.layoutViewport() ?: if (currentBook?.chapters?.isEmpty() == true) viewportSnapshot() else return ReaderResult.Rejected(ReaderRejection.NotReady)
        val metrics = resources.displayMetrics
        val capturedStyle = style
        val bodyPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, capturedStyle.body.textSizeSp, metrics)
        val titlePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, capturedStyle.title.textSizeSp, metrics)
        return ownedOperation {
            coordinator.buildBookPageIndex(viewport, capturedStyle, bodyPx, titlePx) {
                while (navigationState != ReadNavigationState.Idle) delay(16)
            }
        }
    }
    fun buildBookPageIndex(callback: ReaderCallback<ReaderResult<BookPageIndex>>): ReaderRequest = submit(callback) { buildBookPageIndex() }

    suspend fun goToGlobalPage(pageNumber: Long): NavigationResult<ReadingPosition> {
        checkMainThread()
        if (isReleased) return NavigationResult.Rejected(ReaderRejection.Released)
        if (navigationState != ReadNavigationState.Idle || globalIndexNavigation?.isActive == true) return NavigationResult.Rejected(ReaderRejection.Busy)
        val index = coordinator.pageIndex ?: return NavigationResult.Rejected(ReaderRejection.PageIndexUnavailable)
        val position = when (val result = index.positionForPage(pageNumber)) {
            is ReaderResult.Success -> result.value
            is ReaderResult.Rejected -> return NavigationResult.Rejected(result.reason)
            else -> return NavigationResult.Rejected(ReaderRejection.PageIndexUnavailable)
        }
        return ownedOperation {
            when (val content = coordinator.getChapter(position.chapterId)) {
                is ReaderResult.Failure -> return@ownedOperation NavigationResult.Failed(content.error)
                is ReaderResult.Rejected -> return@ownedOperation NavigationResult.Rejected(content.reason)
                ReaderResult.Superseded -> return@ownedOperation NavigationResult.Superseded
                is ReaderResult.Success -> Unit
            }
            if (coordinator.pageIndex !== index) return@ownedOperation NavigationResult.Rejected(ReaderRejection.PageIndexUnavailable)
            coroutineScope {
                val task = async(start = CoroutineStart.LAZY) { navigate(ReadNavigation.Position(position), NavigationOptions()) }
                globalIndexNavigation = task
                try { task.await() }
                catch (_: GlobalIndexInvalidated) {
                    currentCoroutineContext().ensureActive()
                    NavigationResult.Rejected(ReaderRejection.PageIndexUnavailable)
                } finally { if (globalIndexNavigation === task) globalIndexNavigation = null }
            }
        }
    }
    fun goToGlobalPage(pageNumber: Long, callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { goToGlobalPage(pageNumber) }

    protected abstract val supportedModes: Set<ReadingMode>
    override fun isPageTurnEffectActive() = readingMode == ReadingMode.PAGED && presentation?.supportsPageTurnEffects != false
    override fun onPageTurnEffectFallback(id: String, reason: EffectFallbackReason) {
        dispatch(captureEffectConfigurationOwnership()) { it.onEffectFallback(this, id, "none", reason) }
    }
    override fun onPageTurnEffectError(error: ReaderError) {
        publishReaderError(error, captureEffectConfigurationOwnership())
    }
    internal override fun onNavigationCleanupError(error: ReaderError, stillCurrent: () -> Boolean) {
        publishReaderError(error, stillCurrent)
    }
    override fun turnDirection(target: ReadNavigation<ReadingPosition>, from: ReadingPosition?, to: ReadingPosition): TurnDirection {
        val book = currentBook ?: return super.turnDirection(target, from, to)
        if (from == null) return super.turnDirection(target, from, to)
        val oldChapter = book.chapters.indexOfFirst { it.chapterId == from.chapterId }
        val newChapter = book.chapters.indexOfFirst { it.chapterId == to.chapterId }
        return if (newChapter < oldChapter || newChapter == oldChapter && (to.pageNumberHint ?: 1) < (from.pageNumberHint ?: 1)) TurnDirection.Backward else TurnDirection.Forward
    }
    protected abstract fun createPresentation(mode: ReadingMode, host: BookPresentationHost): BookPresentation
    private fun bookPresentation(mode: ReadingMode, host: BookPresentationHost): BookPresentation =
        configuredPresentationFactory?.create(context, mode, coordinator.source, host)
            ?: presentationSource?.createPresentation(context, mode, host) ?: createPresentation(mode, host)

    suspend fun setReadingMode(mode: ReadingMode): ReaderResult<ModeChange> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        if (mode !in supportedModes) return ReaderResult.Rejected(ReaderRejection.Unsupported)
        val old = readingMode
        if (old == mode) {
            modeExplicitlySet = true
            return ReaderResult.Success(ModeChange(old, mode, currentPosition, Restoration.Exact))
        }
        if (requestedMode != null || navigationState != ReadNavigationState.Idle) return ReaderResult.Rejected(ReaderRejection.Busy)
        val position = currentPosition
        if (position == null) {
            if (currentBook != null && phase != ReaderPhase.Empty) return ReaderResult.Rejected(ReaderRejection.NotReady)
            modeExplicitlySet = true
            clearContentAdapter()
            readingMode = mode
            val change = ModeChange(old, mode, null, Restoration.Exact)
            publishState(); dispatch { it.onReadingModeChanged(this, change) }
            return ReaderResult.Success(change)
        }
        requestedMode = mode
        val generation = contentGeneration
        requestedModeGeneration = generation
        try {
            return when (val result = navigateWithReason(ReadNavigation.Position(position), NavigationOptions(animated = false), PositionChangeReason.ModeChange)) {
                is NavigationResult.Moved, is NavigationResult.Unchanged -> {
                    if (generation != contentGeneration) return ReaderResult.Superseded
                    modeExplicitlySet = true
                    clearSelection()
                    val change = ModeChange(old, readingMode, currentPosition, Restoration.Exact)
                    dispatch { it.onReadingModeChanged(this, change) }
                    ReaderResult.Success(change)
                }
                is NavigationResult.Failed -> ReaderResult.Failure(result.error)
                is NavigationResult.Rejected -> ReaderResult.Rejected(result.reason)
                NavigationResult.Superseded -> ReaderResult.Superseded
            }
        } finally { requestedMode = null }
    }
    fun setReadingMode(mode: ReadingMode, callback: ReaderCallback<ReaderResult<ModeChange>>): ReaderRequest = submit(callback) { setReadingMode(mode) }

    final override fun createContentAdapter(): ReadContentAdapter<ReadingPosition> {
        val root = FrameLayout(context)
        val placeholder = FrameLayout(context)
        val placeholders = org.peyilo.ireadview.internal.render.ReaderPlaceholderController(placeholder) { placeholderFactory }
        placeholderController = placeholders
        var genericHost: ReadContentHost<ReadingPosition>? = null
        val host = object : BookPresentationHost {
            override val presentationComponents get() = this@BookReadView.presentationComponents
            override val pageCacheMode get() = this@BookReadView.pageCacheMode
            override val generation get() = contentGeneration
            override val viewport get() = genericHost?.viewport ?: viewportSnapshot()
            override val committedLocation get() = currentPosition
            override val book get() = currentBook
            override val style get() = this@BookReadView.style
            override val inputOptions get() = this@BookReadView.inputOptions
            override fun reportBodyTouch(event: MotionEvent): Boolean {
                val view = presentation?.contentView ?: return false
                val offset = Rect(); offsetDescendantRectToMyCoords(view, offset)
                val local = MotionEvent.obtain(event)
                return try {
                    local.offsetLocation(offset.left.toFloat(), offset.top.toFloat())
                    this@BookReadView.onTouchEvent(local)
                } finally { local.recycle() }
            }
            override fun reportDocumentSelection(selected: DocumentTextSelection?, actions: org.peyilo.ireadview.spi.selection.DocumentSelectionHandle?,
                bounds: android.graphics.RectF?, generation: Long): Boolean {
                if (isReleased || !isEnabled || generation != contentGeneration) return false
                val view = presentation?.contentView ?: return false
                val offset = Rect(); offsetDescendantRectToMyCoords(view, offset)
                val area = bounds?.let { android.graphics.RectF(it).apply { offset(offset.left.toFloat(), offset.top.toFloat()) } }
                return bindDocumentSelection(selected, actions, area?.let { Rect().apply { it.roundOut(this) } }, generation)
            }
            override val pageFactory get() = this@BookReadView.pageFactory
            override val pageChromeProvider get() = pageChromeBridge
            override val placeholderFactory get() = this@BookReadView.placeholderFactory
            override suspend fun retryChapter(chapterId: String) = this@BookReadView.retryChapter(chapterId)
            override fun requestReaderAction(action: ReaderAction, position: ReadingPosition, generation: Long): Boolean {
                checkMainThread()
                if (isReleased || generation != contentGeneration || position != currentLocation) return false
                return this@BookReadView.requestReaderAction(action)
            }
            override val selection get() = this@BookReadView.selection
            override val documentSelection get() = this@BookReadView.documentSelection?.selected
            override fun clearSelection() = this@BookReadView.clearSelection()
            override fun clearAccessibilitySelection(generation: Long): Boolean {
                checkMainThread()
                if (isReleased || !isEnabled || generation != contentGeneration || navigationState != ReadNavigationState.Idle) return false
                this@BookReadView.clearSelection()
                return true
            }
            override fun reportSelectionDrag(active: Boolean) {
                draggingSelection = active
                if (active) selectionToolbar.pause()
                else {
                    selection?.let { showSelectionToolbar(it, selectionRevision) }
                    this@BookReadView.documentSelection?.takeIf { it.actions.isValid() }?.let { selectionToolbar.show(it.selected, it.actions, it.bounds) }
                }
            }
            override suspend fun requestSelectionPage(chapterId: String, pageNumber: Int, generation: Long): NavigationResult<ReadingPosition> =
                requestSelectionPage(chapterId, pageNumber, generation) { true }
            override suspend fun requestSelectionPage(chapterId: String, pageNumber: Int, generation: Long, isCurrent: () -> Boolean): NavigationResult<ReadingPosition> {
                if (generation != contentGeneration) return NavigationResult.Superseded
                if (currentSelection?.chapterId != chapterId || currentPosition?.chapterId != chapterId) return NavigationResult.Rejected(ReaderRejection.InvalidSelection)
                val book = currentBook ?: return NavigationResult.Rejected(ReaderRejection.NoBook)
                val target = presentation?.positionForSelectionPage(chapterId, pageNumber)
                    ?: ReadingPosition(book.bookId, chapterId, pageNumberHint = pageNumber)
                return navigateWithReason(ReadNavigation.Position(target), NavigationOptions(animated = false, respectInterval = false), PositionChangeReason.UserNavigation) {
                    generation == contentGeneration && !isReleased && isEnabled && isCurrent()
                }
            }
            override suspend fun requestAccessibilityNavigation(target: ReadNavigation<ReadingPosition>, generation: Long): NavigationResult<ReadingPosition> {
                return requestAccessibilityNavigation(target, generation) { true }
            }
            override suspend fun requestAccessibilityNavigation(target: ReadNavigation<ReadingPosition>, generation: Long,
                isCurrent: () -> Boolean): NavigationResult<ReadingPosition> {
                checkMainThread()
                if (generation != contentGeneration) return NavigationResult.Superseded
                if (isReleased || !isEnabled || !isAttachedToWindow) return NavigationResult.Rejected(ReaderRejection.NotReady)
                return navigateWithReason(target, NavigationOptions(animated = false, respectInterval = false), PositionChangeReason.UserNavigation) {
                    generation == contentGeneration && !isReleased && isEnabled && isAttachedToWindow && isCurrent()
                }
            }
            override fun reportSelection(selection: TextSelection, generation: Long): ReaderResult<Unit> {
                if (generation != contentGeneration) return ReaderResult.Superseded
                if (!isEnabled) return ReaderResult.Rejected(ReaderRejection.NotReady)
                if (navigationState != ReadNavigationState.Idle) return ReaderResult.Rejected(ReaderRejection.Busy)
                return selectText(selection)
            }
            override fun documentAnnotations(chapterId: String): List<DocumentRangedAnnotation> =
                annotations?.items?.filterIsInstance<DocumentRangedAnnotation>()?.filter { it.selection.chapterId == chapterId }.orEmpty()
            override fun reportDocumentAnnotationStatus(annotation: DocumentRangedAnnotation, status: AnnotationStatus, generation: Long) {
                if (generation != contentGeneration || isReleased) return
                if (annotationStore.reportStatus(annotation, status)) {
                    val revision = annotationStore.revision
                    dispatch { if (generation == contentGeneration && revision == annotationStore.revision) it.onAnnotationStatusChanged(this@BookReadView, annotation.id, status) }
                    if (generation == contentGeneration && revision == annotationStore.revision && isAttachedToWindow && !draggingSelection) {
                        this@BookReadView.documentSelection?.takeIf { it.actions.isValid() }?.let { selectionToolbar.show(it.selected, it.actions, it.bounds) }
                    }
                }
            }
            override fun validHighlights(chapterId: String): List<Highlight> = annotations?.highlights?.filter {
                it.range.chapterId == chapterId && annotationStore.status(it.id) == AnnotationStatus.Valid
            } ?: emptyList()
            override fun validUnderlines(chapterId: String): List<Underline> = annotations?.underlines?.filter {
                it.range.chapterId == chapterId && annotationStore.status(it.id) == AnnotationStatus.Valid
            } ?: emptyList()
            override fun reportHighlightStyleFallback(id: String, styleId: String) {
                dispatch { it.onHighlightStyleFallback(this@BookReadView, id, styleId) }
            }
            override fun reportHighlightTapped(id: String): Boolean {
                val highlight = annotations?.allHighlights?.firstOrNull { it.id == id } ?: return false
                if (annotationStore.status(id) != AnnotationStatus.Valid) return false
                dispatch { it.onHighlightTapped(this@BookReadView, highlight) }
                return true
            }
            override fun reportLinkTapped(target: LinkTarget, chapterId: String, generation: Long): Boolean =
                activateLink(target, chapterId, generation)
            override fun reportTap(xPx: Float, yPx: Float) {
                val view = presentation?.contentView ?: return
                val offset = Rect(); offsetDescendantRectToMyCoords(view, offset)
                dispatchContentTap(xPx + offset.left, yPx + offset.top)
            }
            override fun reportSelectionGeometryChanged() {
                if (!draggingSelection && isAttachedToWindow && navigationState == ReadNavigationState.Idle) selection?.let { showSelectionToolbar(it, selectionRevision) }
            }
            override suspend fun requestNavigation(target: ReadNavigation<ReadingPosition>) = navigate(target)
            override fun reportVisiblePosition(location: ReadingPosition, generation: Long): ReaderResult<Unit> {
                if (location.bookId != currentBook?.bookId) return ReaderResult.Rejected(ReaderRejection.BookMismatch)
                if (currentBook?.chapters?.none { it.chapterId == location.chapterId } != false) return ReaderResult.Rejected(ReaderRejection.UnknownChapter)
                return genericHost?.reportVisiblePosition(location, generation) ?: ReaderResult.Rejected(ReaderRejection.NotReady)
            }
            override fun reportError(error: ReaderError) = reportReaderError(error)
            override fun reportNavigationCapabilitiesChanged(generation: Long) { genericHost?.reportNavigationCapabilitiesChanged(generation) }
            override suspend fun getChapter(chapterId: String) = coordinator.getChapter(chapterId)
            override suspend fun layoutChapter(chapterId: String, viewport: ReadViewport): ReaderResult<ChapterLayout> {
                val metrics = resources.displayMetrics
                val bodyPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.body.textSizeSp, metrics)
                val titlePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.title.textSizeSp, metrics)
                return coordinator.layoutChapter(chapterId, viewport, style, bodyPx, titlePx)
            }
        }
        var content = bookPresentation(readingMode, host)
        var contentVersion = presentationConfigurationVersion
        presentation = content
        root.addView(content.contentView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        root.addView(placeholder, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        return object : ReadContentAdapter<ReadingPosition>, ReadVisualHandoff<ReadingPosition> {
            override suspend fun prepareVisualHandoff(candidate: PreparedRead<ReadingPosition>) {
                if (candidate is ModeCandidate) candidate.owner.prepareVisualHandoff(candidate.prepared)
                else content.prepareVisualHandoff(candidate)
            }
            override val currentSurface get() = content.currentSurface
            override fun navigationAvailability(target: ReadNavigation<ReadingPosition>) = content.navigationAvailability(target)
            override val contentView: View get() = root
            override fun attach(host: ReadContentHost<ReadingPosition>) { genericHost = host; content.attach(host) }
            override fun onViewportChanged(viewport: ReadViewport) = content.onViewportChanged(viewport)
            override suspend fun prepare(target: ReadNavigation<ReadingPosition>): ReadPreparation<ReadingPosition> {
                if (target is ReadNavigation.Position && target.location.documentLocator != null && presentationSource == null && configuredPresentationFactory == null)
                    return ReadPreparation.Rejected(ReaderRejection.Unsupported)
                val mode = requestedMode?.takeIf { requestedModeGeneration == contentGeneration }
                    ?: readingMode.takeIf { contentVersion != presentationConfigurationVersion } ?: return content.prepare(target)
                val nextVersion = presentationConfigurationVersion
                val next = bookPresentation(mode, host)
                check(next.contentView.parent == null) { "Presentation must own a fresh root" }
                next.contentView.visibility = View.INVISIBLE
                root.addView(next.contentView, 0, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                var transferred = false
                try {
                    next.attach(genericHost ?: host)
                    next.onViewportChanged(host.viewport)
                    return when (val result = next.prepare(target)) {
                        is ReadPreparation.Ready -> {
                            transferred = true
                            ReadPreparation.Ready(ModeCandidate(next, result.candidate, mode, nextVersion))
                        }
                        else -> result
                    }
                } finally { if (!transferred) { next.detach(); root.removeView(next.contentView) } }
            }
            override suspend fun show(candidate: PreparedRead<ReadingPosition>): ReadingPosition {
                if (candidate !is ModeCandidate) return content.show(candidate)
                val actual = candidate.owner.show(candidate.prepared)
                currentCoroutineContext().ensureActive()
                check(candidate.generation == contentGeneration)
                val old = content
                content = candidate.owner; presentation = content; readingMode = candidate.mode
                contentVersion = candidate.version
                content.contentView.visibility = View.VISIBLE
                candidate.retained = true
                val cleanup = runCatching { old.detach() }
                root.removeView(old.contentView)
                cleanup.exceptionOrNull()?.let { error ->
                    val generation = contentGeneration
                    post { if (generation == contentGeneration) reportReaderError(ReaderError(ReaderStage.NAVIGATION, "PresentationCleanupFailed", error.message ?: "Previous presentation cleanup failed", retryable = false, cause = error)) }
                }
                return actual
            }
            override fun detach() { placeholders.clear(); content.detach(); genericHost = null; if (placeholderController === placeholders) placeholderController = null; presentation = null }
            inner class ModeCandidate(val owner: BookPresentation, val prepared: PreparedRead<ReadingPosition>, val mode: ReadingMode, val version: Long) : PreparedRead<ReadingPosition> {
                override val target get() = prepared.target
                override val generation get() = prepared.generation
                var retained = false
                override fun dispose() {
                    prepared.dispose()
                    if (!retained) { owner.detach(); root.removeView(owner.contentView) }
                }
            }
        }
    }

    suspend fun openBook(source: BookSource, request: OpenBookRequest = OpenBookRequest()): OpenResult {
        checkMainThread()
        if (isReleased) return OpenResult.Failed(ReaderError(ReaderStage.BOOK, "Released", "Widget has been released", retryable = false))
        if (request.initialPosition != null && request.initialPosition.bookId != source.bookId) {
            return OpenResult.Failed(ReaderError(ReaderStage.BOOK, "BookMismatch", "Initial position belongs to another book", retryable = false))
        }
        val sequence = ++openSequence
        lastOpenRequest = request
        invalidateContentGeneration()
        if (sequence != openSequence || isReleased) return OpenResult.Superseded
        resetCommittedLocation()
        if (sequence != openSequence || isReleased) return OpenResult.Superseded
        clearContentAdapter()
        if (sequence != openSequence || isReleased) return OpenResult.Superseded
        lastError = null
        presentationSource = source as? PresentationBookSource
        ensureContentAdapter()
        if (sequence != openSequence || isReleased) return OpenResult.Superseded
        return try {
            ownedOperation {
                val book = when (val opened = coordinator.open(source)) {
                    is ReaderResult.Success -> opened.value
                    is ReaderResult.Failure -> {
                        reportReaderError(opened.error)
                        return@ownedOperation if (sequence == openSequence) OpenResult.Failed(opened.error) else OpenResult.Superseded
                    }
                    else -> return@ownedOperation OpenResult.Superseded
                }
                if (sequence != openSequence) return@ownedOperation OpenResult.Superseded
                if (book.chapters.isEmpty()) {
                    phase = ReaderPhase.Empty; publishState()
                    return@ownedOperation if (sequence == openSequence) OpenResult.Empty(book) else OpenResult.Superseded
                }
                val saved = restoredPosition?.takeIf { it.bookId == book.bookId }
                restoredPosition = null
                val explicit = request.initialPosition ?: saved
                val requestedChapter = request.initialChapterNumber
                val initialChapter = if (requestedChapter in 1..book.chapters.size) book.chapters[requestedChapter - 1] else null
                val initialRejection = if (initialChapter == null) ReaderRejection.UnknownChapter else ReaderRejection.InvalidPage
                val initial = explicit ?: initialChapter?.takeIf { request.initialPageNumber > 0 }?.let {
                    ReadingPosition(book.bookId, it.chapterId, pageNumberHint = request.initialPageNumber)
                }
                phase = if (width == 0 || height == 0) ReaderPhase.AwaitingViewport else ReaderPhase.LoadingChapter
                publishState()
                if (sequence != openSequence) return@ownedOperation OpenResult.Superseded
                suspend fun initialNavigation(target: ReadNavigation<ReadingPosition>, reason: PositionChangeReason): NavigationResult<ReadingPosition> {
                    while (sequence == openSequence) {
                        val navigation = navigateWithReason(target, NavigationOptions(force = true), reason)
                        if (navigation != NavigationResult.Superseded) return navigation
                        yield()
                    }
                    return NavigationResult.Superseded
                }
                var result: NavigationResult<ReadingPosition> = if (initial == null) NavigationResult.Rejected(initialRejection)
                    else initialNavigation(ReadNavigation.Position(initial), if (explicit != null) PositionChangeReason.Restore else PositionChangeReason.Open)
                if (sequence != openSequence) return@ownedOperation OpenResult.Superseded
                var fallback: ReaderRejection? = null
                if (result is NavigationResult.Rejected && request.initialFallback == InitialFallback.BOOK_START) {
                    fallback = result.reason
                    result = initialNavigation(ReadNavigation.First, PositionChangeReason.Restore)
                }
                if (sequence != openSequence) return@ownedOperation OpenResult.Superseded
                when (val final = result) {
                    is NavigationResult.Moved -> OpenResult.Opened(final.to, fallback?.let { Restoration.Fallback(it, final.to) } ?: Restoration.Exact)
                    is NavigationResult.Unchanged -> final.location?.let { actual ->
                        OpenResult.Opened(actual, fallback?.let { Restoration.Fallback(it, actual) } ?: Restoration.Exact)
                    } ?: OpenResult.Failed(ReaderError(ReaderStage.NAVIGATION, "NoPosition", "No readable position"))
                    is NavigationResult.Failed -> {
                        reportReaderError(final.error)
                        if (sequence == openSequence) OpenResult.Failed(final.error) else OpenResult.Superseded
                    }
                    is NavigationResult.Rejected -> {
                        val error = ReaderError(ReaderStage.NAVIGATION, final.reason.name, "Cannot open requested position: ${final.reason}")
                        reportReaderError(error)
                        if (sequence == openSequence) OpenResult.Failed(error) else OpenResult.Superseded
                    }
                    NavigationResult.Superseded -> OpenResult.Superseded
                }
            }
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (sequence != openSequence) OpenResult.Superseded else throw cancelled
        }
    }

    @JvmOverloads
    fun openBook(source: BookSource, request: OpenBookRequest = OpenBookRequest(), callback: ReaderCallback<OpenResult>): ReaderRequest = submit(callback) { openBook(source, request) }

    /** Refreshes the current source's catalogue and returns after visible-position recovery. */
    suspend fun reloadBook(): ReaderResult<BookDescriptor> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        if (coordinator.source == null) return ReaderResult.Rejected(ReaderRejection.NoBook)
        val sequence = ++openSequence
        val previousPhase = phase
        var installed = false
        phase = ReaderPhase.LoadingBook; lastError = null; publishState()
        if (sequence != openSequence) return ReaderResult.Superseded
        return try {
            ownedOperation {
                val loaded = coordinator.readCatalogue()
                if (sequence != openSequence) return@ownedOperation ReaderResult.Superseded
                val book = when (loaded) {
                    is ReaderResult.Success -> loaded.value
                    is ReaderResult.Failure -> {
                        reportReaderError(loaded.error)
                        return@ownedOperation if (sequence == openSequence) loaded else ReaderResult.Superseded
                    }
                    is ReaderResult.Rejected -> return@ownedOperation loaded
                    ReaderResult.Superseded -> return@ownedOperation loaded
                }
                // Capture the latest committed position: the reader can navigate while IO runs.
                val position = currentPosition
                val retained = coordinator.reusableChapters(book)
                invalidateContentGeneration()
                if (sequence != openSequence) return@ownedOperation ReaderResult.Superseded
                if (position == null || position.chapterId !in retained) {
                    resetCommittedLocation(PositionChangeReason.Restore)
                    if (sequence != openSequence) return@ownedOperation ReaderResult.Superseded
                    clearContentAdapter()
                    ensureContentAdapter()
                }
                installed = true
                val installation = coordinator.installCatalogue(book, retained)
                if (installation !is ReaderResult.Success) return@ownedOperation when (installation) {
                    is ReaderResult.Failure -> installation
                    is ReaderResult.Rejected -> installation
                    else -> ReaderResult.Superseded
                }
                if (sequence != openSequence) return@ownedOperation ReaderResult.Superseded
                if (book.chapters.isEmpty()) {
                    phase = ReaderPhase.Empty; publishState()
                    return@ownedOperation if (sequence == openSequence) ReaderResult.Success(book) else ReaderResult.Superseded
                }
                phase = ReaderPhase.LoadingChapter; publishState()
                if (sequence != openSequence) return@ownedOperation ReaderResult.Superseded
                var fallback: ReaderRejection? = null
                val hasChapter = book.chapters.any { it.chapterId == position?.chapterId }
                val target = if (position != null && hasChapter) ReadNavigation.Position(position.copy(contentRevision = null)) else ReadNavigation.First
                if (position != null && !hasChapter) fallback = ReaderRejection.UnknownChapter
                var result = navigateWithReason(target, NavigationOptions(force = true), PositionChangeReason.Restore)
                if (sequence != openSequence) return@ownedOperation ReaderResult.Superseded
                if (result is NavigationResult.Rejected && (result.reason == ReaderRejection.InvalidAnchor || result.reason == ReaderRejection.InvalidPage)) {
                    fallback = result.reason
                    val chapter = position?.chapterId ?: book.chapters.first().chapterId
                    result = navigateWithReason(ReadNavigation.Position(ReadingPosition(book.bookId, chapter, pageNumberHint = 1)), NavigationOptions(force = true), PositionChangeReason.Restore)
                }
                if (sequence != openSequence) return@ownedOperation ReaderResult.Superseded
                when (val final = result) {
                    is NavigationResult.Moved, is NavigationResult.Unchanged -> {
                        phase = ReaderPhase.Ready; lastError = null; publishState()
                        if (sequence != openSequence) return@ownedOperation ReaderResult.Superseded
                        currentPosition?.let { actual ->
                            dispatch { it.onPositionRestored(this, fallback?.let { reason -> Restoration.Fallback(reason, actual) } ?: Restoration.Exact, PositionChangeReason.Restore) }
                            if (sequence == openSequence) coordinator.preloadNear(actual)
                        }
                        if (sequence == openSequence) ReaderResult.Success(book) else ReaderResult.Superseded
                    }
                    is NavigationResult.Failed -> {
                        reportReaderError(final.error)
                        if (sequence == openSequence) ReaderResult.Failure(final.error) else ReaderResult.Superseded
                    }
                    is NavigationResult.Rejected -> {
                        reportReaderError(ReaderError(ReaderStage.NAVIGATION, final.reason.name, "Cannot restore position after catalogue reload"))
                        if (sequence == openSequence) ReaderResult.Rejected(final.reason) else ReaderResult.Superseded
                    }
                    NavigationResult.Superseded -> ReaderResult.Superseded
                }
            }
        } catch (cancelled: CancellationException) {
            if (sequence == openSequence && !isReleased) {
                if (installed && currentPosition == null) reportReaderError(ReaderError(ReaderStage.NAVIGATION, "ReloadCancelled", "Catalogue updated; position recovery was cancelled"))
                else { phase = if (currentPosition != null) ReaderPhase.Ready else previousPhase; publishState() }
            }
            throw cancelled
        }
    }
    fun reloadBook(callback: ReaderCallback<ReaderResult<BookDescriptor>>): ReaderRequest = submit(callback) { reloadBook() }

    fun closeBook() {
        checkMainThread()
        if (isReleased) return
        val sequence = ++openSequence
        invalidateContentGeneration(superseded = false)
        if (sequence != openSequence || isReleased) return
        resetCommittedLocation()
        if (sequence != openSequence || isReleased) return
        coordinator.close()
        if (sequence != openSequence || isReleased) return
        clearContentAdapter()
        if (sequence != openSequence || isReleased) return
        presentationSource = null
        if (isAttachedToWindow) ensureContentAdapter()
        if (sequence != openSequence || isReleased) return
        phase = ReaderPhase.Closed; lastError = null
        publishState()
    }

    suspend fun goToPosition(position: ReadingPosition) = navigate(ReadNavigation.Position(position))
    suspend fun goToChapter(chapterId: String, pageNumber: Int = 1): NavigationResult<ReadingPosition> {
        if (pageNumber < 1) return NavigationResult.Rejected(ReaderRejection.InvalidPage)
        val book = currentBook ?: return NavigationResult.Rejected(ReaderRejection.NoBook)
        return goToPosition(ReadingPosition(book.bookId, chapterId, pageNumberHint = pageNumber))
    }
    suspend fun goToChapterNumber(chapterNumber: Int, pageNumber: Int = 1): NavigationResult<ReadingPosition> {
        val book = currentBook ?: return NavigationResult.Rejected(ReaderRejection.NoBook)
        val chapter = book.chapters.getOrNull(chapterNumber - 1) ?: return NavigationResult.Rejected(ReaderRejection.UnknownChapter)
        return goToChapter(chapter.chapterId, pageNumber)
    }
    suspend fun nextChapter(): NavigationResult<ReadingPosition> = adjacentChapter(1)
    suspend fun previousChapter(): NavigationResult<ReadingPosition> = adjacentChapter(-1)
    private suspend fun adjacentChapter(delta: Int): NavigationResult<ReadingPosition> {
        val book = currentBook ?: return NavigationResult.Rejected(ReaderRejection.NoBook)
        val current = currentPosition ?: return NavigationResult.Rejected(ReaderRejection.NotReady)
        val index = book.chapters.indexOfFirst { it.chapterId == current.chapterId }
        val target = book.chapters.getOrNull(index + delta) ?: return NavigationResult.Rejected(ReaderRejection.AtBoundary)
        return goToChapter(target.chapterId)
    }
    suspend fun goToPage(pageNumber: Int): NavigationResult<ReadingPosition> {
        val current = currentPosition ?: return NavigationResult.Rejected(ReaderRejection.NotReady)
        return goToChapter(current.chapterId, pageNumber)
    }
    suspend fun chapterFirstPage() = goToPage(1)
    suspend fun chapterLastPage(): NavigationResult<ReadingPosition> {
        val current = currentPosition ?: return NavigationResult.Rejected(ReaderRejection.NotReady)
        val pages = coordinator.paginationState(current.chapterId) as? ChapterPaginationState.Ready ?: return NavigationResult.Rejected(ReaderRejection.NotReady)
        return goToPage(pages.pageCount)
    }

    @JvmOverloads
    fun goToChapter(chapterId: String, pageNumber: Int = 1, callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { goToChapter(chapterId, pageNumber) }
    @JvmOverloads
    fun goToChapterNumber(chapterNumber: Int, pageNumber: Int = 1, callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { goToChapterNumber(chapterNumber, pageNumber) }
    fun goToPosition(position: ReadingPosition, callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { goToPosition(position) }
    fun goToPage(pageNumber: Int, callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { goToPage(pageNumber) }
    fun nextChapter(callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { nextChapter() }
    fun previousChapter(callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { previousChapter() }
    fun chapterFirstPage(callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { chapterFirstPage() }
    fun chapterLastPage(callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { chapterLastPage() }

    suspend fun applyStyle(style: ReaderStyle): ReaderResult<StyleApplication> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        styleExplicitlySet = true
        if (this.style == style) return ReaderResult.Success(StyleApplication(false, currentPosition))
        val old = this.style
        if (old.copy(highlightStyles = style.highlightStyles, selectionStyle = style.selectionStyle) == style) {
            val generation = contentGeneration
            this.style = style
            presentation?.onDecorationsChanged()
            if (generation != contentGeneration || isReleased || this.style != style) return ReaderResult.Superseded
            return ReaderResult.Success(StyleApplication(false, currentPosition))
        }
        this.style = style
        val generation = contentGeneration + 1
        invalidateContentGeneration()
        if (generation != contentGeneration || isReleased) return ReaderResult.Superseded
        coordinator.invalidateLayouts()
        if (generation != contentGeneration || isReleased) return ReaderResult.Superseded
        val position = currentPosition ?: return ReaderResult.Success(StyleApplication(true, null))
        fun rollback(): Boolean {
            if (generation != contentGeneration || isReleased) return false
            // Decoration-only edits can be accepted while this layout is being prepared.
            this.style = old.copy(highlightStyles = this.style.highlightStyles, selectionStyle = this.style.selectionStyle)
            val rollbackGeneration = contentGeneration + 1
            invalidateContentGeneration()
            if (rollbackGeneration != contentGeneration || isReleased) return false
            coordinator.invalidateLayouts()
            if (rollbackGeneration != contentGeneration || isReleased) return false
            phase = ReaderPhase.Ready
            publishState()
            if (rollbackGeneration != contentGeneration || isReleased) return false
            restoreVisiblePosition()
            return rollbackGeneration == contentGeneration && !isReleased
        }
        val result = try {
            navigateWithReason(ReadNavigation.Position(position), NavigationOptions(force = true), PositionChangeReason.Relayout)
        } catch (cancelled: CancellationException) {
            // Only the owner of this style transaction may roll back a staged configuration.
            rollback()
            throw cancelled
        }
        if (generation != contentGeneration || isReleased) return ReaderResult.Superseded
        return when (result) {
            is NavigationResult.Moved, is NavigationResult.Unchanged -> {
                phase = ReaderPhase.Ready
                publishState()
                if (generation != contentGeneration || isReleased) ReaderResult.Superseded
                else ReaderResult.Success(StyleApplication(true, currentPosition))
            }
            NavigationResult.Superseded -> ReaderResult.Superseded
            else -> {
                if (!rollback()) return ReaderResult.Superseded
                when (result) {
                    is NavigationResult.Failed -> ReaderResult.Failure(result.error)
                    is NavigationResult.Rejected -> ReaderResult.Rejected(result.reason)
                    else -> ReaderResult.Superseded
                }
            }
        }
    }
    fun applyStyle(style: ReaderStyle, callback: ReaderCallback<ReaderResult<StyleApplication>>): ReaderRequest = submit(callback) { applyStyle(style) }
    protected fun setInitialStyle(style: ReaderStyle) { check(currentBook == null); this.style = style }
    fun setPlaceholderFactory(factory: ReaderPlaceholderFactory) {
        checkMainThread()
        if (isReleased) return
        placeholderFactory = factory
        placeholderController?.clear()
        presentation?.onPlaceholdersChanged()
        publishState()
    }
    protected fun setInitialReadingMode(mode: ReadingMode) {
        check(currentBook == null && presentation == null)
        require(mode in supportedModes)
        readingMode = mode
    }

    /** Set state-driven content/actions for default chrome; null restores built-in content.
     * Main thread. Refreshes only the header/footer, never repaginates or changes location.
     * Custom templates can read ReaderPageContext.chrome and implement refreshChrome().
     */
    fun setPageChromeProvider(provider: ReaderPageChromeProvider?) {
        checkMainThread(); if (isReleased) return
        pageChromeProvider = provider
        refreshPageChrome()
    }
    /** Configure the default header/footer's six positions and the separate back button.
     * Replaces the current chrome provider with a constant configuration. For content
     * depending on host state use setPageChromeProvider and refreshPageChrome instead.
     * Built-in chapter/page/time/battery contents still resolve per page automatically.
     */
    fun setPageChrome(chrome: ReaderPageChrome) {
        setPageChromeProvider(ReaderPageChromeProvider { chrome })
    }
    /** Re-read captured host state without replacing the page factory or text layout.
     * During a page transition this is deferred until Idle so snapshots stay consistent.
     * Single-line text and actions may change; use style/factory APIs to change slot geometry.
     */
    fun refreshPageChrome() {
        checkMainThread(); if (isReleased || refreshingChrome) return
        chromeRefreshPending = true
        if (navigationState != ReadNavigationState.Idle) return
        chromeRefreshPending = false
        refreshingChrome = true
        try { presentation?.onPageChromeChanged() } finally { refreshingChrome = false }
        pageClock?.update()
    }

    fun setPageFactory(factory: ReaderPageFactory) {
        checkMainThread(); if (isReleased) return
        pageFactory = factory
        refreshLayoutConfiguration()
    }
    fun setBlockLayoutProviders(providers: List<BlockLayoutProvider>) {
        checkMainThread(); if (isReleased) return
        require(providers.map { it.id }.toSet().size == providers.size)
        coordinator.providers = providers.toList()
        refreshLayoutConfiguration()
    }
    val imageLoadingOptions: org.peyilo.ireadview.spi.content.ReaderImageLoadingOptions
        get() = coordinator.imageLoadingOptions

    /** Native ImageBlock pipeline only; document engines own their resource loading.
     * Main thread. Replacing the strategy invalidates native layouts and the whole-book index,
     * then restores the source anchor without reopening the source. Host owns loader lifetime.
     */
    fun setImageLoadingOptions(options: org.peyilo.ireadview.spi.content.ReaderImageLoadingOptions): ReaderResult<Unit> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        if (navigationState != ReadNavigationState.Idle || draggingSelection || requestedMode != null || phase == ReaderPhase.LoadingBook)
            return ReaderResult.Rejected(ReaderRejection.Busy)
        if (coordinator.imageLoadingOptions != options) {
            coordinator.imageLoadingOptions = options
            refreshLayoutConfiguration()
        }
        return if (isReleased || coordinator.imageLoadingOptions != options) ReaderResult.Superseded else ReaderResult.Success(Unit)
    }
    private fun refreshLayoutConfiguration() = refreshPresentationConfiguration(invalidateLayouts = true)
    private fun refreshPresentationConfiguration(invalidateLayouts: Boolean) {
        val generation = contentGeneration + 1
        invalidateContentGeneration()
        if (generation != contentGeneration || isReleased) return
        if (invalidateLayouts) coordinator.invalidateLayouts()
        if (generation != contentGeneration || isReleased) return
        restoreVisiblePosition()
    }
    fun setLoadingOptions(options: LoadingOptions) {
        checkMainThread()
        if (!isReleased) { coordinator.setLoadingOptions(options); coordinator.preloadNear(currentPosition) }
    }
    fun getChapterState(chapterId: String) = coordinator.chapterState(chapterId)
    fun getChapterPagination(chapterId: String) = coordinator.paginationState(chapterId)

    suspend fun invalidateChapter(chapterId: String): ReaderResult<Unit> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        val book = currentBook ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        val sequence = openSequence
        if (getChapterState(chapterId) == null) return ReaderResult.Rejected(ReaderRejection.UnknownChapter)
        if (currentPosition?.chapterId == chapterId || currentPosition == null) invalidateContentGeneration()
        if (sequence != openSequence || currentBook !== book) return ReaderResult.Superseded
        val result = coordinator.invalidateChapter(chapterId)
        if (result !is ReaderResult.Success) return result
        if (sequence != openSequence || currentBook !== book) return ReaderResult.Superseded
        val position = currentPosition?.takeIf { it.chapterId == chapterId } ?: return result
        return refreshChapterPosition(position)
    }

    private suspend fun refreshChapterPosition(position: ReadingPosition): ReaderResult<Unit> {
        val generation = contentGeneration
        var navigation = navigateWithReason(ReadNavigation.Position(position.copy(contentRevision = null)), NavigationOptions(force = true), PositionChangeReason.ContentUpdate)
        if (generation != contentGeneration) return ReaderResult.Superseded
        val rejection = (navigation as? NavigationResult.Rejected)?.reason
        if (rejection == ReaderRejection.InvalidAnchor || rejection == ReaderRejection.InvalidPage) {
            navigation = navigateWithReason(ReadNavigation.Position(position.copy(anchor = null, contentRevision = null, pageNumberHint = 1)), NavigationOptions(force = true), PositionChangeReason.ContentUpdate)
            if (generation != contentGeneration) return ReaderResult.Superseded
            val actual = when (navigation) {
                is NavigationResult.Moved -> navigation.to
                is NavigationResult.Unchanged -> navigation.location
                else -> null
            }
            if (actual != null) dispatch { it.onPositionRestored(this, Restoration.Fallback(rejection, actual), PositionChangeReason.ContentUpdate) }
        }
        if (generation != contentGeneration) return ReaderResult.Superseded
        return when (navigation) {
            is NavigationResult.Failed -> {
                reportReaderError(navigation.error)
                if (generation == contentGeneration) ReaderResult.Failure(navigation.error) else ReaderResult.Superseded
            }
            is NavigationResult.Rejected -> ReaderResult.Rejected(navigation.reason)
            NavigationResult.Superseded -> ReaderResult.Superseded
            else -> ReaderResult.Success(Unit)
        }
    }
    fun invalidateChapter(chapterId: String, callback: ReaderCallback<ReaderResult<Unit>>): ReaderRequest = submit(callback) { invalidateChapter(chapterId) }

    /** Retries failed content/layout work. Healthy chapters are left cached. */
    suspend fun retryChapter(chapterId: String): ReaderResult<Unit> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        val book = currentBook ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        val sequence = openSequence
        val chapterState = getChapterState(chapterId) ?: return ReaderResult.Rejected(ReaderRejection.UnknownChapter)
        if (chapterState !is ChapterState.Failed && getChapterPagination(chapterId) !is ChapterPaginationState.Failed) return ReaderResult.Success(Unit)
        val position = currentPosition
        if (position?.chapterId == chapterId || position == null) {
            invalidateContentGeneration()
            if (sequence != openSequence || currentBook !== book) return ReaderResult.Superseded
            val invalidated = coordinator.invalidateChapter(chapterId)
            if (invalidated !is ReaderResult.Success) return invalidated
            if (sequence != openSequence || currentBook !== book) return ReaderResult.Superseded
            return refreshChapterPosition(position ?: ReadingPosition(book.bookId, chapterId, pageNumberHint = 1))
        }
        // Retry a failed neighbor without interrupting the visible chapter's navigation.
        val invalidated = coordinator.invalidateChapter(chapterId)
        if (invalidated !is ReaderResult.Success) return invalidated
        if (sequence != openSequence || currentBook !== book) return ReaderResult.Superseded
        return ownedOperation {
            val loaded = coordinator.getChapter(chapterId)
            if (sequence != openSequence || currentBook !== book) return@ownedOperation ReaderResult.Superseded
            when (loaded) {
                is ReaderResult.Success -> ReaderResult.Success(Unit)
                is ReaderResult.Failure -> {
                    reportReaderError(loaded.error)
                    if (sequence == openSequence && currentBook === book) loaded else ReaderResult.Superseded
                }
                is ReaderResult.Rejected -> loaded
                ReaderResult.Superseded -> ReaderResult.Superseded
            }
        }
    }
    fun retryChapter(chapterId: String, callback: ReaderCallback<ReaderResult<Unit>>): ReaderRequest = submit(callback) { retryChapter(chapterId) }

    /** Current selection for TXT, network text and EPUB alike. Main thread; null after clearing. */
    fun getSelection(): ReaderSelection? { checkMainThread(); return if (isReleased) null else currentSelection }
    fun getSelectedText(): String? = getSelection()?.text
    /** Capture a source-native navigation target for host-owned notes, sharing or bookmarks.
     * Main thread. Does not clear selection, create an annotation or move the reader.
     * Null for no active selection or an engine without a matching locator conversion.
     * Persist the selected text separately when the host needs quote validation on restore.
     */
    fun getSelectionPosition(): ReadingPosition? {
        checkMainThread()
        return when (val selected = getSelection()) {
            is TextSelection -> ReadingPosition(selected.bookId, selected.chapterId,
                selected.range.start, selected.range.contentRevision)
            is DocumentTextSelection -> presentation?.positionForDocumentSelection(selected)
            else -> null
        }
    }
    /** Defensive copy in Widget-local physical pixels. */
    fun getSelectionBounds(): android.graphics.RectF? {
        checkMainThread()
        documentSelection?.bounds?.let { return android.graphics.RectF(it) }
        val content = presentation ?: return null
        return content.selectionBounds()?.let { area ->
            val offset = Rect(); offsetDescendantRectToMyCoords(content.contentView, offset)
            android.graphics.RectF(area).apply { offset(offset.left.toFloat(), offset.top.toFloat()) }
        }
    }
    fun getSelectionHandleBounds(start: Boolean): android.graphics.RectF? {
        checkMainThread()
        val content = presentation ?: return null
        return content.selectionHandleBounds(start)?.let { area ->
            val offset = Rect(); offsetDescendantRectToMyCoords(content.contentView, offset)
            android.graphics.RectF(area).apply { offset(offset.left.toFloat(), offset.top.toFloat()) }
        }
    }
    /** Format-specific access only for callers that explicitly need source UTF-16 anchors. */
    fun getTextSelection(): TextSelection? { checkMainThread(); return selection }
    fun copySelectedText(): ReaderResult<Unit> = getSelectionActions()?.copyText() ?: ReaderResult.Rejected(ReaderRejection.InvalidSelection)
    @JvmOverloads fun highlightSelection(styleId: String = "default"): ReaderResult<HighlightAnnotation> =
        getSelectionActions()?.highlight(styleId) ?: ReaderResult.Rejected(ReaderRejection.InvalidSelection)
    @JvmOverloads fun underlineSelection(style: org.peyilo.ireadview.api.style.TextLineStyle = org.peyilo.ireadview.api.style.TextLineStyle()): ReaderResult<UnderlineAnnotation> =
        getSelectionActions()?.underline(style) ?: ReaderResult.Rejected(ReaderRejection.InvalidSelection)
    @JvmOverloads fun toggleSelectionHighlight(styleId: String = "default"): ReaderResult<Unit> =
        getSelectionActions()?.toggleHighlight(styleId) ?: ReaderResult.Rejected(ReaderRejection.InvalidSelection)
    @JvmOverloads fun toggleSelectionUnderline(style: org.peyilo.ireadview.api.style.TextLineStyle = org.peyilo.ireadview.api.style.TextLineStyle()): ReaderResult<Unit> =
        getSelectionActions()?.toggleUnderline(style) ?: ReaderResult.Rejected(ReaderRejection.InvalidSelection)
    private fun canPerformSelectionAccessibilityAction(): Boolean = !isReleased && isEnabled &&
        !draggingSelection && navigationState == ReadNavigationState.Idle && currentSelection != null &&
        presentation?.selectionBounds() != null

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        accessibleSelectionActions = if (canPerformSelectionAccessibilityAction()) getSelectionActions() else null
        if (accessibleSelectionActions != null) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_COPY, context.getString(R.string.iread_accessibility_copy)))
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.iread_action_highlight_selection, context.getString(R.string.iread_accessibility_highlight_selection)))
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.iread_action_clear_selection, context.getString(R.string.iread_accessibility_clear_selection)))
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_COPY || action == R.id.iread_action_highlight_selection || action == R.id.iread_action_clear_selection) {
            if (!canPerformSelectionAccessibilityAction()) return false
            val actions = accessibleSelectionActions ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_COPY -> actions.copyText() is ReaderResult.Success
                R.id.iread_action_highlight_selection -> actions.highlight() is ReaderResult.Success
                else -> { actions.clearSelection(); currentSelection == null }
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }
    /** Actions bound to the current selection, generation and revision; stale actions are rejected. */
    fun getSelectionActions(): SelectionActions? {
        checkMainThread()
        if (isReleased) return null
        return documentSelection?.actions ?: selection?.let { createSelectionActions(it, selectionRevision) }
    }
    private fun documentMarks(selected: DocumentTextSelection): List<DocumentRangedAnnotation> =
        annotations?.items?.filterIsInstance<DocumentRangedAnnotation>()?.filter {
            it.selection.bookId == selected.bookId && it.selection.chapterId == selected.chapterId &&
                it.selection.contentRevision == selected.contentRevision && annotationStore.status(it.id) == AnnotationStatus.Valid &&
                presentation?.documentSelectionCoveredBy(selected, it.selection) == true
        }.orEmpty()
    private fun toggleDocumentMark(selected: DocumentTextSelection, highlight: Boolean, add: () -> ReaderResult<Unit>): ReaderResult<Unit> {
        val items = documentMarks(selected).filter { if (highlight) it is HighlightAnnotation else it is UnderlineAnnotation }
        if (items.isEmpty()) return add()
        val binding = documentSelection
        val result = mutateAnnotations { items.forEach { annotationStore.removeAnnotation(it.id) }; ReaderResult.Success(Unit) }
        if (documentSelection === binding) clearSelection()
        return result
    }
    private inline fun <T> selectionOperation(code: String, action: () -> ReaderResult<T>): ReaderResult<T> = try {
        checkMainThread()
        action()
    } catch (error: Exception) {
        ReaderResult.Failure(ReaderError(ReaderStage.SELECTION, code, error.message ?: "Selection action failed", cause = error))
    }
    private fun bindDocumentSelection(selected: DocumentTextSelection?, delegate: org.peyilo.ireadview.spi.selection.DocumentSelectionHandle?, bounds: Rect?, generation: Long): Boolean {
        if (selected == null) {
            if (documentSelection != null) clearSelection()
            return true
        }
        if (selected.bookId != currentBook?.bookId || delegate == null) return false
        val old = documentSelection
        if (old?.selected == selected && old.generation == generation) {
            old.delegate = delegate
            if (old.bounds != bounds) { old.bounds = bounds; if (!draggingSelection) selectionToolbar.show(selected, old.actions, bounds) }
            return true
        }
        // Replacing a DOM range must not clear the newly selected range through old actions.
        documentSelection = null
        if (selection != null) clearSelection() else selectionToolbar.dismiss()
        if (isReleased || generation != contentGeneration || selected.bookId != currentBook?.bookId) return false
        val binding = DocumentSelectionBinding(selected, delegate, bounds, generation)
        fun valid(): Boolean { checkMainThread(); return !isReleased && isEnabled && contentGeneration == generation && documentSelection === binding && binding.delegate.isValid() }
        binding.actions = object : SelectionActions {
            override fun isValid() = valid()
            override fun hasHighlight() = valid() && documentMarks(selected).any { it is HighlightAnnotation }
            override fun hasUnderline() = valid() && documentMarks(selected).any { it is UnderlineAnnotation }
            override fun toggleHighlight(styleId: String): ReaderResult<Unit> =
                if (valid()) toggleDocumentMark(selected, true) { highlight(styleId).mapToUnit() } else ReaderResult.Rejected(ReaderRejection.InvalidSelection)
            override fun toggleUnderline(style: org.peyilo.ireadview.api.style.TextLineStyle): ReaderResult<Unit> =
                if (valid()) toggleDocumentMark(selected, false) { underline(style).mapToUnit() } else ReaderResult.Rejected(ReaderRejection.InvalidSelection)
            override fun highlight(styleId: String): ReaderResult<HighlightAnnotation> = selectionOperation("HighlightActionFailed") {
                if (!valid()) ReaderResult.Rejected(ReaderRejection.InvalidSelection) else {
                    val id = annotationIds.newHighlightId()
                    if (valid()) this@BookReadView.highlightSelection(id, styleId) else ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                }
            }
            override fun underline(style: org.peyilo.ireadview.api.style.TextLineStyle): ReaderResult<UnderlineAnnotation> = selectionOperation("UnderlineActionFailed") {
                if (!valid()) ReaderResult.Rejected(ReaderRejection.InvalidSelection) else {
                    val id = annotationIds.newHighlightId()
                    if (valid()) this@BookReadView.underlineSelection(id, style) else ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                }
            }
            override fun copyText(): ReaderResult<Unit> {
                checkMainThread()
                if (!valid()) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                return selectionOperation("CopyActionFailed") {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("阅读选区", selected.text))
                    if (valid()) this@BookReadView.clearSelection()
                    ReaderResult.Success(Unit)
                }
            }
            override fun clearSelection() { if (valid()) this@BookReadView.clearSelection() }
        }
        documentSelection = binding
        val revision = ++selectionRevision
        dispatch { if (revision == selectionRevision) it.onSelectionChanged(this, selected) }
        if (documentSelection === binding && valid() && isAttachedToWindow && !draggingSelection) selectionToolbar.show(selected, binding.actions, bounds)
        return true
    }
    fun clearSelection() {
        checkMainThread()
        val document = documentSelection
        documentSelection = null
        if (selection == null && document == null) { selectionToolbar.dismiss(); return }
        selection = null; draggingSelection = false; val revision = ++selectionRevision
        accessibleSelectionActions = null
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        selectionToolbar.dismiss()
        document?.delegate?.clearSelection()
        presentation?.onDecorationsChanged()
        dispatch { if (revision == selectionRevision) it.onSelectionChanged(this, null) }
    }
    fun setAnnotationIdFactory(factory: AnnotationIdFactory) { checkMainThread(); if (!isReleased) annotationIds = factory }
    /** Null restores the built-in icon/text selection menu. Factories are runtime objects, not saved state. */
    fun setSelectionToolbarFactory(factory: SelectionToolbarFactory?) {
        checkMainThread(); if (isReleased) return
        selectionToolbar.factory = factory
        documentSelection?.let { selectionToolbar.show(it.selected, it.actions, it.bounds) }
        selection?.let { showSelectionToolbar(it, selectionRevision) }
    }
    /** Installs icon/text items in a paged floating menu. Runtime callbacks are not saved state.
     * Use SelectionToolbars.defaultItems() plus custom items to retain copy/highlight.
     */
    @JvmOverloads fun setSelectionMenuItems(
        items: List<org.peyilo.ireadview.spi.selection.SelectionMenuItem>,
        options: org.peyilo.ireadview.spi.selection.SelectionMenuOptions = org.peyilo.ireadview.spi.selection.SelectionMenuOptions()
    ) = setSelectionToolbarFactory(org.peyilo.ireadview.spi.selection.SelectionToolbars.menu(items, options))
    fun highlightSelection(id: String, styleId: String): ReaderResult<HighlightAnnotation> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        documentSelection?.let { document ->
            if (!document.actions.isValid()) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
            val item = DocumentHighlight(id, document.selected, styleId)
            val result = addAnnotation(item)
            if (result is ReaderResult.Success) {
                if (documentSelection === document) clearSelection()
                return ReaderResult.Success(item)
            }
            return when (result) {
                is ReaderResult.Rejected -> result
                is ReaderResult.Failure -> result
                else -> ReaderResult.Superseded
            }
        }
        val selected = selection ?: return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
        val content = presentation?.contentForChapter(selected.range.chapterId) ?: return ReaderResult.Rejected(ReaderRejection.NotReady)
        if (!TextSelectionResolver.matches(content, selected.range, selected.text)) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
        val revision = selectionRevision
        val result = addHighlight(Highlight(id, selected.range, selected.text, styleId))
        if (result is ReaderResult.Success && revision == selectionRevision) clearSelection()
        return result
    }
    /** Converts the current source selection to an independent line annotation. Dimensions are dp. */
    @JvmOverloads fun underlineSelection(id: String, style: org.peyilo.ireadview.api.style.TextLineStyle = org.peyilo.ireadview.api.style.TextLineStyle()): ReaderResult<UnderlineAnnotation> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        documentSelection?.let { document ->
            if (!document.actions.isValid()) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
            val item = DocumentUnderline(id, document.selected, style)
            val result = addAnnotation(item)
            if (result is ReaderResult.Success) {
                if (documentSelection === document) clearSelection()
                return ReaderResult.Success(item)
            }
            return when (result) {
                is ReaderResult.Rejected -> result
                is ReaderResult.Failure -> result
                else -> ReaderResult.Superseded
            }
        }
        val selected = selection ?: return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
        val content = presentation?.contentForChapter(selected.range.chapterId) ?: return ReaderResult.Rejected(ReaderRejection.NotReady)
        if (!TextSelectionResolver.matches(content, selected.range, selected.text)) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
        val revision = selectionRevision
        val result = addUnderline(Underline(id, selected.range, selected.text, style))
        if (result is ReaderResult.Success && revision == selectionRevision) clearSelection()
        return result
    }

    private fun selectText(selected: TextSelection): ReaderResult<Unit> {
        if (documentSelection != null) clearSelection()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        val content = presentation?.contentForChapter(selected.range.chapterId) ?: return ReaderResult.Rejected(ReaderRejection.NotReady)
        if (!TextSelectionResolver.matches(content, selected.range, selected.text)) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
        selection = selected; val revision = ++selectionRevision
        accessibleSelectionActions = null
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        presentation?.onDecorationsChanged()
        dispatch { if (revision == selectionRevision) it.onSelectionChanged(this, selected) }
        if (revision == selectionRevision && isAttachedToWindow && !draggingSelection) showSelectionToolbar(selected, revision)
        return ReaderResult.Success(Unit)
    }

    private fun ReaderResult<*>.mapToUnit(): ReaderResult<Unit> = when (this) {
        is ReaderResult.Success -> ReaderResult.Success(Unit)
        is ReaderResult.Rejected -> this
        is ReaderResult.Failure -> this
        ReaderResult.Superseded -> ReaderResult.Superseded
    }
    private fun createSelectionActions(selected: TextSelection, revision: Long): SelectionActions {
        val generation = contentGeneration
        fun valid() = revision == selectionRevision && selection == selected && !isReleased && generation == contentGeneration
        fun covers(range: TextRange): Boolean {
            if (range.bookId != selected.range.bookId || range.chapterId != selected.range.chapterId ||
                range.contentRevision != null && range.contentRevision != selected.range.contentRevision) return false
            val content = presentation?.contentForChapter(range.chapterId) ?: return false
            val start = TextSelectionResolver.compareAnchors(content, range.start, selected.range.start) ?: return false
            val end = TextSelectionResolver.compareAnchors(content, range.endExclusive, selected.range.endExclusive) ?: return false
            return start <= 0 && end >= 0
        }
        fun highlighted() = annotations?.highlights?.filter { annotationStore.status(it.id) == AnnotationStatus.Valid && covers(it.range) } ?: emptyList()
        fun underlined() = annotations?.underlines?.filter { annotationStore.status(it.id) == AnnotationStatus.Valid && covers(it.range) } ?: emptyList()
        return object : SelectionActions {
            override fun isValid(): Boolean { checkMainThread(); return valid() }
            override fun hasHighlight(): Boolean { checkMainThread(); return valid() && highlighted().isNotEmpty() }
            override fun hasUnderline(): Boolean { checkMainThread(); return valid() && underlined().isNotEmpty() }
            override fun toggleHighlight(styleId: String): ReaderResult<Unit> {
                checkMainThread()
                if (!valid()) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                val existing = highlighted()
                if (existing.isEmpty()) return highlight(styleId).mapToUnit()
                val result = mutateAnnotations { existing.forEach { annotationStore.remove(it.id, highlight = true) }; ReaderResult.Success(Unit) }
                if (valid()) clearSelection()
                return result
            }
            override fun toggleUnderline(style: org.peyilo.ireadview.api.style.TextLineStyle): ReaderResult<Unit> {
                checkMainThread()
                if (!valid()) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                val existing = underlined()
                if (existing.isEmpty()) return underline(style).mapToUnit()
                val result = mutateAnnotations { existing.forEach { annotationStore.removeUnderline(it.id) }; ReaderResult.Success(Unit) }
                if (valid()) clearSelection()
                return result
            }
            override fun underline(style: org.peyilo.ireadview.api.style.TextLineStyle): ReaderResult<UnderlineAnnotation> {
                checkMainThread()
                if (!valid()) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                return try {
                    val id = annotationIds.newHighlightId()
                    if (!valid()) ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                    else underlineSelection(id, style)
                } catch (error: Exception) {
                    ReaderResult.Failure(ReaderError(ReaderStage.SELECTION, "UnderlineActionFailed", error.message ?: "Cannot create underline", cause = error))
                }
            }
            override fun copyText(): ReaderResult<Unit> {
                checkMainThread()
                if (!valid()) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                val content = presentation?.contentForChapter(selected.range.chapterId) ?: return ReaderResult.Rejected(ReaderRejection.NotReady)
                if (!TextSelectionResolver.matches(content, selected.range, selected.text)) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("阅读选区", selected.text))
                } catch (exception: Exception) {
                    return ReaderResult.Failure(ReaderError(ReaderStage.SELECTION, "CopyActionFailed", exception.message ?: "Cannot copy selection", retryable = false, cause = exception))
                }
                if (valid()) this@BookReadView.clearSelection()
                return ReaderResult.Success(Unit)
            }
            override fun highlight(styleId: String): ReaderResult<HighlightAnnotation> {
                checkMainThread()
                if (!valid()) return ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                return try {
                    val id = annotationIds.newHighlightId()
                    if (!valid()) ReaderResult.Rejected(ReaderRejection.InvalidSelection)
                    else highlightSelection(id, styleId)
                }
                catch (exception: Exception) {
                    ReaderResult.Failure(ReaderError(ReaderStage.SELECTION, "HighlightActionFailed", exception.message ?: "Cannot create highlight", cause = exception))
                }
            }
            override fun clearSelection() { checkMainThread(); if (valid()) this@BookReadView.clearSelection() }
        }
    }

    private fun showSelectionToolbar(selected: TextSelection, revision: Long) {
        val generation = contentGeneration
        val actions = createSelectionActions(selected, revision)
        val bounds = presentation?.selectionBounds()?.let { local ->
            val offset = Rect()
            presentation?.contentView?.let { offsetDescendantRectToMyCoords(it, offset) }
            local.offset(offset.left.toFloat(), offset.top.toFloat())
            Rect().apply { local.roundOut(this) }
        }
        try { selectionToolbar.show(selected, actions, bounds) }
        catch (exception: Exception) {
            if (generation != contentGeneration || revision != selectionRevision || selection !== selected) return
            selectionToolbar.dismiss()
            if (generation != contentGeneration || revision != selectionRevision || selection !== selected) return
            reportReaderError(ReaderError(ReaderStage.SELECTION, "ToolbarFailed", exception.message ?: "Selection toolbar failed", retryable = false, cause = exception))
        }
    }

    val annotations: AnnotationSnapshot? get() = annotationStore.snapshot()
    fun exportAnnotations(): AnnotationSnapshot? { checkMainThread(); return annotations }
    fun getAnnotationStatus(id: String): AnnotationStatus? { checkMainThread(); return annotationStore.status(id) }
    /** Navigation target for any supported annotation, independent of its source format. */
    fun getAnnotationPosition(id: String): ReadingPosition? {
        checkMainThread()
        return when (val item = annotations?.items?.firstOrNull { it.id == id }) {
            is Bookmark -> item.position
            is RangedAnnotation -> ReadingPosition(item.bookId, item.range.chapterId, item.range.start, item.range.contentRevision)
            is DocumentRangedAnnotation -> presentation?.positionForDocumentSelection(item.selection)
            else -> null
        }
    }
    suspend fun goToAnnotation(id: String): NavigationResult<ReadingPosition> {
        checkMainThread()
        if (annotationStore.bookmark(id) != null) return goToBookmark(id)
        val target = getAnnotationPosition(id) ?: return NavigationResult.Rejected(ReaderRejection.UnknownAnnotation)
        return goToPosition(target)
    }
    fun goToAnnotation(id: String, callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { goToAnnotation(id) }

    fun replaceAnnotations(snapshot: AnnotationSnapshot): ReaderResult<Unit> = mutateAnnotations { annotationStore.replace(snapshot) }
    fun addBookmark(bookmark: Bookmark): ReaderResult<Bookmark> = mutateAnnotations { annotationStore.add(bookmark) }
    /** Unified mutation entry points; all kinds share id uniqueness, book binding and validation. */
    fun addAnnotation(annotation: ReaderAnnotation): ReaderResult<ReaderAnnotation> = mutateAnnotations { annotationStore.putAnnotation(annotation, false) }
    fun updateAnnotation(annotation: ReaderAnnotation): ReaderResult<ReaderAnnotation> = mutateAnnotations { annotationStore.putAnnotation(annotation, true) }
    fun removeAnnotation(id: String): ReaderResult<Unit> = mutateAnnotations(changed = annotations?.items?.any { it.id == id } == true) { annotationStore.removeAnnotation(id) }
    fun addHighlight(highlight: Highlight): ReaderResult<Highlight> = mutateAnnotations { annotationStore.put(highlight, update = false) }
    fun addHighlight(highlight: HighlightAnnotation): ReaderResult<HighlightAnnotation> = mutateAnnotations { annotationStore.putAnnotation(highlight, false) }
    fun updateHighlight(highlight: Highlight): ReaderResult<Highlight> = mutateAnnotations { annotationStore.put(highlight, update = true) }
    fun updateHighlight(highlight: HighlightAnnotation): ReaderResult<HighlightAnnotation> = mutateAnnotations { annotationStore.putAnnotation(highlight, true) }
    fun addUnderline(underline: Underline): ReaderResult<Underline> = mutateAnnotations { annotationStore.putUnderline(underline, update = false) }
    fun addUnderline(underline: UnderlineAnnotation): ReaderResult<UnderlineAnnotation> = mutateAnnotations { annotationStore.putAnnotation(underline, false) }
    fun updateUnderline(underline: Underline): ReaderResult<Underline> = mutateAnnotations { annotationStore.putUnderline(underline, update = true) }
    fun updateUnderline(underline: UnderlineAnnotation): ReaderResult<UnderlineAnnotation> = mutateAnnotations { annotationStore.putAnnotation(underline, true) }
    fun removeUnderline(id: String): ReaderResult<Unit> = mutateAnnotations(changed = annotations?.allUnderlines?.any { it.id == id } == true) { annotationStore.removeUnderline(id) }
    fun removeBookmark(id: String): ReaderResult<Unit> = mutateAnnotations(changed = annotationStore.bookmark(id) != null) { annotationStore.remove(id, highlight = false) }
    fun removeHighlight(id: String): ReaderResult<Unit> = mutateAnnotations(changed = annotations?.allHighlights?.any { it.id == id } == true) { annotationStore.remove(id, highlight = true) }

    private fun <T> mutateAnnotations(changed: Boolean = true, mutation: () -> ReaderResult<T>): ReaderResult<T> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        val result = mutation()
        if (result is ReaderResult.Success && changed) publishAnnotations()
        return result
    }

    private fun publishAnnotations() {
        val snapshot = annotationStore.snapshot() ?: return
        presentation?.currentContent()?.let { annotationStore.validate(it) }
        val chapterIds = (snapshot.bookmarks.map { it.position.chapterId } + snapshot.highlights.map { it.range.chapterId } + snapshot.underlines.map { it.range.chapterId }).toSet()
        chapterIds.forEach { id -> (presentation?.contentForChapter(id) ?: coordinator.peekChapter(id))?.let { annotationStore.validate(it) } }
        val generation = contentGeneration
        val revision = annotationStore.revision
        dispatch { if (revision == annotationStore.revision) it.onAnnotationsChanged(this, snapshot) }
        if (generation == contentGeneration && revision == annotationStore.revision) publishAnnotationStatuses()
    }

    private fun publishAnnotationStatuses() {
        presentation?.onDecorationsChanged()
        val generation = contentGeneration
        val revision = annotationStore.revision
        annotationStore.statusSnapshot().forEach { (id, status) ->
            if (generation == contentGeneration && revision == annotationStore.revision) dispatch {
                if (revision == annotationStore.revision) it.onAnnotationStatusChanged(this, id, status)
            }
        }
    }

    /** Bookmark recovery may use a page hint/first page; normal position navigation is strict. */
    suspend fun goToBookmark(id: String): NavigationResult<ReadingPosition> {
        checkMainThread()
        if (isReleased) return NavigationResult.Rejected(ReaderRejection.Released)
        if (currentBook == null) return NavigationResult.Rejected(ReaderRejection.NoBook)
        val bookmark = annotationStore.bookmark(id) ?: return NavigationResult.Rejected(ReaderRejection.UnknownAnnotation)
        val generation = contentGeneration
        suspend fun move(position: ReadingPosition) = navigateWithReason(ReadNavigation.Position(position), NavigationOptions(), PositionChangeReason.Restore)
        var result = move(bookmark.position)
        if (generation != contentGeneration) return NavigationResult.Superseded
        var fallback: ReaderRejection? = null
        if (result is NavigationResult.Rejected && result.reason == ReaderRejection.InvalidAnchor) {
            fallback = result.reason
            result = move(bookmark.position.copy(contentRevision = null))
            if (generation != contentGeneration) return NavigationResult.Superseded
            if (result is NavigationResult.Rejected && result.reason == ReaderRejection.InvalidAnchor) {
                result = move(bookmark.position.copy(anchor = null, contentRevision = null))
            }
        }
        if (generation != contentGeneration) return NavigationResult.Superseded
        if (result is NavigationResult.Rejected && result.reason == ReaderRejection.InvalidPage) {
            fallback = fallback ?: result.reason
            result = move(bookmark.position.copy(anchor = null, contentRevision = null, pageNumberHint = 1))
        }
        if (generation != contentGeneration) return NavigationResult.Superseded
        val actual = when (result) { is NavigationResult.Moved -> result.to; is NavigationResult.Unchanged -> result.location; else -> null }
        if (actual != null) dispatch { it.onPositionRestored(this, fallback?.let { reason -> Restoration.Fallback(reason, actual) } ?: Restoration.Exact, PositionChangeReason.Restore) }
        return if (generation == contentGeneration) result else NavigationResult.Superseded
    }
    fun goToBookmark(id: String, callback: ReaderCallback<NavigationResult<ReadingPosition>>): ReaderRequest = submit(callback) { goToBookmark(id) }

    fun addReaderListener(listener: ReaderListener) { checkMainThread(); if (!isReleased) listeners.add(listener) }
    fun removeReaderListener(listener: ReaderListener) { checkMainThread(); listeners.remove(listener) }
    fun exportSnapshot(): ReaderSnapshot? {
        checkMainThread()
        return currentPosition?.let { ReaderSnapshot(it.bookId, it, readingMode.id, builtInEffectId = builtInPageTurnEffectId) }
    }
    suspend fun restoreSnapshot(source: BookSource, snapshot: ReaderSnapshot): OpenResult {
        checkMainThread()
        if (isReleased) return OpenResult.Failed(ReaderError(ReaderStage.BOOK, "Released", "Widget has been released", retryable = false))
        if (snapshot.bookId != source.bookId) return OpenResult.Failed(ReaderError(ReaderStage.BOOK, "BookMismatch", "Snapshot belongs to another book", retryable = false))
        val mode = ReadingMode.custom(snapshot.modeId)
        if (mode !in supportedModes) return OpenResult.Failed(ReaderError(ReaderStage.BOOK, "UnsupportedMode", "Snapshot mode is not supported", retryable = false))
        val factory = snapshot.builtInEffectId?.let { id -> PageTurnEffects.fromId(id)
            ?: return OpenResult.Failed(ReaderError(ReaderStage.EFFECT, "UnsupportedSnapshotEffect", "Snapshot effect is not supported: $id", retryable = false)) }
        val sequence = openSequence + 1
        closeBook()
        if (sequence != openSequence || isReleased) return OpenResult.Superseded
        when (val change = setReadingMode(mode)) {
            is ReaderResult.Failure -> return OpenResult.Failed(change.error)
            is ReaderResult.Rejected -> return OpenResult.Failed(ReaderError(ReaderStage.BOOK, change.reason.name, "Cannot restore snapshot mode", retryable = false))
            ReaderResult.Superseded -> return OpenResult.Superseded
            is ReaderResult.Success -> Unit
        }
        if (sequence != openSequence || isReleased) return OpenResult.Superseded
        if (factory != null) setPageTurnEffect(factory)
        if (sequence != openSequence || isReleased) return OpenResult.Superseded
        return openBook(source, OpenBookRequest(initialPosition = snapshot.position))
    }
    fun restoreSnapshot(source: BookSource, snapshot: ReaderSnapshot, callback: ReaderCallback<OpenResult>): ReaderRequest = submit(callback) { restoreSnapshot(source, snapshot) }

    override fun onCommittedLocationChanged(change: ReadLocationChange<ReadingPosition>) {
        val generation = contentGeneration
        val sequence = openSequence
        val book = currentBook
        fun current() = generation == contentGeneration && sequence == openSequence && currentBook === book &&
            currentPosition == change.to && !isReleased
        documentSelection?.let { document ->
            if (change.to?.chapterId != document.selected.chapterId || !draggingSelection && change.reason != PositionChangeReason.Scroll) clearSelection()
        }
        if (!current()) return
        val selected = selection
        if (selected != null && selected.range.chapterId != change.to?.chapterId) {
            val stillVisible = change.reason == PositionChangeReason.Scroll && presentation?.contentForChapter(selected.range.chapterId) != null && presentation?.selectionBounds() != null
            if (!stillVisible) clearSelection()
        }
        if (!current()) return
        if (change.to != null) { phase = ReaderPhase.Ready; lastError = null }
        else if (!isReleased) phase = ReaderPhase.Closed
        dispatch(::current) { it.onPositionChanged(this, change) }
        if (!current()) return
        publishState()
        if (!current()) return
        if (presentationSource?.supportsBlockLayout != false && presentation?.supportsBlockLayout == true) coordinator.preloadNear(change.to)
        if (current() && !draggingSelection && isAttachedToWindow) selection?.let { showSelectionToolbar(it, selectionRevision) }
    }
    override fun onNavigationStateChanged(state: ReadNavigationState) {
        val generation = contentGeneration
        val sequence = openSequence
        val content = presentation
        val position = currentPosition
        fun current() = generation == contentGeneration && sequence == openSequence && presentation === content &&
            navigationState == state && currentPosition == position && !isReleased
        if (documentSelection?.generation?.let { it != contentGeneration } == true) clearSelection()
        if (!current()) return
        content?.onNavigationStateChanged(state)
        if (!current()) return
        if (state == ReadNavigationState.Idle && chromeRefreshPending) refreshPageChrome()
        if (!current()) return
        if (state == ReadNavigationState.Idle && currentPosition != null && !isReleased) { phase = ReaderPhase.Ready; publishState() }
        if (!current()) return
        if (state == ReadNavigationState.Idle && !draggingSelection && isAttachedToWindow && presentation?.currentContent() != null) selection?.let { showSelectionToolbar(it, selectionRevision) }
    }
    override fun onContainerViewportChanged() {
        val generation = contentGeneration
        coordinator.invalidateLayouts()
        if (generation == contentGeneration && !isReleased) restoreVisiblePosition()
    }
    override fun onContentTap(xPx: Float, yPx: Float): Boolean {
        val content = presentation ?: return false
        val offset = Rect()
        offsetDescendantRectToMyCoords(content.contentView, offset)
        return content.onTap(xPx - offset.left, yPx - offset.top)
    }
    override fun onContentLongPress(xPx: Float, yPx: Float): Boolean {
        val content = presentation ?: return false
        val offset = Rect()
        offsetDescendantRectToMyCoords(content.contentView, offset)
        return content.onLongPress(xPx - offset.left, yPx - offset.top)
    }
    override fun isContentGestureBlocked(): Boolean = selection != null || documentSelection != null
    override fun onContentTouch(event: MotionEvent): Boolean {
        val content = presentation ?: return false
        val offset = Rect()
        offsetDescendantRectToMyCoords(content.contentView, offset)
        return content.onSelectionTouch(event.actionMasked, event.x - offset.left, event.y - offset.top)
    }
    override fun onContainerDetached() {
        linkSequence++
        linkRequest?.cancel(); linkRequest = null
        val generation = contentGeneration
        val sequence = openSequence
        relayoutSequence++
        val old = relayout; relayout = null
        phase = ReaderPhase.AwaitingViewport
        // Selection notifications may open another book; only this session may suspend its work.
        org.peyilo.ireadview.internal.releaseReaderResources(
            { pageClock?.clear() },
            { clearSelection() },
            { old?.cancel() },
            { if (!isReleased && generation == contentGeneration && sequence == openSequence) coordinator.suspendWork() }
        )
    }
    override fun onContainerAttached() {
        val generation = contentGeneration
        val sequence = openSequence
        fun current() = !isReleased && generation == contentGeneration && sequence == openSequence
        coordinator.resumeWork()
        if (!current()) return
        restoreVisiblePosition()
        if (current()) pageClock?.update()
    }
    override fun onRelease() {
        presentationSource = null
        linkSequence++
        linkRequest?.cancel(); linkRequest = null
        linkHandler = null
        openSequence++
        phase = ReaderPhase.Released
        pageChromeProvider = null
        chromeRefreshPending = false
        listeners.clear()
        org.peyilo.ireadview.internal.releaseReaderResources(
            { pageClock?.clear() },
            { coordinator.close() }
        )
    }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); pageClock?.update() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); pageClock?.update() }

    private fun restoreVisiblePosition() {
        if (currentPosition == null || isReleased) return
        val sequence = ++relayoutSequence
        val generation = contentGeneration
        val old = relayout; relayout = null
        old?.cancel()
        if (isReleased || contentGeneration != generation || sequence != relayoutSequence) return
        // Size callbacks run inside View.layout. Adding a candidate there skips its measure pass.
        post {
            if (isReleased || contentGeneration != generation || sequence != relayoutSequence) return@post
            val position = currentPosition ?: return@post
            lateinit var task: Job
            task = readerScope.launch(start = CoroutineStart.LAZY) {
                try {
                    if (isReleased || contentGeneration != generation || sequence != relayoutSequence) return@launch
                    val result = navigateWithReason(ReadNavigation.Position(position), NavigationOptions(), PositionChangeReason.Relayout)
                    if (result is NavigationResult.Failed && !isReleased && generation == contentGeneration && sequence == relayoutSequence) reportReaderError(result.error)
                } finally {
                    if (relayout === task) relayout = null
                }
            }
            relayout = task
            task.start()
        }
    }

    private fun reportReaderError(error: ReaderError) {
        phase = if (currentPosition == null) ReaderPhase.Error else ReaderPhase.Ready
        publishReaderError(error)
    }
    /** Cleanup/effect diagnostics keep the current phase; book error listeners share ownership checks. */
    private fun publishReaderError(error: ReaderError, stillCurrent: () -> Boolean = { true }) {
        val generation = contentGeneration
        val sequence = openSequence
        lastError = error
        fun current() = generation == contentGeneration && sequence == openSequence && lastError === error &&
            !isReleased && stillCurrent()
        dispatch(::current) { it.onError(this, error) }
        if (current()) publishState()
    }
    private fun publishState() {
        val publication = ++statePublicationSequence
        val sequence = openSequence
        val generation = contentGeneration
        fun current() = publication == statePublicationSequence && sequence == openSequence &&
            generation == contentGeneration && !isReleased
        pageClock?.update()
        if (!current()) return
        val state = readerState
        if (!current()) return
        val kind = if (state.position != null || state.phase == ReaderPhase.Ready || state.phase == ReaderPhase.Released) null else when (state.phase) {
            ReaderPhase.Error -> when (state.error?.stage) {
                ReaderStage.BOOK -> PlaceholderKind.BOOK_FAILED
                ReaderStage.LAYOUT, ReaderStage.VIEWPORT -> PlaceholderKind.LAYOUT_FAILED
                else -> PlaceholderKind.CHAPTER_FAILED
            }
            ReaderPhase.Empty -> PlaceholderKind.EMPTY_BOOK
            ReaderPhase.Closed -> PlaceholderKind.CLOSED
            ReaderPhase.LoadingBook -> PlaceholderKind.BOOK_LOADING
            else -> PlaceholderKind.CHAPTER_LOADING
        }
        val source = coordinator.source
        val request = lastOpenRequest
        val actions = object : PlaceholderActions {
            override val canRetry get() = !isReleased && isEnabled && source != null && state.phase == ReaderPhase.Error &&
                state.error?.retryable == true && sequence == openSequence && generation == contentGeneration && currentPosition == null &&
                navigationState == ReadNavigationState.Idle && coordinator.source === source
            override fun retry(): Boolean {
                checkMainThread()
                if (!canRetry) return false
                readerScope.launch { if (canRetry) openBook(requireNotNull(source), request) }
                return true
            }
        }
        placeholderController?.show(kind?.let { PlaceholderState(it, state.bookId, state.error?.chapterId, state.error, style) }, sequence to generation, actions)
        if (!current()) return
        dispatch(::current) { it.onStateChanged(this, state) }
    }
    private fun dispatch(stillCurrent: () -> Boolean = { true }, event: (ReaderListener) -> Unit) {
        val generation = contentGeneration
        listeners.toList().forEach { listener ->
            if (contentGeneration == generation && !isReleased && stillCurrent()) {
                try { event(listener) } catch (error: Exception) { Log.w("iReadView", "Reader listener failed", error) }
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable = SavedState(super.onSaveInstanceState(), exportSnapshot()?.position, readingMode.id, ReaderStyleStateCodec.encode(style))
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) { super.onRestoreInstanceState(state); return }
        super.onRestoreInstanceState(state.superState)
        if (!styleExplicitlySet && currentBook == null) ReaderStyleStateCodec.decode(state.styleState, style)?.let { style = it }
        restoredPosition = state.position
        val mode = ReadingMode.custom(state.modeId)
        if (mode in supportedModes && currentBook == null && !modeExplicitlySet) { clearContentAdapter(); readingMode = mode }
    }

    internal class SavedState : androidx.customview.view.AbsSavedState {
        val position: ReadingPosition?
        val modeId: String
        val styleState: Bundle?
        constructor(superState: Parcelable?, position: ReadingPosition?, modeId: String, styleState: Bundle?) : super(superState ?: EMPTY_STATE) {
            this.position = position; this.modeId = modeId; this.styleState = styleState
        }
        constructor(parcel: Parcel, loader: ClassLoader? = null) : super(parcel, loader ?: SavedState::class.java.classLoader) {
            val data = parcel.readBundle(loader ?: javaClass.classLoader)
            styleState = data?.getBundle("style")
            modeId = data?.getString("mode")?.takeIf { it.isNotBlank() } ?: ReadingMode.PAGED.id
            position = data?.let { values -> runCatching {
                ReadingPosition(values.getString("book")!!, values.getString("chapter")!!,
                    values.getString("block")?.let { ContentAnchor(it, values.getInt("offset")) }, values.getString("revision"),
                    values.getInt("page").takeIf { it > 0 }, values.getInt("chapterNumber").takeIf { it > 0 }, values.getString("documentLocator"))
            }.getOrNull() }
        }
        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeBundle(Bundle().apply {
                putString("mode", modeId)
                putBundle("style", styleState)
                position?.let { value ->
                putString("book", value.bookId); putString("chapter", value.chapterId); putString("revision", value.contentRevision)
                putString("block", value.anchor?.blockId); putInt("offset", value.anchor?.offset ?: 0)
                putInt("page", value.pageNumberHint ?: 0); putInt("chapterNumber", value.chapterNumberHint ?: 0)
                putString("documentLocator", value.documentLocator)
                }
            })
        }
        companion object {
            @JvmField val CREATOR: Parcelable.ClassLoaderCreator<SavedState> = object : Parcelable.ClassLoaderCreator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)
                override fun createFromParcel(source: Parcel, loader: ClassLoader?) = SavedState(source, loader)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }
}
