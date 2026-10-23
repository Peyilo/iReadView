package org.peyilo.ireadview.internal.presentation.scroll

import android.content.Context
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.render.*
import org.peyilo.ireadview.internal.presentation.AccessibilityChapterTraversal
import org.peyilo.ireadview.internal.presentation.preserveAccessibilityAnchor
import org.peyilo.ireadview.internal.selection.TextSelectionResolver
import org.peyilo.ireadview.spi.page.*
import org.peyilo.ireadview.spi.presentation.*
import kotlin.math.ceil

/** A bounded chapter window. Items share measured fragments with virtual pagination. */
internal class ScrollingBookPresentation(context: Context, private val host: BookPresentationHost) : BookPresentation() {
    override val supportsBlockLayout = true
    override val contentFeatures = BookPresentationFeatures(ReaderSelectionModel.Text,
        ReaderFeatureSupport.Supported, ReaderFeatureSupport.Supported)
    private val container = FrameLayout(context)
    override val contentView: View get() = container
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var active: ScrollCandidate? = null
    override fun onPlaceholdersChanged() { active?.adapter?.refreshPlaceholders() }
    override val needsTimeUpdates get() = !detached && active?.takeIf { it.generation == host.generation }?.template?.needsTimeUpdates == true
    override fun onTimeChanged(timeMillis: Long) { active?.takeIf { !detached && it.generation == host.generation }?.template?.onTimeChanged(timeMillis) }
    override fun onPageChromeChanged() { active?.takeIf { !detached && it.generation == host.generation }?.template?.refreshChrome() }
    override fun navigationAvailability(target: ReadNavigation<ReadingPosition>): NavigationAvailability {
        val candidate = active?.takeIf { it.generation == host.generation } ?: return NavigationAvailability.NotReady
        val book = host.book ?: return NavigationAvailability.NotReady
        val manager = candidate.list.layoutManager as? LinearLayoutManager ?: return NavigationAvailability.NotReady
        val direction = when (target) { ReadNavigation.Next -> 1; ReadNavigation.Previous -> -1; else -> return NavigationAvailability.Unknown }
        if (candidate.list.isLayoutRequested || candidate.list.height <= 0 || candidate.list.width <= 0 ||
            candidate.chapters.isEmpty() || manager.findFirstVisibleItemPosition() == RecyclerView.NO_POSITION) return NavigationAvailability.Unknown
        if (candidate.list.canScrollVertically(direction)) return NavigationAvailability.Available
        val moreChapters = if (direction < 0) candidate.chapters.first().index > 0 else candidate.chapters.last().index < book.chapters.lastIndex
        return if (moreChapters) NavigationAvailability.Available else NavigationAvailability.AtBoundary
    }
    private var detached = false
    private var navigationBusy = false
    private var accessibilityJob: Job? = null
    private var draggingStart: Boolean? = null
    private var dragPointer = PointF()
    private val reportedFallbacks = HashSet<Pair<String, String>>()
    override fun layoutViewport() = active?.takeIf { it.generation == host.generation }?.viewport
    override fun onNavigationStateChanged(state: ReadNavigationState) {
        navigationBusy = state != ReadNavigationState.Idle
        if (navigationBusy) { active?.list?.stopScroll(); stopDrag() }
        else active?.list?.post { active?.adapter?.refreshPlaceholders(); active?.reportAndLoad() }
    }

