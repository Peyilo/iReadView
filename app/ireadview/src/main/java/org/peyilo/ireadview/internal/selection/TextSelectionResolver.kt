package org.peyilo.ireadview.internal.selection

import java.text.BreakIterator
import java.util.Locale
import org.peyilo.ireadview.api.model.*

/** Uses platform boundaries; offsets always refer to the original, unchanged UTF-16 text. */
internal object TextSelectionResolver {
    /** Restore the complete stored source range, never just its visible page slice. */
    fun fromHighlight(content: ChapterContent, highlight: Highlight): TextSelection? {
        val range = highlight.range
        if (range.bookId != content.bookId || range.chapterId != content.chapterId ||
            range.contentRevision != null && range.contentRevision != content.revision) return null
        val current = range.copy(contentRevision = content.revision)
        if (!matches(content, current, highlight.quote)) return null
        return TextSelection(current, highlight.quote)
    }
    /** A visible accessibility slice must name the exact source text, not just its length. */
    fun matchesBlockRange(content: ChapterContent, range: org.peyilo.ireadview.spi.content.BlockTextRange,
        visibleText: String): Boolean {
        val text = (content.blockForId(range.blockId) as? TextContentBlock)?.text ?: return false
        if (range.endExclusive - range.startOffset != visibleText.length ||
            !TextCharacterBoundaries.isRange(text, range.startOffset, range.endExclusive)) return false
        return text.regionMatches(range.startOffset, visibleText, 0, visibleText.length)
    }

