package org.peyilo.ireadview.internal.book

import kotlinx.coroutines.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.api.style.HighlightStyles
import org.peyilo.ireadview.api.style.SelectionStyle
import org.peyilo.ireadview.internal.cache.ByteBudgetCache
import org.peyilo.ireadview.internal.layout.PaginationEngine
import org.peyilo.ireadview.internal.layout.ImageBlockLayoutProvider
import org.peyilo.ireadview.spi.content.*
import org.peyilo.ireadview.spi.presentation.*

/** Book services only. The container remains the owner of the committed position. */
internal class ReaderCoordinator(
    private val ownerScope: () -> CoroutineScope,
    private val onBook: (BookDescriptor?) -> Unit,
    private val onChapter: (String, ChapterState) -> Unit,
    private val onPagination: (String, ChapterPaginationState) -> Unit,
    private val onPhase: (ReaderPhase) -> Unit,
    private val onContent: (ChapterContent) -> Unit = {},
    private val onIndex: (BookPageIndexState) -> Unit = {},
    private val resolveFonts: (ReaderStyle) -> ReaderStyle = { it }
) {
    var book: BookDescriptor? = null
        private set
    var source: BookSource? = null
        private set
    var sessionId: Long = 0
        private set
    var loadingOptions = LoadingOptions()
        private set
    var providers: List<BlockLayoutProvider> = emptyList()
    var imageLoadingOptions = ReaderImageLoadingOptions()
    private var repository: ChapterRepository? = null
    private var sessionScope: CoroutineScope? = null
    private var workGeneration = 0L
    private val paginator = PaginationEngine()
    private val textProvider = BlockLayoutProviders.text()
    private val paginationStates = HashMap<String, ChapterPaginationState>()
    private val paginationOwners = HashMap<String, Any>()
    private val layouts = ByteBudgetCache<LayoutKey, ChapterLayout>(loadingOptions.layoutCacheBytes) { it.estimatedRetainedBytes }
    private val layoutRequests = HashMap<LayoutKey, Deferred<ReaderResult<ChapterLayout>>>()
    private val failedLayoutRequests = HashSet<Deferred<ReaderResult<ChapterLayout>>>()
    private var layoutSerial = 0L
    private var invalidationSerial = 0L
    private val preloads = HashMap<String, Job>()
    private var preloadSerial = 0L
    private var catalogueInstallation: Any? = null
    private var catalogueGeneration = 0L
    private var catalogueVersion = 0L
    private val pageIndexBuilder = BookPageIndexBuilder(onIndex)
    val pageIndexState get() = pageIndexBuilder.state
    val pageIndex get() = pageIndexBuilder.index
    private val layoutConsumers = HashMap<Deferred<ReaderResult<ChapterLayout>>, Int>()
    private var indexInput: IndexInput? = null
    private var paginationInput: IndexLayoutInput? = null
    private fun indexStyle(style: ReaderStyle) = style.copy(highlightStyles = HighlightStyles(), selectionStyle = SelectionStyle())

    suspend fun buildBookPageIndex(viewport: ReadViewport, style: ReaderStyle, bodyPx: Float, titlePx: Float,
        awaitForeground: suspend () -> Unit): ReaderResult<BookPageIndex> {
        val book = book ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        val scope = sessionScope ?: return ReaderResult.Rejected(ReaderRejection.NotReady)
        if (!viewport.isValid) return ReaderResult.Rejected(ReaderRejection.NotReady)
        val input = IndexInput(sessionId, catalogueVersion, IndexLayoutInput(viewport, indexStyle(style), bodyPx, titlePx, providerInputs(), imageLoadingOptions))
        indexInput = input
        return pageIndexBuilder.build(book, input, catalogueVersion, scope) { id ->
            awaitForeground()
            layoutChapter(id, viewport, style, bodyPx, titlePx, background = true)
        }
    }

    suspend fun open(source: BookSource): ReaderResult<BookDescriptor> {
        if (!close()) return ReaderResult.Superseded
        this.source = source
        val session = sessionId
        val catalogue = ++catalogueGeneration
        createRepository(source)
        onPhase(ReaderPhase.LoadingBook)
        return try {
            currentCoroutineContext().ensureActive()
            if (session != sessionId || this.source !== source) return ReaderResult.Superseded
            val descriptor = withContext(Dispatchers.IO) { source.loadBook() }
            currentCoroutineContext().ensureActive()
            if (session != sessionId || catalogue != catalogueGeneration) return ReaderResult.Superseded
            require(descriptor.bookId == source.bookId) { "Source returned another book's catalogue" }
            book = descriptor
            catalogueVersion++
            onBook(descriptor)
            ReaderResult.Success(descriptor)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) {
            if (session != sessionId || catalogue != catalogueGeneration) ReaderResult.Superseded
            else ReaderResult.Failure(ReaderError(ReaderStage.BOOK, "BookLoadFailed", exception.message ?: "Catalogue load failed", cause = exception))
        }
    }

    suspend fun getChapter(id: String): ReaderResult<ChapterContent> {
        val book = book ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        if (book.chapters.none { it.chapterId == id }) return ReaderResult.Rejected(ReaderRejection.UnknownChapter)
        val repository = repository ?: return ReaderResult.Rejected(ReaderRejection.NotReady)
        val work = workGeneration
        preloadSerial++
        val previous = preloads.keys.filter { it != id }.mapNotNull { preloads.remove(it) }
        previous.forEach { it.cancel() }
        currentCoroutineContext().ensureActive()
        if (this.book !== book || this.repository !== repository || work != workGeneration) return ReaderResult.Superseded
        val result = repository.get(id)
        currentCoroutineContext().ensureActive()
        return if (this.book === book && this.repository === repository && work == workGeneration) result else ReaderResult.Superseded
    }

    /** Reading the catalogue does not mutate the active session on failure/cancellation. */
    suspend fun readCatalogue(): ReaderResult<BookDescriptor> {
        val source = source ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        val session = sessionId
        val catalogue = ++catalogueGeneration
        return try {
            val descriptor = withContext(Dispatchers.IO) { source.loadBook() }
            currentCoroutineContext().ensureActive()
            if (session != sessionId || this.source !== source || catalogue != catalogueGeneration) return ReaderResult.Superseded
            require(descriptor.bookId == source.bookId) { "Source returned another book's catalogue" }
            ReaderResult.Success(descriptor)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) {
            if (session != sessionId || this.source !== source || catalogue != catalogueGeneration) ReaderResult.Superseded
            else ReaderResult.Failure(ReaderError(ReaderStage.BOOK, "BookReloadFailed", exception.message ?: "Catalogue reload failed", cause = exception))
        }
    }

    fun reusableChapters(descriptor: BookDescriptor): Set<String> {
        val oldBook = book ?: return emptySet()
        val oldChapters = oldBook.chapters.associateBy { it.chapterId }
        val unchangedBook = oldBook.revision != null && oldBook.revision == descriptor.revision
        return descriptor.chapters.filter { chapter ->
            val old = oldChapters[chapter.chapterId]
            old != null && ((old.revision != null && old.revision == chapter.revision) ||
                (old.revision == null && chapter.revision == null && unchangedBook))
        }.map { it.chapterId }.toSet()
    }

    fun installCatalogue(descriptor: BookDescriptor, retained: Set<String>): ReaderResult<Unit> {
        require(descriptor.bookId == source?.bookId)
        val owner = Any()
        val session = sessionId
        val repository = repository
        val version = ++catalogueVersion
        fun current() = session == sessionId && this.repository === repository &&
            catalogueVersion == version && book === descriptor
        catalogueInstallation = owner
        try {
            catalogueGeneration++
            indexInput = null
            val preloads = preloads.values.toList()
            this.preloads.clear()
            book = descriptor
            paginationOwners.keys.toList().filter { it !in retained }.forEach { paginationOwners.remove(it) }
            val requests = layoutRequests.keys.filter { it.chapterId !in retained }.mapNotNull { layoutRequests.remove(it) }
            layouts.removeWhere { it.chapterId !in retained }
            paginationStates.keys.toList().filter { it !in retained }.forEach { paginationStates.remove(it) }
            repository?.retainChapters(retained)
            requests.forEach { it.cancel() }; preloads.forEach { it.cancel() }
            if (!current()) return ReaderResult.Superseded
            pageIndexBuilder.invalidate()
            if (!current()) return ReaderResult.Superseded
        } finally { if (catalogueInstallation === owner) catalogueInstallation = null }
        onBook(descriptor)
        return if (current()) ReaderResult.Success(Unit) else ReaderResult.Superseded
    }

    suspend fun layoutChapter(id: String, viewport: ReadViewport, style: ReaderStyle, bodyTextSizePx: Float, titleTextSizePx: Float, background: Boolean = false): ReaderResult<ChapterLayout> {
        if (!viewport.isValid) return ReaderResult.Failure(ReaderError(ReaderStage.VIEWPORT, "InvalidViewport", "No usable body area", id))
        if (!background) {
            val actual = IndexLayoutInput(viewport, indexStyle(style), bodyTextSizePx, titleTextSizePx, providerInputs(), imageLoadingOptions)
            if (paginationInput != null && paginationInput != actual) invalidateLayouts()
            paginationInput = actual
            indexInput?.let { previous ->
                if (previous.layout != actual) { indexInput = null; pageIndexBuilder.invalidate() }
            }
        }
        val content = when (val result = if (background) repository?.get(id) ?: ReaderResult.Rejected(ReaderRejection.NotReady) else getChapter(id)) {
            is ReaderResult.Success -> result.value
            is ReaderResult.Failure -> return result
            is ReaderResult.Rejected -> return result
            ReaderResult.Superseded -> return ReaderResult.Superseded
        }
        // A source may return a fresh immutable snapshot after content-cache eviction.
        // Fingerprints prove equivalence only with the default provider chain.
        val fingerprint = content.contentFingerprint
        val inputs = providerInputs()
        val identity = content.takeIf { fingerprint == null || inputs.isNotEmpty() }
        val key = LayoutKey(content.bookId, content.chapterId, content.revision, fingerprint,
            identity, viewport, style, bodyTextSizePx, titleTextSizePx, inputs, imageLoadingOptions)
        layouts[key]?.let { cached ->
            val session = sessionId
            val invalidation = invalidationSerial
            val catalogue = catalogueVersion
            paginationOwners.remove(id)
            val ready = ChapterPaginationState.Ready(cached.pageCount, cached.layoutKey)
            if (paginationStates[id] != ready) publishPagination(id, ready)
            currentCoroutineContext().ensureActive()
            if (session != sessionId || invalidation != invalidationSerial || catalogue != catalogueVersion)
                return ReaderResult.Superseded
            return ReaderResult.Success(cached)
        }
        layoutRequests[key]?.takeIf { it !in failedLayoutRequests }?.let { return consumeLayout(it) }
        val session = sessionId
        val scope = sessionScope ?: return ReaderResult.Rejected(ReaderRejection.NotReady)
        val resourceSource = source
        val resourceRevision = book?.revision
        val owner = Any()
        lateinit var request: Deferred<ReaderResult<ChapterLayout>>
        request = scope.async(start = CoroutineStart.LAZY) {
            val opaqueKey = "$session:${++layoutSerial}"
            paginationOwners[id] = owner
            publishPagination(id, ChapterPaginationState.Calculating)
            try {
                val resolvedStyle = withContext(Dispatchers.IO) { resolveFonts(style) }
                val imageProvider = ImageBlockLayoutProvider.prepare(content, resourceSource, resourceRevision, viewport, key.providers.map { it.provider }, key.images)
                val layout = withContext(Dispatchers.Default) {
                    val computation = currentCoroutineContext()
                    paginator.paginate(content, BlockLayoutContext(viewport, resolvedStyle, bodyTextSizePx, titleTextSizePx) { computation.ensureActive() }, key.providers.map { it.provider } + listOf(imageProvider, textProvider), opaqueKey)
                }
                currentCoroutineContext().ensureActive()
                if (session != sessionId || layoutRequests[key] !== request) return@async ReaderResult.Superseded
                layouts.put(key, layout)
                if (paginationOwners[id] === owner) publishPagination(id, ChapterPaginationState.Ready(layout.pageCount, layout.layoutKey))
                currentCoroutineContext().ensureActive()
                if (session != sessionId || layoutRequests[key] !== request) return@async ReaderResult.Superseded
                ReaderResult.Success(layout)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (exception: Exception) {
                currentCoroutineContext().ensureActive()
                if (session != sessionId || layoutRequests[key] !== request) return@async ReaderResult.Superseded
                val error = ReaderError(ReaderStage.LAYOUT, "LayoutFailed", exception.message ?: "Layout failed", id, cause = exception)
                failedLayoutRequests.add(request)
                if (paginationOwners[id] === owner) publishPagination(id, ChapterPaginationState.Failed(error))
                currentCoroutineContext().ensureActive()
                if (session != sessionId || layoutRequests[key] !== request) return@async ReaderResult.Superseded
                ReaderResult.Failure(error)
            }
        }
        layoutRequests[key] = request
        request.invokeOnCompletion {
            failedLayoutRequests.remove(request)
            if (layoutRequests[key] === request) layoutRequests.remove(key)
            if (paginationOwners[id] === owner) {
                paginationOwners.remove(id)
                if (request.isCancelled && session == sessionId && paginationStates[id] == ChapterPaginationState.Calculating)
                    publishPagination(id, ChapterPaginationState.Unknown)
            }
        }
        return consumeLayout(request)
    }

    private suspend fun consumeLayout(request: Deferred<ReaderResult<ChapterLayout>>): ReaderResult<ChapterLayout> {
        layoutConsumers[request] = (layoutConsumers[request] ?: 0) + 1
        try { return request.await() } finally {
            val count = (layoutConsumers[request] ?: 1) - 1
            if (count == 0) { layoutConsumers.remove(request); if (request.isActive) request.cancel() }
            else layoutConsumers[request] = count
        }
    }

    fun chapterState(id: String): ChapterState? = if (book?.chapterForId(id) != null) repository?.state(id) ?: ChapterState.Unloaded else null
    fun peekChapter(id: String): ChapterContent? = repository?.peek(id)
    fun paginationState(id: String): ChapterPaginationState? = if (book?.chapterForId(id) != null) paginationStates[id] ?: ChapterPaginationState.Unknown else null

    fun invalidateLayouts() {
        val token = ++invalidationSerial
        val session = sessionId
        val requests = clearLayouts()
        requests.forEach { it.cancel() }
        if (token == invalidationSerial && session == sessionId) pageIndexBuilder.invalidate()
    }

    private fun clearLayouts(): List<Deferred<ReaderResult<ChapterLayout>>> {
        paginationInput = null; indexInput = null
        val requests = layoutRequests.values.toList()
        layoutRequests.clear(); layouts.clear(); paginationStates.clear(); paginationOwners.clear()
        return requests
    }

    fun invalidateChapter(id: String): ReaderResult<Unit> {
        if (book?.chapters?.none { it.chapterId == id } != false) return ReaderResult.Rejected(ReaderRejection.UnknownChapter)
        val token = ++invalidationSerial
        val session = sessionId
        val repository = repository
        fun current() = token == invalidationSerial && session == sessionId && this.repository === repository
        val preload = preloads.remove(id)
        paginationOwners.remove(id)
        val requests = layoutRequests.keys.filter { it.chapterId == id }.mapNotNull { layoutRequests.remove(it) }
        layouts.removeWhere { it.chapterId == id }
        paginationStates.remove(id)
        val notifyChapter = repository?.invalidateSilently(id)
        requests.forEach { it.cancel() }; preload?.cancel()
        if (!current()) return ReaderResult.Superseded
        pageIndexBuilder.invalidate()
        if (!current()) return ReaderResult.Superseded
        if (paginationStates[id] == null) publishPagination(id, ChapterPaginationState.Unknown)
        if (!current()) return ReaderResult.Superseded
        notifyChapter?.invoke()
        if (!current()) return ReaderResult.Superseded
        return ReaderResult.Success(Unit)
    }

    fun setLoadingOptions(options: LoadingOptions) {
        loadingOptions = options
        repository?.setOptions(options)
        layouts.budgetBytes = options.layoutCacheBytes
        layouts.trim()
    }

    fun suspendWork() {
        workGeneration++
        invalidationSerial++
        val preloads = preloads.values.toList()
        val repository = repository
        val scope = sessionScope
        this.repository = null; sessionScope = null
        val layouts = clearLayouts()
        this.preloads.clear()
        pageIndexBuilder.invalidate()
        repository?.close()
        preloads.forEach { it.cancel() }; layouts.forEach { it.cancel() }
        scope?.cancel()
    }

    fun resumeWork() { source?.let { if (repository == null) createRepository(it) } }

    fun preloadNear(position: ReadingPosition?) {
        val book = book ?: return
        if (position?.bookId != book.bookId) return
        val chapters = book.chapters
        val index = chapters.indexOfFirst { it.chapterId == position?.chapterId }
        if (index < 0) return
        val scope = sessionScope ?: return
        val repository = repository ?: return
        val options = loadingOptions
        val work = workGeneration
        val token = ++preloadSerial
        fun current() = token == preloadSerial && work == workGeneration && this.book === book &&
            this.repository === repository && sessionScope === scope && loadingOptions == options
        val ids = ((index - minOf(options.preloadBefore, index))..(index + minOf(options.preloadAfter, chapters.lastIndex - index)))
            .filter { it != index }.map { chapters[it].chapterId }.toSet()
        val previous = preloads.keys.filter { it !in ids }.mapNotNull { preloads.remove(it) }
        previous.forEach { it.cancel() }
        if (!current()) return
        ids.forEach { id ->
            if (!current()) return
            if (preloads[id]?.isActive == true) return@forEach
            val request = scope.launch(start = CoroutineStart.LAZY) { repository.get(id) }
            preloads[id] = request
            request.invokeOnCompletion { if (preloads[id] === request) preloads.remove(id) }
            request.start()
            if (!current()) return
        }
    }

    fun close(): Boolean {
        val hadSource = source != null || book != null
        val session = ++sessionId
        catalogueGeneration++
        source = null; book = null
        suspendWork()
        if (session != sessionId) return false
        if (hadSource) onBook(null)
        return session == sessionId && source == null && book == null
    }

    private fun createRepository(source: BookSource) {
        val work = ++workGeneration
        val scope = CoroutineScope(ownerScope().coroutineContext + SupervisorJob(ownerScope().coroutineContext[Job]))
        sessionScope = scope
        val session = sessionId
        fun current() = session == sessionId && work == workGeneration
        repository = ChapterRepository(source, scope, loadingOptions, onContent = { content ->
            if (current()) { pageIndexBuilder.observe(content); if (current()) onContent(content) }
        }) { id, state -> if (current() && catalogueInstallation == null) onChapter(id, state) }
    }

    private fun publishPagination(id: String, state: ChapterPaginationState) { paginationStates[id] = state; onPagination(id, state) }
    private fun providerInputs() = providers.map { ProviderInput(it, it.id, it.version) }
    /** Plugin equality must not merge renderers; version values must survive later host mutation. */
    private class ProviderInput(val provider: BlockLayoutProvider, private val id: String, private val version: String) {
        override fun equals(other: Any?) = other is ProviderInput && provider === other.provider && id == other.id && version == other.version
        override fun hashCode() = 31 * (31 * System.identityHashCode(provider) + id.hashCode()) + version.hashCode()
    }
    private data class LayoutKey(val bookId: String, val chapterId: String, val revision: String?, val fingerprint: String?,
        val customContentIdentity: ChapterContent?, val viewport: ReadViewport, val style: ReaderStyle,
        val bodyTextSizePx: Float, val titleTextSizePx: Float, val providers: List<ProviderInput>, val images: ReaderImageLoadingOptions)
    private data class IndexInput(val session: Long, val catalogue: Long, val layout: IndexLayoutInput)
    private data class IndexLayoutInput(val viewport: ReadViewport, val style: ReaderStyle, val bodyPx: Float, val titlePx: Float, val providers: List<ProviderInput>, val images: ReaderImageLoadingOptions)
}
