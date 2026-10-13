package org.peyilo.ireadview

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.source.MemoryBookSource

class BookModelsTest {
    @Test fun catalogueAndContentDoNotRetainMutableHostCollections() {
        val hostEntries = mutableListOf<TocEntry>(TocEntry.Chapter(ChapterDescriptor("one", "One")))
        val book = BookDescriptor("book", "Book", tableOfContents = hostEntries)
        hostEntries.clear()
        assertEquals("one", book.chapters.single().chapterId)
        val blocks = mutableListOf<ContentBlock>(TextBlock("p", "Original"))
        val content = ChapterContent("book", "one", blocks = blocks)
        blocks.clear()
        assertEquals("Original", (content.blocks.single() as TextBlock).text)
        try {
            (content.blocks as MutableList).clear()
            fail("Exported collection must not be mutable")
        } catch (_: UnsupportedOperationException) { }
    }

    @Test fun groupsFlattenInSourceOrderAndRejectDuplicateChapterIds() {
        val chapters = listOf(ChapterDescriptor("a", "A"), ChapterDescriptor("b", "B"), ChapterDescriptor("c", "C"))
        val entries = listOf(TocEntry.Group("part", "Part", chapters.take(2).map { TocEntry.Chapter(it) }), TocEntry.Chapter(chapters.last()))
        assertEquals(chapters, BookDescriptor("book", "Book", tableOfContents = entries).chapters)
        assertThrows(IllegalArgumentException::class.java) {
            BookDescriptor("book", "Book", tableOfContents = listOf(TocEntry.Chapter(chapters.first()), TocEntry.Chapter(chapters.first())))
        }
    }

    @Test fun memorySourceUpdatesAreExplicitAndCannotCrossBookIdentities() = runTest {
        val book = BookDescriptor("book", "Book", tableOfContents = listOf(TocEntry.Chapter(ChapterDescriptor("one", "One"))))
        val old = ChapterContent("book", "one", "1", listOf(TextBlock("p", "Old")))
        val updated = ChapterContent("book", "one", "2", listOf(TextBlock("p", "New")))
        val source = MemoryBookSource(book, listOf(old))
        assertSame(old, source.loadChapter("one"))
        source.replaceChapter(updated)
        assertSame(updated, source.loadChapter("one"))
        assertThrows(IllegalArgumentException::class.java) { source.replaceChapter(ChapterContent("other", "one", blocks = emptyList())) }
    }
}
