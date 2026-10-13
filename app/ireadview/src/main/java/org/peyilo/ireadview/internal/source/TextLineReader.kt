package org.peyilo.ireadview.internal.source

import java.io.Reader
import org.peyilo.ireadview.api.source.TextNormalization

/** One set of BOM/newline/whitespace rules for memory and disk snapshots. Does not own the reader. */
internal object TextLineReader {
    fun read(reader: Reader, normalization: TextNormalization, checkActive: () -> Unit, emit: (String) -> Unit) {
        val buffer = CharArray(4096)
        val line = StringBuilder()
        var first = true
        var afterCr = false
        fun emitLine() {
            checkActive()
            val original = line.toString()
            val text = when (normalization) {
                TextNormalization.PRESERVE -> original
                TextNormalization.TRIM_LINE_END -> original.trimEnd()
                TextNormalization.TRIM_LINE_EDGES -> original.trim()
            }
            emit(text)
            line.setLength(0)
        }
        while (true) {
            checkActive()
            val count = reader.read(buffer)
            if (count < 0) break
            for (index in 0 until count) {
                val character = buffer[index]
                if (first) { first = false; if (character == '\uFEFF') continue }
                if (afterCr) { afterCr = false; if (character == '\n') continue }
                if (character == '\r' || character == '\n') {
                    emitLine()
                    afterCr = character == '\r'
                } else line.append(character)
            }
        }
        // Test the original line: whitespace-only input still emits a blank block after trimming.
        if (line.isNotEmpty()) emitLine()
        checkActive()
    }

}
