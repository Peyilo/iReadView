package org.peyilo.iread.demo.reader

import org.peyilo.iread.demo.R

import org.peyilo.ireadview.api.style.*

/** Demo presets compose public styles; the Widget has no fixed theme catalogue. */
internal object ReaderThemes {
    val labels = arrayOf("纸色", "清晰白", "柔和绿", "夜间", "墨黑")
    private val backgrounds = intArrayOf(0xfff7f0e5.toInt(), 0xfff6f6f6.toInt(), 0xffe4eddd.toInt(), 0xff181c22.toInt(), 0xff0f1013.toInt())
    fun indexOf(style: ReaderStyle) = backgrounds.indexOf(style.page.backgroundColor)
    fun apply(index: Int, style: ReaderStyle): ReaderStyle {
        require(index in labels.indices)
        val background = backgrounds[index]
        val text = intArrayOf(0xff302c28.toInt(), 0xff1e1a1a.toInt(), 0xff203825.toInt(), 0xffe5e9ef.toInt(), 0xffc8c8cf.toInt())[index]
        val chrome = intArrayOf(0xff6f6256.toInt(), 0xff000000.toInt(), 0xff455946.toInt(), 0xffb8c1ce.toInt(), 0xff898d98.toInt())[index]
        return style.copy(
            page = style.page.copy(backgroundColor = background,
                readerBackground = if (index == 0) ReaderBackground.Resource(R.drawable.reader_paper) else ReaderBackground.Color(background)),
            body = style.body.copy(textColor = text), title = style.title.copy(textColor = if (index == 1) 0xff000000.toInt() else text),
            header = style.header.copy(textColor = chrome, background = null), footer = style.footer.copy(textColor = chrome, background = null),
            selectionStyle = style.selectionStyle)
    }
}
