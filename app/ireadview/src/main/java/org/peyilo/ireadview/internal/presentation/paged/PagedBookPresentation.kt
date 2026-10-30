package org.peyilo.ireadview.internal.presentation.paged

import android.content.Context
import android.graphics.Rect
import android.graphics.PointF
import android.graphics.RectF
import android.view.View
import android.view.MotionEvent
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.widget.FrameLayout
import kotlinx.coroutines.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.render.TextReaderBodyView
import org.peyilo.ireadview.internal.render.ReaderPlaceholderController
import org.peyilo.ireadview.internal.render.AccessibilitySourceTraversal
import org.peyilo.ireadview.internal.render.AccessibilityTraversalBoundary
import org.peyilo.ireadview.internal.presentation.AccessibilityChapterTraversal
import org.peyilo.ireadview.internal.presentation.preserveAccessibilityAnchor
import org.peyilo.ireadview.internal.render.AccessibilityTraversalAnchor
import org.peyilo.ireadview.internal.render.ColoredTextRange
import org.peyilo.ireadview.internal.selection.TextSelectionResolver
import org.peyilo.ireadview.internal.render.awaitMeasured
import org.peyilo.ireadview.internal.render.viewPageSurface
import org.peyilo.ireadview.spi.page.*
import org.peyilo.ireadview.spi.presentation.*

