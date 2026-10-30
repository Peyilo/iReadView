package org.peyilo.ireadview.epub

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout

/**
 * Parked documents draw only while their visual fence is pending. A transparent layer isolates
 * Chromium's drawing from the visible page; an empty clip is insufficient on older WebViews.
 * Revealing a candidate is reversible until navigation commits.
 */
internal open class EpubPageDeck(context: Context) : FrameLayout(context) {
    var snapshotPlayback = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun dispatchDraw(canvas: Canvas) {
        if (!snapshotPlayback) super.dispatchDraw(canvas)
    }

    private val revealed = HashSet<View>()
    private val rendering = HashSet<View>()

    fun setRendering(page: View, required: Boolean) {
        val changed = if (required) rendering.add(page) else rendering.remove(page)
        if (changed) invalidate()
    }

    fun isRevealed(page: View) = page in revealed

    fun reveal(page: View) {
        revealed.add(page)
        page.bringToFront()
        invalidate()
    }

    fun park(page: View) {
        revealed.remove(page)
        invalidate()
    }

    override fun onViewRemoved(child: View) {
        revealed.remove(child)
        rendering.remove(child)
        super.onViewRemoved(child)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child in revealed) return super.drawChild(canvas, child, drawingTime)
        if (child !in rendering) return true
        val saved = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), 0)
        try {
            return super.drawChild(canvas, child, drawingTime)
        } finally {
            canvas.restoreToCount(saved)
        }
    }
}
