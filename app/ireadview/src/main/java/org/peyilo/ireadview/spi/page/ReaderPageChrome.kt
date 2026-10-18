package org.peyilo.ireadview.spi.page

import org.peyilo.ireadview.api.style.ChromeContent

/** One of the six single-line positions in the default template.
 * content resolves book/chapter/page/time/battery data automatically. text overrides
 * it for host-defined state; null uses content, "" clears the slot. action is optional.
 * Long text is ellipsized within its third of the row; text style inherits ChromeStyle.
 */
data class ReaderPageChromeSlot @JvmOverloads constructor(
    val content: ChromeContent = ChromeContent.NONE,
    val text: String? = null,
    val action: ReaderPageChromeAction? = null
)

/** High-level configuration for the built-in single-line header/footer only.
 * This is not the contract for custom decorations or full page templates; those use
 * ReaderPageDecorationFactory/ReaderPageFactory and may ignore this model entirely.
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
    val footerEndAction: ReaderPageChromeAction? = null,
    /** Set any header slot to enable three equal positions after the separate back button.
     * Null slots inherit legacy header text at start and empty center/end.
     */
    val headerStart: ReaderPageChromeSlot? = null,
    val headerCenter: ReaderPageChromeSlot? = null,
    val headerEnd: ReaderPageChromeSlot? = null,
    /** Set any footer slot to enable three equal positions; null inherits legacy defaults.
     * NONE explicitly clears a position. Positions never overlap or wrap onto another line.
     */
    val footerStart: ReaderPageChromeSlot? = null,
    val footerCenter: ReaderPageChromeSlot? = null,
    val footerEnd: ReaderPageChromeSlot? = null
) {
    /** Configure start/center/end positions. null inherits the position's legacy default. */
    fun withHeader(start: ReaderPageChromeSlot?, center: ReaderPageChromeSlot?, end: ReaderPageChromeSlot?) =
        copy(headerStart = start, headerCenter = center, headerEnd = end)

    /** Configure footer independently. Use a NONE slot to intentionally leave a position empty. */
    fun withFooter(start: ReaderPageChromeSlot?, center: ReaderPageChromeSlot?, end: ReaderPageChromeSlot?) =
        copy(footerStart = start, footerCenter = center, footerEnd = end)

    /** The button is before headerStart, outside the three text positions.
     * A null callback restores the default guarded ExitReader action. Hiding it
     * frees its horizontal width without changing the body's vertical viewport.
     */
    @JvmOverloads fun withBackButton(visible: Boolean, action: ReaderPageChromeAction? = null) =
        copy(showBackButton = visible, backAction = action)
}

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
