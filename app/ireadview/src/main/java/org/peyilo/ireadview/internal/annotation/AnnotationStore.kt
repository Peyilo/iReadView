package org.peyilo.ireadview.internal.annotation

import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.selection.TextCharacterBoundaries

/** One id-indexed store and validation lifecycle for all annotation kinds. */
internal class AnnotationStore {
    private var book: BookDescriptor? = null
    private val items = LinkedHashMap<String, ReaderAnnotation>()
    private val statuses = LinkedHashMap<String, AnnotationStatus>()
    var revision = 0L
        private set
    fun bind(book: BookDescriptor?) {
        revision++
        if (book == null || this.book?.bookId != book.bookId) { items.clear(); statuses.clear() }
        this.book = book; markPending()
    }
    fun snapshot(): AnnotationSnapshot? = book?.let { AnnotationSnapshot.fromItems(it.bookId, items.values.toList()) }
    fun bookmark(id: String): Bookmark? = items[id] as? Bookmark
    fun status(id: String): AnnotationStatus? = statuses[id]
    fun statusSnapshot(): Map<String, AnnotationStatus> = statuses.toMap()
    fun reportStatus(item: ReaderAnnotation, status: AnnotationStatus): Boolean {
        if (items[item.id] != item || statuses[item.id] == status) return false
        statuses[item.id] = status; revision++
        return true
    }
    fun replace(snapshot: AnnotationSnapshot): ReaderResult<Unit> {
        val current = book ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        if (snapshot.bookId != current.bookId || snapshot.items.any { it.bookId != current.bookId }) return ReaderResult.Rejected(ReaderRejection.BookMismatch)
        if (snapshot.items.map { it.id }.distinct().size != snapshot.items.size) return ReaderResult.Rejected(ReaderRejection.DuplicateAnnotation)
        items.clear(); statuses.clear(); revision++
        snapshot.items.forEach { items[it.id] = it }; markPending()
        return ReaderResult.Success(Unit)
    }
    private fun <T : ReaderAnnotation> putItem(item: T, update: Boolean): ReaderResult<T> {
        val current = book ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        if (item.bookId != current.bookId) return ReaderResult.Rejected(ReaderRejection.BookMismatch)
        val previous = items[item.id]
        if (update && (previous == null || previous::class != item::class)) return ReaderResult.Rejected(ReaderRejection.UnknownAnnotation)
        if (!update && previous != null) return ReaderResult.Rejected(ReaderRejection.DuplicateAnnotation)
        items[item.id] = item; revision++; statuses[item.id] = initialStatus(itemChapterId(item))
        return ReaderResult.Success(item)
    }
    fun add(bookmark: Bookmark) = putItem(bookmark, false)
    fun put(highlight: Highlight, update: Boolean) = putItem(highlight, update)
    fun putUnderline(item: Underline, update: Boolean) = putItem(item, update)
    fun <T : ReaderAnnotation> putAnnotation(item: T, update: Boolean): ReaderResult<T> = putItem(item, update)
    private fun removeItem(id: String, matches: (ReaderAnnotation) -> Boolean): ReaderResult<Unit> {
        if (book == null) return ReaderResult.Rejected(ReaderRejection.NoBook)
        val item = items[id]
        if (item != null && matches(item)) { items.remove(id); statuses.remove(id); revision++ }
        return ReaderResult.Success(Unit)
    }
    fun remove(id: String, highlight: Boolean) = removeItem(id) { if (highlight) it is HighlightAnnotation else it is Bookmark }
    fun removeUnderline(id: String) = removeItem(id) { it is UnderlineAnnotation }
    fun removeAnnotation(id: String) = removeItem(id) { true }
    fun markPending(chapterId: String? = null) {
        revision++
        items.values.filter { chapterId == null || itemChapterId(it) == chapterId }.forEach { statuses[it.id] = initialStatus(itemChapterId(it)) }
    }
    private fun itemChapterId(item: ReaderAnnotation): String = when (item) {
        is Bookmark -> item.position.chapterId
        is RangedAnnotation -> item.range.chapterId
        is DocumentRangedAnnotation -> item.selection.chapterId
    }
    fun validate(content: ChapterContent): Map<String, AnnotationStatus> {
        if (content.bookId != book?.bookId) return emptyMap()
        val changes = LinkedHashMap<String, AnnotationStatus>()
        for (item in items.values) {
            if (itemChapterId(item) != content.chapterId) continue
            val status = when (item) {
                is DocumentRangedAnnotation -> continue // Document renderer validates actual DOM ranges and quotes.
                is RangedAnnotation -> validateRange(item, content)
                is Bookmark -> validateBookmark(item, content)
            }
            if (statuses[item.id] != status) { statuses[item.id] = status; changes[item.id] = status; revision++ }
        }
        return changes
    }
    private fun validateBookmark(item: Bookmark, content: ChapterContent): AnnotationStatus {
        val position = item.position
        if (position.contentRevision != null && position.contentRevision != content.revision) return AnnotationStatus.Invalid(AnnotationInvalidReason.RevisionMismatch)
        val anchor = position.anchor ?: return AnnotationStatus.Valid
        val block = content.blockForId(anchor.blockId) ?: return AnnotationStatus.Invalid(AnnotationInvalidReason.InvalidRange)
        val value = text(block) ?: return AnnotationStatus.Invalid(AnnotationInvalidReason.UnsupportedContent)
        return if (TextCharacterBoundaries.isBoundary(value, anchor.offset)) AnnotationStatus.Valid else AnnotationStatus.Invalid(AnnotationInvalidReason.InvalidRange)
    }
    private fun validateRange(item: RangedAnnotation, content: ChapterContent): AnnotationStatus {
        val range = item.range
        fun invalid(reason: AnnotationInvalidReason) = AnnotationStatus.Invalid(reason)
        if (range.contentRevision != null && range.contentRevision != content.revision) return invalid(AnnotationInvalidReason.RevisionMismatch)
        val start = content.blockIndexForId(range.start.blockId)
        val end = content.blockIndexForId(range.endExclusive.blockId)
        if (start < 0 || end < start) return invalid(AnnotationInvalidReason.InvalidRange)
        var quoteOffset = 0
        for (index in start..end) {
            val value = text(content.blocks[index]) ?: return invalid(AnnotationInvalidReason.UnsupportedContent)
            val from = if (index == start) range.start.offset else 0
            val to = if (index == end) range.endExclusive.offset else value.length
            if (!TextCharacterBoundaries.isRange(value, from, to)) return invalid(AnnotationInvalidReason.InvalidRange)
            if (index != start) {
                if (quoteOffset >= item.quote.length || item.quote[quoteOffset] != '\n') return invalid(AnnotationInvalidReason.QuoteMismatch)
                quoteOffset++
            }
            val length = to - from
            if (length > item.quote.length - quoteOffset || !item.quote.regionMatches(quoteOffset, value, from, length)) return invalid(AnnotationInvalidReason.QuoteMismatch)
            quoteOffset += length
        }
        if (quoteOffset == 0) return invalid(AnnotationInvalidReason.InvalidRange)
        if (quoteOffset != item.quote.length) return invalid(AnnotationInvalidReason.QuoteMismatch)
        return AnnotationStatus.Valid
    }

    private fun initialStatus(chapterId: String): AnnotationStatus = if (book?.chapterForId(chapterId) != null) AnnotationStatus.PendingValidation else AnnotationStatus.Invalid(AnnotationInvalidReason.UnknownChapter)
    private fun text(block: ContentBlock?): String? = when (block) { is TextContentBlock -> block.text; else -> null }
}
