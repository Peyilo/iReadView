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
    /** Optional default-template configuration bridge, not a required custom-template input.
     * Custom templates/decorations can ignore it and bind directly from page data/host state.
     * Prepared and visible default templates always read the latest provider.
     */
    val chromeProvider: ReaderPageChromeProvider? = null
) {
    /** Resolve the built-in template's configuration only when a template opts into it. */
    val chrome: ReaderPageChrome get() = chromeProvider?.provide(this) ?: ReaderPageChrome()
}

/**
 * Each create returns an independent unowned template/root; preparation may overlap across Widgets.
 * The factory releases its own partial resources if creation fails before returning a valid template.
 */
fun interface ReaderPageFactory {
    fun create(context: Context): ReaderPageTemplate
}

object ReaderPageFactories {
    /** High-level ready-made template implementing the same SPI available to custom factories.
     * Six-position/back-button policies belong to this template, not ReaderPageTemplate.
     */
    @JvmStatic
    fun default(): ReaderPageFactory =
        org.peyilo.ireadview.internal.render.DefaultReaderPageFactory()

    /** Low-level decoration composition. Null preserves the built-in region; a supplied
     * factory completely replaces that region with any View/ViewGroup, independent of
     * ReaderPageChromeSlot, column count, content enum or built-in back-button policy.
     */
    @JvmStatic
    @JvmOverloads
    fun withDecorations(
        header: ReaderPageDecorationFactory? = null,
        footer: ReaderPageDecorationFactory? = null
    ): ReaderPageFactory =
        org.peyilo.ireadview.internal.render.DecoratedReaderPageFactory(header, footer)
}

/** Structural page SPI. No required column count, built-in content types or navigation button.
 * For an arbitrary whole-page composition implement this directly. For arbitrary header/footer
 * Views with the standard body placement use ReaderPageDecorationFactory.
 */
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
