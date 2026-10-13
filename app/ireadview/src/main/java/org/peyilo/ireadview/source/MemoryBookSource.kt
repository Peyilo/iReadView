package org.peyilo.ireadview.source

import org.peyilo.ireadview.api.model.BookDescriptor
import org.peyilo.ireadview.api.model.ChapterContent
import org.peyilo.ireadview.api.source.BookSource

/** Immutable snapshots under a short lock; changing it does not implicitly update a Widget. */
class MemoryBookSource(book: BookDescriptor, chapters: Collection<ChapterContent>) : BookSource {
    override val bookId: String = book.bookId
    private val lock = Any()
    private var catalogue = book
    private val contents = LinkedHashMap<String, ChapterContent>()

    init {
        require(chapters.map { it.chapterId }.toSet().size == chapters.size)
        chapters.forEach { require(it.bookId == bookId); contents[it.chapterId] = it }
    }

    override suspend fun loadBook(): BookDescriptor = synchronized(lock) { catalogue }
    override suspend fun loadChapter(chapterId: String): ChapterContent = synchronized(lock) {
        contents[chapterId] ?: throw NoSuchElementException("Chapter not supplied: $chapterId")
    }

    fun replaceBook(book: BookDescriptor) = synchronized(lock) {
        require(book.bookId == bookId)
        catalogue = book
        val validIds = book.chapters.map { it.chapterId }.toSet()
        contents.keys.retainAll(validIds)
    }

    fun replaceChapter(content: ChapterContent) = synchronized(lock) {
        require(content.bookId == bookId)
        require(catalogue.chapters.any { it.chapterId == content.chapterId })
        contents[content.chapterId] = content
    }
}
