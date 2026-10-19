package org.peyilo.ireadview

import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.ReaderResult
import org.peyilo.ireadview.internal.annotation.AnnotationStore

class DocumentAnnotationLifecycleTest {
    private val book = BookDescriptor("book", "Book", "revision", listOf(TocEntry.Chapter(ChapterDescriptor("chapter", "Chapter"))))
    private val selected = DocumentTextSelection("book", "chapter", "revision", "document-range", "selected words")

    @Test fun pendingDocumentRangesAreNotValidatedAgainstEmptyBlockChapters() {
        val store = AnnotationStore().apply { bind(book) }
        val mark = DocumentHighlight("id", selected)
        assertTrue(store.putAnnotation(mark, false) is ReaderResult.Success)
        store.validate(ChapterContent("book", "chapter", "revision", emptyList()))
        assertEquals(AnnotationStatus.PendingValidation, store.status("id"))
        assertTrue(store.reportStatus(mark, AnnotationStatus.Valid))
        store.validate(ChapterContent("book", "chapter", "revision", emptyList()))
        assertEquals(AnnotationStatus.Valid, store.status("id"))
    }

    @Test fun asynchronousValidationCannotApproveAReplacedOrDeletedMark() {
        val store = AnnotationStore().apply { bind(book) }
        val old = DocumentHighlight("id", selected)
        store.putAnnotation(old, false)
        val replacement = old.copy(styleId = "blue")
        store.putAnnotation(replacement, true)
        assertFalse(store.reportStatus(old, AnnotationStatus.Valid))
        assertEquals(AnnotationStatus.PendingValidation, store.status("id"))
        store.removeAnnotation("id")
        assertFalse(store.reportStatus(replacement, AnnotationStatus.Valid))
        assertNull(store.status("id"))
    }

    @Test fun oneSnapshotAndRemovalApiCoverBothSourceFormats() {
        val native = Highlight("text", TextRange("book", "chapter", "revision", ContentAnchor("p", 0), ContentAnchor("p", 4)), "text")
        val document = DocumentHighlight("html", selected)
        val line = DocumentUnderline("line", selected)
        val snapshot = AnnotationSnapshot.fromItems("book", listOf(native, document, line))
        assertEquals(listOf(native, document), snapshot.allHighlights)
        assertEquals(listOf(line), snapshot.allUnderlines)
        val store = AnnotationStore().apply { bind(book); replace(snapshot) }
        store.removeAnnotation("html")
        assertEquals(listOf(native, line), store.snapshot()!!.items)
        store.bind(BookDescriptor("other", "Other", tableOfContents = emptyList()))
        assertTrue(store.snapshot()!!.items.isEmpty())
    }
}
