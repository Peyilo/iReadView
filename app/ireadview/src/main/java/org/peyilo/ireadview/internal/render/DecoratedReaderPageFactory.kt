package org.peyilo.ireadview.internal.render

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.peyilo.ireadview.internal.releaseReaderResources
import org.peyilo.ireadview.internal.style.ReaderBackgroundBinding
import org.peyilo.ireadview.api.style.EdgeInsets
import org.peyilo.ireadview.spi.page.*

internal class DecoratedReaderPageFactory(
    private val headerFactory: ReaderPageDecorationFactory?,
    private val footerFactory: ReaderPageDecorationFactory?
) : ReaderPageFactory {
    override fun create(context: Context): ReaderPageTemplate {
        var header: ReaderPageDecoration? = null
        var footer: ReaderPageDecoration? = null
        var page: ReaderPageTemplate? = null
        try {
            header = headerFactory?.create(context)
            footer = footerFactory?.create(context)
            page = DefaultReaderPageFactory().create(context)
            return DecoratedPage(context, page, header, footer)
        } catch (error: Throwable) {
            try { releaseReaderResources({ header?.unbind() }, { footer?.unbind() }, { page?.unbind() }) }
            catch (cleanup: Throwable) { if (cleanup !== error) error.addSuppressed(cleanup) }
            throw error
        }
    }
}

private class DecoratedPage(context: Context, private val page: ReaderPageTemplate,
    private val header: ReaderPageDecoration?, private val footer: ReaderPageDecoration?) : ReaderPageTemplate {
    private val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val headerView = header?.view
    private val footerView = footer?.view
    private val background = ReaderBackgroundBinding(layout)
    private var bindingGeneration = 0L
    private var bound = false
    private var boundContext: ReaderPageContext? = null
    private fun current(generation: Long) = bound && bindingGeneration == generation
    override val root: View get() = layout
    override val bodyHost: ViewGroup get() = page.bodyHost
    override val needsTimeUpdates: Boolean get() {
        val generation = bindingGeneration
        if (!current(generation)) return false
        val pageUpdates = page.needsTimeUpdates
        if (!current(generation)) return false
        if (pageUpdates) return true
        val headerUpdates = headerView?.visibility == View.VISIBLE && header?.needsTimeUpdates == true
        if (!current(generation)) return false
        if (headerUpdates) return true
        val footerUpdates = footerView?.visibility == View.VISIBLE && footer?.needsTimeUpdates == true
        return current(generation) && footerUpdates
    }
    init {
        require(headerView?.parent == null && footerView?.parent == null) { "Decoration Views must be unparented" }
        require(headerView == null || headerView !== footerView) { "Header and footer must have separate Views" }
        headerView?.let { layout.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        layout.addView(page.root, LinearLayout.LayoutParams(-1, 0, 1f))
        footerView?.let { layout.addView(it, LinearLayout.LayoutParams(-1, -2)) }
    }
    override fun bind(context: ReaderPageContext) {
        val generation = ++bindingGeneration
        bound = true
        boundContext = context
        val style = context.style
        background.bind(style.page)
        if (!current(generation)) return
        val padding = style.page.padding
        val density = layout.resources.displayMetrics.density
        layout.setPaddingRelative((padding.startDp * density).toInt(),
            (padding.topDp * density).toInt(), (padding.endDp * density).toInt(),
            (padding.bottomDp * density).toInt())
        if (!current(generation)) return
        fun slot(view: View?, visible: Boolean, heightDp: Float?) {
            if (view == null) return
            view.visibility = if (visible) View.VISIBLE else View.GONE
            if (!current(generation)) return
            val height = heightDp?.let { (it * view.resources.displayMetrics.density).toInt().coerceAtLeast(1) } ?: -2
            if (view.layoutParams.height != height) view.layoutParams = view.layoutParams.apply { this.height = height }
        }
        slot(headerView, style.header.visible, style.header.heightDp)
        if (!current(generation)) return
        slot(footerView, style.footer.visible, style.footer.heightDp)
        if (!current(generation)) return
        page.bind(context.copy(style = style.copy(
            // The wrapper owns the complete page surface, including custom slots.
            // Binding it again on the inner template creates a second Drawable and
            // restarts its coordinate system at the body's smaller rectangle.
            page = style.page.copy(backgroundColor = Color.TRANSPARENT,
                readerBackground = null, padding = EdgeInsets()),
            header = if (header != null) style.header.copy(visible = false) else style.header,
            footer = if (footer != null) style.footer.copy(visible = false) else style.footer
        )))
        if (!current(generation)) return
        header?.bind(context)
        if (!current(generation)) return
        footer?.bind(context)
    }
    override fun refreshChrome() {
        val context = boundContext?.copy(timeMillis = System.currentTimeMillis()) ?: return
        boundContext = context
        val generation = bindingGeneration
        page.refreshChrome()
        if (!current(generation)) return
        header?.bind(context)
        if (!current(generation)) return
        footer?.bind(context)
    }
    override fun onTimeChanged(timeMillis: Long) {
        val generation = bindingGeneration
        if (!current(generation)) return
        val pageUpdates = page.needsTimeUpdates
        if (!current(generation)) return
        if (pageUpdates) page.onTimeChanged(timeMillis)
        if (!current(generation)) return
        val headerUpdates = headerView?.visibility == View.VISIBLE && header?.needsTimeUpdates == true
        if (!current(generation)) return
        if (headerUpdates) header?.onTimeChanged(timeMillis)
        if (!current(generation)) return
        val footerUpdates = footerView?.visibility == View.VISIBLE && footer?.needsTimeUpdates == true
        if (!current(generation)) return
        if (footerUpdates) footer?.onTimeChanged(timeMillis)
    }
    override fun unbind() {
        bound = false
        boundContext = null
        bindingGeneration++
        releaseReaderResources(
            { header?.unbind() }, { footer?.unbind() }, { page.unbind() }, { background.clear() }
        )
    }
}
