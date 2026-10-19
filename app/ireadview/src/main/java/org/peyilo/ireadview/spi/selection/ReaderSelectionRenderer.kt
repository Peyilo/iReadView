package org.peyilo.ireadview.spi.selection

import android.content.Context
import android.graphics.*
import org.peyilo.ireadview.api.style.SelectionStyle

/** Body-owned renderer. Drawing and handle hit geometry must agree; main thread.
 * Canvas/Path inputs are borrowed and must not be retained or modified.
 * close is final disposal, not a content rebind or temporary RecyclerView detach.
 */
interface ReaderSelectionRenderer : AutoCloseable {
    fun createHandle(x: Float, top: Float, bottom: Float, end: Boolean, width: Int, height: Int,
        style: SelectionStyle, density: Float, clampDots: Boolean): ReaderSelectionHandle
    fun drawFill(canvas: Canvas, path: Path, style: SelectionStyle)
    fun drawHandles(canvas: Canvas, handles: List<ReaderSelectionHandle>, style: SelectionStyle, density: Float, width: Int)
    fun handleAt(handles: List<ReaderSelectionHandle>, x: Float, y: Float, style: SelectionStyle, density: Float): Boolean?
    fun dragOffset(handle: ReaderSelectionHandle, x: Float, y: Float): PointF
    fun handleBounds(handle: ReaderSelectionHandle, style: SelectionStyle, density: Float): RectF
    override fun close() {}
}

fun interface ReaderSelectionRendererFactory { fun create(context: Context): ReaderSelectionRenderer }
