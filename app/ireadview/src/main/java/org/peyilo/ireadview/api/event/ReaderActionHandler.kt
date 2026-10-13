package org.peyilo.ireadview.api.event

import org.peyilo.ireadview.widget.BaseReadView

enum class ReaderAction { ShowTableOfContents, ShowSettings, ExitReader }

/** Main-thread host request. Return true only when handled; the Widget starts no application UI. */
fun interface ReaderActionHandler {
    fun onAction(view: BaseReadView<*>, action: ReaderAction): Boolean
}
