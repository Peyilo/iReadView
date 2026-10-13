package org.peyilo.ireadview.internal.selection

import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.graphics.Rect
import android.os.Build
import android.widget.PopupWindow
import android.util.Log
import org.peyilo.ireadview.internal.render.ReaderViewOwnership
import org.peyilo.ireadview.api.model.TextSelection
import org.peyilo.ireadview.spi.selection.*

internal class SelectionToolbarController(private val owner: View) {
    private class CustomBinding(val factory: SelectionToolbarFactory, val root: View) {
        var window: PopupWindow? = null
        var closed = false
    }
    var factory: SelectionToolbarFactory? = null
    private var mode: ActionMode? = null
    private var customBinding: CustomBinding? = null
    private var activeActions: SelectionActions? = null
    private var contentBounds: Rect? = null
    private var serial = 0L

    fun show(selection: TextSelection, actions: SelectionActions, bounds: Rect?) {
        val epoch = ++serial
        try { showAtEpoch(selection, actions, bounds, epoch) }
        catch (error: Exception) {
            if (epoch == serial) throw error
            Log.w("iReadView", "Replaced selection toolbar preparation failed", error)
        }
    }

    private fun showAtEpoch(selection: TextSelection, actions: SelectionActions, bounds: Rect?, epoch: Long) {
        contentBounds = bounds?.let { Rect(it) }
        if (factory == null && mode != null) {
            activeActions = actions
            mode?.menu?.setGroupEnabled(0, true)
            if (epoch != serial) return
            mode?.invalidate()
            if (epoch == serial && Build.VERSION.SDK_INT >= 23) mode?.invalidateContentRect()
            return
        }
        releaseCurrent()
        if (epoch != serial) return
        activeActions = actions
        val custom = factory
        if (custom != null) {
            val root = custom.create(owner.context, selection, actions)
            val next = CustomBinding(custom, root)
            require(ReaderViewOwnership.acquire(root, next)) { "Selection toolbar must return a fresh unowned root" }
            try {
                if (epoch != serial) return
                customBinding = next
                val frame = Rect()
                owner.getWindowVisibleDisplayFrame(frame)
                root.measure(View.MeasureSpec.makeMeasureSpec(frame.width().coerceAtLeast(1), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(frame.height().coerceAtLeast(1), View.MeasureSpec.AT_MOST))
                if (epoch != serial || next.closed) return
                require(root.parent == null) { "Selection toolbar must not attach its root during preparation" }
                val window = PopupWindow(root, root.measuredWidth.coerceAtLeast(1), root.measuredHeight.coerceAtLeast(1), false)
                next.window = window
                window.setOnDismissListener {
                    if (customBinding === next) {
                        customBinding = null
                        activeActions = null
                        val dismissedAt = ++serial
                        closeCustom(next)
                        if (serial == dismissedAt) actions.clearSelection()
                    }
                }
                val location = IntArray(2)
                owner.getLocationOnScreen(location)
                val target = contentBounds ?: Rect(0, 0, owner.width, 0)
                val gap = (8 * owner.resources.displayMetrics.density).toInt()
                val left = (location[0] + target.centerX() - root.measuredWidth / 2).coerceIn(frame.left, maxOf(frame.left, frame.right - root.measuredWidth))
                val above = location[1] + target.top - root.measuredHeight - gap
                val top = (if (above >= frame.top) above else location[1] + target.bottom + gap).coerceIn(frame.top, maxOf(frame.top, frame.bottom - root.measuredHeight))
                window.showAtLocation(owner, Gravity.TOP or Gravity.LEFT, left, top)
            } catch (error: Exception) {
                if (customBinding === next) { customBinding = null; activeActions = null }
                closeCustom(next)
                throw error
            } finally {
                if (epoch != serial) {
                    if (customBinding === next) customBinding = null
                    closeCustom(next)
                }
            }
            return
        }
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, 1, 0, "复制").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(0, 2, 1, "高亮").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (this@SelectionToolbarController.mode !== mode) return false
                val current = activeActions ?: return false
                return when (item.itemId) { 1 -> { current.copyText(); true }; 2 -> { current.highlight(); true }; else -> false }
            }
            override fun onDestroyActionMode(mode: ActionMode) {
                if (this@SelectionToolbarController.mode === mode) {
                    this@SelectionToolbarController.mode = null
                    val current = activeActions; activeActions = null
                    serial++
                    current?.clearSelection()
                }
            }
        }
        val created = if (Build.VERSION.SDK_INT >= 23) owner.startActionMode(floatingCallback(callback), ActionMode.TYPE_FLOATING) else owner.startActionMode(callback)
        if (epoch != serial) { if (created !== mode) created?.finish(); return }
        mode = created
        check(created != null) { "Host could not create the selection action mode" }
    }

    @android.annotation.TargetApi(23)
    private fun floatingCallback(callback: ActionMode.Callback): ActionMode.Callback2 = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = callback.onCreateActionMode(mode, menu)
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = callback.onPrepareActionMode(mode, menu)
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = callback.onActionItemClicked(mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            outRect.set(contentBounds ?: Rect(0, 0, owner.width, owner.height))
        }
    }

    fun dismiss() {
        serial++
        releaseCurrent()
    }

    private fun releaseCurrent() {
        activeActions = null
        val oldMode = mode; mode = null
        val oldCustom = customBinding; customBinding = null
        cleanup("action mode finish") { oldMode?.finish() }
        oldCustom?.let(::closeCustom)
    }

    private fun closeCustom(binding: CustomBinding) {
        if (binding.closed) return
        binding.closed = true
        val window = binding.window; binding.window = null
        try {
            cleanup("dismiss listener removal") { window?.setOnDismissListener(null) }
            cleanup("popup dismissal") { window?.dismiss() }
            cleanup("popup content release") { window?.contentView = null }
            cleanup("custom toolbar unbind") { binding.factory.unbind(binding.root) }
        } finally {
            ReaderViewOwnership.release(binding.root, binding)
        }
    }

    private fun cleanup(step: String, action: () -> Unit) {
        try { action() } catch (error: Exception) { Log.w("iReadView", "Selection toolbar $step failed", error) }
    }

    /** Keep native action-mode chrome in place during dragging so the body does not resize. */
    fun pause() {
        serial++
        activeActions = null
        val oldCustom = customBinding; customBinding = null
        cleanup("action mode pause") { mode?.menu?.setGroupEnabled(0, false) }
        oldCustom?.let(::closeCustom)
    }
}
