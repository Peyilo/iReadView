package org.peyilo.ireadview.internal.render

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.peyilo.ireadview.spi.page.*
import org.peyilo.ireadview.internal.style.ReaderBackgroundBinding
import org.peyilo.ireadview.api.style.*

internal class DefaultReaderPageFactory : ReaderPageFactory {
    override fun create(context: Context): ReaderPageTemplate = DefaultPage(context)
}

private class DefaultPage(context: Context) : ReaderPageTemplate {
    private val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val header = TextView(context).apply { maxLines = 1; gravity = Gravity.CENTER_VERTICAL }
    private val body = FrameLayout(context)
    private val footer = TextView(context).apply { maxLines = 1; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
    private val background = ReaderBackgroundBinding(layout)
    private val headerBackground = ReaderBackgroundBinding(header)
    private val footerBackground = ReaderBackgroundBinding(footer)
    private var headerTime = false
    private var footerTime = false
    private var headerTimePrefix = ""
    private var footerTimePrefix = ""
    override val needsTimeUpdates get() = headerTime || footerTime
    override fun onTimeChanged(timeMillis: Long) {
        val time = android.text.format.DateFormat.getTimeFormat(layout.context).format(java.util.Date(timeMillis))
        if (headerTime) header.text = headerTimePrefix + time
        if (footerTime) footer.text = footerTimePrefix + time
    }
    override val root: View get() = layout
    override val bodyHost: ViewGroup get() = body

    init {
        layout.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun bind(context: ReaderPageContext) {
        val style = context.style
        val progress = context.position?.pageNumberHint?.let { "$it / ${context.pageCount ?: "…"}" } ?: ""
        fun content(chrome: ChromeStyle, isHeader: Boolean) = if (chrome.content == ChromeContent.DEFAULT) {
            if (isHeader) ChromeContent.CHAPTER_TITLE else ChromeContent.PAGE_PROGRESS_AND_TIME
        } else chrome.content
        fun showsTime(chrome: ChromeStyle, isHeader: Boolean) = chrome.visible &&
            content(chrome, isHeader) in listOf(ChromeContent.TIME, ChromeContent.PAGE_PROGRESS_AND_TIME)
        fun timePrefix(chrome: ChromeStyle, isHeader: Boolean) =
            if (content(chrome, isHeader) == ChromeContent.PAGE_PROGRESS_AND_TIME && progress.isNotEmpty()) "$progress · " else ""
        headerTime = showsTime(style.header, true)
        footerTime = showsTime(style.footer, false)
        headerTimePrefix = timePrefix(style.header, true)
        footerTimePrefix = timePrefix(style.footer, false)
        val density = layout.resources.displayMetrics.density
        val padding = style.page.padding
        background.bind(style.page)
        layout.setPaddingRelative((padding.startDp * density).toInt(), (padding.topDp * density).toInt(), (padding.endDp * density).toInt(), (padding.bottomDp * density).toInt())
        fun bindChrome(view: TextView, chrome: ChromeStyle, binding: ReaderBackgroundBinding, isHeader: Boolean) {
            view.visibility = if (chrome.visible) View.VISIBLE else View.GONE
            view.textSize = chrome.textSizeSp
            view.typeface = chrome.font ?: android.graphics.Typeface.DEFAULT
            view.setTextColor(chrome.textColor)
            binding.bind(PageStyle(backgroundColor = 0, readerBackground = chrome.background))
            val insets = chrome.padding
            view.setPaddingRelative((insets.startDp * density).toInt(), (insets.topDp * density).toInt(),
                (insets.endDp * density).toInt(), (insets.bottomDp * density).toInt())
            view.gravity = Gravity.CENTER_VERTICAL or when (chrome.alignment) {
                ChromeAlignment.START -> Gravity.START
                ChromeAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
                ChromeAlignment.END -> Gravity.END
            }
            val height = chrome.heightDp?.let { (it * density).toInt().coerceAtLeast(1) } ?: ViewGroup.LayoutParams.WRAP_CONTENT
            if (view.layoutParams.height != height) view.layoutParams = view.layoutParams.apply { this.height = height }
            view.text = when (content(chrome, isHeader)) {
                ChromeContent.BOOK_TITLE -> context.bookTitle
                ChromeContent.CHAPTER_TITLE -> context.chapter?.title ?: context.bookTitle
                ChromeContent.PAGE_NUMBER -> context.position?.pageNumberHint?.toString() ?: ""
                ChromeContent.PAGE_COUNT -> context.pageCount?.toString() ?: ""
                ChromeContent.PAGE_PROGRESS -> progress
                ChromeContent.NONE, ChromeContent.DEFAULT -> ""
                ChromeContent.TIME -> android.text.format.DateFormat.getTimeFormat(layout.context).format(java.util.Date(context.timeMillis))
                ChromeContent.PAGE_PROGRESS_AND_TIME -> timePrefix(chrome, isHeader) +
                    android.text.format.DateFormat.getTimeFormat(layout.context).format(java.util.Date(context.timeMillis))
            }
        }
        bindChrome(header, style.header, headerBackground, true)
        bindChrome(footer, style.footer, footerBackground, false)
    }
    override fun unbind() { headerTime = false; footerTime = false; headerTimePrefix = ""; footerTimePrefix = ""; header.text = ""; footer.text = ""; background.clear(); headerBackground.clear(); footerBackground.clear() }
}
