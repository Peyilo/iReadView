package org.peyilo.ireadview.epub

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout

/**
 * Visibility belongs to the deck, not to WebView's ancestor alpha. Parked documents still record
 * their display lists, allowing Chromium's visual fence to complete while reading. Their pixels are
 * clipped out before GPU composition. Revealing a candidate is reversible until the navigation
 * commits.
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
        super.onViewRemoved(child)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child in revealed) return super.drawChild(canvas, child, drawingTime)
        val saved = canvas.save()
        try {
            canvas.clipRect(0, 0, 0, 0)
            child.draw(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
        return true
    }
}