    override fun attach(host: ReadContentHost<ReadingPosition>) {
        detached = false
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    override fun onViewportChanged(viewport: ReadViewport) { accessibilityJob?.cancel(); stopDrag(); active?.list?.stopScroll(); container.requestLayout() }
    override fun currentContent(): ChapterContent? {
        val candidate = active?.takeIf { it.generation == host.generation } ?: return null
        val id = host.committedLocation?.chapterId ?: candidate.target.chapterId
        return candidate.chapters.firstOrNull { it.layout.chapterId == id }?.layout?.contentSnapshot
    }
    override fun contentForChapter(chapterId: String): ChapterContent? = active?.takeIf { it.generation == host.generation }
        ?.chapters?.firstOrNull { it.layout.chapterId == chapterId }?.layout?.contentSnapshot
    override fun onDecorationsChanged() {
        val candidate = active ?: return
        if (host.selection == null && selectionInteraction.isDragging) stopDrag()
        val missing = candidate.chapters.flatMap { host.validHighlights(it.layout.chapterId) }.filter { it.styleId !in host.style.highlightStyles.styles }.map { it.id to it.styleId }.toSet()
        reportedFallbacks.retainAll(missing)
        if (candidate.decorationPending) return
        candidate.decorationPending = true
        candidate.list.postOnAnimation {
            candidate.decorationPending = false
            if (active === candidate && candidate.generation == host.generation) {
                candidate.refreshDecorations()
                if (!selectionInteraction.isDragging && host.selection != null) {
                    if (selectionBounds() == null) host.clearSelection() else host.reportSelectionGeometryChanged()
                }
            }
        }
    }
    override fun selectionBounds(): RectF? {
        val candidate = active ?: return null
        var result: RectF? = null
        for (index in 0 until candidate.list.childCount) {
            val body = candidate.list.getChildAt(index) as? TextReaderBodyView ?: continue
            val rect = body.selectionBounds() ?: continue
            val offset = Rect(); container.offsetDescendantRectToMyCoords(body, offset)
            rect.offset(offset.left.toFloat(), offset.top.toFloat())
            val clip = Rect(); container.offsetDescendantRectToMyCoords(candidate.list, clip)
            if (rect.intersect(clip.left.toFloat(), clip.top.toFloat(), (clip.left + candidate.list.width).toFloat(), (clip.top + candidate.list.height).toFloat())) {
                if (result == null) result = rect else result.union(rect)
            }
        }
        return result
    }
    override fun selectionHandleBounds(start: Boolean): RectF? {
        val candidate = active ?: return null
        for (index in 0 until candidate.list.childCount) {
            val body = candidate.list.getChildAt(index) as? TextReaderBodyView ?: continue
            val bounds = body.selectionHandleBounds(start) ?: continue
            val offset = Rect(); container.offsetDescendantRectToMyCoords(body, offset)
            bounds.offset(offset.left.toFloat(), offset.top.toFloat())
            return bounds
        }
        return null
    }
    private fun bodyAt(x: Float, y: Float): Pair<TextReaderBodyView, Row.Text>? {
        val candidate = active?.takeIf { it.generation == host.generation } ?: return null
        for (index in 0 until candidate.list.childCount) {
            val body = candidate.list.getChildAt(index) as? TextReaderBodyView ?: continue
            val rect = Rect(0, 0, body.width, body.height); container.offsetDescendantRectToMyCoords(body, rect)
            if (rect.contains(x.toInt(), y.toInt())) {
                val row = candidate.adapter.rows.getOrNull(candidate.list.getChildAdapterPosition(body)) as? Row.Text ?: return null
                return body to row
            }
        }
        return null
    }
    private fun local(body: View, x: Float, y: Float): Pair<Float, Float> {
        val offset = Rect(); container.offsetDescendantRectToMyCoords(body, offset)
        return x - offset.left to y - offset.top
    }
    override fun onTap(xPx: Float, yPx: Float): Boolean {
        if (host.selection != null) { host.clearSelection(); return true }
        val (body, row) = bodyAt(xPx, yPx) ?: return false
        val (x, y) = local(body, xPx, yPx)
        body.linkAt(x, y)?.let { return host.reportLinkTapped(it, row.chapter.layout.chapterId, host.generation) }
        return body.highlightAt(x, y)?.let(host::reportHighlightTapped) ?: false
    }
    override fun onLongPress(xPx: Float, yPx: Float): Boolean {
        selectionInteraction.onLongPress(xPx, yPx)?.let { return it }
        val (body, row) = bodyAt(xPx, yPx) ?: return false
        val (x, y) = local(body, xPx, yPx)
        val content = row.chapter.layout.contentSnapshot ?: return false
        val highlightId = body.highlightAt(x, y)
        val restored = highlightId?.let { id -> host.validHighlights(content.chapterId).firstOrNull { it.id == id } }
            ?.let { TextSelectionResolver.fromHighlight(content, it) }
        val selection = restored ?: run {
            val anchor = body.anchorAt(x, y) ?: return false
            TextSelectionResolver.paragraphAt(content, anchor) ?: return false
        }
        return host.reportSelection(selection, host.generation) is ReaderResult.Success
    }
    private fun findSelectionHandle(x: Float, y: Float): Pair<Boolean, PointF>? {
        val candidate = active ?: return null
        val selection = host.selection ?: return null
        for (index in 0 until candidate.list.childCount) {
            val body = candidate.list.getChildAt(index) as? TextReaderBodyView ?: continue
            val row = candidate.adapter.rows.getOrNull(candidate.list.getChildAdapterPosition(body)) as? Row.Text ?: continue
            if (row.chapter.layout.chapterId != selection.range.chapterId) continue
            val (bx, by) = local(body, x, y)
            val start = body.selectionHandleAt(bx, by) ?: continue
            return start to (body.selectionDragOffset(start, bx, by) ?: PointF())
        }
        return null
    }
    private val selectionInteraction = host.presentationComponents.selectionControllerFactory.create(container.context,
        object : org.peyilo.ireadview.spi.selection.ReaderSelectionDelegate() {
            override fun ready() = !detached && !navigationBusy && active?.generation == host.generation && host.selection != null
            override fun handleAt(x: Float, y: Float): Boolean? = findSelectionHandle(x, y)?.first
            override fun grabOffset(start: Boolean, x: Float, y: Float) = findSelectionHandle(x, y)?.second
            override suspend fun extend(start: Boolean, point: PointF, current: () -> Boolean): Boolean {
                val candidate = active ?: return false
                if (!current()) return false
                draggingStart = start
                dragPointer = PointF(point.x, point.y)
                return updateDraggedSelection(candidate)
            }
            override fun scrollSpeed(point: PointF): Float {
                val list = active?.list ?: return 0f
                val (_, y) = local(list, point.x, point.y)
                return org.peyilo.ireadview.spi.selection.ReaderSelectionScrollPolicy.speed(y,
                    list.paddingTop.toFloat(), (list.height-list.paddingBottom).toFloat(), list.resources.displayMetrics.density)
            }
            override fun edgeDirection(point: PointF): Int = scrollSpeed(point).let { if (it<0) -1 else if (it>0) 1 else 0 }
            override suspend fun movePage(direction: Int, start: Boolean, current: () -> Boolean) = false
            override suspend fun nextScrollFrame() = active?.list?.awaitAnimationFrame() ?: 0L
            override suspend fun scrollBy(pixels: Int, start: Boolean, point: PointF, current: () -> Boolean): Boolean {
                val candidate = active ?: return false
                if (!current()) return false
                draggingStart = start
                val step = candidate.selectionScrollStep(pixels, point.y)
                if (step == 0) return false
                val before = candidate.selectedChapterTop() ?: return false
                candidate.list.scrollBy(0, step)
                currentCoroutineContext().ensureActive()
                if (!current() || active !== candidate) return false
                val after = candidate.selectedChapterTop() ?: return false
                return kotlin.math.abs(before.second-after.second) >= .5f
            }
            override fun reportDragging(active: Boolean) {
                if (active) this@ScrollingBookPresentation.active?.list?.stopScroll() else draggingStart = null
                host.reportSelectionDrag(active)
                if (!active) this@ScrollingBookPresentation.active?.let { candidate ->
                    candidate.list.post { if (this@ScrollingBookPresentation.active === candidate) candidate.reportAndLoad() }
                }
            }
            override fun reportError(error: Exception) = host.reportError(ReaderError(ReaderStage.SELECTION,
                "SelectionScrollFailed", error.message ?: "Cannot extend selection while scrolling", cause=error))
        })
    private fun stopDrag() = selectionInteraction.reset()
    override fun onSelectionTouch(action: Int, xPx: Float, yPx: Float) = selectionInteraction.onTouch(action, xPx, yPx)
    private fun updateDraggedSelection(candidate: ScrollCandidate): Boolean {
        val start = draggingStart ?: return false
        val selection = host.selection ?: return false
        if (active !== candidate || candidate.generation != host.generation || navigationBusy) return false
        // Controllers supply the caret position including the handle's grab offset.
        // Keep hit testing inside the visible viewport, as DOM caretAt already does;
        // the unbounded pointer still drives edge speed and automatic scrolling.
        val viewport = Rect(0, 0, candidate.list.width, candidate.list.height)
        container.offsetDescendantRectToMyCoords(candidate.list, viewport)
        val hitX = dragPointer.x.coerceIn(viewport.left + 1f, maxOf(viewport.left + 1f, viewport.right - 1f))
        val hitY = dragPointer.y.coerceIn(viewport.top + candidate.list.paddingTop + 1f,
            maxOf(viewport.top + candidate.list.paddingTop + 1f, viewport.bottom - candidate.list.paddingBottom - 1f))
        val candidates = (0 until candidate.list.childCount).mapNotNull { index ->
            val body = candidate.list.getChildAt(index) as? TextReaderBodyView ?: return@mapNotNull null
            val row = candidate.adapter.rows.getOrNull(candidate.list.getChildAdapterPosition(body)) as? Row.Text ?: return@mapNotNull null
            if (row.chapter.layout.chapterId != selection.range.chapterId) return@mapNotNull null
            val (x, y) = local(body, hitX, hitY)
            Triple(body, row, PointF(x, y))
        }
        val nearest = candidates.minByOrNull { (body, _, point) -> when { point.y < 0f -> -point.y; point.y > body.height -> point.y - body.height; else -> 0f } }
        if (nearest != null) {
            val (body, row, point) = nearest
            val anchor = body.nearestAnchorAt(point.x, point.y)
            val content = row.chapter.layout.contentSnapshot
            if (anchor != null && content != null) {
                val extended = TextSelectionResolver.extend(content, selection, anchor, start) ?: return false
                return extended == selection || host.reportSelection(extended, candidate.generation) is ReaderResult.Success
            }
        }
        return false
    }
    override suspend fun prepare(target: ReadNavigation<ReadingPosition>): ReadPreparation<ReadingPosition> {
        if (target is ReadNavigation.Position && target.location.documentLocator != null) return ReadPreparation.Rejected(ReaderRejection.Unsupported)
        active?.list?.stopScroll()
        val book = host.book ?: return ReadPreparation.Rejected(ReaderRejection.NoBook)
        if (book.chapters.isEmpty()) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
        val current = host.committedLocation
        var index = current?.let { position -> book.chapters.indexOfFirst { it.chapterId == position.chapterId } } ?: 0
        var desired = (target as? ReadNavigation.Position)?.location
        if (desired != null) {
            if (desired.bookId != book.bookId) return ReadPreparation.Rejected(ReaderRejection.BookMismatch)
            index = book.chapters.indexOfFirst { it.chapterId == desired.chapterId }
        }
        if (target == ReadNavigation.First) index = 0
        if (target == ReadNavigation.Last) index = book.chapters.lastIndex
        if (index !in book.chapters.indices) return ReadPreparation.Rejected(ReaderRejection.UnknownChapter)
        val generation = host.generation
        val style = when (val result = org.peyilo.ireadview.internal.style.ReaderFontResolver(container.context).prepareChrome(host.style)) {
            is ReaderResult.Success -> result.value
            is ReaderResult.Failure -> return ReadPreparation.Failed(result.error)
            else -> return ReadPreparation.Rejected(ReaderRejection.NotReady)
        }
        if (detached || generation != host.generation) return ReadPreparation.Rejected(ReaderRejection.NotReady)
        val template = host.pageFactory.create(container.context)
        var transferred = false
        var ownsTemplate = false
        var rootMountAttempted = false
        var cleanupList: RecyclerView? = null
        try {
            ownsTemplate = org.peyilo.ireadview.internal.presentation.ReaderPageOwnership.acquire(template, template.root)
            require(ownsTemplate && contains(template.root, template.bodyHost)) { "Page factory must return a fresh root containing bodyHost" }
            val list = RecyclerView(container.context).apply {
                layoutManager = LinearLayoutManager(context)
                itemAnimator = null
                clipToPadding = true
            }
            cleanupList = list
            template.bodyHost.addView(list, ViewGroup.LayoutParams(-1, -1))
            template.bind(ReaderPageContext(book.title, book.chapters[index], null, null, style, ReadingMode.CONTINUOUS_SCROLL, chromeProvider = host.pageChromeProvider))
            currentCoroutineContext().ensureActive()
            if (detached || generation != host.generation) return ReadPreparation.Rejected(ReaderRejection.NotReady)
            require(template.root.parent == null) { "Page bind attached its root outside the presentation" }
            template.root.visibility = View.INVISIBLE
            rootMountAttempted = true
            container.addView(template.root, FrameLayout.LayoutParams(-1, -1))
            template.root.awaitMeasured()
            org.peyilo.ireadview.internal.style.ReaderBodyInsets.apply(list, style)
            val viewport = host.viewport.copy(widthPx = list.width - list.paddingLeft - list.paddingRight, heightPx = list.height - list.paddingTop - list.paddingBottom)
            var chapter = loadChapter(index, viewport)
            var offset = 0.0
            when (target) {
                ReadNavigation.Next, ReadNavigation.Previous -> {
                    val shown = active ?: return ReadPreparation.Rejected(ReaderRejection.NotReady)
                    val visible = shown.visible() ?: return ReadPreparation.Rejected(ReaderRejection.NotReady)
                    index = visible.first.index
                    chapter = loadChapter(index, viewport)
                    offset = visible.second + if (target == ReadNavigation.Next) viewport.heightPx else -viewport.heightPx
                    while (offset >= chapter.height && index < book.chapters.lastIndex) { offset -= chapter.height; index++; chapter = loadChapter(index, viewport) }
                    while (offset < 0.0 && index > 0) { index--; chapter = loadChapter(index, viewport); offset += chapter.height }
                    val bounded = offset.coerceIn(0.0, maxOf(0.0, chapter.height - if (index == book.chapters.lastIndex) viewport.heightPx.toDouble() else 1.0))
                    if (index == visible.first.index && kotlin.math.abs(bounded - visible.second) < 1f) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
                    offset = bounded
                }
                ReadNavigation.Last -> offset = maxOf(0.0, chapter.height - viewport.heightPx)
                ReadNavigation.First -> Unit
                is ReadNavigation.Position -> {
                    val position = desired!!
                    if (position.anchor != null) {
                        if (position.contentRevision != null && position.contentRevision != chapter.layout.contentRevision) return ReadPreparation.Rejected(ReaderRejection.InvalidAnchor)
                        if (chapter.layout.pageForAnchor(position.anchor) !is ReaderResult.Success) return ReadPreparation.Rejected(ReaderRejection.InvalidAnchor)
                        offset = chapter.offsetFor(position.anchor) ?: return ReadPreparation.Rejected(ReaderRejection.InvalidAnchor)
                    } else {
                        val anchor = when (val result = chapter.layout.anchorForPage(position.pageNumberHint ?: 1)) {
                            is ReaderResult.Success -> result.value
                            else -> return ReadPreparation.Rejected(ReaderRejection.InvalidPage)
                        }
                        offset = anchor?.let(chapter::offsetFor) ?: 0.0
                    }
                }
            }
            val candidate = ScrollCandidate(generation, template, list, viewport, chapter, offset, style)
            if (offset + viewport.heightPx > chapter.height && index < book.chapters.lastIndex) candidate.chapters.add(loadChapter(index + 1, viewport))
            list.adapter = candidate.adapter
            candidate.rebuild()
            candidate.seek(chapter, offset)
            list.awaitMeasured()
            currentCoroutineContext().ensureActive()
            if (detached || generation != host.generation) return ReadPreparation.Rejected(ReaderRejection.NotReady)
            candidate.target = candidate.visiblePosition() ?: chapter.positionAt(offset)
            val boundPosition = candidate.target
            template.bind(ReaderPageContext(book.title, book.chapters[index], boundPosition, chapter.layout.pageCount, style, ReadingMode.CONTINUOUS_SCROLL,
                requestReaderAction = { action -> active === candidate && host.requestReaderAction(action, boundPosition, generation) }, chromeProvider = host.pageChromeProvider))
            transferred = true
            return ReadPreparation.Ready(candidate)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: PreparationFailure) { return failure.result }
        finally { if (!transferred && ownsTemplate) cleanupPage(template, listOf({ cleanupList?.adapter = null },
            { cleanupList?.let { (it.parent as? ViewGroup)?.removeView(it) } }), generation, rootMountAttempted) }
    }

    private suspend fun loadChapter(index: Int, viewport: ReadViewport): Chapter {
        val book = host.book ?: throw PreparationFailure(ReadPreparation.Rejected(ReaderRejection.NoBook))
        val generation = host.generation
        fun checkCurrent() {
            if (detached || host.book !== book || generation != host.generation)
                throw PreparationFailure(ReadPreparation.Rejected(ReaderRejection.NotReady))
        }
        val layout = when (val result = host.layoutChapter(book.chapters[index].chapterId, viewport)) {
            is ReaderResult.Success -> result.value
            is ReaderResult.Failure -> throw PreparationFailure(ReadPreparation.Failed(result.error))
            is ReaderResult.Rejected -> throw PreparationFailure(ReadPreparation.Rejected(result.reason))
            ReaderResult.Superseded -> throw PreparationFailure(ReadPreparation.Rejected(ReaderRejection.NotReady))
        }
        checkCurrent()
        val chunks = when (val result = layout.continuousFragments()) {
            is ReaderResult.Success -> result.value
            else -> throw PreparationFailure(ReadPreparation.Rejected(ReaderRejection.Unsupported))
        }
        checkCurrent()
        val emptyHeight = 128.0 * container.resources.displayMetrics.density
        val chapter = withContext(Dispatchers.Default) {
            val work = currentCoroutineContext()
            work.ensureActive()
            val heights = IntArray(chunks.size)
            val starts = DoubleArray(chunks.size + 1)
            val blockChunks = HashMap<String, MutableList<Int>>()
            for (chunk in chunks.indices) {
                work.ensureActive()
                heights[chunk] = ceil(chunks[chunk].heightPx.toDouble()).toInt().coerceAtLeast(1)
                starts[chunk + 1] = starts[chunk] + heights[chunk]
                blockChunks.getOrPut(chunks[chunk].fragment.blockId) { ArrayList() }.add(chunk)
            }
            val frozenBlockChunks = HashMap<String, IntArray>(blockChunks.size)
            for ((blockId, indices) in blockChunks) {
                work.ensureActive()
                val frozen = IntArray(indices.size)
                for (index in indices.indices) {
                    work.ensureActive()
                    frozen[index] = indices[index]
                }
                frozenBlockChunks[blockId] = frozen
            }
            work.ensureActive()
            val geometry = ChapterGeometry(heights, starts, if (chunks.isEmpty()) emptyHeight else starts.last(), frozenBlockChunks)
            Chapter(index, layout, chunks, geometry) { work.ensureActive() }
        }
        currentCoroutineContext().ensureActive()
        checkCurrent()
        return chapter
    }
    override suspend fun show(candidate: PreparedRead<ReadingPosition>): ReadingPosition {
        val next = candidate as ScrollCandidate
        check(!detached && next.generation == host.generation)
        val old = active
        next.template.refreshChrome()
        next.template.root.visibility = View.VISIBLE
        currentCoroutineContext().ensureActive()
        if (detached || next.generation != host.generation || active !== old) return next.target
        active = next; next.retained = true
        old?.close()
        currentCoroutineContext().ensureActive()
        if (detached || next.generation != host.generation || active !== next) return next.target
        next.installInput()
        next.list.post { if (!detached && active === next && next.generation == host.generation) { onDecorationsChanged(); next.reportAndLoad() } }
        return next.target
    }
    override fun detach() {
        val old = active
        active = null
        detached = true; stopDrag(); selectionInteraction.release(); scope.cancel()
        accessibilityJob = null
        old?.close(); reportedFallbacks.clear()
        container.removeAllViews()
    }

    private fun moveAccessibilityOffscreen(candidate: ScrollCandidate, chapter: Chapter, action: Int,
        arguments: Bundle, boundary: AccessibilityTraversalBoundary?, fixed: AccessibilityTraversalAnchor?, valid: () -> Boolean): Boolean {
        if (detached || active !== candidate || candidate.generation != host.generation || navigationBusy ||
            selectionInteraction.isDragging || accessibilityJob != null || !valid()) return false
        val forward = when (action) {
            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> true
            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> false
            else -> return false
        }
        val extend = arguments.getBoolean(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false)
        if (extend && (fixed == null || !fixed.isValid() || boundary !is AccessibilityTraversalBoundary.Source)) return false
        val direction = if (forward) ReadNavigation.Next else ReadNavigation.Previous
        val book = host.book ?: return false
        if (navigationAvailability(direction) == NavigationAvailability.AtBoundary) return false
        val granularity = arguments.getInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, 0)
        val selectionBefore = host.selection
        lateinit var job: Job
        fun sourceCurrent() = !detached && active === candidate && candidate.generation == host.generation &&
            host.book === book && !selectionInteraction.isDragging && valid() &&
            (!extend || host.selection == selectionBefore && fixed?.isValid() == true)
        fun current() = !navigationBusy && sourceCurrent()
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!current()) return@launch
                val content = chapter.layout.contentSnapshot
                val resolved = if (content != null && boundary != null) {
                    if (extend) withContext(Dispatchers.Default) {
                        val work = currentCoroutineContext()
                        AccessibilitySourceTraversal.find(content, boundary, forward, granularity) { work.ensureActive() }
                    } else {
                        val lookup = AccessibilityChapterTraversal.resolve(book, content, boundary, forward, granularity,
                            ::current, host::getChapter)
                        (lookup as? ReaderResult.Success)?.value ?: return@launch
                    }
                } else AccessibilitySourceTraversal.Result.Unsupported()
                if (!current()) return@launch
                if (resolved == AccessibilitySourceTraversal.Result.Invalid || resolved == AccessibilitySourceTraversal.Result.Exhausted) return@launch
                val destination = resolved as? AccessibilitySourceTraversal.Result.Found
                if (extend && (destination == null || destination.position.chapterId != chapter.layout.chapterId || fixed?.isValid() != true)) return@launch
                val customPosition = (resolved as? AccessibilitySourceTraversal.Result.Unsupported)?.position
                val seek = destination?.position ?: customPosition
                val target = seek?.let { ReadNavigation.Position(it) } ?: direction
                val result = host.requestAccessibilityNavigation(target, candidate.generation, ::sourceCurrent)
                currentCoroutineContext().ensureActive()
                val shown = active ?: return@launch
                val position = when (result) {
                    is NavigationResult.Moved -> result.to
                    is NavigationResult.Unchanged -> result.location
                    else -> return@launch
                }
                fun shownCurrent() = !detached && active === shown && !navigationBusy && host.book === book &&
                    shown.generation == candidate.generation && shown.generation == host.generation &&
                    host.committedLocation == position
                if (!shownCurrent()) return@launch
                if (extend && (host.selection != selectionBefore || fixed?.isValid() != true)) return@launch
                val attached = (0 until shown.list.childCount).map { shown.list.getChildAt(it) }
                    .sortedBy { shown.list.getChildAdapterPosition(it) }.let { if (forward) it else it.asReversed() }
                for (view in attached) {
                    if (!shownCurrent()) return@launch
                    val body = view as? TextReaderBodyView ?: continue
                    val row = shown.adapter.rows.getOrNull(shown.list.getChildAdapterPosition(body)) as? Row.Text ?: continue
                    val accepted = when {
                        destination != null -> row.chapter.layout.chapterId == destination.position.chapterId &&
                            body.accessibilityMoveAtAnchor(action, arguments, destination.caret, fixed)
                        customPosition?.anchor != null -> row.chapter.layout.chapterId == customPosition.chapterId &&
                            body.accessibilityMoveAtBlock(action, arguments, customPosition.anchor.blockId)
                        else -> body.accessibilityMoveFromBoundary(action, arguments, null)
                    }
                    if (accepted) break
                }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                android.util.Log.w("iReadView", "Accessibility scroll traversal failed", error)
            }
        }
        accessibilityJob = job
        job.invokeOnCompletion { if (accessibilityJob === job) accessibilityJob = null }
        container.post { if (accessibilityJob === job) job.start() }
        return true
    }

    /** Worker-prepared lookup data only; no Views, tasks or cancellation callbacks retained. */
    private class ChapterGeometry(val heights: IntArray, val starts: DoubleArray, val height: Double,
        val blockChunks: Map<String, IntArray>)

    private inner class Chapter(val index: Int, val layout: ChapterLayout, val chunks: List<ContinuousFragment>,
        geometry: ChapterGeometry, checkCancellation: () -> Unit) {
        val heights = geometry.heights
        val starts = geometry.starts
        val height = geometry.height
        private val blockChunks = geometry.blockChunks
        val rows: List<Row> = if (chunks.isEmpty()) {
            checkCancellation()
            listOf(Row.Empty(this))
        } else {
            val prepared = ArrayList<Row>(chunks.size)
            for (index in chunks.indices) {
                checkCancellation()
                prepared.add(Row.Text(this, index))
            }
            checkCancellation()
            immutableList(prepared)
        }
        /** Built only for anchorless fragments; future starts take priority over earlier ends. */
        private val fallbackAnchors: Array<ContentAnchor?> by lazy {
            val result = arrayOfNulls<ContentAnchor>(chunks.size)
            var next: ContentAnchor? = null
            for (index in chunks.indices.reversed()) {
                chunks[index].fragment.anchorForVerticalOffset(0f)?.let { next = it }
                result[index] = next
            }
            var previous: ContentAnchor? = null
            for (index in chunks.indices) {
                val fragment = chunks[index].fragment
                fragment.anchorForVerticalOffset(maxOf(0f, fragment.heightPx - 1f))?.let { previous = it }
                if (result[index] == null) result[index] = previous
            }
            result
        }
        fun chunkForOffset(offset: Double): Int {
            if (chunks.isEmpty()) return 0
            var low = 0
            var high = chunks.lastIndex
            while (low < high) {
                val middle = low + (high - low + 1) / 2
                if (starts[middle] <= offset) low = middle else high = middle - 1
            }
            return low
        }
        fun offsetFor(anchor: ContentAnchor): Double? {
            val indices = blockChunks[anchor.blockId] ?: return null
            var fallback: Double? = null
            for (index in indices) {
                val fragment = chunks[index].fragment
                val top = starts[index]
                if (anchor.offset in (fragment.startOffset ?: 0)..(fragment.endOffset ?: 0)) {
                    fallback = top + fragment.heightPx
                    if (anchor.offset < (fragment.endOffset ?: 0) || fragment.startOffset == fragment.endOffset) return top + (fragment.caretForOffset(anchor.offset, false)?.topPx ?: 0f)
                }
            }
            return fallback
        }
        fun positionAt(offset: Double): ReadingPosition {
            var anchor: ContentAnchor? = null
            if (chunks.isNotEmpty()) {
                var chunk = chunkForOffset(offset)
                if (offset >= starts[chunk] + chunks[chunk].fragment.heightPx && chunk < chunks.lastIndex) chunk++
                anchor = chunks[chunk].fragment.anchorForVerticalOffset(maxOf(0.0, offset - starts[chunk]).toFloat())
                    ?: fallbackAnchors[chunk]
            }
            val page = anchor?.let { (layout.pageForAnchor(it) as? ReaderResult.Success)?.value } ?: 1
            return ReadingPosition(host.book!!.bookId, layout.chapterId, anchor, layout.contentRevision, page, index + 1)
        }
    }
    private sealed interface Row {
        data class Text(val chapter: Chapter, val chunkIndex: Int) : Row
        data class Empty(val chapter: Chapter) : Row
        data class Boundary(val index: Int, val before: Boolean, val failed: Boolean) : Row
    }
    /** A few retained chapter/boundary segments; never flatten all text rows on the main thread. */
    private class RowSegments(segments: List<List<Row>>) : AbstractList<Row>(), java.util.RandomAccess {
        private val segments = segments.toList()
        private val starts = IntArray(segments.size)
        override val size: Int
        init {
            var total = 0L
            for (index in segments.indices) {
                starts[index] = total.toInt()
                total += segments[index].size
                require(total <= Int.MAX_VALUE) { "Continuous row count exceeds RecyclerView dimensions" }
            }
            size = total.toInt()
        }
        override fun get(index: Int): Row {
            if (index !in 0 until size) throw IndexOutOfBoundsException("Row index: $index, size: $size")
            for (segment in segments.indices.reversed()) {
                if (index >= starts[segment]) return segments[segment][index - starts[segment]]
            }
            error("Missing row segment")
        }
        fun startOf(rows: List<Row>): Int? {
            for (index in segments.indices) if (segments[index] === rows) return starts[index]
            return null
        }
    }
    private inner class ScrollCandidate(
        override val generation: Long, val template: ReaderPageTemplate, val list: RecyclerView,
        val viewport: ReadViewport, chapter: Chapter, offset: Double,
        private val preparedStyle: org.peyilo.ireadview.api.style.ReaderStyle
    ) : PreparedRead<ReadingPosition> {
        override var target = chapter.positionAt(offset)
        var retained = false
        var decorationPending = false
        private var closed = false
        val chapters = arrayListOf(chapter)
        val adapter = RowsAdapter(this)
        private val loading = HashMap<Boolean, Job>()
        private val failed = HashMap<Int, ReaderError>()
        fun boundaryError(index: Int) = failed[index]
        private var framePending = false
        private val manager get() = list.layoutManager as LinearLayoutManager
        fun refreshDecorations() {
            for (index in 0 until list.childCount) {
                val body = list.getChildAt(index) as? TextReaderBodyView ?: continue
                val row = adapter.rows.getOrNull(list.getChildAdapterPosition(body)) as? Row.Text ?: continue
                adapter.decorate(body, row)
            }
        }
        /** Chapter origin in list coordinates, recovered from any visible chunk. */
        fun selectedChapterTop(): Pair<Chapter, Double>? {
            val id = host.selection?.range?.chapterId ?: return null
            for (index in 0 until list.childCount) {
                val child = list.getChildAt(index)
                val row = adapter.rows.getOrNull(list.getChildAdapterPosition(child)) as? Row.Text ?: continue
                if (row.chapter.layout.chapterId == id) return row.chapter to (child.top - row.chapter.starts[row.chunkIndex])
            }
            return null
        }
        fun selectionScrollStep(requested: Int, pointerY: Float): Int {
            val selection = host.selection ?: return 0
            val start = draggingStart ?: return 0
            val (chapter, chapterTop) = selectedChapterTop() ?: return 0
            val top = list.paddingTop.toFloat()
            val bottom = (list.height - list.paddingBottom).toFloat()
            var step = if (requested < 0) maxOf(requested, minOf(0.0, chapterTop - top).toInt())
                else minOf(requested, maxOf(0.0, chapterTop + chapter.height - bottom).toInt())
            // Shrinking toward the fixed endpoint must not scroll it past the finger.
            val fixed = if (start) selection.range.endExclusive else selection.range.start
            val fixedOffset = chapter.offsetFor(fixed) ?: return 0
            val offset = Rect(); container.offsetDescendantRectToMyCoords(list, offset)
            val distance = chapterTop + fixedOffset - (pointerY - offset.top)
            if (start && step > 0) step = minOf(step, maxOf(0.0, distance).toInt())
            if (!start && step < 0) step = maxOf(step, minOf(0.0, distance).toInt())
            return step
        }
        fun rebuild() {
            val segments = ArrayList<List<Row>>()
            val book = host.book ?: return
            if (chapters.first().index > 0) segments.add(listOf(Row.Boundary(chapters.first().index - 1, true, chapters.first().index - 1 in failed)))
            chapters.forEach { chapter ->
                segments.add(chapter.rows)
            }
            if (chapters.last().index < book.chapters.lastIndex) segments.add(listOf(Row.Boundary(chapters.last().index + 1, false, chapters.last().index + 1 in failed)))
            adapter.rows = RowSegments(segments)
            adapter.notifyDataSetChanged()
        }
        fun seek(chapter: Chapter, offset: Double) {
            val chunk = chapter.chunkForOffset(offset)
            val top = chapter.starts[chunk]
            val first = adapter.rows.startOf(chapter.rows) ?: return
            manager.scrollToPositionWithOffset(first + chunk, -(offset - top).toInt())
        }
        fun visible(): Pair<Chapter, Double>? {
            for (position in manager.findFirstVisibleItemPosition() until adapter.itemCount) {
                val view = manager.findViewByPosition(position) ?: break
                if (view.bottom <= list.paddingTop) continue
                when (val row = adapter.rows[position]) {
                    is Row.Text -> return row.chapter to (row.chapter.starts[row.chunkIndex] + maxOf(0, list.paddingTop - view.top))
                    is Row.Empty -> return row.chapter to 0.0
                    is Row.Boundary -> Unit
                }
            }
            return null
        }
        fun visiblePosition() = visible()?.let { (chapter, offset) -> chapter.positionAt(offset) }
        fun reportAndLoad() {
            framePending = false
            if (closed || active !== this || generation != host.generation || detached || navigationBusy) return
            val position = visiblePosition()
            if (position != null && host.reportVisiblePosition(position, generation) is ReaderResult.Success) {
                if (active !== this || generation != host.generation || detached || navigationBusy) return
                val current = chapters.first { it.layout.chapterId == position.chapterId }
                val style = preparedStyle.copy(highlightStyles = host.style.highlightStyles, selectionStyle = host.style.selectionStyle)
                template.bind(ReaderPageContext(host.book!!.title, host.book!!.chapters[current.index], position, current.layout.pageCount, style, ReadingMode.CONTINUOUS_SCROLL,
                    requestReaderAction = { action -> active === this && host.requestReaderAction(action, position, generation) }, chromeProvider = host.pageChromeProvider))
            }
            if (active !== this || generation != host.generation || detached || navigationBusy) return
            host.reportNavigationCapabilitiesChanged(generation)
            if (active !== this || generation != host.generation || detached || navigationBusy) return
            if (!selectionInteraction.isDragging) {
                if (manager.findFirstVisibleItemPosition() <= 2) loadBoundary(true)
                if (manager.findLastVisibleItemPosition() >= adapter.itemCount - 3) loadBoundary(false)
            }
        }
        fun loadBoundary(before: Boolean, retry: Boolean = false): Boolean {
            if (closed || active !== this || detached || generation != host.generation || navigationBusy || selectionInteraction.isDragging) return false
            val book = host.book ?: return false
            val index = if (before) chapters.first().index - 1 else chapters.last().index + 1
            if (index !in book.chapters.indices || loading[before]?.isActive == true || !retry && index in failed) return false
            // Retain at most three chapters; never evict the currently visible chapter.
            val visible = visible() ?: return false
            if (chapters.size >= 3 && (if (before) chapters.last() else chapters.first()) === visible.first) return false
            failed.remove(index)
            lateinit var job: Job
            fun current() = !closed && active === this && !detached && generation == host.generation &&
                host.book === book && loading[before] === job
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    if (retry) when (val result = host.retryChapter(book.chapters[index].chapterId)) {
                        is ReaderResult.Failure -> throw PreparationFailure(ReadPreparation.Failed(result.error))
                        is ReaderResult.Rejected -> throw PreparationFailure(ReadPreparation.Rejected(result.reason))
                        ReaderResult.Superseded -> return@launch
                        is ReaderResult.Success -> Unit
                    }
                    ensureActive()
                    if (!current() || navigationBusy || selectionInteraction.isDragging) return@launch
                    val loaded = loadChapter(index, viewport)
                    ensureActive()
                    if (!current() || navigationBusy || selectionInteraction.isDragging) return@launch
                    val anchor = visible() ?: return@launch
                    if (before) chapters.add(0, loaded) else chapters.add(loaded)
                    if (chapters.size > 3) {
                        val evicted = if (before) chapters.last() else chapters.first()
                        if (evicted !== anchor.first) chapters.remove(evicted) else chapters.remove(loaded)
                    }
                    rebuild()
                    if (current() && !navigationBusy) seek(anchor.first, anchor.second)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    ensureActive()
                    if (current() && !navigationBusy) {
                        failed[index] = ((error as? PreparationFailure)?.result as? ReadPreparation.Failed)?.error
                            ?: ReaderError(ReaderStage.CHAPTER, "BoundaryLoadFailed", error.message ?: "Unable to load neighboring chapter", book.chapters[index].chapterId, cause = error)
                        val anchor = visible()
                        rebuild()
                        if (current() && !navigationBusy) anchor?.let { seek(it.first, it.second) }
                    }
                }
            }
            loading[before] = job
            job.invokeOnCompletion { if (loading[before] === job) loading.remove(before) }
            rebuild()
            if (!current() || navigationBusy) { job.cancel(); return false }
            seek(visible.first, visible.second)
            if (!current() || navigationBusy) { job.cancel(); return false }
            job.start()
            return true
        }
        fun installInput() {
            val detector = GestureDetector(container.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (navigationBusy || active !== this@ScrollCandidate || generation != host.generation) return false
                    val offset = Rect(); container.offsetDescendantRectToMyCoords(list, offset)
                    if (bodyAt(e.x + offset.left, e.y + offset.top) != null) host.reportTap(e.x + offset.left, e.y + offset.top)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    if (navigationBusy || active !== this@ScrollCandidate || generation != host.generation) return
                    val offset = Rect(); container.offsetDescendantRectToMyCoords(list, offset)
                    if (onLongPress(e.x + offset.left, e.y + offset.top)) list.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
            })
            list.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (navigationBusy) return true
                    val offset = Rect(); container.offsetDescendantRectToMyCoords(list, offset)
                    if (onSelectionTouch(e.actionMasked, e.x + offset.left, e.y + offset.top)) return true
                    detector.onTouchEvent(e); return false
                }
                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                    val offset = Rect(); container.offsetDescendantRectToMyCoords(list, offset)
                    onSelectionTouch(e.actionMasked, e.x + offset.left, e.y + offset.top)
                }
            })
            list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (!framePending) { framePending = true; list.postOnAnimation { reportAndLoad() } }
                }
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) { reportAndLoad(); if (active === this@ScrollCandidate && generation == host.generation) host.reportSelectionGeometryChanged() }
                }
            })
        }
        fun close() {
            if (closed) return
            closed = true
            val jobs = loading.values.toList()
            loading.clear()
            jobs.forEach { it.cancel() }
            cleanupPage(template, listOf({ list.stopScroll() }, { list.adapter = null },
                { (list.parent as? ViewGroup)?.removeView(list) }), generation)
        }
        override fun dispose() { if (!retained) close() }
    }
    private inner class RowsAdapter(private val candidate: ScrollCandidate) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val ownedBodies = mutableSetOf<TextReaderBodyView>()
        private inner class PlaceholderHolder(val root: FrameLayout) : RecyclerView.ViewHolder(root) {
            val controller = ReaderPlaceholderController(root) { host.placeholderFactory }
        }
        private val placeholders = HashSet<PlaceholderHolder>()
        private val boundBodies = HashSet<TextReaderBodyView>()
        fun refreshPlaceholders() { placeholders.toList().forEach { holder ->
            val index = holder.bindingAdapterPosition
            if (index in rows.indices) onBindViewHolder(holder, index)
        } }
        var rows = RowSegments(emptyList())
        /** Reject pending/recycled bindings even when a newly created row is structurally equal. */
        private fun isBound(holder: RecyclerView.ViewHolder, row: Row): Boolean =
            holder.bindingAdapter === this && rows.getOrNull(holder.bindingAdapterPosition) === row
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Text) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType != 0) return PlaceholderHolder(FrameLayout(parent.context))
            val body = TextReaderBodyView(parent.context)
            try { body.configureComponents(host.presentationComponents) }
            catch (error: Throwable) { body.release(); throw error }
            ownedBodies.add(body)
            return object : RecyclerView.ViewHolder(body) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Text -> {
                    val body = holder.itemView as TextReaderBodyView
                    body.clearContentBinding()
                    boundBodies.add(body)
                    body.layoutParams = RecyclerView.LayoutParams(-1, row.chapter.heights[row.chunkIndex])
                    body.fragments = listOf(row.chapter.chunks[row.chunkIndex].fragment)
                    body.accessibilityMatchesText = { range, text ->
                        active === candidate && !detached && candidate.generation == host.generation && isBound(holder, row) &&
                            row.chapter.layout.contentSnapshot?.let { TextSelectionResolver.matchesBlockRange(it, range, text) } == true
                    }
                    body.accessibilitySelectText = { range ->
                        if (active !== candidate || detached || candidate.generation != host.generation || navigationBusy || !isBound(holder, row)) false
                        else row.chapter.layout.contentSnapshot?.let { TextSelectionResolver.fromBlockRange(it, range) }
                            ?.let { commitAccessibilitySelection { host.reportSelection(it, candidate.generation) is ReaderResult.Success && host.selection == it } } ?: false
                    }
                    body.accessibilityClearSelection = {
                        active === candidate && !detached && candidate.generation == host.generation && isBound(holder, row) &&
                            commitAccessibilitySelection { host.clearAccessibilitySelection(candidate.generation) && host.selection == null }
                    }
                    body.accessibilityMoveToAdjacent = move@ { action, arguments, fixed ->
                        if (active !== candidate || detached || candidate.generation != host.generation || navigationBusy ||
                            candidate.list.isLayoutRequested) return@move false
                        val origin = holder.bindingAdapterPosition
                        if (!isBound(holder, row) || candidate.list.getChildAdapterPosition(body) != origin) return@move false
                        val forward = action == androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
                        // Inspect attached rows only; a large loaded chapter may contain many offscreen chunks.
                        val attached = (0 until candidate.list.childCount).map {
                            candidate.list.getChildAdapterPosition(candidate.list.getChildAt(it))
                        }.filter { it in rows.indices && if (forward) it > origin else it < origin }.distinct().sorted()
                        val indices = if (forward) attached else attached.asReversed()
                        val originFragment = row.chapter.chunks[row.chunkIndex].fragment
                        for (index in indices) {
                            val targetRow = rows[index] as? Row.Text ?: continue
                            if (fixed != null && targetRow.chapter !== row.chapter) break
                            if (fixed == null && originFragment.accessibilityDescribesWholeBlock &&
                                targetRow.chapter === row.chapter &&
                                targetRow.chapter.chunks[targetRow.chunkIndex].fragment.blockId == originFragment.blockId) continue
                            val adjacent = candidate.list.findViewHolderForAdapterPosition(index)?.itemView as? TextReaderBodyView ?: continue
                            if (adjacent.accessibilityMoveFromBoundary(action, arguments, fixed)) return@move true
                            if (active !== candidate || detached || candidate.generation != host.generation || navigationBusy ||
                                !isBound(holder, row)) return@move false
                        }
                        false
                    }
                    body.accessibilityPreserveAnchor = { anchor ->
                        val chapterId = row.chapter.layout.chapterId
                        if (active !== candidate || detached || candidate.generation != host.generation || !isBound(holder, row)) null else row.chapter.layout.contentSnapshot?.let {
                            preserveAccessibilityAnchor(anchor, it, candidate.generation, host) { contentForChapter(chapterId) }
                        }
                    }
                    body.accessibilityMoveOffscreen = { action, arguments, boundary, fixed, valid ->
                        isBound(holder, row) && moveAccessibilityOffscreen(candidate, row.chapter, action, arguments, boundary, fixed, valid)
                    }
                    body.accessibilitySelectAnchors = { fixed, moving ->
                        if (active !== candidate || detached || candidate.generation != host.generation || navigationBusy || !isBound(holder, row)) false
                        else row.chapter.layout.contentSnapshot?.let { TextSelectionResolver.fromAnchors(it, fixed, moving) }
                            ?.let { commitAccessibilitySelection { host.reportSelection(it, candidate.generation) is ReaderResult.Success && host.selection == it } } ?: false
                    }
                    body.accessibilityCompareAnchors = { first, second ->
                        if (active !== candidate || detached || candidate.generation != host.generation || !isBound(holder, row)) null
                        else row.chapter.layout.contentSnapshot?.let { TextSelectionResolver.compareAnchors(it, first, second) }
                    }
                    decorate(body, row)
                }
                is Row.Empty -> {
                    val item = holder as PlaceholderHolder
                    placeholders.add(item)
                    item.root.layoutParams = RecyclerView.LayoutParams(-1, ceil(row.chapter.height.toDouble()).toInt())
                    item.controller.show(PlaceholderState(PlaceholderKind.EMPTY_CHAPTER, host.book?.bookId, row.chapter.layout.chapterId, null, host.style), row,
                        object : PlaceholderActions { override val canRetry = false; override fun retry() = false })
                }
                is Row.Boundary -> {
                    val item = holder as PlaceholderHolder
                    placeholders.add(item)
                    item.root.layoutParams = RecyclerView.LayoutParams(-1, (128 * item.root.resources.displayMetrics.density).toInt())
                    val error = candidate.boundaryError(row.index)
                    val kind = if (!row.failed) PlaceholderKind.CHAPTER_LOADING else if (error?.stage == ReaderStage.LAYOUT) PlaceholderKind.LAYOUT_FAILED else PlaceholderKind.CHAPTER_FAILED
                    item.controller.show(PlaceholderState(kind, host.book?.bookId, host.book?.chapters?.getOrNull(row.index)?.chapterId, error, host.style), row,
                        object : PlaceholderActions {
                            override val canRetry get() = row.failed && error?.retryable != false && active === candidate && candidate.generation == host.generation &&
                                !detached && !navigationBusy && isBound(item, row)
                            override fun retry() = canRetry && candidate.loadBoundary(row.before, retry = true)
                        })
                }
            }
        }
        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            (holder as? PlaceholderHolder)?.let { it.controller.clear(); placeholders.remove(it) }
            (holder.itemView as? TextReaderBodyView)?.let { body ->
                boundBodies.remove(body)
                body.clearContentBinding()
            }
            super.onViewRecycled(holder)
        }
        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            boundBodies.clear()
            ownedBodies.toList().forEach { it.release() }; ownedBodies.clear()
            placeholders.toList().forEach { it.controller.clear() }; placeholders.clear()
            super.onDetachedFromRecyclerView(recyclerView)
        }
        private fun commitAccessibilitySelection(action: () -> Boolean): Boolean {
            if (!action() || active !== candidate || detached || candidate.generation != host.generation) return false
            // Services query the accepted selection synchronously, before the decoration frame runs.
            candidate.refreshDecorations()
            return active === candidate && !detached && candidate.generation == host.generation
        }
        fun decorate(body: TextReaderBodyView, row: Row.Text) {
            val highlights = host.validHighlights(row.chapter.layout.chapterId).flatMap { highlight ->
                if (active === candidate && highlight.styleId !in host.style.highlightStyles.styles && reportedFallbacks.add(highlight.id to highlight.styleId)) {
                    body.post {
                        if (active === candidate && candidate.generation == host.generation && highlight.styleId !in host.style.highlightStyles.styles && host.validHighlights(row.chapter.layout.chapterId).any { it.id == highlight.id && it.styleId == highlight.styleId }) host.reportHighlightStyleFallback(highlight.id, highlight.styleId)
                    }
                }
                ((row.chapter.layout.rangesForText(highlight.range) as? ReaderResult.Success)?.value ?: emptyList()).map {
                    ColoredTextRange(highlight.id, it, host.style.highlightStyles[highlight.styleId].fillColor)
                }
            }
            body.setHighlights(highlights)
            body.setUnderlines(host.validUnderlines(row.chapter.layout.chapterId).flatMap { underline ->
                ((row.chapter.layout.rangesForText(underline.range) as? ReaderResult.Success)?.value ?: emptyList()).map {
                    org.peyilo.ireadview.internal.render.LineTextRange(underline.id, it, underline.style)
                }
            })
            val selection = host.selection?.takeIf { it.range.chapterId == row.chapter.layout.chapterId }
            body.setSelection(selection?.let { (row.chapter.layout.rangesForText(it.range) as? ReaderResult.Success)?.value } ?: emptyList(), host.style.selectionStyle)
        }
    }
    private fun cleanupPage(template: ReaderPageTemplate, before: List<() -> Unit>, generation: Long, removeRoot: Boolean = true) {
        org.peyilo.ireadview.internal.presentation.cleanupReaderPage(template, before,
            { if (removeRoot) container.removeView(template.root) }) { error ->
            android.util.Log.w("iReadView", "Page cleanup failed", error)
            container.post {
                if (!detached && generation == host.generation) host.reportError(ReaderError(
                    ReaderStage.NAVIGATION, "PageCleanupFailed", error.message ?: "Page cleanup failed", retryable = false, cause = error))
            }
        }
    }
    private class PreparationFailure(val result: ReadPreparation<ReadingPosition>) : Exception()
    private fun contains(root: View, child: View): Boolean {
        var cursor: View? = child
        while (cursor != null) { if (cursor === root) return true; cursor = cursor.parent as? View }
        return false
    }
}
