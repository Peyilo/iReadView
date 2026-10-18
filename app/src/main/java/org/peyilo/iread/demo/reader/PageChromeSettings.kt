package org.peyilo.iread.demo.reader

import org.peyilo.ireadview.api.style.ChromeContent
import org.peyilo.ireadview.spi.page.ReaderPageChrome
import org.peyilo.ireadview.spi.page.ReaderPageChromeSlot

internal enum class PageChromePosition(val label: String) {
    HEADER_START("页眉 · 左侧"), HEADER_CENTER("页眉 · 中间"), HEADER_END("页眉 · 右侧"),
    FOOTER_START("页脚 · 左侧"), FOOTER_CENTER("页脚 · 中间"), FOOTER_END("页脚 · 右侧")
}

/** Demo configures all six positions explicitly; it never inherits legacy chrome content. */
internal data class PageChromeSettings(
    val contents: List<ChromeContent> = listOf(ChromeContent.CHAPTER_TITLE, ChromeContent.NONE, ChromeContent.NONE,
        ChromeContent.PAGE_PROGRESS, ChromeContent.TIME, ChromeContent.BATTERY_LEVEL),
    val showBackButton: Boolean = true
) {
    init { require(contents.size == PageChromePosition.entries.size && ChromeContent.DEFAULT !in contents) }
    fun content(position: PageChromePosition) = contents[position.ordinal]
    fun withContent(position: PageChromePosition, value: ChromeContent) =
        copy(contents = contents.mapIndexed { index, current -> if(index == position.ordinal) value else current })
    fun template() = ReaderPageChrome(
        headerStart = ReaderPageChromeSlot(contents[0]), headerCenter = ReaderPageChromeSlot(contents[1]),
        headerEnd = ReaderPageChromeSlot(contents[2]), footerStart = ReaderPageChromeSlot(contents[3]),
        footerCenter = ReaderPageChromeSlot(contents[4]), footerEnd = ReaderPageChromeSlot(contents[5]),
        showBackButton = showBackButton
    )
}
