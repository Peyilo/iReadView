package org.peyilo.ireadview.epub

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.os.Build
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
    private val invalidationListeners = HashMap<View, (View) -> Unit>()
    private val preparedPixels = HashMap<View, EpubPageRecording>()

    fun presentPrepared(page: View, recording: EpubPageRecording?) {
        if (recording == null) {
            if (preparedPixels.remove(page) != null) invalidate()
        } else if (preparedPixels.put(page, recording) !== recording) invalidate()
    }

    // These properties are applied by ViewGroup.drawChild, outside the child's own draw().
    // Such roots keep the framework path instead of replaying local-coordinate pixels.
    private fun canPresentPrepared(page: View) =
        page.alpha == 1f &&
            page.matrix.isIdentity &&
            page.animation == null &&
            page.clipBounds == null &&
            !page.clipToOutline &&
            page.elevation == 0f &&
            page.translationZ == 0f &&
            page.layerType == View.LAYER_TYPE_NONE

    val tracksInvalidation
        get() = EpubVisualChanges.supported

    fun observeInvalidation(page: View, listener: (View) -> Unit) {
        if (tracksInvalidation) invalidationListeners[page] = listener
    }

    @TargetApi(26)
    override fun onDescendantInvalidated(child: View, target: View) {
        if (Build.VERSION.SDK_INT >= 26) {
            invalidationListeners[child]?.invoke(target)
            super.onDescendantInvalidated(child, target)
        }
    }

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
        invalidationListeners.remove(child)
        preparedPixels.remove(child)
        super.onViewRemoved(child)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child in revealed) {
            val prepared = preparedPixels[child]
            if (prepared != null && canPresentPrepared(child)) {
                val saved = canvas.save()
                try {
                    canvas.translate(child.left.toFloat(), child.top.toFloat())
                    canvas.clipRect(0, 0, child.width, child.height)
                    prepared.draw(canvas)
                    return true
                } finally {
                    canvas.restoreToCount(saved)
                }
            }
            return super.drawChild(canvas, child, drawingTime)
        }
        if (child !in rendering) return true
        // Software page capture needs the visual fence, not a full GPU raster of the hidden page.
        // Modern hardware capture consumes that raster, so keep its full preparation surface.
        val fullRaster = Build.VERSION.SDK_INT >= 29
        val saved =
            canvas.saveLayerAlpha(
                0f,
                0f,
                if (fullRaster) width.toFloat() else 1f,
                if (fullRaster) height.toFloat() else 1f,
                0,
            )
        try {
            return super.drawChild(canvas, child, drawingTime)
        } finally {
            canvas.restoreToCount(saved)
        }
    }
}