internal class PagedBookPresentation(context: Context, private val bookHost: BookPresentationHost) :
    BookPresentation() {
    override val supportsBlockLayout = true
    override val contentFeatures = BookPresentationFeatures(
        ReaderSelectionModel.Text,
        ReaderFeatureSupport.Supported, ReaderFeatureSupport.Supported
    )
    private val container = FrameLayout(context)
    override val contentView: View get() = container
    private var active: PageCandidate? = null
    private val parked = ReaderPageCache<PageCandidate>(bookHost.pageCacheMode) { it.close() }
    private val preloader = ReaderPagePreloader()
    private var heading = 1
    override fun onPlaceholdersChanged() {
        invalidateSparePages(); active?.bindPlaceholder()
    }

    override val needsTimeUpdates get() = !detached && active?.takeIf { it.generation == bookHost.generation }?.template?.needsTimeUpdates == true
    override fun onTimeChanged(timeMillis: Long) {
        active?.takeIf { !detached && it.generation == bookHost.generation }?.template?.onTimeChanged(
            timeMillis
        )
    }

    override fun onPageChromeChanged() {
        invalidateSparePages(); active?.takeIf { !detached && it.generation == bookHost.generation }?.template?.refreshChrome()
    }

    override fun navigationAvailability(target: ReadNavigation<ReadingPosition>): NavigationAvailability {
        val page = active?.takeIf { it.generation == bookHost.generation }
            ?: return NavigationAvailability.NotReady
        val book = bookHost.book ?: return NavigationAvailability.NotReady
        val index = book.chapters.indexOfFirst { it.chapterId == page.target.chapterId }
        val number = page.target.pageNumberHint ?: return NavigationAvailability.Unknown
        if (index < 0 || number !in 1..page.layout.pageCount) return NavigationAvailability.NotReady
        val canMove = when (target) {
            ReadNavigation.Previous -> number > 1 || index > 0
            ReadNavigation.Next -> number < page.layout.pageCount || index < book.chapters.lastIndex
            else -> return NavigationAvailability.Unknown
        }
        return if (canMove) NavigationAvailability.Available else NavigationAvailability.AtBoundary
    }

    override val currentSurface
        get() = active?.takeIf { it.generation == bookHost.generation }?.template?.root?.let(
            ::viewPageSurface
        )
    private var detached = false
    private val reportedFallbacks = HashSet<Pair<String, String>>()
    private var selectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var accessibilityJob: Job? = null
    private var navigationBusy = false
    override fun onNavigationStateChanged(state: ReadNavigationState) {
        navigationBusy = state != ReadNavigationState.Idle
        if (navigationBusy) preloader.cancel() else scheduleWarm()
    }

    override fun onPageCacheModeChanged() {
        preloader.cancel()
        parked.configure(bookHost.pageCacheMode)
        scheduleWarm()
    }

    private fun invalidateSparePages() {
        preloader.cancel()
        parked.clear()
        scheduleWarm()
    }

    private fun scheduleWarm() {
        val current = active ?: return
        if (detached || navigationBusy || !parked.enabled || bookHost.selection != null) return
        preloader.schedule(current to heading) {
            for (direction in listOf(heading, -heading)) {
                if (detached || active !== current || navigationBusy || current.generation != bookHost.generation) break
                val book = bookHost.book ?: break
                var index = book.chapters.indexOfFirst { it.chapterId == current.target.chapterId }
                var number = requireNotNull(current.target.pageNumberHint) + direction
                if (number > current.layout.pageCount) {
                    index++; number = 1
                }
                if (number < 1) {
                    index--; number = Int.MAX_VALUE
                }
                if (index !in book.chapters.indices) continue
                val chapterId = book.chapters[index].chapterId
                if (parked.entries.any { it.matches(chapterId, number, null) }) continue
                val position = ReadingPosition(book.bookId, chapterId, pageNumberHint = number)
                val ready = preparePage(ReadNavigation.Position(position)) as? ReadPreparation.Ready
                    ?: continue
                val candidate = ready.candidate as PageCandidate
                if (!detached && active === current && !navigationBusy && candidate.generation == bookHost.generation)
                    parked.retain(candidate)
                else candidate.close()
            }
        }
    }

    private val selectionInteraction: org.peyilo.ireadview.spi.selection.ReaderSelectionController =
        bookHost.presentationComponents.selectionControllerFactory.create(
            context,
            object : org.peyilo.ireadview.spi.selection.ReaderSelectionInteraction.Delegate() {
                override fun ready() =
                    !detached && active?.generation == bookHost.generation && bookHost.selection != null

                private fun local(x: Float, y: Float): PointF? {
                    val page = active ?: return null
                    val offset = Rect(); container.offsetDescendantRectToMyCoords(page.body, offset)
                    return PointF(x - offset.left, y - offset.top)
                }

                override fun handleAt(x: Float, y: Float): Boolean? =
                    local(x, y)?.let { active?.body?.selectionHandleAt(it.x, it.y) }

                override fun grabOffset(start: Boolean, x: Float, y: Float): PointF? =
                    local(x, y)?.let { active?.body?.selectionDragOffset(start, it.x, it.y) }

                override suspend fun extend(
                    start: Boolean,
                    point: PointF,
                    current: () -> Boolean
                ): Boolean {
                    val page = active ?: return false
                    val content = page.layout.contentSnapshot ?: return false
                    val selected = bookHost.selection ?: return false
                    val at = local(point.x, point.y) ?: return false
                    val anchor = page.body.nearestAnchorAt(at.x, at.y) ?: return false
                    val extended = TextSelectionResolver.extend(content, selected, anchor, start)
                        ?: return false
                    return current() && bookHost.reportSelection(
                        extended,
                        page.generation
                    ) is ReaderResult.Success
                }

                override fun edgeDirection(point: PointF): Int {
                    val page = active ?: return 0
                    val at = local(point.x, point.y) ?: return 0
                    val edge = 24f * container.resources.displayMetrics.density
                    return when {
                        at.y <= page.body.paddingTop + edge -> -1
                        at.y >= page.body.height - page.body.paddingBottom - edge -> 1; else -> 0
                    }
                }

                override suspend fun movePage(
                    direction: Int,
                    start: Boolean,
                    current: () -> Boolean
                ): Boolean {
                    val page = active ?: return false
                    val selected = bookHost.selection ?: return false
                    val number = page.target.pageNumberHint ?: return false
                    val target = number + direction
                    if (target !in 1..page.layout.pageCount || selected.range.chapterId != page.target.chapterId) return false
                    val fixed = if (start) selected.range.endExclusive else selected.range.start
                    val fixedPage =
                        (page.layout.pageForAnchor(fixed) as? ReaderResult.Success)?.value
                            ?: return false
                    if (start && target > fixedPage || !start && target < fixedPage) return false
                    val result = bookHost.requestSelectionPage(
                        page.target.chapterId,
                        target,
                        page.generation,
                        current
                    )
                    if (result is NavigationResult.Failed) bookHost.reportError(result.error)
                    return result is NavigationResult.Moved || result is NavigationResult.Unchanged
                }

                override fun reportDragging(active: Boolean) = bookHost.reportSelectionDrag(active)
                override fun reportError(error: Exception) = bookHost.reportError(
                    ReaderError(
                        ReaderStage.SELECTION,
                        "SelectionPageFailed",
                        error.message ?: "Cannot extend selection",
                        cause = error
                    )
                )
            })

    override fun layoutViewport(): ReadViewport? {
        val page = active?.takeIf { it.generation == bookHost.generation } ?: return null
        return bookHost.viewport.copy(
            widthPx = page.body.width - page.body.paddingLeft - page.body.paddingRight,
            heightPx = page.body.height - page.body.paddingTop - page.body.paddingBottom
        )
    }

    override fun attach(host: ReadContentHost<ReadingPosition>) {
        detached = false
        if (!selectionScope.isActive) selectionScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onViewportChanged(viewport: ReadViewport) {
        accessibilityJob?.cancel(); stopSelectionDrag(); container.requestLayout()
    }

    override fun onDecorationsChanged() {
        invalidateSparePages(); active?.let { updateDecorations(it) }
    }

    override fun currentContent(): ChapterContent? =
        active?.takeIf { it.generation == bookHost.generation }?.layout?.contentSnapshot

    override fun selectionBounds(): RectF? {
        val page = active ?: return null
        val bounds = page.body.selectionBounds() ?: return null
        val offset = Rect()
        container.offsetDescendantRectToMyCoords(page.body, offset)
        return bounds.apply { offset(offset.left.toFloat(), offset.top.toFloat()) }
    }

    override fun selectionHandleBounds(start: Boolean): RectF? {
        val page = active ?: return null
        val bounds = page.body.selectionHandleBounds(start) ?: return null
        val offset = Rect(); container.offsetDescendantRectToMyCoords(page.body, offset)
        return bounds.apply { offset(offset.left.toFloat(), offset.top.toFloat()) }
    }

    override fun onTap(xPx: Float, yPx: Float): Boolean {
        if (bookHost.selection != null) {
            bookHost.clearSelection(); return true
        }
        val page = active ?: return false
        if (page.generation != bookHost.generation) return false
        val offset = Rect()
        container.offsetDescendantRectToMyCoords(page.body, offset)
        val x = xPx - offset.left
        val y = yPx - offset.top
        page.body.linkAt(x, y)
            ?.let { return bookHost.reportLinkTapped(it, page.layout.chapterId, page.generation) }
        return page.body.highlightAt(x, y)?.let(bookHost::reportHighlightTapped) ?: false
    }

    override fun onLongPress(xPx: Float, yPx: Float): Boolean {
        selectionInteraction.onLongPress(xPx, yPx)?.let { return it }
        val page = active ?: return false
        if (page.generation != bookHost.generation) return false
        val content = page.layout.contentSnapshot ?: return false
        val offset = Rect()
        container.offsetDescendantRectToMyCoords(page.body, offset)
        val x = xPx - offset.left
        val y = yPx - offset.top
        val highlightId = page.body.highlightAt(x, y)
        val restored = highlightId?.let { id ->
            bookHost.validHighlights(content.chapterId).firstOrNull { it.id == id }
        }
            ?.let { TextSelectionResolver.fromHighlight(content, it) }
        val selection = restored ?: run {
            val anchor = page.body.anchorAt(x, y) ?: return false
            TextSelectionResolver.paragraphAt(content, anchor) ?: return false
        }
        return bookHost.reportSelection(selection, page.generation) is ReaderResult.Success
    }

    override fun onSelectionTouch(action: Int, xPx: Float, yPx: Float): Boolean =
        selectionInteraction.onTouch(action, xPx, yPx)

    private fun stopSelectionDrag() = selectionInteraction.reset()

    private fun updateDecorations(page: PageCandidate, reportFallbacks: Boolean = true) {
        if (page.generation != bookHost.generation) {
            page.body.setHighlights(emptyList()); page.body.setUnderlines(emptyList()); page.body.setSelection(
                emptyList(),
                bookHost.style.selectionStyle
            ); return
        }
        val styles = bookHost.style.highlightStyles
        val highlights = bookHost.validHighlights(page.target.chapterId)
        val missing =
            highlights.filter { it.styleId !in styles.styles }.map { it.id to it.styleId }.toSet()
        if (reportFallbacks) reportedFallbacks.retainAll(missing)
        page.body.setHighlights(highlights.flatMap { highlight ->
            val ranges =
                (page.layout.rangesForText(highlight.range) as? ReaderResult.Success)?.value
                    ?: emptyList()
            ranges.map { ColoredTextRange(highlight.id, it, styles[highlight.styleId].fillColor) }
        })
        val selection = bookHost.selection
        page.body.setUnderlines(
            bookHost.validUnderlines(page.target.chapterId).flatMap { underline ->
                ((page.layout.rangesForText(underline.range) as? ReaderResult.Success)?.value
                    ?: emptyList()).map {
                    org.peyilo.ireadview.internal.render.LineTextRange(
                        underline.id,
                        it,
                        underline.style
                    )
                }
            })
        val ranges = selection?.takeIf { it.range.chapterId == page.target.chapterId }
            ?.let { (page.layout.rangesForText(it.range) as? ReaderResult.Success)?.value }
            ?: emptyList()
        page.body.setSelection(ranges, bookHost.style.selectionStyle)
        if (!reportFallbacks) return
        missing.filter { reportedFallbacks.add(it) }.forEach { (id, styleId) ->
            val generation = bookHost.generation
            container.post {
                if (!detached && bookHost.generation == generation && active === page && styleId !in bookHost.style.highlightStyles.styles && bookHost.validHighlights(
                        page.target.chapterId
                    ).any { it.id == id && it.styleId == styleId }
                ) bookHost.reportHighlightStyleFallback(id, styleId)
            }
        }
    }

    override suspend fun prepare(target: ReadNavigation<ReadingPosition>): ReadPreparation<ReadingPosition> {
        preloader.stop()
        if (target == ReadNavigation.Previous) heading =
            -1 else if (target == ReadNavigation.Next) heading = 1
        return preparePage(target)
    }

    private suspend fun preparePage(target: ReadNavigation<ReadingPosition>): ReadPreparation<ReadingPosition> {
        val book = bookHost.book ?: return ReadPreparation.Rejected(ReaderRejection.NoBook)
        if (book.chapters.isEmpty()) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
        val current = bookHost.committedLocation
        var index =
            current?.let { position -> book.chapters.indexOfFirst { it.chapterId == position.chapterId } }
                ?: 0
        var page: Int
        var anchor: ContentAnchor? = null
        var requiredRevision: String? = null
        when (target) {
            ReadNavigation.First -> {
                index = 0; page = 1
            }

            ReadNavigation.Last -> {
                index = book.chapters.lastIndex; page = Int.MAX_VALUE
            }

            is ReadNavigation.Position -> {
                if (target.location.documentLocator != null) return ReadPreparation.Rejected(
                    ReaderRejection.Unsupported
                )
                if (target.location.bookId != book.bookId) return ReadPreparation.Rejected(
                    ReaderRejection.BookMismatch
                )
                index = book.chapters.indexOfFirst { it.chapterId == target.location.chapterId }
                if (index < 0) return ReadPreparation.Rejected(ReaderRejection.UnknownChapter)
                page = target.location.pageNumberHint ?: 1
                anchor = target.location.anchor
                requiredRevision = target.location.contentRevision
            }

            ReadNavigation.Next -> {
                val shown = active ?: return ReadPreparation.Rejected(ReaderRejection.NotReady)
                page = (current?.pageNumberHint ?: 1) + 1
                if (page > shown.layout.pageCount) {
                    index++; page = 1
                }
            }

            ReadNavigation.Previous -> {
                if (current == null || active == null) return ReadPreparation.Rejected(
                    ReaderRejection.NotReady
                )
                page = (current.pageNumberHint ?: 1) - 1
                if (page < 1) {
                    index--; page = Int.MAX_VALUE
                }
            }
        }
        if (index !in book.chapters.indices) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
        val chapter = book.chapters[index]
        val generation = bookHost.generation
        parked.discard { it.generation != generation }
        if (anchor == null) parked.take { it.matches(chapter.chapterId, page, requiredRevision) }
            ?.let {
                return ReadPreparation.Ready(it)
            }
        parked.reserveCandidate(active != null)
        val style = when (val result =
            org.peyilo.ireadview.internal.style.ReaderFontResolver(container.context)
                .prepareChrome(bookHost.style)) {
            is ReaderResult.Success -> result.value
            is ReaderResult.Failure -> return ReadPreparation.Failed(result.error)
            else -> return ReadPreparation.Rejected(ReaderRejection.NotReady)
        }
        if (detached || generation != bookHost.generation) return ReadPreparation.Rejected(
            ReaderRejection.NotReady
        )
        val template = bookHost.pageFactory.create(container.context)
        var transferred = false
        var ownsTemplate = false
        var rootMountAttempted = false
        var mountedBody: TextReaderBodyView? = null
        var cleanupPlaceholders: ReaderPlaceholderController? = null
        try {
            ownsTemplate = org.peyilo.ireadview.internal.presentation.ReaderPageOwnership.acquire(
                template,
                template.root
            )
            require(
                ownsTemplate && (template.bodyHost === template.root || isDescendant(
                    template.bodyHost,
                    template.root
                ))
            ) { "Page factory must return a fresh root containing bodyHost" }
            val body =
                TextReaderBodyView(container.context).apply { showOffPageSelectionHandles = true }
            mountedBody = body
            body.configureComponents(bookHost.presentationComponents)
            val emptyRoot = FrameLayout(container.context)
            val placeholders =
                ReaderPlaceholderController(emptyRoot) { bookHost.placeholderFactory }
            cleanupPlaceholders = placeholders
            template.bodyHost.addView(body, FrameLayout.LayoutParams(-1, -1))
            template.bind(
                ReaderPageContext(
                    book.title,
                    chapter,
                    null,
                    null,
                    style,
                    chromeProvider = bookHost.pageChromeProvider
                )
            )
            currentCoroutineContext().ensureActive()
            if (detached || generation != bookHost.generation) return ReadPreparation.Rejected(
                ReaderRejection.NotReady
            )
            require(template.root.parent == null) { "Page bind attached its root outside the presentation" }
            template.root.visibility = View.INVISIBLE
            rootMountAttempted = true
            container.addView(
                template.root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            template.root.awaitMeasured()
            org.peyilo.ireadview.internal.style.ReaderBodyInsets.apply(body, style)
            val viewport = bookHost.viewport.copy(
                widthPx = body.width - body.paddingLeft - body.paddingRight,
                heightPx = body.height - body.paddingTop - body.paddingBottom
            )
            val layout = when (val result = bookHost.layoutChapter(chapter.chapterId, viewport)) {
                is ReaderResult.Success -> result.value
                is ReaderResult.Failure -> return ReadPreparation.Failed(result.error)
                is ReaderResult.Rejected -> return ReadPreparation.Rejected(result.reason)
                ReaderResult.Superseded -> return ReadPreparation.Rejected(ReaderRejection.NotReady)
            }
            if (anchor != null) {
                if (requiredRevision != null && requiredRevision != layout.contentRevision) return ReadPreparation.Rejected(
                    ReaderRejection.InvalidAnchor
                )
                when (val found = layout.pageForAnchor(anchor)) {
                    is ReaderResult.Success -> page = found.value
                    else -> return ReadPreparation.Rejected(ReaderRejection.InvalidAnchor)
                }
            }
            if (page == Int.MAX_VALUE) page = layout.pageCount
            if (page !in 1..layout.pageCount) return ReadPreparation.Rejected(ReaderRejection.InvalidPage)
            val resolvedAnchor = (layout.anchorForPage(page) as ReaderResult.Success).value
            val position = ReadingPosition(
                book.bookId,
                chapter.chapterId,
                resolvedAnchor,
                layout.contentRevision,
                page,
                index + 1
            )
            body.fragments = (layout.fragmentsForPage(page) as ReaderResult.Success).value
            template.bind(
                ReaderPageContext(
                book.title, chapter, position, layout.pageCount, style,
                requestReaderAction = { action ->
                    active?.let { it.template === template && it.generation == generation } == true &&
                            bookHost.requestReaderAction(action, position, generation)
                }, chromeProvider = bookHost.pageChromeProvider
            )
            )
            template.root.awaitMeasured()
            if (detached || generation != bookHost.generation) return ReadPreparation.Rejected(
                ReaderRejection.NotReady
            )
            val candidate = PageCandidate(position, generation, template, layout, body)
            if (layout.contentSnapshot?.blocks?.isEmpty() == true) {
                template.bodyHost.addView(emptyRoot, FrameLayout.LayoutParams(-1, -1))
                candidate.placeholders = placeholders
                candidate.bindPlaceholder()
            }
            body.accessibilityMatchesText = { range, text ->
                active === candidate && !detached && generation == bookHost.generation &&
                        layout.contentSnapshot?.let {
                            TextSelectionResolver.matchesBlockRange(
                                it,
                                range,
                                text
                            )
                        } == true
            }
            body.accessibilitySelectText = { range ->
                if (active !== candidate || detached || generation != bookHost.generation) false
                else layout.contentSnapshot?.let { content ->
                    TextSelectionResolver.fromBlockRange(
                        content,
                        range
                    )
                }
                    ?.let {
                        bookHost.reportSelection(
                            it,
                            generation
                        ) is ReaderResult.Success && bookHost.selection == it
                    } ?: false
            }
            body.accessibilityClearSelection = {
                active === candidate && !detached && bookHost.clearAccessibilitySelection(generation) && bookHost.selection == null
            }
            body.accessibilitySelectAnchors = { fixed, moving ->
                if (active !== candidate || detached || generation != bookHost.generation) false
                else layout.contentSnapshot?.let {
                    TextSelectionResolver.fromAnchors(
                        it,
                        fixed,
                        moving
                    )
                }
                    ?.let {
                        bookHost.reportSelection(
                            it,
                            generation
                        ) is ReaderResult.Success && bookHost.selection == it
                    } ?: false
            }
            body.accessibilityCompareAnchors = { first, second ->
                layout.contentSnapshot?.let {
                    TextSelectionResolver.compareAnchors(
                        it,
                        first,
                        second
                    )
                }
            }
            body.accessibilityPreserveAnchor = { anchor ->
                if (active !== candidate || detached) null else layout.contentSnapshot?.let {
                    preserveAccessibilityAnchor(anchor, it, generation, bookHost, ::currentContent)
                }
            }
            body.accessibilityMoveOffscreen = { action, arguments, boundary, fixed, valid ->
                moveAccessibilityOffscreen(candidate, action, arguments, boundary, fixed, valid)
            }
            updateDecorations(candidate, reportFallbacks = false)
            currentCoroutineContext().ensureActive()
            transferred = true
            return ReadPreparation.Ready(candidate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (!transferred && ownsTemplate) cleanupPage(
                template,
                listOf(
                    { mountedBody?.release() }, { cleanupPlaceholders?.clear() },
                    { mountedBody?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) } }),
                generation,
                rootMountAttempted
            )
        }
    }

    override suspend fun show(candidate: PreparedRead<ReadingPosition>): ReadingPosition {
        val page = candidate as PageCandidate
        check(!detached && page.generation == bookHost.generation)
        val old = active
        updateDecorations(page)
        page.template.refreshChrome()
        currentCoroutineContext().ensureActive()
        if (detached || page.generation != bookHost.generation || active !== old) return page.target
        page.template.root.visibility = View.VISIBLE
        currentCoroutineContext().ensureActive()
        if (detached || page.generation != bookHost.generation || active !== old) return page.target
        active = page
        if (old !== page) old?.let {
            it.template.root.visibility = View.INVISIBLE
            parked.retain(it)
        }
        return page.target
    }

    override fun detach() {
        detached = true
        preloader.close()
        stopSelectionDrag()
        selectionInteraction.release()
        selectionScope.cancel()
        accessibilityJob = null
        val old = active
        active = null
        old?.close()
        parked.clear()
        reportedFallbacks.clear()
        container.removeAllViews()
    }

    private fun moveAccessibilityOffscreen(
        page: PageCandidate,
        action: Int,
        arguments: Bundle,
        boundary: AccessibilityTraversalBoundary?,
        fixed: AccessibilityTraversalAnchor?,
        valid: () -> Boolean
    ): Boolean {
        if (detached || active !== page || page.generation != bookHost.generation || navigationBusy ||
            selectionInteraction.isDragging || accessibilityJob != null || !valid()
        ) return false
        val direction = when (action) {
            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> ReadNavigation.Next
            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> ReadNavigation.Previous
            else -> return false
        }
        val extend = arguments.getBoolean(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
            false
        )
        if (extend && (fixed == null || !fixed.isValid() || boundary !is AccessibilityTraversalBoundary.Source)) return false
        if (navigationAvailability(direction) != NavigationAvailability.Available) return false
        val book = bookHost.book ?: return false
        val forward = direction == ReadNavigation.Next
        val granularity = arguments.getInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            0
        )
        val selectionBefore = bookHost.selection
        fun sourceCurrent() =
            !detached && active === page && page.generation == bookHost.generation &&
                    bookHost.book === book && !selectionInteraction.isDragging && valid() &&
                    (!extend || bookHost.selection == selectionBefore && fixed?.isValid() == true)

        fun current() = !navigationBusy && sourceCurrent()
        lateinit var job: Job
        job = selectionScope.launch(start = CoroutineStart.LAZY) {
            if (!current()) return@launch
            val resolved = try {
                val content = page.layout.contentSnapshot
                val resolved = if (content != null && boundary != null) {
                    if (extend) withContext(Dispatchers.Default) {
                        val work = currentCoroutineContext()
                        AccessibilitySourceTraversal.find(
                            content,
                            boundary,
                            forward,
                            granularity
                        ) { work.ensureActive() }
                    } else {
                        val lookup = AccessibilityChapterTraversal.resolve(
                            book, content, boundary, forward, granularity,
                            ::current, bookHost::getChapter
                        )
                        (lookup as? ReaderResult.Success)?.value ?: return@launch
                    }
                } else AccessibilitySourceTraversal.Result.Unsupported()
                if (!current() || resolved == AccessibilitySourceTraversal.Result.Invalid ||
                    resolved == AccessibilitySourceTraversal.Result.Exhausted
                ) return@launch
                resolved
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                android.util.Log.w("iReadView", "Accessibility source lookup failed", error)
                return@launch
            }
            val destination = resolved as? AccessibilitySourceTraversal.Result.Found
            if (extend && (destination == null || destination.position.chapterId != page.target.chapterId || fixed?.isValid() != true)) return@launch
            val customPosition =
                (resolved as? AccessibilitySourceTraversal.Result.Unsupported)?.position
            val seek = destination?.position ?: customPosition
            val target = seek?.let { ReadNavigation.Position(it) } ?: direction
            val result = try {
                bookHost.requestAccessibilityNavigation(target, page.generation, ::sourceCurrent)
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (!detached && page.generation == bookHost.generation && bookHost.book === book) {
                    bookHost.reportError(
                        ReaderError(
                            ReaderStage.NAVIGATION,
                            "AccessibilityTraversalFailed",
                            error.message ?: "Accessibility text navigation failed",
                            retryable = false,
                            cause = error
                        )
                    )
                }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            val shown = active ?: return@launch
            val position = when (result) {
                is NavigationResult.Moved -> result.to
                is NavigationResult.Unchanged -> result.location
                else -> return@launch
            }
            if (detached || navigationBusy ||
                bookHost.book !== book || shown.generation != page.generation ||
                shown.generation != bookHost.generation || shown.target != position ||
                bookHost.committedLocation != shown.target
            ) return@launch
            if (extend && (bookHost.selection != selectionBefore || fixed?.isValid() != true)) return@launch
            try {
                if (destination != null && shown.target.chapterId == destination.position.chapterId)
                    shown.body.accessibilityMoveAtAnchor(
                        action,
                        arguments,
                        destination.caret,
                        fixed
                    )
                else if (customPosition?.anchor != null && shown.target.chapterId == customPosition.chapterId)
                    shown.body.accessibilityMoveAtBlock(
                        action,
                        arguments,
                        customPosition.anchor.blockId
                    )
                else shown.body.accessibilityMoveFromBoundary(action, arguments, null)
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                android.util.Log.w("iReadView", "Accessibility target traversal failed", error)
            }
        }
        accessibilityJob = job
        job.invokeOnCompletion { if (accessibilityJob === job) accessibilityJob = null }
        // Start after the current virtual-node action returns; failed preparation keeps source focus.
        container.post { if (accessibilityJob === job) job.start() }
        return true
    }

    private inner class PageCandidate(
        override val target: ReadingPosition,
        override val generation: Long,
        val template: ReaderPageTemplate,
        val layout: ChapterLayout,
        val body: TextReaderBodyView
    ) : PreparedRead<ReadingPosition> {
        override val surface get() = viewPageSurface(template.root)
        private var closed = false
        fun matches(chapterId: String, number: Int, revision: String?) =
            !closed && generation == bookHost.generation &&
                    target.chapterId == chapterId && target.pageNumberHint == (if (number == Int.MAX_VALUE) layout.pageCount else number) &&
                    (revision == null || target.contentRevision == revision)

        var placeholders: ReaderPlaceholderController? = null
        fun bindPlaceholder() {
            placeholders?.show(
                org.peyilo.ireadview.spi.page.PlaceholderState(
                    org.peyilo.ireadview.spi.page.PlaceholderKind.EMPTY_CHAPTER,
                    target.bookId, target.chapterId, null, bookHost.style
                ), generation, object : org.peyilo.ireadview.spi.page.PlaceholderActions {
                    override val canRetry = false
                    override fun retry() = false
                })
        }

        fun close() {
            if (closed) return
            closed = true
            cleanupPage(
                template, listOf(
                    { body.release() }, { placeholders?.clear() },
                { (body.parent as? android.view.ViewGroup)?.removeView(body) }), generation
            )
        }

        override fun dispose() {
            if (active !== this && !parked.contains(this)) {
                if (!closed && !detached && generation == bookHost.generation) parked.retain(this) else close()
            }
        }
    }

    private fun cleanupPage(
        template: ReaderPageTemplate,
        before: List<() -> Unit>,
        generation: Long,
        removeRoot: Boolean = true
    ) {
        org.peyilo.ireadview.internal.presentation.cleanupReaderPage(
            template, before,
            { if (removeRoot) container.removeView(template.root) }) { error ->
            android.util.Log.w("iReadView", "Page cleanup failed", error)
            container.post {
                if (!detached && generation == bookHost.generation) bookHost.reportError(
                    ReaderError(
                        ReaderStage.NAVIGATION,
                        "PageCleanupFailed",
                        error.message ?: "Page cleanup failed",
                        retryable = false,
                        cause = error
                    )
                )
            }
        }
    }

    private fun isDescendant(child: View, root: View): Boolean {
        var ancestor = child.parent
        while (ancestor is View) {
            if (ancestor === root) return true; ancestor = ancestor.parent
        }
        return false
    }
}
