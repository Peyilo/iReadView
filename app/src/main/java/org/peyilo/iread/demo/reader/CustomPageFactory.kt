package org.peyilo.iread.demo.reader

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.peyilo.ireadview.api.event.ReaderAction
import org.peyilo.ireadview.spi.page.*

/** Host composition example: a toolbar, Widget-owned body, and a minute-updated footer. */
internal class CustomPageFactory : ReaderPageFactory {
    override fun create(context: Context): ReaderPageTemplate = Page(context)
    private class Page(context: Context) : ReaderPageTemplate {
        private val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val toolbar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xffedf1f5.toInt()) }
        private val contents = Button(context).apply { text = "目录" }
        private val title = TextView(context).apply { maxLines = 1; gravity = Gravity.CENTER }
        private val settings = Button(context).apply { text = "设置" }
        private val body = FrameLayout(context)
        private val footer = TextView(context).apply { maxLines = 1; gravity = Gravity.END }
        private var bound = false
        private var progress = ""
        override val root: View get() = layout
        override val bodyHost: ViewGroup get() = body
        override val needsTimeUpdates get() = bound && footer.visibility == View.VISIBLE
        init {
            toolbar.addView(contents, LinearLayout.LayoutParams(-2, -2))
            toolbar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            toolbar.addView(settings, LinearLayout.LayoutParams(-2, -2))
            layout.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
            layout.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            layout.addView(footer, LinearLayout.LayoutParams(-1, -2))
        }
        override fun bind(context: ReaderPageContext) {
            bound = true
            val density = layout.resources.displayMetrics.density
            val style = context.style
            val padding = style.page.padding
            layout.setBackgroundColor(style.page.backgroundColor)
            layout.setPaddingRelative((padding.startDp * density).toInt(), (padding.topDp * density).toInt(),
                (padding.endDp * density).toInt(), (padding.bottomDp * density).toInt())
            toolbar.visibility = if (style.header.visible) View.VISIBLE else View.GONE
            toolbar.setBackgroundColor(style.page.backgroundColor)
            footer.visibility = if (style.footer.visible) View.VISIBLE else View.GONE
            title.text = context.chapter?.title ?: context.bookTitle
            title.textSize = style.header.textSizeSp
            title.setTextColor(style.header.textColor)
            title.typeface = style.header.font ?: android.graphics.Typeface.DEFAULT
            footer.textSize = style.footer.textSizeSp
            footer.setTextColor(style.footer.textColor)
            footer.typeface = style.footer.font ?: android.graphics.Typeface.DEFAULT
            footer.setPadding(0, (12 * density).toInt(), 0, 0)
            contents.isEnabled = context.position != null
            settings.isEnabled = context.position != null
            contents.setOnClickListener { context.requestReaderAction(ReaderAction.ShowTableOfContents) }
            settings.setOnClickListener { context.requestReaderAction(ReaderAction.ShowSettings) }
            progress = context.position?.pageNumberHint?.let { "$it / ${context.pageCount ?: "…"}  ·  " } ?: ""
            onTimeChanged(context.timeMillis)
        }
        override fun onTimeChanged(timeMillis: Long) {
            if (bound) footer.text = progress + android.text.format.DateFormat.getTimeFormat(layout.context).format(java.util.Date(timeMillis))
        }
        override fun unbind() {
            bound = false; progress = ""
            contents.setOnClickListener(null); settings.setOnClickListener(null)
            title.text = ""; footer.text = ""
        }
    }
}
