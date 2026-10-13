package org.peyilo.ireadview.internal.style

import android.view.View
import org.peyilo.ireadview.api.style.ReaderStyle

/** These nested body-only layers currently have no decoration, so one View owns their sum. */
internal object ReaderBodyInsets {
    fun apply(view: View, style: ReaderStyle) {
        val layers = listOf(style.page.viewportPadding, style.body.containerPadding, style.body.contentPadding)
        val density = view.resources.displayMetrics.density.toDouble()
        fun pixels(value: Double): Int {
            val result = value * density
            require(result.isFinite() && result in 0.0..Int.MAX_VALUE.toDouble()) { "Body insets exceed pixel limits" }
            return result.toInt()
        }
        val start = pixels(layers.sumOf { it.startDp.toDouble() })
        val top = pixels(layers.sumOf { it.topDp.toDouble() })
        val end = pixels(layers.sumOf { it.endDp.toDouble() })
        val bottom = pixels(layers.sumOf { it.bottomDp.toDouble() })
        require(start.toLong() + end < view.width && top.toLong() + bottom < view.height) { "Body insets leave no readable viewport" }
        view.setPaddingRelative(start, top, end, bottom)
    }
}
