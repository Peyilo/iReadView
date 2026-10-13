package org.peyilo.ireadview.internal.annotation

import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.selection.TextCharacterBoundaries

/** Main-thread session data. Full chapter content is borrowed for validation, never retained. */
internal class AnnotationStore {
    private var book: BookDescriptor? = null
    private val bookmarks = LinkedHashMap<String, Bookmark>()
    private val highlights = LinkedHashMap<String, Highlight>()
    private val statuses = LinkedHashMap<String, AnnotationStatus>()
    var revision = 0L
        private set

    fun bind(book: BookDescriptor?) {
        revision++
        if (book == null || this.book?.bookId != book.bookId) {
            bookmarks.clear(); highlights.clear(); statuses.clear()
        }
        this.book = book
        markPending()
    }

    fun snapshot(): AnnotationSnapshot? = book?.let { AnnotationSnapshot(it.bookId, bookmarks.values.toList(), highlights.values.toList()) }
    fun bookmark(id: String): Bookmark? = bookmarks[id]
    fun status(id: String): AnnotationStatus? = statuses[id]
    fun statusSnapshot(): Map<String, AnnotationStatus> = statuses.toMap()

    fun replace(snapshot: AnnotationSnapshot): ReaderResult<Unit> {
        val book = book ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        if (snapshot.bookId != book.bookId || snapshot.bookmarks.any { it.position.bookId != book.bookId } || snapshot.highlights.any { it.range.bookId != book.bookId }) return ReaderResult.Rejected(ReaderRejection.BookMismatch)
        val ids = HashSet<String>()
        for (item in snapshot.bookmarks) if (!ids.add(item.id)) return ReaderResult.Rejected(ReaderRejection.DuplicateAnnotation)
        for (item in snapshot.highlights) if (!ids.add(item.id)) return ReaderResult.Rejected(ReaderRejection.DuplicateAnnotation)
        bookmarks.clear(); highlights.clear(); statuses.clear()
        revision++
        snapshot.bookmarks.forEach { bookmarks[it.id] = it }
        snapshot.highlights.forEach { highlights[it.id] = it }
        markPending()
        return ReaderResult.Success(Unit)
    }

    fun add(bookmark: Bookmark): ReaderResult<Bookmark> {
        val book = book ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        if (bookmark.position.bookId != book.bookId) return ReaderResult.Rejected(ReaderRejection.BookMismatch)
        if (bookmark.id in statuses) return ReaderResult.Rejected(ReaderRejection.DuplicateAnnotation)
        bookmarks[bookmark.id] = bookmark
        revision++
        statuses[bookmark.id] = initialStatus(bookmark.position.chapterId)
        return ReaderResult.Success(bookmark)
    }

    fun put(highlight: Highlight, update: Boolean): ReaderResult<Highlight> {
        val book = book ?: return ReaderResult.Rejected(ReaderRejection.NoBook)
        if (highlight.range.bookId != book.bookId) return ReaderResult.Rejected(ReaderRejection.BookMismatch)
        if (update && highlight.id !in highlights) return ReaderResult.Rejected(ReaderRejection.UnknownAnnotation)
        if (!update && highlight.id in statuses) return ReaderResult.Rejected(ReaderRejection.DuplicateAnnotation)
        highlights[highlight.id] = highlight
        revision++
        statuses[highlight.id] = initialStatus(highlight.range.chapterId)
        return ReaderResult.Success(highlight)
    }

    fun remove(id: String, highlight: Boolean): ReaderResult<Unit> {
        if (book == null) return ReaderResult.Rejected(ReaderRejection.NoBook)
        val removed = if (highlight) highlights.remove(id) else bookmarks.remove(id)
        if (removed != null) { statuses.remove(id); revision++ }
        return ReaderResult.Success(Unit)
    }

    fun markPending(chapterId: String? = null) {
        revision++
        for (item in bookmarks.values) {
            if (chapterId == null || item.position.chapterId == chapterId) statuses[item.id] = initialStatus(item.position.chapterId)
        }
        for (item in highlights.values) {
            if (chapterId == null || item.range.chapterId == chapterId) statuses[item.id] = initialStatus(item.range.chapterId)
        }
    }

    fun validate(content: ChapterContent): Map<String, AnnotationStatus> {
        if (content.bookId != book?.bookId) return emptyMap()
        val changes = LinkedHashMap<String, AnnotationStatus>()
        fun record(id: String, status: AnnotationStatus) {
            if (statuses[id] != status) { statuses[id] = status; changes[id] = status; revision++ }
        }
        for (item in bookmarks.values) {
            if (item.position.chapterId != content.chapterId) continue
            val position = item.position
            val anchor = position.anchor
            val status = when {
                position.contentRevision != null && position.contentRevision != content.revision -> AnnotationStatus.Invalid(AnnotationInvalidReason.RevisionMismatch)
                anchor == null -> AnnotationStatus.Valid
                else -> {
                    val block = content.blockForId(anchor.blockId)
                    val text = text(block)
                    when {
                        block == null -> AnnotationStatus.Invalid(AnnotationInvalidReason.InvalidRange)
                        text == null -> AnnotationStatus.Invalid(AnnotationInvalidReason.UnsupportedContent)
                        !TextCharacterBoundaries.isBoundary(text, anchor.offset) -> AnnotationStatus.Invalid(AnnotationInvalidReason.InvalidRange)
                        else -> AnnotationStatus.Valid
                    }
                }
            }
            record(item.id, status)
        }
        for (item in highlights.values) {
            if (item.range.chapterId == content.chapterId) record(item.id, validateHighlight(item, content))
        }
        return changes
    }

    private fun validateHighlight(item: Highlight, content: ChapterContent): AnnotationStatus {
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
