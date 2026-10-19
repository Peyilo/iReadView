package org.peyilo.ireadview.spi.selection

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlinx.coroutines.*

/** Renderer supplies coordinates/anchors; this controller owns the common drag and edge-page policy. */
class ReaderSelectionInteraction(context: Context, private val delegate: ReaderSelectionDelegate) : ReaderSelectionController {
    /** Compatibility name; new extensions may depend directly on ReaderSelectionDelegate. */
    abstract class Delegate : ReaderSelectionDelegate()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session = 0L
    private var sequence = 0L
    private var start: Boolean? = null
    override val isDragging: Boolean get() = start != null
    private var moved = false
    private var down = PointF()
    private var pointer = PointF()
    private var offset = PointF()
    private var moveJob: Job? = null
    private var edgeJob: Job? = null
    private var edge = 0
    override fun onTouch(action: Int, x: Float, y: Float): Boolean {
        if (!delegate.ready()) { val consumed = isDragging; reset(); return consumed }
        if (action == MotionEvent.ACTION_DOWN) {
            reset()
            start = delegate.handleAt(x, y) ?: return false
            down = PointF(x, y); pointer = PointF(x, y)
            offset = delegate.grabOffset(start!!, x, y) ?: PointF()
            delegate.reportDragging(true)
            return true
        }
        val handle = start ?: return false
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) { reset(); return true }
        if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) return true
        pointer = PointF(x + offset.x, y + offset.y)
        if (!moved && kotlin.math.hypot(x - down.x, y - down.y) > slop) moved = true
        if (!moved) { if (action == MotionEvent.ACTION_UP) reset(); return true }
        val epoch = session; val request = ++sequence; val point = PointF(pointer.x, pointer.y)
        moveJob?.cancel()
        if (action == MotionEvent.ACTION_UP) { edgeJob?.cancel(); edgeJob = null; edge = 0 }
        moveJob = scope.launch {
            try {
                delegate.extend(handle, point) { epoch == session && request == sequence && delegate.ready() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (epoch == session) delegate.reportError(failure) }
            finally { if (action == MotionEvent.ACTION_UP && epoch == session) reset() }
        }
        if (action == MotionEvent.ACTION_MOVE) updateEdge()
        return true
    }
    private fun updateEdge() {
        val direction = delegate.edgeDirection(pointer)
        if (edge == direction && edgeJob?.isActive == true) return
        edgeJob?.cancel(); edgeJob = null; edge = direction
        if (direction == 0) return
        val epoch = session
        edgeJob = scope.launch {
            try {
                if (delegate.scrollSpeed(pointer) != null) { scrollEdges(direction, epoch); return@launch }
                while (epoch == session && isDragging && edge == direction && delegate.ready()) {
                    delay(550)
                    moveJob?.join()
                    val handle = start ?: break
                    fun current() = epoch == session && isDragging && edge == direction && delegate.ready()
                    if (!current() || !delegate.movePage(direction, handle, ::current) || !current()) break
                    if (!delegate.extend(handle, PointF(pointer.x, pointer.y), ::current)) break
                    if (delegate.edgeDirection(pointer) != direction) break
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (epoch == session) delegate.reportError(failure) }
        }
    }
    private suspend fun scrollEdges(direction: Int, epoch: Long) {
        delay(ReaderSelectionScrollPolicy.dwellMillis)
        var previous = delegate.nextScrollFrame()
        var remainder = 0f
        fun current() = epoch == session && isDragging && edge == direction && delegate.ready()
        while (current()) {
            val frame = delegate.nextScrollFrame()
            val velocity = delegate.scrollSpeed(pointer) ?: break
            if (velocity * direction <= 0f) break
            moveJob?.join()
            val handle = start ?: break
            if (!delegate.extend(handle, PointF(pointer.x, pointer.y), ::current)) break
            val delta = velocity * (frame - previous).coerceIn(0L, 40L) / 1000f + remainder
            previous = frame
            val requested = delta.toInt(); remainder = delta - requested
            if (requested == 0) continue
            if (!current() || !delegate.scrollBy(requested, handle, PointF(pointer.x, pointer.y), ::current)) break
            if (!delegate.extend(handle, PointF(pointer.x, pointer.y), ::current)) break
        }
    }
    override fun reset() {
        val wasDragging = isDragging
        session++; sequence++; moved = false; edge = 0; start = null
        moveJob?.cancel(); moveJob = null; edgeJob?.cancel(); edgeJob = null
        if (wasDragging) delegate.reportDragging(false)
    }
    override fun release() { reset(); scope.cancel() }
}
