package org.peyilo.ireadview.internal.source

import java.io.IOException
import org.peyilo.ireadview.api.source.TextBlockIdentity
import org.peyilo.ireadview.api.source.TextChapterIdentity
import org.peyilo.ireadview.api.source.TextContentIdProvider

/** Per-build validation shared by memory and disk parsing; never retained by snapshots. */
internal class TextContentIdentity(private val bookId: String, private val provider: TextContentIdProvider?) {
    private var chapterNumber = 1
    private var blockNumber = 0
    private val chapters = HashSet<String>()
    private val blocks = HashSet<String>()

    fun blockId(text: String, heading: Boolean): String {
        val number = ++blockNumber
        val id = if (provider == null) "line-$number"
            else provider.blockId(TextBlockIdentity(bookId, chapterNumber, number, text, heading))
        if (!validId(id) || (provider != null && !blocks.add(id))) throw IOException("Invalid or duplicate text block ID in chapter $chapterNumber")
        return id
    }

    fun finishChapter(title: String): String {
        val id = if (provider == null) "chapter-$chapterNumber"
            else provider.chapterId(TextChapterIdentity(bookId, chapterNumber, title))
        if (!validId(id) || (provider != null && !chapters.add(id))) throw IOException("Invalid or duplicate text chapter ID at chapter $chapterNumber")
        chapterNumber++
        blockNumber = 0
        blocks.clear()
        return id
    }

    private fun validId(id: String): Boolean {
        if (id.isBlank()) return false
        var index = 0
        while (index < id.length) {
            val character = id[index++]
            if (Character.isHighSurrogate(character)) {
                if (index == id.length || !Character.isLowSurrogate(id[index++])) return false
            } else if (Character.isLowSurrogate(character)) return false
        }
        return true
    }
}
