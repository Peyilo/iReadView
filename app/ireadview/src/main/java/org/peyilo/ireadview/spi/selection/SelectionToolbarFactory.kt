package org.peyilo.ireadview.spi.selection

import android.content.Context
import android.view.View
import org.peyilo.ireadview.api.model.Highlight
import org.peyilo.ireadview.api.model.TextSelection
import org.peyilo.ireadview.api.state.ReaderResult

interface SelectionActions {
    fun copyText(): ReaderResult<Unit>
    fun highlight(styleId: String = "default"): ReaderResult<Highlight>
    fun clearSelection()
}

interface SelectionToolbarFactory {
    /**
     * Return a fresh unowned, unattached view. Actions are bound to this selection revision only.
     * If creation throws before returning a view, release partial resources inside the factory.
     */
    fun create(context: Context, selection: TextSelection, actions: SelectionActions): View
    /** Release listeners/resources after a returned view is dismissed or its preparation is abandoned. */
    fun unbind(view: View) {}
}
