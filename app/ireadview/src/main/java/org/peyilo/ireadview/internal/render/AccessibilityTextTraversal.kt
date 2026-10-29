package org.peyilo.ireadview.internal.render

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.text.BreakIterator
import java.util.Locale
import org.peyilo.ireadview.internal.selection.TextCharacterBoundaries

/** Node-local UTF-16 ranges, using the same platform character boundaries as selection. */
internal object AccessibilityTextTraversal {
    data class Step(val start: Int, val end: Int, val caret: Int)

    const val GRANULARITIES = AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER or
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD or AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH

    fun isCharacterBoundary(text: String, offset: Int): Boolean =
        TextCharacterBoundaries.isBoundary(text, offset)

    fun step(
        text: String, offset: Int?, granularity: Int, forward: Boolean,
        checkCancellation: () -> Unit = {}
    ): Step? {
        checkCancellation()
        val cursor = (offset ?: if (forward) 0 else text.length).coerceIn(0, text.length)
        if (forward && cursor == text.length || !forward && cursor == 0) return null
        val iterator = when (granularity) {
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER -> BreakIterator.getCharacterInstance(
                Locale.getDefault()
            )

            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD -> BreakIterator.getWordInstance(
                Locale.getDefault()
            )

            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH -> null
            else -> return null
        }
        if (iterator != null) {
            iterator.setText(text)
            var from = cursor
            while (true) {
                checkCancellation()
                val to = if (forward) iterator.following(from) else iterator.preceding(from)
                if (to == BreakIterator.DONE) return null
                val start = minOf(from, to)
                val end = maxOf(from, to)
                if (granularity != AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD ||
                    containsWordCharacter(text, start, end, checkCancellation)
                ) return Step(start, end, to)
                from = to
            }
        }
        var from = cursor
        if (forward) {
            while (from < text.length && text[from] == '\n') from++
            if (from == text.length) return null
            val end = text.indexOf('\n', from).let { if (it < 0) text.length else it }
            return Step(from, end, end)
        }
        while (from > 0 && text[from - 1] == '\n') from--
        if (from == 0) return null
        val start = text.lastIndexOf('\n', from - 1) + 1
        return Step(start, from, start)
    }

    private fun containsWordCharacter(
        text: String,
        start: Int,
        end: Int,
        checkCancellation: () -> Unit
    ): Boolean {
        var index = start
        while (index < end) {
            checkCancellation()
            val codePoint = text.codePointAt(index)
            if (Character.isLetterOrDigit(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }
}
