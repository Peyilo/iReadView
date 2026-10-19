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
/** Format-independent annotation views. Source-specific range types remain available on subtypes. */
sealed interface HighlightAnnotation : ReaderAnnotation { val quote: String; val styleId: String }
sealed interface UnderlineAnnotation : ReaderAnnotation { val quote: String; val style: org.peyilo.ireadview.api.style.TextLineStyle }
sealed interface RangedAnnotation : ReaderAnnotation {
    val range: TextRange
    val quote: String
    override val bookId: String get() = range.bookId
}
data class Bookmark(override val id: String, val position: ReadingPosition, val label: String? = null) : ReaderAnnotation {
    override val bookId get() = position.bookId
    init { require(id.isNotBlank()) }
}

data class Highlight(override val id: String, override val range: TextRange, override val quote: String, override val styleId: String = "default") : RangedAnnotation, HighlightAnnotation {
    init { require(id.isNotBlank() && styleId.isNotBlank()) }
}

/** Shared toolbar payload. Source-text ranges and document locations retain their real semantics. */
interface ReaderSelection {
    val bookId: String
    val chapterId: String
    val text: String
}

sealed interface DocumentRangedAnnotation : ReaderAnnotation {
    val selection: DocumentTextSelection
    override val bookId: String get() = selection.bookId
    val quote: String get() = selection.text
}
data class DocumentHighlight(override val id: String, override val selection: DocumentTextSelection,
    override val styleId: String = "default") : DocumentRangedAnnotation, HighlightAnnotation {
    override val quote: String get() = selection.text
    init { require(id.isNotBlank() && styleId.isNotBlank()) }
}
data class DocumentUnderline(override val id: String, override val selection: DocumentTextSelection,
    override val style: org.peyilo.ireadview.api.style.TextLineStyle = org.peyilo.ireadview.api.style.TextLineStyle()) : DocumentRangedAnnotation, UnderlineAnnotation {
    override val quote: String get() = selection.text
    init { require(id.isNotBlank()) }
}
data class TextSelection(val range: TextRange, override val text: String) : ReaderSelection {
    override val bookId get() = range.bookId
    override val chapterId get() = range.chapterId
}
data class DocumentTextSelection(
    override val bookId: String,
    override val chapterId: String,
    val contentRevision: String?,
    val documentLocator: String,
    override val text: String
) : ReaderSelection {
    init { require(bookId.isNotBlank() && chapterId.isNotBlank() && documentLocator.isNotBlank() && text.isNotEmpty()) }
}

/** A line annotation independent from a background Highlight, with per-annotation appearance. */
data class Underline(override val id: String, override val range: TextRange, override val quote: String,
    override val style: org.peyilo.ireadview.api.style.TextLineStyle = org.peyilo.ireadview.api.style.TextLineStyle()) : RangedAnnotation, UnderlineAnnotation {
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
    val allHighlights: List<HighlightAnnotation> = immutableList(items.filterIsInstance<HighlightAnnotation>())
    val allUnderlines: List<UnderlineAnnotation> = immutableList(items.filterIsInstance<UnderlineAnnotation>())
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
