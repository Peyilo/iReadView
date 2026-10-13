package org.peyilo.ireadview.internal.style

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.core.content.ContextCompat
import org.peyilo.ireadview.api.style.PageStyle
import org.peyilo.ireadview.api.style.ReaderBackground

/** One binding per page root, including hidden candidates; never shares a Drawable across roots. */
internal class ReaderBackgroundBinding(private val root: View) {
    private var bound = false
    private var source: ReaderBackground? = null
    private var fallbackColor = 0

    fun bind(style: PageStyle) {
        if (bound && source == style.readerBackground && fallbackColor == style.backgroundColor) return
        val background = when (val value = style.readerBackground) {
            null -> ColorDrawable(style.backgroundColor)
            is ReaderBackground.Color -> ColorDrawable(value.color)
            is ReaderBackground.Resource -> requireNotNull(ContextCompat.getDrawable(root.context, value.resourceId)) { "Unable to load Reader background resource" }.mutate()
            is ReaderBackground.Factory -> value.factory.create(root.context).also {
                require(it.callback == null) { "Reader background factory must return an unattached Drawable" }
            }.mutate()
        }
        val drawable = if (style.readerBackground is ReaderBackground.Resource || style.readerBackground is ReaderBackground.Factory)
            LayerDrawable(arrayOf(ColorDrawable(style.backgroundColor), background)) else background
        root.background = drawable
        source = style.readerBackground
        fallbackColor = style.backgroundColor
        bound = true
    }

    fun clear() {
        root.background = null
        source = null
        bound = false
    }
}
