package org.peyilo.ireadview.internal.presentation

import android.view.View
import org.peyilo.ireadview.spi.page.ReaderPageTemplate
import java.util.IdentityHashMap
import org.peyilo.ireadview.internal.render.ReaderViewOwnership

/** Main-thread reservations cover unattached preparation as well as visible roots, across Widgets. */
internal object ReaderPageOwnership {
    private val templates = IdentityHashMap<ReaderPageTemplate, View>()

    fun acquire(template: ReaderPageTemplate, root: View): Boolean {
        if (templates.containsKey(template) || !ReaderViewOwnership.acquire(root, template)) return false
        templates[template] = root
        return true
    }

    fun release(template: ReaderPageTemplate) {
        val root = templates.remove(template) ?: return
        ReaderViewOwnership.release(root, template)
    }
}
