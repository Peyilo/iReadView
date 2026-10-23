package org.peyilo.ireadview.epub

import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import org.peyilo.ireadview.spi.selection.ReaderSelectionInteraction
import org.peyilo.ireadview.spi.selection.ReaderSelectionScrollPolicy
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.view.*
import android.widget.FrameLayout
import java.net.URI
import kotlinx.coroutines.*
import org.json.JSONObject
import org.peyilo.ireadview.api.effect.PageBackgroundSurface
import org.peyilo.ireadview.api.effect.SnapshotPreferredSurface
import org.peyilo.ireadview.api.effect.RecordedPageSurface
import org.peyilo.ireadview.api.input.PagingAxis
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.TextLineStyle
import org.peyilo.ireadview.spi.page.*
import org.peyilo.ireadview.spi.presentation.*
import org.peyilo.ireadview.spi.selection.DocumentSelectionHandle

/** Coordinates body views with the Widget's existing prepare/commit/cancel and template contracts. */
internal class EpubBookPresentation(private val context: Context, private val source: EpubReaderBookSource,
    private val mode: ReadingMode, private val host: BookPresentationHost) : BookPresentation() {
    private var active: Page? = null
    // Current + at most two parked neighbors. Idle work never moves the committed body.
    private val parked = ReaderPageCache<Page>(if (mode == ReadingMode.PAGED) host.pageCacheMode else ReaderPageCacheMode.SINGLE_PAGE) { it.close() }
    private val preloader = ReaderPagePreloader()
    private var heading = 1
    private var chromeEpoch = 0L
    private var clockMinute = Long.MIN_VALUE
    private var detached = false
    private var navigationBusy = false
    private var inputSerial = 0L
    private var paragraphJob: Job? = null
    private val root: FrameLayout = object : FrameLayout(context) {
        private var ownsTouch = false
        private var downY = 0f
        private var lastY = 0f
        private var scrolling = false
        private var selectionOnDown = false
        private var velocity: VelocityTracker? = null
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                ownsTouch = active?.bodyBounds()?.contains(event.x.toInt(), event.y.toInt()) == true
                if (ownsTouch) {
                    preloader.cancel()
                    inputSerial++; paragraphJob?.cancel(); paragraphJob = null
                    downY = event.y; lastY = event.y; scrolling = false
                    selectionOnDown = host.documentSelection != null
                    velocity?.recycle(); velocity = VelocityTracker.obtain()
                }
            }
            if (!ownsTouch) return super.dispatchTouchEvent(event)
            velocity?.addMovement(event)
            host.reportBodyTouch(event) // Widget owns long-press timing, taps, handle capture and page gestures.
            if (mode == ReadingMode.CONTINUOUS_SCROLL && !selectionInteraction.isDragging && (host.documentSelection == null || selectionOnDown)) {
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    if (kotlin.math.abs(event.y - downY) > slop) scrolling = true
                    if (scrolling) active?.body?.scrollBodyBy((lastY - event.y).toInt())
                } else if (event.actionMasked == MotionEvent.ACTION_UP && scrolling) {
                    velocity?.computeCurrentVelocity(1000)
                    active?.body?.flingBody(-(velocity?.yVelocity ?: 0f).toInt())
                }
            }
            lastY = event.y
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                ownsTouch = false; velocity?.recycle(); velocity = null
                scheduleWarm()
            }
            return true
        }
    }
    private val selectionInteraction: org.peyilo.ireadview.spi.selection.ReaderSelectionController = host.presentationComponents.selectionControllerFactory.create(context, object : ReaderSelectionInteraction.Delegate() {
        override fun ready() = !detached && active?.current() == true && host.documentSelection != null
        private fun local(x: Float, y: Float): PointF? = active?.let { val rect = it.bodyBounds(); PointF(x - rect.left, y - rect.top) }
        override fun handleAt(x: Float, y: Float): Boolean? = local(x, y)?.let { active?.body?.selectionHandleAt(it.x, it.y) }
        override fun grabOffset(start: Boolean, x: Float, y: Float): PointF? = local(x, y)?.let { active?.body?.selectionDragOffset(start, it.x, it.y) }
        override suspend fun extend(start: Boolean, point: PointF, current: () -> Boolean): Boolean {
            val page = active ?: return false
            val selected = host.documentSelection ?: return false
            val at = local(point.x, point.y) ?: return false
            val result = page.body.extendSelection(selected, start, at.x, at.y) ?: return false
            if (!current() || active !== page) return false
            return page.reportSelection(result)
        }
        override fun edgeDirection(point: PointF): Int {
            if (mode == ReadingMode.CONTINUOUS_SCROLL) return (scrollSpeed(point) ?: 0f).let { when { it < 0 -> -1; it > 0 -> 1; else -> 0 } }
            val page = active ?: return 0
            val at = local(point.x, point.y) ?: return 0
            val edge = 24f * root.resources.displayMetrics.density
            return when { at.y <= page.body.paddingTop + edge -> -1
                at.y >= page.body.height - page.body.paddingBottom - edge -> 1; else -> 0 }
        }
        override fun scrollSpeed(point: PointF): Float? {
            if (mode != ReadingMode.CONTINUOUS_SCROLL) return null
            val page = active ?: return 0f
            val at = local(point.x, point.y) ?: return 0f
            return ReaderSelectionScrollPolicy.speed(at.y, page.body.paddingTop.toFloat(),
                (page.body.height - page.body.paddingBottom).toFloat(), root.resources.displayMetrics.density)
        }
        override suspend fun nextScrollFrame(): Long = ReaderSelectionScrollPolicy.nextFrame(root)
        override suspend fun scrollBy(pixels: Int, start: Boolean, point: PointF, current: () -> Boolean): Boolean {
            val page = active ?: return false
            val selected = host.documentSelection ?: return false
            val at = local(point.x, point.y) ?: return false
            return current() && page.body.scrollSelection(selected, start, pixels, at.y)
        }
        override suspend fun movePage(direction: Int, start: Boolean, current: () -> Boolean): Boolean {
            val page = active ?: return false
            val selected = host.documentSelection ?: return false
            val target = page.number + direction
            if (target !in 1..page.pageCount || selected.chapterId != page.position?.chapterId) return false
            val fixedPage = page.body.pageForEndpoint(selected, !start) ?: return false
            if (!current() || start && target > fixedPage || !start && target < fixedPage) return false
            val result = host.requestSelectionPage(selected.chapterId, target, page.generation, current)
            if (result is NavigationResult.Failed) host.reportError(result.error)
            return result is NavigationResult.Moved || result is NavigationResult.Unchanged
        }
        override fun reportDragging(active: Boolean) = host.reportSelectionDrag(active)
        override fun reportError(error: Exception) = host.reportError(ReaderError(ReaderStage.SELECTION,
            "SelectionPageFailed", error.message ?: "Cannot extend selection", cause = error))
    })
    override val contentView: View get() = root
    override val currentSurface get() = active?.surface
    override val supportsPageTurnEffects get() = mode == ReadingMode.PAGED
    override val contentFeatures = BookPresentationFeatures(ReaderSelectionModel.Document,
        ReaderFeatureSupport.Supported, ReaderFeatureSupport.Supported)
    override val currentPageCount get() = active?.pageCount
    override val needsTimeUpdates get() = active?.template?.needsTimeUpdates == true
    override fun onTimeChanged(timeMillis: Long) {
        val minute = timeMillis / 60000L
        if (clockMinute == minute) return
        clockMinute = minute
        chromeEpoch++; active?.template?.onTimeChanged(timeMillis); invalidateSurfaces()
    }
    override fun onPageChromeChanged() {
        chromeEpoch++; active?.template?.refreshChrome(); invalidateSurfaces()
    }
    private fun invalidateSurfaces() {
        active?.invalidateSurface(); parked.entries.forEach { it.invalidateSurface() }; scheduleWarm()
    }
    override fun onDecorationsChanged() {
        if (host.documentSelection == null) selectionInteraction.reset()
        preloader.cancel()
        invalidateSurfaces()
        active?.refreshAnnotations()
    }
    override fun onNavigationStateChanged(state: ReadNavigationState) {
        navigationBusy = state != ReadNavigationState.Idle
        if (state == ReadNavigationState.Preparing) preloader.cancel()
        else if (!selectionInteraction.isDragging) { active?.refreshAnnotations(); scheduleWarm() }
    }
    override fun onTap(xPx: Float, yPx: Float): Boolean {
        if (host.documentSelection != null) { host.clearSelection(); return true }
        val page = active?.takeIf { it.current() } ?: return false
        val rect = page.bodyBounds()
        page.body.linkAt(xPx - rect.left, yPx - rect.top)?.let { page.follow(it); return true }
        page.body.highlightAt(xPx - rect.left, yPx - rect.top)?.let { return host.reportHighlightTapped(it) }
        return false
    }
    override fun onLongPress(xPx: Float, yPx: Float): Boolean {
        selectionInteraction.onLongPress(xPx, yPx)?.let { return it }
        val page = active?.takeIf { it.current() } ?: return false
        if (navigationBusy) return false
        val serial = inputSerial
        val rect = page.bodyBounds()
        paragraphJob?.cancel()
        paragraphJob = page.launchSelection {
            val selected = page.body.selectParagraph(xPx - rect.left, yPx - rect.top)
            if (selected != null && serial == inputSerial && active === page && page.current() && !navigationBusy) page.reportSelection(selected)
        }
        return true
    }
    override fun onSelectionTouch(action: Int, xPx: Float, yPx: Float): Boolean = selectionInteraction.onTouch(action, xPx, yPx)
    override fun positionForSelectionPage(chapterId: String, pageNumber: Int): ReadingPosition? {
        val page = active ?: return null
        if (page.position?.chapterId != chapterId || pageNumber !in 1..page.pageCount) return null
        return page.snapshot.position(page.index, page.snapshot.locator(page.index).put("selectionPage", pageNumber))
    }
    override fun documentSelectionCoveredBy(selected: DocumentTextSelection, outer: DocumentTextSelection): Boolean =
        EpubSelectionRanges.covers(outer, selected)
    override fun positionForDocumentSelection(selection: DocumentTextSelection): ReadingPosition? {
        val locator = EpubSelectionRanges.locator(selection) ?: return null
        return ReadingPosition(selection.bookId, selection.chapterId, contentRevision = selection.contentRevision, documentLocator = locator.toString())
    }
    override fun attach(host: ReadContentHost<ReadingPosition>) { detached = false }
    override fun onViewportChanged(viewport: ReadViewport) { selectionInteraction.reset(); inputSerial++; paragraphJob?.cancel() }
    override fun selectionBounds(): RectF? = active?.let { page -> page.body.selectionBounds()?.apply {
        val rect = page.bodyBounds(); offset(rect.left.toFloat(), rect.top.toFloat())
    } }
    override fun selectionHandleBounds(start: Boolean): RectF? = active?.let { page -> page.body.selectionHandleBounds(start)?.apply {
        val rect = page.bodyBounds(); offset(rect.left.toFloat(), rect.top.toFloat())
    } }
    override fun navigationAvailability(target: ReadNavigation<ReadingPosition>): NavigationAvailability {
        val page = active ?: return NavigationAvailability.NotReady
        val available = when (target) {
            ReadNavigation.Previous -> page.number > 1 || page.index > 0
            ReadNavigation.Next -> page.number < page.pageCount || page.index < page.snapshot.book.chapters.lastIndex
            else -> return NavigationAvailability.Unknown
        }
        return if (available) NavigationAvailability.Available else NavigationAvailability.AtBoundary
    }
    override suspend fun prepare(target: ReadNavigation<ReadingPosition>): ReadPreparation<ReadingPosition> {
        preloader.stop()
        if (detached) return ReadPreparation.Rejected(ReaderRejection.NotReady)
        if (navigationAvailability(target) == NavigationAvailability.AtBoundary) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
        val snapshot = source.acquire()
        var page: Page? = null
        var transferred = false
        try {
            val displayed = active
            var requestedPage: Int? = null
            val position = when (target) {
                ReadNavigation.First -> snapshot.position(0, snapshot.locator(0))
                ReadNavigation.Last -> snapshot.book.chapters.lastIndex.let { snapshot.position(it, snapshot.locator(it, 1.0)) }
                is ReadNavigation.Position -> target.location
                ReadNavigation.Next, ReadNavigation.Previous -> {
                    if (displayed == null) return ReadPreparation.Rejected(ReaderRejection.NotReady)
                    var index = displayed.index
                    val next = displayed.number + if (target == ReadNavigation.Next) 1 else -1
                    when {
                        next > displayed.pageCount -> { index++; requestedPage = 1 }
                        next < 1 -> { index--; requestedPage = Int.MAX_VALUE }
                        else -> requestedPage = next
                    }
                    if (index !in snapshot.book.chapters.indices) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
                    snapshot.position(index, snapshot.locator(index))
                }
            }
            val locator = snapshot.locator(position) ?: return ReadPreparation.Rejected(ReaderRejection.InvalidAnchor)
            if (requestedPage == null && locator.has("selectionPage")) requestedPage = locator.getInt("selectionPage")
            val index = snapshot.index(position)
            parked.discard { !it.current() }
            val reusable = if (mode == ReadingMode.PAGED && requestedPage != null)
                parked.take { it.matches(index, requestedPage) } ?: parked.take { it.index == index }
                else null
            if (reusable == null) parked.reserveCandidate(displayed != null)
            val created = reusable ?: Page(snapshot, index, host.generation)
            page = created
            transferred = true
            if (reusable != null) {
                snapshot.release() // Reused page already owns a snapshot reference.
                if (!created.mounted || !created.body.isBound) created.mount(locator, requestedPage)
                else if (!created.matches(index, requireNotNull(requestedPage))) created.moveToPage(requestedPage)
                else created.syncAnnotations()
            } else created.mount(locator, requestedPage)
            currentCoroutineContext().ensureActive()
            if (!created.current()) return ReadPreparation.Rejected(ReaderRejection.NotReady)
            val actual = requireNotNull(created.position)
            created.bindChrome()
            if (mode == ReadingMode.PAGED && host.documentSelection == null) {
                displayed?.prepareSurface()
                created.prepareSurface()
            }
            page = null // ownership is transferred only to the returned candidate
            val direction = if (target == ReadNavigation.Previous) -1 else 1
            return ReadPreparation.Ready(Candidate(created, actual, host.generation, direction))
        } finally { page?.close(); if (!transferred) snapshot.release() }
    }
    private inner class Candidate(val page: Page, override val target: ReadingPosition, override val generation: Long, val heading: Int) : PreparedRead<ReadingPosition> {
        override val surface get() = page.surface
        override fun dispose() { if (active !== page) page.close() }
    }
    override suspend fun prepareVisualHandoff(candidate: PreparedRead<ReadingPosition>) {
        val page = (candidate as Candidate).page
        page.reveal()
        try { page.body.awaitPresented() }
        catch (failure: Throwable) { if (active !== page) page.park(); throw failure }
    }
    override suspend fun show(candidate: PreparedRead<ReadingPosition>): ReadingPosition {
        val request = candidate as Candidate
        currentCoroutineContext().ensureActive()
        check(request.generation == host.generation && request.page.current())
        val previous = active
        active = request.page
        heading = request.heading
        request.page.reveal()
        request.page.rebindSelection()
        if (previous !== request.page) {
            previous?.park()
            previous?.let(::keepParked)
        }
        return request.target
    }
    override fun detach() {
        detached = true
        preloader.close()
        inputSerial++; paragraphJob?.cancel(); selectionInteraction.release()
        active?.close(); active = null
        parked.clear()
        root.removeAllViews()
    }

    private fun keepParked(page: Page) {
        if (!page.current() || page === active) { if (page !== active) page.close(); return }
        parked.retain(page)
    }
    override fun onPageCacheModeChanged() {
        preloader.cancel()
        parked.configure(if (mode == ReadingMode.PAGED) host.pageCacheMode else ReaderPageCacheMode.SINGLE_PAGE)
        scheduleWarm()
    }
    private fun neighbor(page: Page, direction: Int): Pair<Int, Int>? {
        var index = page.index
        var number = page.number + direction
        if (number > page.pageCount) { index++; number = 1 }
        if (number < 1) { index--; number = Int.MAX_VALUE }
        return if (index in page.snapshot.book.chapters.indices) index to number else null
    }
    private fun scheduleWarm(base: Page? = active, direction: Int = heading) {
        if (detached || navigationBusy || !parked.enabled || mode != ReadingMode.PAGED || host.documentSelection != null) return
        val displayed = base ?: return
        preloader.schedule(displayed to direction) {
            // Chromium work competes with the animation's window GPU even on a parked page.
            // Prepare neighbors only while reading; never trade animation frames for read-ahead.
            try {
                if (!displayed.current() || active !== displayed || navigationBusy) return@schedule
                displayed.prepareSurface()
                val targets = listOfNotNull(neighbor(displayed, direction), neighbor(displayed, -direction))
                    .filterNot { active?.matches(it.first, it.second) == true }
                for ((index, number) in targets) {
                    if (parked.entries.any { it.matches(index, number) && it.hasSurface }) continue
                    val reusable = parked.take { it.matches(index, number) }
                        ?: parked.take { p -> !targets.any { p.matches(it.first, it.second) } && p.index == index }
                        ?: parked.take { p -> !targets.any { p.matches(it.first, it.second) } }
                    val page = if (reusable?.index == index) reusable else {
                        reusable?.close()
                        parked.reserveCandidate(active != null)
                        val snapshot = source.acquire()
                        try { Page(snapshot, index, host.generation) } catch (failure: Throwable) { snapshot.release(); throw failure }
                    }
                    try {
                        page.invalidateSurface()
                        if (page.matches(index, number)) page.syncAnnotations()
                        else if (page.mounted && page.body.isBound) page.moveToPage(number)
                        else page.mount(page.snapshot.locator(index), number)
                        page.bindChrome()
                        page.prepareSurface()
                    } finally {
                        page.park()
                        if (page.mounted && page.body.isBound) keepParked(page) else page.close()
                    }
                    if (!displayed.current() || active !== displayed || navigationBusy) break
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Optional read-ahead failure is retried by real navigation. */ }
        }
    }

    private inner class Page(val snapshot: EpubSnapshot, val index: Int, val generation: Long) {
        val template = host.pageFactory.create(context)
        private val pageLease = ReaderPageLease.acquire(template)
        val body = try { EpubReaderBodyView(context) } catch (failure: Throwable) {
            try { template.unbind() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            pageLease.close()
            throw failure
        }
        init {
            try { body.configureComponents(host.presentationComponents) }
            catch (failure: Throwable) {
                try { body.release() } finally { try { template.unbind() } finally { pageLease.close() } }
                throw failure
            }
        }
        private var closed = false
        var mounted = false
            private set
        private var positionReady = false
        private var recording: EpubPageRecording? = null
        private var boundPosition: ReadingPosition? = null
        private var boundChromeEpoch = -1L
        val hasSurface get() = recording != null
        var position: ReadingPosition? = null
        var number = 1
        var pageCount = 1
        val surface: PageBackgroundSurface get() {
            val recorded = recording
            return object : PageBackgroundSurface, SnapshotPreferredSurface, RecordedPageSurface {
            override val preferSnapshot get() = recorded == null
            override val isRecorded get() = recorded != null
            override val widthPx get() = template.root.width
            override val heightPx get() = template.root.height
            override val backgroundColor get() = host.style.page.backgroundColor
            override fun draw(canvas: Canvas) {
                if (recorded?.draw(canvas) != true) template.root.draw(canvas)
            }
        } }
        fun invalidateSurface() { recording = null }
        fun matches(index: Int, page: Int) = current() && mounted && body.isBound && positionReady && this.index == index &&
            number == if (page == Int.MAX_VALUE) pageCount else page
        fun current() = !closed && !detached && generation == host.generation && snapshot.revision == host.book?.revision
        fun bodyBounds() = Rect(0, 0, body.width, body.height).also { root.offsetDescendantRectToMyCoords(body, it) }
        private fun accept(state: JSONObject) {
            number = state.optInt("page", 1).coerceAtLeast(1)
            pageCount = state.optInt("pages", 1).coerceAtLeast(number)
            position = snapshot.position(index, state, number)
        }
        fun bindChrome() {
            if (boundPosition == position && boundChromeEpoch == chromeEpoch) return
            invalidateSurface()
            template.bind(ReaderPageContext(snapshot.book.title, snapshot.book.chapters[index], position, pageCount,
                host.style, mode, requestReaderAction = { action ->
                    if (active === this && current()) position?.let { host.requestReaderAction(action, it, generation) } ?: false else false
                }, chromeProvider = host.pageChromeProvider))
            boundPosition = position; boundChromeEpoch = chromeEpoch
        }
        suspend fun prepareSurface() {
            if (recording != null || !current() || android.os.Build.VERSION.SDK_INT < 29) return
            if (template.root.isLayoutRequested) {
                // We own this MATCH_PARENT template. Resolve chrome's pending layout now;
                // waiting for an invisible neighbor's traversal adds whole frames to input.
                val width = root.width
                val height = root.height
                template.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                template.root.layout(0, 0, width, height)
            }
            currentCoroutineContext().ensureActive()
            if (!current()) return
            recording = HardwareEpubPageRecording(template.root)
        }
        fun reveal() {
            // The recording references child RenderNodes. A real hierarchy traversal after
            // revealing a parked WebView can replace their display lists; the recording
            // made while parked must not become the next turn's current-page surface.
            if (template.root.alpha != 1f) invalidateSurface()
            template.root.alpha = 1f
        }
        fun park() {
            annotationJob?.cancel()
            // Keep recordings local to one visibility state, including reverse navigation.
            if (template.root.alpha != 0f) invalidateSurface()
            template.root.alpha = 0f
        }
        suspend fun syncAnnotations() = renderAnnotations(host.documentAnnotations(snapshot.book.chapters[index].chapterId))
        suspend fun moveToPage(requestedPage: Int) {
            invalidateSurface()
            positionReady = false
            accept(body.goToPage(requestedPage.coerceIn(1, pageCount)))
            syncAnnotations()
            positionReady = true
        }
        suspend fun mount(locator: JSONObject, requestedPage: Int?) {
            invalidateSurface()
            mounted = false
            positionReady = false
            if (body.parent == null) template.bodyHost.addView(body, FrameLayout.LayoutParams(-1, -1))
            position = snapshot.position(index, locator)
            bindChrome()
            require(template.root.parent == null || template.root.parent === root) { "Page bind attached its root outside the reader" }
            template.root.alpha = 0f
            template.root.visibility = View.VISIBLE
            if (template.root.parent == null) root.addView(template.root, FrameLayout.LayoutParams(-1, -1))
            val state = body.bind(snapshot.publication, snapshot.publication.readingOrder[index].path, host.style, mode, locator)
            accept(state)
            mounted = true
            if (requestedPage != null) accept(body.goToPage(requestedPage.coerceIn(1, pageCount)))
            // Prepare decorations before supplying the candidate surface to a page-turn effect.
            renderAnnotations(host.documentAnnotations(snapshot.book.chapters[index].chapterId))
            body.onGeometryChanged = { if (current() && active === this) {
                invalidateSurface()
                rebindSelection()
                host.reportSelectionGeometryChanged()
            } }
            if (mode == ReadingMode.CONTINUOUS_SCROLL) body.onScrollLocation = { location ->
                if (active === this && current() && host.committedLocation?.chapterId == position?.chapterId) {
                    accept(location)
                    host.reportVisiblePosition(requireNotNull(position), generation)
                    if (active === this && current()) bindChrome()
                }
            }
            positionReady = true
        }
        fun follow(href: String) {
            val uri = runCatching { URI(href) }.getOrNull() ?: return
            if (uri.isAbsolute || uri.rawAuthority != null) {
                val target = runCatching { LinkTarget.External(href) }.getOrNull() ?: return
                host.reportLinkTapped(target, snapshot.book.chapters[index].chapterId, generation)
                return
            }
            val base = URI(null, null, "/" + snapshot.publication.readingOrder[index].path, null)
            val resolved = base.resolve(uri).normalize()
            val targetPath = resolved.path.removePrefix("/")
            val targetIndex = snapshot.publication.readingOrder.indexOfFirst { it.path == targetPath }
            if (targetIndex < 0) return // Auxiliary non-spine documents require a separate navigation entry contract.
            val target = snapshot.position(targetIndex, snapshot.locator(targetIndex, fragment = resolved.fragment))
            // Same navigation queue as directory/programmatic/page movement; never loadUrl from a body tap.
            scope.launch { if (current() && active === this@Page) host.requestNavigation(ReadNavigation.Position(target)) }
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var currentSelection: DocumentTextSelection? = null
        private var renderedAnnotations: List<DocumentRangedAnnotation>? = null
        private var annotationJob: Job? = null
        fun refreshAnnotations() {
            annotationJob?.cancel()
            val items = host.documentAnnotations(snapshot.book.chapters[index].chapterId)
            annotationJob = launchSelection { renderAnnotations(items) }
        }
        private suspend fun renderAnnotations(items: List<DocumentRangedAnnotation>) {
            val selection = host.documentSelection?.takeIf { it.chapterId == snapshot.book.chapters[index].chapterId }
            if (renderedAnnotations != items || currentSelection != selection) invalidateSurface()
            val matching = items.filter { it.selection.contentRevision == snapshot.revision &&
                EpubSelectionRanges.href(it.selection) == snapshot.publication.readingOrder[index].path }
            val statuses = body.applyAnnotations(matching, host.style)
            if (!current()) return
            val sourceSelection = host.documentSelection?.takeIf { it.chapterId == snapshot.book.chapters[index].chapterId }
            body.showSelection(sourceSelection)
            currentSelection = sourceSelection
            renderedAnnotations = items.toList()
            if (active === this) rebindSelection()
            if (!current()) return
            items.forEach { item ->
                val status = if (item.selection.contentRevision != snapshot.revision)
                    AnnotationStatus.Invalid(AnnotationInvalidReason.RevisionMismatch)
                else statuses[item.id] ?: AnnotationStatus.Invalid(AnnotationInvalidReason.InvalidRange)
                host.reportDocumentAnnotationStatus(item, status, generation)
            }
        }
        fun launchSelection(action: suspend () -> Unit): Job = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (current() && active === this@Page) host.reportError(ReaderError(ReaderStage.SELECTION,
                "SelectionFailed", failure.message ?: "Cannot select document text", cause = failure)) }
        }
        fun reportSelection(selected: JSONObject): Boolean {
            val text = selected.optString("text")
            if (text.isEmpty() || !current() || active !== this) return false
            body.acceptExtendedSelection(selected)
            invalidateSurface()
            val payload = DocumentTextSelection(snapshot.bookId, snapshot.book.chapters[index].chapterId, snapshot.revision,
                JSONObject().put("format", "iread-epub-selection").put("version", 1).put("href", snapshot.publication.readingOrder[index].path)
                    .put("range", JSONObject().put("start", selected.getJSONObject("start")).put("end", selected.getJSONObject("end"))
                        .put("domText", selected.optString("domText"))).toString(), text)
            currentSelection = payload
            val rect = bodyBounds()
            return host.reportDocumentSelection(payload, selectionHandle(payload), body.selectionBounds()?.apply {
                offset(rect.left.toFloat(), rect.top.toFloat())
            }, generation)
        }
        fun rebindSelection() {
            val value = currentSelection ?: return
            if (!current() || active !== this || host.documentSelection != value) return
            val rect = bodyBounds()
            host.reportDocumentSelection(value, selectionHandle(value), body.selectionBounds()?.apply {
                offset(rect.left.toFloat(), rect.top.toFloat())
            }, generation)
        }
        private fun selectionHandle(value: DocumentTextSelection) = object : DocumentSelectionHandle {
            override fun isValid() = current() && active === this@Page && body.hasSelection &&
                value == currentSelection && value.contentRevision == snapshot.revision
            override fun clearSelection() = body.clearSelection()
        }
        fun close() {
            if (closed) return
            closed = true
            recording = null
            scope.cancel()
            try { body.release() } finally {
                try { (template.root.parent as? android.view.ViewGroup)?.removeView(template.root); template.unbind() }
                finally { pageLease.close(); snapshot.release() }
            }
        }
    }
}
