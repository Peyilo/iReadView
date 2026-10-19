package org.peyilo.ireadview.api.event

import org.peyilo.ireadview.api.model.LinkTarget
import org.peyilo.ireadview.widget.BookReadView

/** Main-thread link event. Return true when handled. External links never launch UI automatically. */
fun interface ReaderLinkHandler {
    fun onLink(view: BookReadView, chapterId: String, target: LinkTarget): Boolean
}
