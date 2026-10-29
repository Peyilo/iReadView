package org.peyilo.ireadview.epub

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout

/** Visibility belongs to the deck, not to WebView's ancestor alpha. Parked
 * documents still record their display lists, allowing Chromium's visual fence
 * to complete while reading. Their pixels are composited at zero opacity.
 * Revealing a candidate is reversible until the navigation commits.
 */
internal open class EpubPageDeck(context: Context) : FrameLayout(context) {
    private val revealed = HashSet<View>()
    fun isRevealed(page: View) = page in revealed
    fun reveal(page: View) { revealed.add(page); page.bringToFront(); invalidate() }
    fun park(page: View) { revealed.remove(page); invalidate() }
    override fun onViewRemoved(child: View) { revealed.remove(child); super.onViewRemoved(child) }
    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child in revealed) return super.drawChild(canvas, child, drawingTime)
        val saved = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), 0)
        return try { super.drawChild(canvas, child, drawingTime) }
        finally { canvas.restoreToCount(saved) }
    }
}