    fun compareAnchors(content: ChapterContent, first: ContentAnchor, second: ContentAnchor): Int? {
        val left = content.blockIndexForId(first.blockId)
        val right = content.blockIndexForId(second.blockId)
        if (left < 0 || right < 0) return null
        return if (left == right) first.offset.compareTo(second.offset) else left.compareTo(right)
    }
    fun fromAnchors(content: ChapterContent, fixed: ContentAnchor, moving: ContentAnchor): TextSelection? {
        fun valid(anchor: ContentAnchor): Boolean {
            val text = when (val block = content.blockForId(anchor.blockId)) {
                is TextContentBlock -> block.text
                else -> return false
            }
            return TextCharacterBoundaries.isBoundary(text, anchor.offset)
        }
        if (!valid(fixed) || !valid(moving)) return null
        val forward = (compareAnchors(content, fixed, moving) ?: return null) < 0
        val range = TextRange(content.bookId, content.chapterId, content.revision,
            if (forward) fixed else moving, if (forward) moving else fixed)
        return extract(content, range)?.let { TextSelection(range, it) }
    }
    fun fromBlockRange(content: ChapterContent, range: org.peyilo.ireadview.spi.content.BlockTextRange): TextSelection? {
        val text = when (val block = content.blockForId(range.blockId)) {
            is TextContentBlock -> block.text
            else -> return null
        }
        if (range.startOffset >= range.endExclusive || range.endExclusive > text.length) return null
        if (!TextCharacterBoundaries.isRange(text, range.startOffset, range.endExclusive)) return null
        val sourceRange = TextRange(content.bookId, content.chapterId, content.revision,
            ContentAnchor(range.blockId, range.startOffset), ContentAnchor(range.blockId, range.endExclusive))
        return extract(content, sourceRange)?.let { TextSelection(sourceRange, it) }
    }
    fun extend(content: ChapterContent, selection: TextSelection, anchor: ContentAnchor, startHandle: Boolean): TextSelection? {
        val block = content.blockForId(anchor.blockId) ?: return null
        val text = when (block) { is TextContentBlock -> block.text; else -> return null }
        if (anchor.offset > text.length) return null
        val boundaries = BreakIterator.getCharacterInstance(Locale.getDefault()).apply { setText(text) }
        val offset = if (boundaries.isBoundary(anchor.offset)) anchor.offset else
            (if (startHandle) boundaries.preceding(anchor.offset) else boundaries.following(anchor.offset)).takeIf { it != BreakIterator.DONE } ?: return null
        val moving = ContentAnchor(anchor.blockId, offset)
        val start = if (startHandle) moving else selection.range.start
        val end = if (startHandle) selection.range.endExclusive else moving
        val range = selection.range.copy(start = start, endExclusive = end)
        val value = extract(content, range) ?: return null
        return TextSelection(range, value)
    }
    fun extract(content: ChapterContent, range: TextRange): String? {
        val result = StringBuilder()
        if (!visitRange(content, range) { value, start, end, separated ->
            if (separated) result.append('\n')
            result.append(value, start, end)
        }) return null
        return result.toString().takeIf { it.isNotEmpty() }
    }
    /** Checks an existing quote without allocating a second copy of the selected text. */
    fun matches(content: ChapterContent, range: TextRange, quote: String): Boolean {
        if (quote.isEmpty()) return false
        var offset = 0
        if (!visitRange(content, range) { value, start, end, separated ->
            if (separated) {
                if (offset >= quote.length || quote[offset] != '\n') return false
                offset++
            }
            val length = end - start
            if (length > quote.length - offset || !quote.regionMatches(offset, value, start, length)) return false
            offset += length
        }) return false
        return offset == quote.length
    }
    private inline fun visitRange(content: ChapterContent, range: TextRange,
        visit: (String, Int, Int, Boolean) -> Unit): Boolean {
        if (range.bookId != content.bookId || range.chapterId != content.chapterId || range.contentRevision != content.revision) return false
        val first = content.blockIndexForId(range.start.blockId)
        val last = content.blockIndexForId(range.endExclusive.blockId)
        if (first < 0 || last < first) return false
        for (index in first..last) {
            val value = when (val block = content.blocks[index]) { is TextContentBlock -> block.text; else -> return false }
            val start = if (index == first) range.start.offset else 0
            val end = if (index == last) range.endExclusive.offset else value.length
            if (!TextCharacterBoundaries.isRange(value, start, end)) return false
            visit(value, start, end, index != first)
        }
        return true
    }
    fun wordAt(content: ChapterContent, anchor: ContentAnchor): TextSelection? {
        val block = content.blockForId(anchor.blockId) ?: return null
        val text = when (block) { is TextContentBlock -> block.text; else -> return null }
        if (text.isEmpty() || anchor.offset > text.length) return null
        val offset = anchor.offset.coerceAtMost(text.length - 1)
        val words = BreakIterator.getWordInstance(Locale.getDefault()).apply { setText(text) }
        var start = words.preceding(offset + 1).takeIf { it != BreakIterator.DONE } ?: 0
        var end = words.following(offset).takeIf { it != BreakIterator.DONE } ?: text.length
        val characters = BreakIterator.getCharacterInstance(Locale.getDefault()).apply { setText(text) }
        if (!characters.isBoundary(start)) start = characters.preceding(start).takeIf { it != BreakIterator.DONE } ?: 0
        if (!characters.isBoundary(end)) end = characters.following(end).takeIf { it != BreakIterator.DONE } ?: text.length
        if (start >= end || text.substring(start, end).isBlank()) return null
        return TextSelection(TextRange(content.bookId, content.chapterId, content.revision, ContentAnchor(block.blockId, start), ContentAnchor(block.blockId, end)), text.substring(start, end))
    }
    /** Source paragraph, including its invisible continuation on other pages. Never spans blocks. */
    fun paragraphAt(content: ChapterContent, anchor: ContentAnchor): TextSelection? {
        val block = content.blockForId(anchor.blockId) as? TextContentBlock ?: return null
        val text = block.text
        if (text.isEmpty() || anchor.offset !in 0..text.length) return null
        val offset = anchor.offset.coerceAtMost(text.lastIndex)
        if (text[offset] == '\n' || text[offset] == '\r') return null
        val start = text.lastIndexOfAny(charArrayOf('\n', '\r'), offset - 1) + 1
        val end = text.indexOfAny(charArrayOf('\n', '\r'), offset).let { if (it < 0) text.length else it }
        if (start >= end || text.substring(start, end).isBlank()) return null
        return TextSelection(TextRange(content.bookId, content.chapterId, content.revision,
            ContentAnchor(block.blockId, start), ContentAnchor(block.blockId, end)), text.substring(start, end))
    }
}
