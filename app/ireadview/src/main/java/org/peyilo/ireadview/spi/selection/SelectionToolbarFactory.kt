package org.peyilo.ireadview.spi.selection

import android.content.Context
import android.view.View
import org.peyilo.ireadview.api.model.Highlight
import org.peyilo.ireadview.api.model.ReaderSelection
import org.peyilo.ireadview.api.state.ReaderResult

interface SelectionActions {
    /** False after selection, content generation or book changes. Call on the main thread. */
    fun isValid(): Boolean
    fun hasHighlight(): Boolean
    fun hasUnderline(): Boolean
    /** Removes marks covering the selection, otherwise adds one. Existing add-only APIs remain. */
    fun toggleHighlight(styleId: String = "default"): ReaderResult<Unit>
    fun toggleUnderline(style: org.peyilo.ireadview.api.style.TextLineStyle = org.peyilo.ireadview.api.style.TextLineStyle()): ReaderResult<Unit>
    fun copyText(): ReaderResult<Unit>
    fun highlight(styleId: String = "default"): ReaderResult<org.peyilo.ireadview.api.model.HighlightAnnotation>
    /** Adds an independent line annotation, using source range/revision validation. */
    fun underline(style: org.peyilo.ireadview.api.style.TextLineStyle = org.peyilo.ireadview.api.style.TextLineStyle()): ReaderResult<org.peyilo.ireadview.api.model.UnderlineAnnotation>
    fun clearSelection()
}

interface SelectionToolbarFactory {
    /**
     * Return a fresh unowned, unattached view. Actions are bound to this selection revision only.
     * If creation throws before returning a view, release partial resources inside the factory.
     */
    fun create(context: Context, selection: ReaderSelection, actions: SelectionActions): View
    /** Release listeners/resources after a returned view is dismissed or its preparation is abandoned. */
    fun unbind(view: View) {}
}
