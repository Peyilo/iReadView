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
import org.peyilo.ireadview.internal.render.ReaderBodyView
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

internal class PagedBookPresentation(context: Context, private val bookHost: BookPresentationHost) : BookPresentation() {
    private val container = FrameLayout(context)
    override val contentView: View get() = container
    private var active: PageCandidate? = null
    override fun onPlaceholdersChanged() { active?.bindPlaceholder() }
    override val needsTimeUpdates get() = !detached && active?.takeIf { it.generation == bookHost.generation }?.template?.needsTimeUpdates == true
    override fun onTimeChanged(timeMillis: Long) { active?.takeIf { !detached && it.generation == bookHost.generation }?.template?.onTimeChanged(timeMillis) }
    override fun navigationAvailability(target: ReadNavigation<ReadingPosition>): NavigationAvailability {
        val page = active?.takeIf { it.generation == bookHost.generation } ?: return NavigationAvailability.NotReady
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
    override val currentSurface get() = active?.takeIf { it.generation == bookHost.generation }?.template?.root?.let(::viewPageSurface)
    private var detached = false
    private val reportedFallbacks = HashSet<Pair<String, String>>()
    private var draggingHandle: Boolean? = null
    private var dragOffset = PointF()
    private var selectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var edgeJob: Job? = null
    private var accessibilityJob: Job? = null
    private var navigationBusy = false
    override fun onNavigationStateChanged(state: ReadNavigationState) { navigationBusy = state != ReadNavigationState.Idle }
    private var edgeDirection = 0
    private var dragSerial = 0L
    private var dragPointer = PointF()
    override fun layoutViewport(): ReadViewport? {
        val page = active?.takeIf { it.generation == bookHost.generation } ?: return null
        return bookHost.viewport.copy(widthPx = page.body.width - page.body.paddingLeft - page.body.paddingRight,
            heightPx = page.body.height - page.body.paddingTop - page.body.paddingBottom)
    }

    override fun attach(host: ReadContentHost<ReadingPosition>) {
        detached = false
        if (!selectionScope.isActive) selectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    override fun onViewportChanged(viewport: ReadViewport) { accessibilityJob?.cancel(); stopSelectionDrag(); container.requestLayout() }
    override fun onDecorationsChanged() { active?.let { updateDecorations(it) } }
    override fun currentContent(): ChapterContent? = active?.takeIf { it.generation == bookHost.generation }?.layout?.contentSnapshot
    override fun selectionBounds(): RectF? {
        val page = active ?: return null
        val bounds = page.body.selectionBounds() ?: return null
        val offset = Rect()
        container.offsetDescendantRectToMyCoords(page.body, offset)
        return bounds.apply { offset(offset.left.toFloat(), offset.top.toFloat()) }
    }
    override fun onTap(xPx: Float, yPx: Float): Boolean {
        if (bookHost.selection != null) { bookHost.clearSelection(); return true }
        val page = active ?: return false
        if (page.generation != bookHost.generation) return false
        val offset = Rect()
        container.offsetDescendantRectToMyCoords(page.body, offset)
        val id = page.body.highlightAt(xPx - offset.left, yPx - offset.top) ?: return false
        return bookHost.reportHighlightTapped(id)
    }
    override fun onLongPress(xPx: Float, yPx: Float): Boolean {
        val page = active ?: return false
        if (page.generation != bookHost.generation) return false
        val content = page.layout.contentSnapshot ?: return false
        val offset = Rect()
        container.offsetDescendantRectToMyCoords(page.body, offset)
        val anchor = page.body.anchorAt(xPx - offset.left, yPx - offset.top) ?: return false
        val selection = TextSelectionResolver.wordAt(content, anchor) ?: return false
        return bookHost.reportSelection(selection, page.generation) is ReaderResult.Success
    }
    override fun onSelectionTouch(action: Int, xPx: Float, yPx: Float): Boolean {
        val page = active
        if (page == null || page.generation != bookHost.generation || bookHost.selection == null) {
            val consumed = draggingHandle != null
            stopSelectionDrag()
            return consumed
        }
        val offset = Rect()
        container.offsetDescendantRectToMyCoords(page.body, offset)
        val x = xPx - offset.left
        val y = yPx - offset.top
        if (action == MotionEvent.ACTION_DOWN) {
            stopSelectionDrag()
            draggingHandle = page.body.selectionHandleAt(x, y) ?: return false
            dragSerial++
            dragPointer = PointF(xPx, yPx)
            dragOffset = page.body.selectionDragOffset(draggingHandle!!, x, y) ?: PointF()
            bookHost.reportSelectionDrag(true)
            return true
        }
        val start = draggingHandle ?: return false
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            dragPointer = PointF(xPx, yPx)
            val content = page.layout.contentSnapshot
            val selected = bookHost.selection
            // Preserve where the finger grabbed the handle instead of jumping to the circle center.
            val anchor = page.body.nearestAnchorAt(x + dragOffset.x, y + dragOffset.y)
            if (content != null && selected != null && anchor != null) {
                TextSelectionResolver.extend(content, selected, anchor, start)?.let { bookHost.reportSelection(it, page.generation) }
            }
            if (action == MotionEvent.ACTION_MOVE) updateEdgeNavigation(page, y + dragOffset.y)
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) stopSelectionDrag()
        return true
    }
    private fun stopSelectionDrag() {
        dragSerial++
        edgeDirection = 0
        edgeJob?.cancel(); edgeJob = null
        if (draggingHandle != null) { draggingHandle = null; bookHost.reportSelectionDrag(false) }
    }

    private fun edgeDirectionFor(page: PageCandidate, textY: Float): Int {
        val edge = 24f * container.resources.displayMetrics.density
        return when {
            textY <= page.body.paddingTop + edge -> -1
            textY >= page.body.height - page.body.paddingBottom - edge -> 1
            else -> 0
        }
    }

    private fun updateEdgeNavigation(page: PageCandidate, textY: Float) {
        val direction = edgeDirectionFor(page, textY)
        if (direction == edgeDirection && edgeJob?.isActive == true) return
        edgeJob?.cancel(); edgeJob = null; edgeDirection = direction
        if (direction == 0) return
        val serial = dragSerial
        val request = selectionScope.launch(start = CoroutineStart.LAZY) {
            try {
                navigateSelectionEdges(direction, serial)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (exception: Exception) {
                if (!detached && serial == dragSerial) bookHost.reportError(ReaderError(ReaderStage.SELECTION, "SelectionPageFailed", exception.message ?: "Cannot extend selection across a page", cause = exception))
            }
        }
        edgeJob = request
        request.invokeOnCompletion { if (edgeJob === request) edgeJob = null }
        request.start()
    }

    private suspend fun navigateSelectionEdges(direction: Int, serial: Long) {
        while (!detached && serial == dragSerial && draggingHandle != null && edgeDirection == direction) {
            delay(550)
            val current = active ?: break
            if (current.generation != bookHost.generation) break
            val currentNumber = current.target.pageNumberHint ?: break
            if (direction < 0 && currentNumber == 1 || direction > 0 && currentNumber >= current.layout.pageCount) break
            val selected = bookHost.selection ?: break
            if (selected.range.chapterId != current.target.chapterId) break
            val startHandle = draggingHandle ?: break
            val fixed = if (startHandle) selected.range.endExclusive else selected.range.start
            val fixedPage = (current.layout.pageForAnchor(fixed) as? ReaderResult.Success)?.value ?: break
            val targetNumber = currentNumber + direction
            if (startHandle && targetNumber > fixedPage || !startHandle && targetNumber < fixedPage) break
            val result = bookHost.requestSelectionPage(current.target.chapterId, targetNumber, current.generation)
            currentCoroutineContext().ensureActive()
            if (result is NavigationResult.Failed) bookHost.reportError(result.error)
            if (result !is NavigationResult.Moved && result !is NavigationResult.Unchanged) break
            if (serial != dragSerial || edgeDirection != direction) break
            val shown = active ?: break
            if (shown.target.chapterId != current.target.chapterId || shown.generation != bookHost.generation) break
            val content = shown.layout.contentSnapshot ?: break
            val selection = bookHost.selection ?: break
            val start = draggingHandle ?: break
            val offset = Rect()
            container.offsetDescendantRectToMyCoords(shown.body, offset)
            val anchor = shown.body.nearestAnchorAt(dragPointer.x - offset.left + dragOffset.x, dragPointer.y - offset.top + dragOffset.y) ?: break
            val extended = TextSelectionResolver.extend(content, selection, anchor, start) ?: break
            if (bookHost.reportSelection(extended, shown.generation) !is ReaderResult.Success) break
            if (edgeDirectionFor(shown, dragPointer.y - offset.top + dragOffset.y) != direction) break
        }
    }

    private fun updateDecorations(page: PageCandidate, reportFallbacks: Boolean = true) {
        if (page.generation != bookHost.generation) { page.body.setHighlights(emptyList()); page.body.setSelection(emptyList(), bookHost.style.selectionStyle); return }
        val styles = bookHost.style.highlightStyles
        val highlights = bookHost.validHighlights(page.target.chapterId)
        val missing = highlights.filter { it.styleId !in styles.styles }.map { it.id to it.styleId }.toSet()
        if (reportFallbacks) reportedFallbacks.retainAll(missing)
        page.body.setHighlights(highlights.flatMap { highlight ->
            val ranges = (page.layout.rangesForText(highlight.range) as? ReaderResult.Success)?.value ?: emptyList()
            ranges.map { ColoredTextRange(highlight.id, it, styles[highlight.styleId].fillColor) }
        })
        val selection = bookHost.selection
        val ranges = selection?.takeIf { it.range.chapterId == page.target.chapterId }?.let { (page.layout.rangesForText(it.range) as? ReaderResult.Success)?.value } ?: emptyList()
        page.body.setSelection(ranges, bookHost.style.selectionStyle)
        if (!reportFallbacks) return
        missing.filter { reportedFallbacks.add(it) }.forEach { (id, styleId) ->
            val generation = bookHost.generation
            container.post {
                if (!detached && bookHost.generation == generation && active === page && styleId !in bookHost.style.highlightStyles.styles && bookHost.validHighlights(page.target.chapterId).any { it.id == id && it.styleId == styleId }) bookHost.reportHighlightStyleFallback(id, styleId)
            }
        }
    }

    override suspend fun prepare(target: ReadNavigation<ReadingPosition>): ReadPreparation<ReadingPosition> {
        val book = bookHost.book ?: return ReadPreparation.Rejected(ReaderRejection.NoBook)
        if (book.chapters.isEmpty()) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
        val current = bookHost.committedLocation
        var index = current?.let { position -> book.chapters.indexOfFirst { it.chapterId == position.chapterId } } ?: 0
        var page: Int
        var anchor: ContentAnchor? = null
        var requiredRevision: String? = null
        when (target) {
            ReadNavigation.First -> { index = 0; page = 1 }
            ReadNavigation.Last -> { index = book.chapters.lastIndex; page = Int.MAX_VALUE }
            is ReadNavigation.Position -> {
                if (target.location.bookId != book.bookId) return ReadPreparation.Rejected(ReaderRejection.BookMismatch)
                index = book.chapters.indexOfFirst { it.chapterId == target.location.chapterId }
                if (index < 0) return ReadPreparation.Rejected(ReaderRejection.UnknownChapter)
                page = target.location.pageNumberHint ?: 1
                anchor = target.location.anchor
                requiredRevision = target.location.contentRevision
            }
            ReadNavigation.Next -> {
                val shown = active ?: return ReadPreparation.Rejected(ReaderRejection.NotReady)
                page = (current?.pageNumberHint ?: 1) + 1
                if (page > shown.layout.pageCount) { index++; page = 1 }
            }
            ReadNavigation.Previous -> {
                if (current == null || active == null) return ReadPreparation.Rejected(ReaderRejection.NotReady)
                page = (current.pageNumberHint ?: 1) - 1
                if (page < 1) { index--; page = Int.MAX_VALUE }
            }
        }
        if (index !in book.chapters.indices) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
        val chapter = book.chapters[index]
        val generation = bookHost.generation
        val style = when (val result = org.peyilo.ireadview.internal.style.ReaderFontResolver(container.context).prepareChrome(bookHost.style)) {
            is ReaderResult.Success -> result.value
            is ReaderResult.Failure -> return ReadPreparation.Failed(result.error)
            else -> return ReadPreparation.Rejected(ReaderRejection.NotReady)
        }
        if (detached || generation != bookHost.generation) return ReadPreparation.Rejected(ReaderRejection.NotReady)
        val template = bookHost.pageFactory.create(container.context)
        var transferred = false
        var ownsTemplate = false
        var rootMountAttempted = false
        var mountedBody: ReaderBodyView? = null
        var cleanupPlaceholders: ReaderPlaceholderController? = null
        try {
            ownsTemplate = org.peyilo.ireadview.internal.presentation.ReaderPageOwnership.acquire(template, template.root)
            require(ownsTemplate && (template.bodyHost === template.root || isDescendant(template.bodyHost, template.root))) { "Page factory must return a fresh root containing bodyHost" }
            val body = ReaderBodyView(container.context)
            mountedBody = body
            val emptyRoot = FrameLayout(container.context)
            val placeholders = ReaderPlaceholderController(emptyRoot) { bookHost.placeholderFactory }
            cleanupPlaceholders = placeholders
            template.bodyHost.addView(body, FrameLayout.LayoutParams(-1, -1))
            template.bind(ReaderPageContext(book.title, chapter, null, null, style))
            currentCoroutineContext().ensureActive()
            if (detached || generation != bookHost.generation) return ReadPreparation.Rejected(ReaderRejection.NotReady)
            require(template.root.parent == null) { "Page bind attached its root outside the presentation" }
            template.root.visibility = View.INVISIBLE
            rootMountAttempted = true
            container.addView(template.root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            template.root.awaitMeasured()
            org.peyilo.ireadview.internal.style.ReaderBodyInsets.apply(body, style)
            val viewport = bookHost.viewport.copy(widthPx = body.width - body.paddingLeft - body.paddingRight, heightPx = body.height - body.paddingTop - body.paddingBottom)
            val layout = when (val result = bookHost.layoutChapter(chapter.chapterId, viewport)) {
                is ReaderResult.Success -> result.value
                is ReaderResult.Failure -> return ReadPreparation.Failed(result.error)
                is ReaderResult.Rejected -> return ReadPreparation.Rejected(result.reason)
                ReaderResult.Superseded -> return ReadPreparation.Rejected(ReaderRejection.NotReady)
            }
            if (anchor != null) {
                if (requiredRevision != null && requiredRevision != layout.contentRevision) return ReadPreparation.Rejected(ReaderRejection.InvalidAnchor)
                when (val found = layout.pageForAnchor(anchor)) {
                    is ReaderResult.Success -> page = found.value
                    else -> return ReadPreparation.Rejected(ReaderRejection.InvalidAnchor)
                }
            }
            if (page == Int.MAX_VALUE) page = layout.pageCount
            if (page !in 1..layout.pageCount) return ReadPreparation.Rejected(ReaderRejection.InvalidPage)
            val resolvedAnchor = (layout.anchorForPage(page) as ReaderResult.Success).value
            val position = ReadingPosition(book.bookId, chapter.chapterId, resolvedAnchor, layout.contentRevision, page, index + 1)
            body.fragments = (layout.fragmentsForPage(page) as ReaderResult.Success).value
            template.bind(ReaderPageContext(book.title, chapter, position, layout.pageCount, style,
                requestReaderAction = { action ->
                    active?.let { it.template === template && it.generation == generation } == true &&
                        bookHost.requestReaderAction(action, position, generation)
                }))
            template.root.awaitMeasured()
            if (detached || generation != bookHost.generation) return ReadPreparation.Rejected(ReaderRejection.NotReady)
            val candidate = PageCandidate(position, generation, template, layout, body)
            if (layout.contentSnapshot?.blocks?.isEmpty() == true) {
                template.bodyHost.addView(emptyRoot, FrameLayout.LayoutParams(-1, -1))
                candidate.placeholders = placeholders
                candidate.bindPlaceholder()
            }
            body.accessibilityMatchesText = { range, text ->
                active === candidate && !detached && generation == bookHost.generation &&
                    layout.contentSnapshot?.let { TextSelectionResolver.matchesBlockRange(it, range, text) } == true
            }
            body.accessibilitySelectText = { range ->
                if (active !== candidate || detached || generation != bookHost.generation) false
                else layout.contentSnapshot?.let { content -> TextSelectionResolver.fromBlockRange(content, range) }
                    ?.let { bookHost.reportSelection(it, generation) is ReaderResult.Success && bookHost.selection == it } ?: false
            }
            body.accessibilityClearSelection = {
                active === candidate && !detached && bookHost.clearAccessibilitySelection(generation) && bookHost.selection == null
            }
            body.accessibilitySelectAnchors = { fixed, moving ->
                if (active !== candidate || detached || generation != bookHost.generation) false
                else layout.contentSnapshot?.let { TextSelectionResolver.fromAnchors(it, fixed, moving) }
                    ?.let { bookHost.reportSelection(it, generation) is ReaderResult.Success && bookHost.selection == it } ?: false
            }
            body.accessibilityCompareAnchors = { first, second ->
                layout.contentSnapshot?.let { TextSelectionResolver.compareAnchors(it, first, second) }
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
            transferred = true
            return ReadPreparation.Ready(candidate)
        } catch (cancelled: CancellationException) { throw cancelled }
        finally {
            if (!transferred && ownsTemplate) cleanupPage(template, listOf({ mountedBody?.clearContentBinding() }, { cleanupPlaceholders?.clear() },
                { mountedBody?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) } }), generation, rootMountAttempted)
        }
    }

    override suspend fun show(candidate: PreparedRead<ReadingPosition>): ReadingPosition {
        val page = candidate as PageCandidate
        check(!detached && page.generation == bookHost.generation)
        val old = active
        updateDecorations(page)
        currentCoroutineContext().ensureActive()
        if (detached || page.generation != bookHost.generation || active !== old) return page.target
        page.template.root.visibility = View.VISIBLE
        currentCoroutineContext().ensureActive()
        if (detached || page.generation != bookHost.generation || active !== old) return page.target
        active = page
        page.retained = true
        old?.close()
        return page.target
    }

    override fun detach() {
        detached = true
        stopSelectionDrag()
        selectionScope.cancel()
        accessibilityJob = null
        val old = active
        active = null
        old?.close()
        reportedFallbacks.clear()
        container.removeAllViews()
    }

    private fun moveAccessibilityOffscreen(page: PageCandidate, action: Int, arguments: Bundle,
        boundary: AccessibilityTraversalBoundary?, fixed: AccessibilityTraversalAnchor?, valid: () -> Boolean): Boolean {
        if (detached || active !== page || page.generation != bookHost.generation || navigationBusy ||
            draggingHandle != null || accessibilityJob != null || !valid()) return false
        val direction = when (action) {
            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> ReadNavigation.Next
            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> ReadNavigation.Previous
            else -> return false
        }
        val extend = arguments.getBoolean(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false)
        if (extend && (fixed == null || !fixed.isValid() || boundary !is AccessibilityTraversalBoundary.Source)) return false
        if (navigationAvailability(direction) != NavigationAvailability.Available) return false
        val book = bookHost.book ?: return false
        val forward = direction == ReadNavigation.Next
        val granularity = arguments.getInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, 0)
        val selectionBefore = bookHost.selection
        fun sourceCurrent() = !detached && active === page && page.generation == bookHost.generation &&
            bookHost.book === book && draggingHandle == null && valid() &&
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
                        AccessibilitySourceTraversal.find(content, boundary, forward, granularity) { work.ensureActive() }
                    } else {
                        val lookup = AccessibilityChapterTraversal.resolve(book, content, boundary, forward, granularity,
                            ::current, bookHost::getChapter)
                        (lookup as? ReaderResult.Success)?.value ?: return@launch
                    }
                } else AccessibilitySourceTraversal.Result.Unsupported()
                if (!current() || resolved == AccessibilitySourceTraversal.Result.Invalid ||
                    resolved == AccessibilitySourceTraversal.Result.Exhausted) return@launch
                resolved
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                android.util.Log.w("iReadView", "Accessibility source lookup failed", error)
                return@launch
            }
            val destination = resolved as? AccessibilitySourceTraversal.Result.Found
            if (extend && (destination == null || destination.position.chapterId != page.target.chapterId || fixed?.isValid() != true)) return@launch
            val customPosition = (resolved as? AccessibilitySourceTraversal.Result.Unsupported)?.position
            val seek = destination?.position ?: customPosition
            val target = seek?.let { ReadNavigation.Position(it) } ?: direction
            val result = try { bookHost.requestAccessibilityNavigation(target, page.generation, ::sourceCurrent) }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (!detached && page.generation == bookHost.generation && bookHost.book === book) {
                    bookHost.reportError(ReaderError(ReaderStage.NAVIGATION, "AccessibilityTraversalFailed",
                        error.message ?: "Accessibility text navigation failed", retryable = false, cause = error))
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
                bookHost.committedLocation != shown.target) return@launch
            if (extend && (bookHost.selection != selectionBefore || fixed?.isValid() != true)) return@launch
            try {
                if (destination != null && shown.target.chapterId == destination.position.chapterId)
                    shown.body.accessibilityMoveAtAnchor(action, arguments, destination.caret, fixed)
                else if (customPosition?.anchor != null && shown.target.chapterId == customPosition.chapterId)
                    shown.body.accessibilityMoveAtBlock(action, arguments, customPosition.anchor.blockId)
                else shown.body.accessibilityMoveFromBoundary(action, arguments, null)
            }
            catch (error: Exception) {
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
        val body: ReaderBodyView
    ) : PreparedRead<ReadingPosition> {
        override val surface get() = viewPageSurface(template.root)
        var retained = false
        private var closed = false
        var placeholders: ReaderPlaceholderController? = null
        fun bindPlaceholder() {
            placeholders?.show(org.peyilo.ireadview.spi.page.PlaceholderState(org.peyilo.ireadview.spi.page.PlaceholderKind.EMPTY_CHAPTER,
                target.bookId, target.chapterId, null, bookHost.style), generation, object : org.peyilo.ireadview.spi.page.PlaceholderActions {
                override val canRetry = false
                override fun retry() = false
            })
        }
        fun close() {
            if (closed) return
            closed = true
            cleanupPage(template, listOf({ body.clearContentBinding() }, { placeholders?.clear() },
                { (body.parent as? android.view.ViewGroup)?.removeView(body) }), generation)
        }
        override fun dispose() { if (!retained) close() }
    }

    private fun cleanupPage(template: ReaderPageTemplate, before: List<() -> Unit>, generation: Long, removeRoot: Boolean = true) {
        org.peyilo.ireadview.internal.presentation.cleanupReaderPage(template, before,
            { if (removeRoot) container.removeView(template.root) }) { error ->
            android.util.Log.w("iReadView", "Page cleanup failed", error)
            container.post {
                if (!detached && generation == bookHost.generation) bookHost.reportError(ReaderError(
                    ReaderStage.NAVIGATION, "PageCleanupFailed", error.message ?: "Page cleanup failed", retryable = false, cause = error))
            }
        }
    }

    private fun isDescendant(child: View, root: View): Boolean {
        var ancestor = child.parent
        while (ancestor is View) { if (ancestor === root) return true; ancestor = ancestor.parent }
        return false
    }
}
