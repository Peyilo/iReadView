package org.peyilo.ireadview.api.model

data class TextRange(
    val bookId: String,
    val chapterId: String,
    val contentRevision: String? = null,
    val start: ContentAnchor,
    val endExclusive: ContentAnchor
) {
    init { require(bookId.isNotBlank() && chapterId.isNotBlank()) }
}

data class Bookmark(val id: String, val position: ReadingPosition, val label: String? = null) {
    init { require(id.isNotBlank()) }
}

data class Highlight(val id: String, val range: TextRange, val quote: String, val styleId: String = "default") {
    init { require(id.isNotBlank() && styleId.isNotBlank()) }
}

data class TextSelection(val range: TextRange, val text: String)

interface AnnotationIdFactory {
    fun newBookmarkId(): String
    fun newHighlightId(): String
}

/** A persistence-neutral snapshot; collections cannot be mutated by either side. */
class AnnotationSnapshot(val bookId: String, bookmarks: List<Bookmark> = emptyList(), highlights: List<Highlight> = emptyList()) {
    val bookmarks: List<Bookmark> = immutableList(bookmarks)
    val highlights: List<Highlight> = immutableList(highlights)
    init { require(bookId.isNotBlank()) }
}

enum class AnnotationInvalidReason { UnknownChapter, RevisionMismatch, InvalidRange, QuoteMismatch, UnsupportedContent }
sealed interface AnnotationStatus {
    data object PendingValidation : AnnotationStatus
    data object Valid : AnnotationStatus
    data class Invalid(val reason: AnnotationInvalidReason) : AnnotationStatus
}
