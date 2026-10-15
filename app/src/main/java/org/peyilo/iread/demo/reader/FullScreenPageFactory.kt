package org.peyilo.iread.demo.reader

import android.content.Context
import org.peyilo.ireadview.api.style.EdgeInsets
import org.peyilo.ireadview.spi.page.*

/** The page root/background fills ReaderView; only content inside the template avoids system UI. */
internal class FullScreenPageFactory(private val safe: EdgeInsets,
    private val delegate: ReaderPageFactory = ReaderPageFactories.default()) : ReaderPageFactory {
    override fun create(context: Context): ReaderPageTemplate {
        val page = delegate.create(context)
        return object : ReaderPageTemplate {
            override val root get() = page.root
            override val bodyHost get() = page.bodyHost
            override val needsTimeUpdates get() = page.needsTimeUpdates
            override fun onTimeChanged(timeMillis: Long) = page.onTimeChanged(timeMillis)
            override fun unbind() = page.unbind()
            override fun refreshChrome() = page.refreshChrome()
            override fun bind(context: ReaderPageContext) {
                val padding = context.style.page.padding
                val style = context.style.copy(page = context.style.page.copy(padding = EdgeInsets(
                    padding.startDp + safe.startDp, padding.topDp + safe.topDp,
                    padding.endDp + safe.endDp, padding.bottomDp + safe.bottomDp)))
                page.bind(context.copy(style = style))
            }
        }
    }
}
 