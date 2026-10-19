package org.peyilo.ireadview.internal.selection

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import android.util.Log
import org.peyilo.ireadview.internal.render.ReaderViewOwnership
import org.peyilo.ireadview.api.model.ReaderSelection
import org.peyilo.ireadview.spi.selection.*

/** Same popup lifetime for built-in menus and host-defined toolbars. */
internal class SelectionToolbarController(private val owner: View) {
    private class Binding(val factory: SelectionToolbarFactory, val root: View) {
        var window: PopupWindow? = null
        var closed = false
    }
    var factory: SelectionToolbarFactory? = null
    private val defaultFactory = SelectionToolbars.menu(SelectionToolbars.defaultItems())
    private var binding: Binding? = null
    private var serial = 0L

    fun show(selection: ReaderSelection, actions: SelectionActions, bounds: Rect?) {
        val epoch = ++serial
        releaseCurrent()
        if (epoch != serial) return
        val custom = factory ?: defaultFactory
        val root = custom.create(owner.context, selection, actions)
        val next = Binding(custom, root)
        require(ReaderViewOwnership.acquire(root, next)) { "Selection toolbar must return a fresh unowned root" }
        try {
            if (epoch != serial) return
            binding = next
            val frame = Rect()
            owner.getWindowVisibleDisplayFrame(frame)
            (root as? PagedSelectionMenu)?.availableWidthPx = frame.width().coerceAtLeast(1)
            root.measure(View.MeasureSpec.makeMeasureSpec(frame.width().coerceAtLeast(1), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(frame.height().coerceAtLeast(1), View.MeasureSpec.AT_MOST))
            if (epoch != serial || next.closed) return
            require(root.parent == null) { "Selection toolbar must not attach its root during preparation" }
            if (root.measuredWidth == 0 || root.measuredHeight == 0) {
                binding = null; close(next); return
            }
            val window = PopupWindow(root, root.measuredWidth, root.measuredHeight, false)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.elevation = 4 * owner.resources.displayMetrics.density
            next.window = window
            window.setOnDismissListener {
                if (binding === next) {
                    binding = null
                    val dismissedAt = ++serial
                    close(next)
                    if (serial == dismissedAt) actions.clearSelection()
                }
            }
            val location = IntArray(2)
            owner.getLocationOnScreen(location)
            val target = bounds ?: Rect(0, 0, owner.width, 0)
            val gap = (8 * owner.resources.displayMetrics.density).toInt()
            val left = (location[0] + target.centerX() - root.measuredWidth / 2)
                .coerceIn(frame.left, maxOf(frame.left, frame.right - root.measuredWidth))
            val above = location[1] + target.top - root.measuredHeight - gap
            val below = location[1] + target.bottom + gap
            val top = (if (above >= frame.top) above else if (below + root.measuredHeight <= frame.bottom) below
                else location[1] + target.top + gap)
                .coerceIn(frame.top, maxOf(frame.top, frame.bottom - root.measuredHeight))
            window.showAtLocation(owner, Gravity.TOP or Gravity.LEFT, left, top)
            (root as? PagedSelectionMenu)?.onPageChanged = {
                if (binding === next && !next.closed && actions.isValid() && window.isShowing) {
                    owner.getWindowVisibleDisplayFrame(frame)
                    root.availableWidthPx = frame.width().coerceAtLeast(1)
                    root.measure(View.MeasureSpec.makeMeasureSpec(frame.width().coerceAtLeast(1), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(frame.height().coerceAtLeast(1), View.MeasureSpec.AT_MOST))
                    owner.getLocationOnScreen(location)
                    val updatedLeft = (location[0] + target.centerX() - root.measuredWidth / 2)
                        .coerceIn(frame.left, maxOf(frame.left, frame.right - root.measuredWidth))
                    val updatedAbove = location[1] + target.top - root.measuredHeight - gap
                    val updatedBelow = location[1] + target.bottom + gap
                    val updatedTop = (if (updatedAbove >= frame.top) updatedAbove else if (updatedBelow + root.measuredHeight <= frame.bottom) updatedBelow
                        else location[1] + target.top + gap)
                        .coerceIn(frame.top, maxOf(frame.top, frame.bottom - root.measuredHeight))
                    window.update(updatedLeft, updatedTop, root.measuredWidth, root.measuredHeight)
                }
            }
        } catch (error: Exception) {
            if (binding === next) binding = null
            close(next)
            if (epoch == serial) throw error
            Log.w("iReadView", "Replaced selection toolbar preparation failed", error)
        } finally {
            if (epoch != serial) {
                if (binding === next) binding = null
                close(next)
            }
        }
    }
    fun dismiss() { serial++; releaseCurrent() }
    /** Dragging/cross-page navigation hides the popup; releasing the handle rebinds current actions. */
    fun pause() = dismiss()
    private fun releaseCurrent() {
        val old = binding; binding = null
        old?.let(::close)
    }
    private fun close(value: Binding) {
        if (value.closed) return
        value.closed = true
        val window = value.window; value.window = null
        try {
            cleanup("listener removal") { window?.setOnDismissListener(null) }
            cleanup("popup dismissal") { window?.dismiss() }
            cleanup("content release") { window?.contentView = null }
            cleanup("toolbar unbind") { value.factory.unbind(value.root) }
        } finally { ReaderViewOwnership.release(value.root, value) }
    }
    private fun cleanup(step: String, block: () -> Unit) {
        try { block() } catch (error: Exception) { Log.w("iReadView", "Selection toolbar $step failed", error) }
    }
}
