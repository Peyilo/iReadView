package org.peyilo.ireadview.api.source

/**
 * Applied once to each decoded logical line, before heading detection and identity callbacks.
 * All modes remove only the initial BOM, normalize CRLF/CR line boundaries, retain blank lines,
 * and omit an extra block for a final newline. Offsets refer to the resulting block text.
 * Changing this policy changes the snapshot revision; it does not modify displayed layouts.
 */
enum class TextNormalization {
    /** Preserve line whitespace; the existing default. */
    PRESERVE,
    /** Remove trailing Kotlin Unicode whitespace, retaining leading indentation. */
    TRIM_LINE_END,
    /** Remove leading and trailing Kotlin Unicode whitespace. */
    TRIM_LINE_EDGES
}
