package org.peyilo.ireadview.internal.selection

import java.text.BreakIterator
import java.util.Locale

/** Platform character boundaries shared by annotations, selection and built-in layout anchors. */
internal object TextCharacterBoundaries {
    fun isBoundary(text: String, offset: Int): Boolean {
        if (offset !in 0..text.length) return false
        if (offset == 0 || offset == text.length) return true
        return BreakIterator.getCharacterInstance(Locale.getDefault()).apply { setText(text) }.isBoundary(offset)
    }

    fun isRange(text: String, start: Int, end: Int): Boolean {
        if (start < 0 || start > end || end > text.length) return false
        if (start == 0 && end == text.length) return true
        val boundaries = BreakIterator.getCharacterInstance(Locale.getDefault()).apply { setText(text) }
        return boundaries.isBoundary(start) && boundaries.isBoundary(end)
    }
}
