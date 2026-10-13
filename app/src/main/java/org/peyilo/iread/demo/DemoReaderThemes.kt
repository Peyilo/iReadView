package org.peyilo.iread.demo

import org.peyilo.ireadview.api.style.*

/** Demo presets compose public styles; the Widget has no fixed theme catalogue. */
internal object DemoReaderThemes {
    val labels = arrayOf("纸色", "清晰白", "柔和绿", "夜间")
    fun apply(index: Int, style: ReaderStyle): ReaderStyle {
        require(index in labels.indices)
        val background = intArrayOf(0xfff7f0e5.toInt(), 0xffffffff.toInt(), 0xffe4eddd.toInt(), 0xff181c22.toInt())[index]
        val text = intArrayOf(0xff302c28.toInt(), 0xff202124.toInt(), 0xff203825.toInt(), 0xffe5e9ef.toInt())[index]
        val chrome = intArrayOf(0xff6f6256.toInt(), 0xff50565f.toInt(), 0xff455946.toInt(), 0xffb8c1ce.toInt())[index]
        return style.copy(
            page = style.page.copy(backgroundColor = background,
                readerBackground = if (index == 0) ReaderBackground.Resource(R.drawable.reader_paper) else ReaderBackground.Color(background)),
            body = style.body.copy(textColor = text), title = style.title.copy(textColor = text),
            header = style.header.copy(textColor = chrome, background = null), footer = style.footer.copy(textColor = chrome, background = null),
            selectionStyle = style.selectionStyle.copy(handleColor = if (index == 3) 0xff83c7ff.toInt() else 0xff2786c5.toInt()))
    }
}
