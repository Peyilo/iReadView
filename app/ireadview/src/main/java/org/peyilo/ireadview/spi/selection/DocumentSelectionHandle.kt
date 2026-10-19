package org.peyilo.ireadview.spi.selection

/** Backend lifetime only. Clipboard, annotation mutations and toolbar actions belong to the Widget. */
interface DocumentSelectionHandle {
    fun isValid(): Boolean
    fun clearSelection()
}
