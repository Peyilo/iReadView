package org.peyilo.ireadview.internal.render

import org.peyilo.ireadview.api.style.ChromeContent
import org.peyilo.ireadview.spi.page.*

/** Default-template policy only. Custom decorations never have to consume these cells. */
internal data class DefaultChromeCell(
    val text: String,
    val action: ReaderPageChromeAction? = null,
    val usesTime: Boolean = false,
    val usesBattery: Boolean = false
)

internal data class DefaultChromeRow(val cells: List<DefaultChromeCell>, val threeColumns: Boolean)
internal data class DefaultChromeContent(
    val header: DefaultChromeRow,
    val footer: DefaultChromeRow,
    val legacyFooterEndVisible: Boolean
)

internal fun resolveDefaultPageChrome(
    page: ReaderPageContext, chrome: ReaderPageChrome,
    time: String, battery: () -> String
): DefaultChromeContent {
    val number = page.position?.pageNumberHint
    val count = page.pageCount
    val progress = number?.let { "$it/${count ?: "…"}" }.orEmpty()
    val empty = DefaultChromeCell("")
    fun content(type: ChromeContent): DefaultChromeCell {
        val text = when (type) {
            ChromeContent.BOOK_TITLE -> page.bookTitle
            ChromeContent.CHAPTER_TITLE -> page.chapter?.title ?: page.bookTitle
            ChromeContent.PAGE_NUMBER -> number?.toString().orEmpty()
            ChromeContent.PAGE_COUNT -> count?.toString().orEmpty()
            ChromeContent.PAGE_PROGRESS -> progress
            ChromeContent.TIME -> time
            ChromeContent.PAGE_PROGRESS_AND_TIME -> if (progress.isEmpty()) time else "$progress · $time"
            ChromeContent.BATTERY_LEVEL -> battery()
            ChromeContent.DEFAULT, ChromeContent.NONE -> ""
        }
        return DefaultChromeCell(
            text,
            usesTime = type == ChromeContent.TIME || type == ChromeContent.PAGE_PROGRESS_AND_TIME,
            usesBattery = type == ChromeContent.BATTERY_LEVEL
        )
    }

    fun overrideText(base: DefaultChromeCell, text: String?, action: ReaderPageChromeAction?) =
        if (text == null) base.copy(action = action) else DefaultChromeCell(text, action)

    val headerBase = if (page.style.header.content == ChromeContent.DEFAULT)
        DefaultChromeCell(
            if (number == 1) page.bookTitle else page.chapter?.title ?: page.bookTitle
        )
    else content(page.style.header.content)
    val split = page.style.footer.content in listOf(
        ChromeContent.DEFAULT,
        ChromeContent.PAGE_PROGRESS_AND_TIME
    )
    val headerDefaults =
        listOf(overrideText(headerBase, chrome.headerText, chrome.headerAction), empty, empty)
    val footerDefaults = listOf(
        overrideText(
            if (split) DefaultChromeCell(progress) else content(page.style.footer.content),
            chrome.footerStartText,
            chrome.footerStartAction
        ),
        empty,
        overrideText(
            if (split) content(ChromeContent.TIME) else empty,
            chrome.footerEndText,
            chrome.footerEndAction
        )
    )

    fun row(
        slots: List<ReaderPageChromeSlot?>,
        defaults: List<DefaultChromeCell>
    ): DefaultChromeRow {
        val cells = slots.mapIndexed { index, slot ->
            when {
                slot == null -> defaults[index]
                slot.text != null -> DefaultChromeCell(slot.text, slot.action)
                slot.content == ChromeContent.DEFAULT -> defaults[index].copy(action = slot.action)
                else -> content(slot.content).copy(action = slot.action)
            }
        }
        return DefaultChromeRow(cells, slots.any { it != null })
    }
    return DefaultChromeContent(
        row(listOf(chrome.headerStart, chrome.headerCenter, chrome.headerEnd), headerDefaults),
        row(listOf(chrome.footerStart, chrome.footerCenter, chrome.footerEnd), footerDefaults),
        split || chrome.footerEndText != null
    )
}
