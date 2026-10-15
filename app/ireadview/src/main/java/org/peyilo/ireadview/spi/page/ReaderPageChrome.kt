package org.peyilo.ireadview.spi.page

/** Immutable content for the built-in single-line header/footer.
 * Null inherits the current page/style default; an empty string clears that text.
 * Keep business state in the host; no Android Views or mutable model are retained here.
 */
data class ReaderPageChrome @JvmOverloads constructor(
    val headerText: String? = null,
    val footerStartText: String? = null,
    val footerEndText: String? = null,
    val showBackButton: Boolean = true,
    val backAction: ReaderPageChromeAction? = null,
    val headerAction: ReaderPageChromeAction? = null,
    val footerStartAction: ReaderPageChromeAction? = null,
    val footerEndAction: ReaderPageChromeAction? = null
)

/** Main-thread, fast, side-effect-free content mapping. May run for prepared pages
 * before position/count are known. Read page fields and captured host state;
 * do not call page.chrome (recursive), navigate, or update the Widget here.
 * After captured state changes call BookReadView.refreshPageChrome().
 */
fun interface ReaderPageChromeProvider { fun provide(page: ReaderPageContext): ReaderPageChrome }

/** Main-thread click on a bound chrome item; the context belongs to that page.
 * Use page.requestReaderAction for standard guarded reader actions.
 * The default back button requests ExitReader; arbitrary host actions may override it.
 */
fun interface ReaderPageChromeAction { fun onClick(page: ReaderPageContext) }
