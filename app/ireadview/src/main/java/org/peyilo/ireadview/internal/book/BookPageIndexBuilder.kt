package org.peyilo.ireadview.internal.book

import kotlinx.coroutines.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.spi.presentation.ChapterLayout

/** Main-dispatcher bookkeeping; same-input callers share a build until its last consumer leaves. */
internal class BookPageIndexBuilder(private val onState: (BookPageIndexState) -> Unit) {
    var state: BookPageIndexState = BookPageIndexState.Unavailable
        private set
    var index: BookPageIndex? = null
        private set
    private var key: Any? = null
    private var request: Deferred<ReaderResult<BookPageIndex>>? = null
    private val consumers = HashMap<Deferred<ReaderResult<BookPageIndex>>, Int>()
    private val completed = HashMap<String, IndexedChapter>()
    private var generation = 0L

    fun invalidate(): Long {
        val token = ++generation
        val old = request
        request = null; key = null; index = null; completed.clear()
        old?.cancel(IndexInvalidated())
        if (generation == token && request == null && index == null) publish(BookPageIndexState.Unavailable)
        return token
    }
    fun observe(content: ChapterContent) {
        val previous = completed[content.chapterId] ?: return
        if (previous.contentRevision != content.revision ||
            previous.contentFingerprint != content.contentFingerprint ||
            content.revision == null && content.contentFingerprint == null) invalidate()
    }
    suspend fun build(book: BookDescriptor, input: Any, catalogueGeneration: Long, scope: CoroutineScope,
        layout: suspend (String) -> ReaderResult<ChapterLayout>): ReaderResult<BookPageIndex> {
        if (key == input) {
            index?.let { return ReaderResult.Success(it) }
            request?.takeIf { it.isActive && state !is BookPageIndexState.Failed }?.let { return consume(it) }
        }
        val token = invalidate()
        if (token != generation) return ReaderResult.Superseded
        key = input
        lateinit var task: Deferred<ReaderResult<BookPageIndex>>
        fun current() = token == generation && request === task
        task = scope.async(start = CoroutineStart.LAZY) {
            try {
                publish(BookPageIndexState.Building(0, book.chapters.size))
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                val chapters = ArrayList<IndexedChapter>()
                for ((number, chapter) in book.chapters.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val result = layout(chapter.chapterId)
                    currentCoroutineContext().ensureActive()
                    if (!current()) return@async ReaderResult.Superseded
                    val measured = when (result) {
                        is ReaderResult.Success -> result.value
                        is ReaderResult.Failure -> {
                            publish(BookPageIndexState.Failed(result.error))
                            currentCoroutineContext().ensureActive()
                            if (!current()) return@async ReaderResult.Superseded
                            return@async result
                        }
                        is ReaderResult.Rejected -> return@async result
                        ReaderResult.Superseded -> return@async ReaderResult.Superseded
                    }
                    currentCoroutineContext().ensureActive()
                    if (!current()) return@async ReaderResult.Superseded
                    val snapshot = measured.contentSnapshot ?: return@async ReaderResult.Rejected(ReaderRejection.Unsupported)
                    val entry = IndexedChapter(chapter.chapterId, measured.pageCount, measured.contentRevision, measured.layoutKey, snapshot.contentFingerprint)
                    chapters.add(entry); completed[chapter.chapterId] = entry
                    publish(BookPageIndexState.Building(number + 1, book.chapters.size))
                    yield()
                }
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                val built = BookPageIndex(book.bookId, catalogueGeneration, "index:$catalogueGeneration:$token", chapters)
                index = built
                publish(BookPageIndexState.Ready(built.summary))
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                ReaderResult.Success(built)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                val failure = ReaderError(ReaderStage.LAYOUT, "PageIndexFailed", error.message ?: "Cannot build book page index", cause = error)
                publish(BookPageIndexState.Failed(failure))
                currentCoroutineContext().ensureActive()
                if (!current()) return@async ReaderResult.Superseded
                ReaderResult.Failure(failure)
            } finally {
                if (current() && index == null && state !is BookPageIndexState.Failed) publish(BookPageIndexState.Unavailable)
            }
        }
        request = task
        task.invokeOnCompletion { if (request === task) request = null }
        return consume(task)
    }
    private suspend fun consume(task: Deferred<ReaderResult<BookPageIndex>>): ReaderResult<BookPageIndex> {
        consumers[task] = (consumers[task] ?: 0) + 1
        try { return task.await() }
        catch (_: IndexInvalidated) { return ReaderResult.Superseded }
        finally {
            val count = (consumers[task] ?: 1) - 1
            if (count == 0) { consumers.remove(task); if (task.isActive) task.cancel() }
            else consumers[task] = count
        }
    }
    private fun publish(value: BookPageIndexState) { if (state != value) { state = value; onState(value) } }
    private class IndexInvalidated : CancellationException("Book page index invalidated")
}
