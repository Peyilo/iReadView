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

/** Six independent chrome positions; the optional back button precedes the header positions. */
private class DefaultPage(context: Context) : ReaderPageTemplate {
    private val density = context.resources.displayMetrics.density
    private val layout = DefaultChromeRoot(context).apply { orientation = LinearLayout.VERTICAL }
    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }

    private fun text() =
        TextView(layout.context).apply { setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }

    private val title = text()
    private val headerCenter = text()
    private val headerEnd = text()
    private val back = ImageView(context).apply {
        setImageResource(R.drawable.iread_page_back)
        contentDescription = context.getString(R.string.iread_accessibility_exit)
        setPaddingRelative(0, dp(1f), dp(5f), dp(1f))
    }
    private val body = FrameLayout(context)
    private val footer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private val progress = text()
    private val footerCenter = text()
    private val clock = text()
    private val background = ReaderBackgroundBinding(layout)
    private val headerBackground = ReaderBackgroundBinding(header)
    private val footerBackground = ReaderBackgroundBinding(footer)
    private var boundContext: ReaderPageContext? = null
    private var headerTime = false
    private var footerTime = false
    private val battery = DefaultChromeBatterySource(layout) { refreshChrome() }
    override val root: View get() = layout
    override val bodyHost: ViewGroup get() = body
    override val needsTimeUpdates get() = headerTime || footerTime

    init {
        layout.onVisibilityUpdated = { battery.updateVisibility() }
        layout.onDetached = { battery.detach() }
        header.addView(back, LinearLayout.LayoutParams(dp(19f), dp(16f)))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(headerCenter, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(headerEnd, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(progress, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(footerCenter, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(clock, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(header, LinearLayout.LayoutParams(-1, -2))
        layout.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(footer, LinearLayout.LayoutParams(-1, -2))
    }

    private fun dp(value: Float) = (value * density).toInt()
    private fun gravity(value: ChromeAlignment) = Gravity.CENTER_VERTICAL or when (value) {
        ChromeAlignment.START -> Gravity.START
        ChromeAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
        ChromeAlignment.END -> Gravity.END
    }

    private fun slot(
        view: View,
        chrome: ChromeStyle,
        binding: ReaderBackgroundBinding,
        labels: List<TextView>
    ) {
        view.visibility = if (chrome.visible) View.VISIBLE else View.GONE
        binding.bind(PageStyle(backgroundColor = 0, readerBackground = chrome.background))
        val p = chrome.padding
        view.setPaddingRelative(dp(p.startDp), dp(p.topDp), dp(p.endDp), dp(p.bottomDp))
        val height = chrome.heightDp?.let { dp(it).coerceAtLeast(1) } ?: -2
        if (view.layoutParams.height != height) view.layoutParams =
            view.layoutParams.apply { this.height = height }
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
        slot(header, context.style.header, headerBackground, listOf(title, headerCenter, headerEnd))
        slot(footer, context.style.footer, footerBackground, listOf(progress, footerCenter, clock))
        // Hiding the icon does not alter the vertical body viewport.
        header.minimumHeight =
            dp(16f + context.style.header.padding.topDp + context.style.header.padding.bottomDp)
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
        if (boundContext !== page) return // Provider may close/rebind its owner.
        val style = page.style
        val time = android.text.format.DateFormat.getTimeFormat(layout.context)
            .format(java.util.Date(page.timeMillis))
        val batteryText by lazy { battery.text() }
        val resolved = resolveDefaultPageChrome(page, chrome, time) { batteryText }
        fun action(view: View, callback: ReaderPageChromeAction?, fallback: (() -> Unit)? = null) {
            view.setOnClickListener(null)
            view.isClickable = callback != null || fallback != null
            if (view.isClickable) view.setOnClickListener {
                if (boundContext == null) return@setOnClickListener
                if (callback != null) callback.onClick(page) else fallback?.invoke()
            }
        }

        fun row(views: List<TextView>, content: DefaultChromeRow, headerRow: Boolean) {
            views.forEachIndexed { index, view ->
                val cell = content.cells[index]
                val visible = content.threeColumns || index == 0 ||
                        !headerRow && index == 2 && resolved.legacyFooterEndVisible
                view.visibility = if (visible) View.VISIBLE else View.GONE
                view.text = cell.text
                val alignment = if (content.threeColumns) {
                    listOf(
                        ChromeAlignment.START,
                        ChromeAlignment.CENTER,
                        ChromeAlignment.END
                    )[index]
                } else if (headerRow) style.header.alignment
                else if (resolved.legacyFooterEndVisible) {
                    if (index == 0) ChromeAlignment.START else ChromeAlignment.END
                } else style.footer.alignment
                view.gravity = gravity(alignment)
                action(view, cell.action)
            }
        }
        row(listOf(title, headerCenter, headerEnd), resolved.header, true)
        row(listOf(progress, footerCenter, clock), resolved.footer, false)
        back.visibility = if (chrome.showBackButton) View.VISIBLE else View.GONE
        action(back, chrome.backAction) { page.requestReaderAction(ReaderAction.ExitReader); Unit }
        headerTime = style.header.visible && resolved.header.cells.any { it.usesTime }
        footerTime = style.footer.visible && resolved.footer.cells.any { it.usesTime }
        battery.setEnabled(style.header.visible && resolved.header.cells.any { it.usesBattery } ||
                style.footer.visible && resolved.footer.cells.any { it.usesBattery })
    }

    override fun unbind() {
        boundContext = null; headerTime = false; footerTime = false
        battery.close()
        listOf(
            back,
            title,
            headerCenter,
            headerEnd,
            progress,
            footerCenter,
            clock
        ).forEach { it.setOnClickListener(null); it.isClickable = false }
        listOf(title, headerCenter, headerEnd, progress, footerCenter, clock).forEach {
            it.text = ""
        }
        background.clear(); headerBackground.clear(); footerBackground.clear()
    }
}
