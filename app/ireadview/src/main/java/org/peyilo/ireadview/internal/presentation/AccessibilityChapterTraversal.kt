package org.peyilo.ireadview.internal.presentation

import kotlinx.coroutines.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.render.AccessibilitySourceTraversal
import org.peyilo.ireadview.internal.render.AccessibilityTraversalBoundary

/** Sequential, cancellable source borrowing; intermediate empty chapters never commit a position. */
internal object AccessibilityChapterTraversal {
    suspend fun resolve(book: BookDescriptor, initial: ChapterContent, boundary: AccessibilityTraversalBoundary,
        forward: Boolean, granularity: Int, isCurrent: () -> Boolean,
        getChapter: suspend (String) -> ReaderResult<ChapterContent>): ReaderResult<AccessibilitySourceTraversal.Result> {
        var index = book.chapters.indexOfFirst { it.chapterId == initial.chapterId }
        if (initial.bookId != book.bookId || index < 0) return ReaderResult.Rejected(ReaderRejection.InvalidAnchor)
        var content = initial
        var anchor: AccessibilityTraversalBoundary? = boundary
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) return ReaderResult.Superseded
            val result = withContext(Dispatchers.Default) {
                val work = currentCoroutineContext()
                AccessibilitySourceTraversal.find(content, anchor, forward, granularity) { work.ensureActive() }
            }
            if (!isCurrent()) return ReaderResult.Superseded
            if (result != AccessibilitySourceTraversal.Result.Exhausted) return ReaderResult.Success(result)
            index += if (forward) 1 else -1
            if (index !in book.chapters.indices) return ReaderResult.Success(result)
            yield()
            if (!isCurrent()) return ReaderResult.Superseded
            val loaded = getChapter(book.chapters[index].chapterId)
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) return ReaderResult.Superseded
            content = when (loaded) {
                is ReaderResult.Success -> loaded.value
                is ReaderResult.Failure -> return loaded
                is ReaderResult.Rejected -> return loaded
                ReaderResult.Superseded -> return ReaderResult.Superseded
            }
            if (content.bookId != book.bookId || content.chapterId != book.chapters[index].chapterId)
                return ReaderResult.Rejected(ReaderRejection.InvalidAnchor)
            anchor = null
        }
    }
}
