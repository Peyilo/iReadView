package org.peyilo.ireadview.internal.book

import kotlinx.coroutines.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.cache.ByteBudgetCache
import org.peyilo.ireadview.internal.cache.RetainedSize

/** All bookkeeping is confined to its owner's dispatcher. Source calls run on IO. */
internal class ChapterRepository(
    private val source: BookSource,
    private val scope: CoroutineScope,
    options: LoadingOptions,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onContent: (ChapterContent) -> Unit = {},
    private val onState: (String, ChapterState) -> Unit
) {
    private data class PreparedContent(val content: ChapterContent, val bytes: Long?)
    private val cache = ByteBudgetCache<String, PreparedContent>(options.contentCacheBytes) { it.bytes }
    private val inFlight = HashMap<String, Deferred<ReaderResult<ChapterContent>>>()
    private val consumers = HashMap<Deferred<ReaderResult<ChapterContent>>, Int>()
    private val states = HashMap<String, ChapterState>()
    private val generations = HashMap<String, Long>()
    private var closed = false
    private var maxConcurrentLoads = options.maxConcurrentLoads
    private var activeLoads = 0
    private val permitWaiters = ArrayDeque<CompletableDeferred<Unit>>()

    fun state(chapterId: String): ChapterState = states[chapterId] ?: ChapterState.Unloaded
    fun peek(chapterId: String): ChapterContent? = cache[chapterId]?.content

    suspend fun get(chapterId: String): ReaderResult<ChapterContent> {
        if (closed) return ReaderResult.Superseded
        cache[chapterId]?.let { return ReaderResult.Success(it.content) }
        inFlight[chapterId]?.takeIf { states[chapterId] !is ChapterState.Failed }?.let { return consume(it) }
        val generation = generations[chapterId] ?: 0L
        lateinit var request: Deferred<ReaderResult<ChapterContent>>
        fun current() = !closed && (generations[chapterId] ?: 0L) == generation && inFlight[chapterId] === request
        request = scope.async(start = CoroutineStart.LAZY) {
            val result = try {
                publish(chapterId, ChapterState.Loading)
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                acquirePermit()
                val prepared = try {
                    withContext(ioDispatcher) {
                        val content = source.loadChapter(chapterId)
                        val computation = currentCoroutineContext()
                        content.prepareContentFingerprint { computation.ensureActive() }
                        val bytes = RetainedSize.content(content) { computation.ensureActive() }
                        PreparedContent(content, bytes)
                    }
                } finally { releasePermit() }
                val content = prepared.content
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                require(content.bookId == source.bookId && content.chapterId == chapterId) { "Source returned content for another book/chapter" }
                cache.put(chapterId, prepared)
                if (current()) onContent(content)
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                publish(chapterId, ChapterState.Loaded(content.revision))
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                ReaderResult.Success(content)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (exception: Exception) {
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                val error = ReaderError(ReaderStage.CHAPTER, "ChapterLoadFailed", exception.message ?: "Chapter load failed", chapterId, cause = exception)
                publish(chapterId, ChapterState.Failed(error))
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                ReaderResult.Failure(error)
            }
            result
        }
        inFlight[chapterId] = request
        request.invokeOnCompletion {
            if (inFlight[chapterId] === request) {
                inFlight.remove(chapterId)
                if (request.isCancelled && !closed && states[chapterId] == ChapterState.Loading) publish(chapterId, ChapterState.Unloaded)
            }
        }
        return consume(request)
    }

    fun setOptions(options: LoadingOptions) {
        maxConcurrentLoads = options.maxConcurrentLoads
        cache.budgetBytes = options.contentCacheBytes
        cache.trim()
        wakeWaiters()
    }

    private suspend fun consume(request: Deferred<ReaderResult<ChapterContent>>): ReaderResult<ChapterContent> {
        consumers[request] = (consumers[request] ?: 0) + 1
        try { return request.await() } finally {
            val remaining = (consumers[request] ?: 1) - 1
            if (remaining == 0) { consumers.remove(request); if (request.isActive) request.cancel() }
            else consumers[request] = remaining
        }
    }

    private suspend fun acquirePermit() {
        while (activeLoads >= maxConcurrentLoads) {
            val waiter = CompletableDeferred<Unit>()
            permitWaiters.addLast(waiter)
            try { waiter.await() } finally { permitWaiters.remove(waiter) }
        }
        activeLoads++
    }
    private fun releasePermit() { activeLoads--; wakeWaiters() }
    private fun wakeWaiters() {
        permitWaiters.take((maxConcurrentLoads - activeLoads).coerceAtLeast(0)).forEach { it.complete(Unit) }
    }

    fun invalidate(chapterId: String) {
        invalidateSilently(chapterId).invoke()
    }

    /** Clears ownership before cancellation; the coordinator publishes after clearing its other layers. */
    fun invalidateSilently(chapterId: String): () -> Unit {
        generations[chapterId] = (generations[chapterId] ?: 0L) + 1
        val generation = generations[chapterId]
        val previous = inFlight.remove(chapterId)
        cache.remove(chapterId)
        states[chapterId] = ChapterState.Unloaded
        previous?.cancel()
        return {
            if (!closed && generations[chapterId] == generation && states[chapterId] == ChapterState.Unloaded)
                onState(chapterId, ChapterState.Unloaded)
        }
    }

    /** Only chapters whose version is proven unchanged may survive a catalogue refresh. */
    fun retainChapters(chapterIds: Set<String>) {
        (states.keys + inFlight.keys).filter { it !in chapterIds }.forEach { invalidate(it) }
        cache.removeWhere { it !in chapterIds }
    }

    fun close() {
        closed = true
        val requests = inFlight.values.toList()
        val waiters = permitWaiters.toList()
        inFlight.clear(); cache.clear(); states.clear()
        permitWaiters.clear()
        requests.forEach { it.cancel() }; waiters.forEach { it.cancel() }
    }

    private fun publish(id: String, state: ChapterState) { if (!closed) { states[id] = state; onState(id, state) } }
}
