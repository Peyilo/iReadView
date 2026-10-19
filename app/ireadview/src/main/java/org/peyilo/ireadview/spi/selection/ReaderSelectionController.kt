package org.peyilo.ireadview.spi.selection

import android.content.Context
import android.graphics.PointF

/** Replaceable selection gesture session; reset cancels pending work, release is final. */
interface ReaderSelectionController {
    val isDragging: Boolean
    /** Null uses the backend's initial range selection; true/false handles/suppresses it. */
    fun onLongPress(x: Float, y: Float): Boolean? = null
    fun onTouch(action: Int, x: Float, y: Float): Boolean
    fun reset()
    fun release()
}

/** Coordinates are presentation-local physical pixels. Async work must recheck current
 * immediately before applying a selection or moving a page.
 */
abstract class ReaderSelectionDelegate {
    abstract fun ready(): Boolean
    abstract fun handleAt(x: Float, y: Float): Boolean?
    abstract fun grabOffset(start: Boolean, x: Float, y: Float): PointF?
    abstract suspend fun extend(start: Boolean, point: PointF, current: () -> Boolean): Boolean
    abstract fun edgeDirection(point: PointF): Int
    abstract suspend fun movePage(direction: Int, start: Boolean, current: () -> Boolean): Boolean
    open fun scrollSpeed(point: PointF): Float? = null
    open suspend fun nextScrollFrame(): Long = 0L
    open suspend fun scrollBy(pixels: Int, start: Boolean, point: PointF, current: () -> Boolean): Boolean = false
    abstract fun reportDragging(active: Boolean)
    abstract fun reportError(error: Exception)
}

fun interface ReaderSelectionControllerFactory {
    fun create(context: Context, delegate: ReaderSelectionDelegate): ReaderSelectionController
}
