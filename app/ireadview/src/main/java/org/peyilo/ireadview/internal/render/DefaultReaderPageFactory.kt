package org.peyilo.ireadview.internal.render

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.peyilo.ireadview.R
import org.peyilo.ireadview.api.event.ReaderAction
import org.peyilo.ireadview.api.style.*
import org.peyilo.ireadview.internal.style.ReaderBackgroundBinding
import org.peyilo.ireadview.spi.page.*

internal class DefaultReaderPageFactory : ReaderPageFactory {
    override fun create(context: Context): ReaderPageTemplate = DefaultPage(context)
}

/** Built-in reference template: back/title above; progress and clock on opposite footer ends. */
private class DefaultPage(context: Context) : ReaderPageTemplate {
    private val density = context.resources.displayMetrics.density
    private val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text() = TextView(layout.context).apply { setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }
    private val title = text()
    private val back = ImageView(context).apply {
        setImageResource(R.drawable.iread_page_back)
        contentDescription = context.getString(R.string.iread_accessibility_exit)
        setPaddingRelative(0, dp(1f), dp(5f), dp(1f))
    }
    private val body = FrameLayout(context)
    private val footer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val progress = text()
    private val clock = text()
    private val background = ReaderBackgroundBinding(layout)
    private val headerBackground = ReaderBackgroundBinding(header)
    private val footerBackground = ReaderBackgroundBinding(footer)
    private var boundContext: ReaderPageContext? = null
    private var headerTime = false
    private var footerTime = false
    override val root: View get() = layout
    override val bodyHost: ViewGroup get() = body
    override val needsTimeUpdates get() = headerTime || footerTime

