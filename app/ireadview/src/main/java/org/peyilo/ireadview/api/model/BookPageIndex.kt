package org.peyilo.ireadview.api.model

import org.peyilo.ireadview.api.state.ReaderRejection
import org.peyilo.ireadview.api.state.ReaderResult

data class IndexedChapter(
    val chapterId: String,
    val pageCount: Int,
    val contentRevision: String?,
    val layoutKey: String,
    val contentFingerprint: String?
) {
    init { require(chapterId.isNotBlank() && pageCount > 0 && layoutKey.isNotBlank()) }
}

data class BookPageIndexSummary(val bookId: String, val chapterCount: Int, val totalPageCount: Long,
    val catalogueGeneration: Long, val layoutKey: String)

/** Lightweight complete snapshot; no chapter text, Android Views or measured layouts retained. */
class BookPageIndex internal constructor(
    val bookId: String,
    val catalogueGeneration: Long,
    val layoutKey: String,
    chapters: List<IndexedChapter>
) {
    val chapters = immutableList(chapters)
    /** Pages before each chapter; first chapter starts at zero. */
    val pagesBefore: List<Long>
    val totalPageCount: Long
    private val chapterIndices = HashMap<String, Int>()
    val summary get() = BookPageIndexSummary(bookId, chapters.size, totalPageCount, catalogueGeneration, layoutKey)
    init {
        this.chapters.forEachIndexed { index, chapter ->
            require(chapterIndices.put(chapter.chapterId, index) == null) { "Duplicate chapterId" }
        }
        var total = 0L
        pagesBefore = immutableList(this.chapters.map { chapter ->
            val previous = total
            require(Long.MAX_VALUE - total >= chapter.pageCount)
            total += chapter.pageCount
            previous
        })
        totalPageCount = total
    }
    internal fun chapterForId(chapterId: String): IndexedChapter? = chapterIndices[chapterId]?.let { chapters[it] }
    fun positionForPage(pageNumber: Long): ReaderResult<ReadingPosition> {
        if (pageNumber !in 1..totalPageCount) return ReaderResult.Rejected(ReaderRejection.InvalidPage)
        var low = 0
        var high = chapters.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (pagesBefore[middle] < pageNumber) low = middle else high = middle - 1
        }
        val chapter = chapters[low]
        return ReaderResult.Success(ReadingPosition(bookId, chapter.chapterId, contentRevision = chapter.contentRevision,
            pageNumberHint = (pageNumber - pagesBefore[low]).toInt(), chapterNumberHint = low + 1))
    }
    fun pageForPosition(position: ReadingPosition): Long? {
        if (position.bookId != bookId) return null
        val index = chapterIndices[position.chapterId] ?: return null
        val chapter = chapters[index]
        if (position.contentRevision != chapter.contentRevision) return null
        val page = position.pageNumberHint ?: return null
        return if (page in 1..chapter.pageCount) pagesBefore[index] + page else null
    }
}
