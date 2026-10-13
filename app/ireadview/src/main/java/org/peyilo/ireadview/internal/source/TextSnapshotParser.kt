package org.peyilo.ireadview.internal.source

import java.io.Reader
import java.security.MessageDigest
import org.peyilo.ireadview.source.TextBookOptions

/** Shared streaming grammar. Storage callbacks own records, never parsing state or a full book copy. */
internal object TextSnapshotParser {
    fun parse(
        reader: Reader,
        bookId: String,
        options: TextBookOptions,
        checkActive: () -> Unit,
        onBlock: (id: String, text: String, heading: Boolean) -> Unit,
        onChapter: (id: String, title: String, blockCount: Int) -> Unit
    ): ByteArray {
        val identities = TextContentIdentity(bookId, options.contentIdProvider)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(4096)
        fun number(value: Int) {
            for (shift in 24 downTo 0 step 8) digest.update((value ushr shift).toByte())
        }
        fun text(value: String) {
            number(value.length)
            var length = 0
            for (character in value) {
                buffer[length++] = (character.code ushr 8).toByte()
                buffer[length++] = character.code.toByte()
                if (length == buffer.size) { checkActive(); digest.update(buffer); length = 0 }
            }
            if (length > 0) digest.update(buffer, 0, length)
        }
        text(options.title)
        text(options.normalization.name)
        var title = options.title
        var count = 0
        var headingSeen = false
        fun finish() {
            checkActive()
            val id = identities.finishChapter(title)
            checkActive()
            digest.update(2.toByte())
            text(id); text(title); number(count)
            checkActive()
            onChapter(id, title, count)
            count = 0
        }
        TextLineReader.read(reader, options.normalization, checkActive) { line ->
            val heading = options.chapterHeading?.matches(line.trim()) == true
            if (heading) {
                if (count > 0) {
                    if (!headingSeen) title = options.preambleTitle
                    finish()
                }
                headingSeen = true
                title = line.trim()
            }
            val id = identities.blockId(line, heading)
            checkActive()
            digest.update(0.toByte())
            text(id); text(line)
            digest.update(if (heading) 1.toByte() else 0.toByte())
            checkActive()
            onBlock(id, line, heading)
            count++
        }
        // Empty input is one readable chapter; retained blank lines count as real blocks.
        finish()
        checkActive()
        return digest.digest()
    }

    /** Versioned identity of both original bytes and the emitted structure, independent of storage. */
    fun revision(originalDigest: ByteArray, parsedDigest: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("iRead:txt-snapshot:v2\u0000".toByteArray(Charsets.UTF_8))
        digest.update(originalDigest)
        return digest.digest(parsedDigest).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
