package org.peyilo.ireadview.internal.render

import android.graphics.Canvas
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import org.peyilo.ireadview.api.effect.PageSurface
import org.peyilo.ireadview.api.effect.PageBackgroundSurface

internal fun viewPageSurface(view: View): PageSurface? {
    fun unsupported(node: View): Boolean = node is SurfaceView || node is TextureView ||
        node is ViewGroup && (0 until node.childCount).any { unsupported(node.getChildAt(it)) }
    if (unsupported(view) || view.width <= 0 || view.height <= 0) return null
    return object : PageBackgroundSurface {
        override val backgroundColor get() = pageBackgroundColor(view)
        override val widthPx get() = view.width
        override val heightPx get() = view.height
        override fun draw(canvas: Canvas) { view.draw(canvas) }
    }
}
