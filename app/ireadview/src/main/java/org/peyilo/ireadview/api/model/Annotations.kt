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

/** Common identity for all built-in annotations. New kinds share storage and lifecycle. */
sealed interface ReaderAnnotation { val id: String; val bookId: String }
sealed interface RangedAnnotation : ReaderAnnotation {
    val range: TextRange
    val quote: String
    override val bookId: String get() = range.bookId
}
data class Bookmark(override val id: String, val position: ReadingPosition, val label: String? = null) : ReaderAnnotation {
    override val bookId get() = position.bookId
    init { require(id.isNotBlank()) }
}

data class Highlight(override val id: String, override val range: TextRange, override val quote: String, val styleId: String = "default") : RangedAnnotation {
    init { require(id.isNotBlank() && styleId.isNotBlank()) }
}

data class TextSelection(val range: TextRange, val text: String)

/** A line annotation independent from a background Highlight, with per-annotation appearance. */
data class Underline(override val id: String, override val range: TextRange, override val quote: String,
    val style: org.peyilo.ireadview.api.style.TextLineStyle = org.peyilo.ireadview.api.style.TextLineStyle()) : RangedAnnotation {
    init { require(id.isNotBlank()) }
}

interface AnnotationIdFactory {
    fun newBookmarkId(): String
    fun newHighlightId(): String
}

/** A persistence-neutral snapshot; collections cannot be mutated by either side. */
class AnnotationSnapshot private constructor(val bookId: String, annotations: List<ReaderAnnotation>, @Suppress("UNUSED_PARAMETER") canonical: Unit) {
    val items: List<ReaderAnnotation> = immutableList(annotations)
    /** Typed views retain the earlier host API; items is the canonical collection. */
    val bookmarks: List<Bookmark> = immutableList(items.filterIsInstance<Bookmark>())
    val highlights: List<Highlight> = immutableList(items.filterIsInstance<Highlight>())
    val underlines: List<Underline> = immutableList(items.filterIsInstance<Underline>())
    @JvmOverloads constructor(bookId: String, bookmarks: List<Bookmark> = emptyList(), highlights: List<Highlight> = emptyList(), underlines: List<Underline> = emptyList()) :
        this(bookId, bookmarks + highlights + underlines, Unit)
    companion object {
        @JvmStatic fun fromItems(bookId: String, items: List<ReaderAnnotation>): AnnotationSnapshot =
            AnnotationSnapshot(bookId, items, Unit)
    }
    init { require(bookId.isNotBlank()) }
}

enum class AnnotationInvalidReason { UnknownChapter, RevisionMismatch, InvalidRange, QuoteMismatch, UnsupportedContent }
sealed interface AnnotationStatus {
    data object PendingValidation : AnnotationStatus
    data object Valid : AnnotationStatus
    data class Invalid(val reason: AnnotationInvalidReason) : AnnotationStatus
}