    init {
        header.addView(back, LinearLayout.LayoutParams(dp(19f), dp(16f)))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(progress, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(clock, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(header, LinearLayout.LayoutParams(-1, -2))
        layout.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(footer, LinearLayout.LayoutParams(-1, -2))
    }
    private fun dp(value: Float) = (value * density).toInt()
    private fun gravity(value: ChromeAlignment) = Gravity.CENTER_VERTICAL or when(value) {
        ChromeAlignment.START -> Gravity.START
        ChromeAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
        ChromeAlignment.END -> Gravity.END
    }
    private fun slot(view: View, chrome: ChromeStyle, binding: ReaderBackgroundBinding, labels: List<TextView>) {
        view.visibility = if(chrome.visible) View.VISIBLE else View.GONE
        binding.bind(PageStyle(backgroundColor = 0, readerBackground = chrome.background))
        val p = chrome.padding
        view.setPaddingRelative(dp(p.startDp), dp(p.topDp), dp(p.endDp), dp(p.bottomDp))
        val height = chrome.heightDp?.let { dp(it).coerceAtLeast(1) } ?: -2
        if(view.layoutParams.height != height) view.layoutParams = view.layoutParams.apply { this.height = height }
        labels.forEach { label ->
            label.textSize = chrome.textSizeSp
            label.typeface = chrome.font ?: Typeface.DEFAULT
            label.setTextColor(chrome.textColor)
        }
    }
    override fun bind(context: ReaderPageContext) {
        boundContext = context
        background.bind(context.style.page)
        val p = context.style.page.padding
        layout.setPaddingRelative(dp(p.startDp), dp(p.topDp), dp(p.endDp), dp(p.bottomDp))
        slot(header, context.style.header, headerBackground, listOf(title))
        slot(footer, context.style.footer, footerBackground, listOf(progress, clock))
        // Hiding the icon does not alter the vertical body viewport.
        header.minimumHeight = dp(16f + context.style.header.padding.topDp + context.style.header.padding.bottomDp)
        back.imageTintList = ColorStateList.valueOf(context.style.header.textColor)
        renderChrome(context)
    }
    override fun refreshChrome() {
        val page = boundContext ?: return
        val updated = page.copy(timeMillis = System.currentTimeMillis())
        boundContext = updated
        renderChrome(updated)
    }
    override fun onTimeChanged(timeMillis: Long) {
        val page = boundContext ?: return
        val updated = page.copy(timeMillis = timeMillis)
        boundContext = updated
        renderChrome(updated)
    }
    private fun renderChrome(page: ReaderPageContext) {
        val chrome = page.chrome
        if(boundContext !== page) return // Provider may close/rebind its owner.
        val style = page.style
        val time = android.text.format.DateFormat.getTimeFormat(layout.context).format(java.util.Date(page.timeMillis))
        val number = page.position?.pageNumberHint
        val count = page.pageCount
        val pageProgress = number?.let { "$it/${count ?: "…"}" }.orEmpty()
        fun content(type: ChromeContent): String = when(type) {
            ChromeContent.BOOK_TITLE -> page.bookTitle
            ChromeContent.CHAPTER_TITLE -> page.chapter?.title ?: page.bookTitle
            ChromeContent.PAGE_NUMBER -> number?.toString().orEmpty()
            ChromeContent.PAGE_COUNT -> count?.toString().orEmpty()
            ChromeContent.PAGE_PROGRESS -> pageProgress
            ChromeContent.TIME -> time
            ChromeContent.PAGE_PROGRESS_AND_TIME -> if(pageProgress.isEmpty()) time else "$pageProgress · $time"
            ChromeContent.DEFAULT, ChromeContent.NONE -> ""
        }
        val headerType = style.header.content
        val headerDefault = if(headerType == ChromeContent.DEFAULT) {
            if(number == 1) page.bookTitle else page.chapter?.title ?: page.bookTitle
        } else content(headerType)
        val splitFooter = style.footer.content in listOf(ChromeContent.DEFAULT, ChromeContent.PAGE_PROGRESS_AND_TIME)
        val footerDefault = if(splitFooter) pageProgress else content(style.footer.content)
        title.text = chrome.headerText ?: headerDefault
        title.gravity = gravity(style.header.alignment)
        progress.text = chrome.footerStartText ?: footerDefault
        clock.text = chrome.footerEndText ?: if(splitFooter) time else ""
        val showEnd = splitFooter || chrome.footerEndText != null
        clock.visibility = if(showEnd) View.VISIBLE else View.GONE
        progress.gravity = if(showEnd) gravity(ChromeAlignment.START) else gravity(style.footer.alignment)
        clock.gravity = gravity(ChromeAlignment.END)
        back.visibility = if(chrome.showBackButton) View.VISIBLE else View.GONE
        headerTime = style.header.visible && chrome.headerText == null &&
            headerType in listOf(ChromeContent.TIME, ChromeContent.PAGE_PROGRESS_AND_TIME)
        footerTime = style.footer.visible && ((splitFooter && chrome.footerEndText == null) ||
            (!splitFooter && chrome.footerStartText == null && style.footer.content == ChromeContent.TIME))
        fun action(view: View, callback: ReaderPageChromeAction?, fallback: (() -> Unit)? = null) {
            view.setOnClickListener(null)
            view.isClickable = callback != null || fallback != null
            if(view.isClickable) view.setOnClickListener {
                if(boundContext == null) return@setOnClickListener
                if(callback != null) callback.onClick(page) else fallback?.invoke()
            }
        }
        action(back, chrome.backAction) { page.requestReaderAction(ReaderAction.ExitReader); Unit }
        action(title, chrome.headerAction)
        action(progress, chrome.footerStartAction)
        action(clock, chrome.footerEndAction)
    }
    override fun unbind() {
        boundContext = null; headerTime = false; footerTime = false
        listOf(back, title, progress, clock).forEach { it.setOnClickListener(null); it.isClickable = false }
        title.text = ""; progress.text = ""; clock.text = ""
        background.clear(); headerBackground.clear(); footerBackground.clear()
    }
}
