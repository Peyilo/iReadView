package org.peyilo.ireadview.spi.page

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.peyilo.ireadview.api.model.ChapterDescriptor
import org.peyilo.ireadview.api.model.ReadingPosition
import org.peyilo.ireadview.api.model.ReadingMode
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.api.event.ReaderAction

data class ReaderPageContext(
    val bookTitle: String,
    val chapter: ChapterDescriptor?,
    val position: ReadingPosition?,
    val pageCount: Int?,
    val style: ReaderStyle,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    /** Bound to this page's position/generation; stale or uncommitted pages return false. */
    val requestReaderAction: (ReaderAction) -> Boolean = { false },
    val timeMillis: Long = System.currentTimeMillis(),
    /** Live host bridge; prepared and visible pages always read the latest provider. */
    val chromeProvider: ReaderPageChromeProvider? = null
) {
    val chrome: ReaderPageChrome get() = chromeProvider?.provide(this) ?: ReaderPageChrome()
}

/**
 * Each create returns an independent unowned template/root; preparation may overlap across Widgets.
 * The factory releases its own partial resources if creation fails before returning a valid template.
 */
fun interface ReaderPageFactory { fun create(context: Context): ReaderPageTemplate }

object ReaderPageFactories {
    @JvmStatic fun default(): ReaderPageFactory = org.peyilo.ireadview.internal.render.DefaultReaderPageFactory()
    /** Null preserves the built-in slot. Each supplied factory may create any View/ViewGroup. */
    @JvmStatic @JvmOverloads fun withDecorations(
        header: ReaderPageDecorationFactory? = null,
        footer: ReaderPageDecorationFactory? = null
    ): ReaderPageFactory = org.peyilo.ireadview.internal.render.DecoratedReaderPageFactory(header, footer)
}

interface ReaderPageTemplate {
    /** Stable, non-throwing identities for this template's lifetime; bind/unbind must not replace them. */
    val root: View
    val bodyHost: ViewGroup
    fun bind(context: ReaderPageContext)
    fun unbind()
    /** Refresh chrome content/actions only, retaining body and measured slot geometry.
     * Custom templates may override; custom multiline/height changes require setPageFactory.
     */
    fun refreshChrome() {}
    /** Opt in to minute updates for the active visible page; hidden candidates receive only bind time. */
    val needsTimeUpdates: Boolean get() = false
    fun onTimeChanged(timeMillis: Long) {}
}
