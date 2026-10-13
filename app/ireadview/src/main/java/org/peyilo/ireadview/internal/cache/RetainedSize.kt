package org.peyilo.ireadview.internal.cache

import org.peyilo.ireadview.api.model.*

/** Conservative cache weights, not heap measurements or a cap on the displayed working set. */
internal object RetainedSize {
    fun add(first: Long, second: Long): Long {
        require(first >= 0 && second >= 0)
        return if (first > Long.MAX_VALUE - second) Long.MAX_VALUE else first + second
    }
    fun text(value: String): Long = 48L + value.length.toLong() * 2
    fun content(content: ChapterContent): Long? = content(content) { }
    fun content(content: ChapterContent, checkCancellation: () -> Unit): Long? {
        checkCancellation()
        var bytes = add(256, add(text(content.bookId), text(content.chapterId)))
        content.revision?.let { bytes = add(bytes, text(it)) }
        for (block in content.blocks) {
            checkCancellation()
            val estimate = block.estimatedRetainedBytes
            checkCancellation()
            val weight = estimate?.takeIf { it >= 0 } ?: return null
            // List reference plus the chapter's ID-to-index entry, table and boxed index.
            bytes = add(bytes, add(72, weight))
        }
        checkCancellation()
        return bytes
    }
}
