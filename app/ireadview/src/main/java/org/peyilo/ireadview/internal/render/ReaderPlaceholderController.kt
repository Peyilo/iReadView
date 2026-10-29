package org.peyilo.ireadview.internal.render

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import org.peyilo.ireadview.spi.page.*

/** Owns at most one visible placeholder and invalidates actions before calling host code. */
internal class ReaderPlaceholderController(
    private val root: FrameLayout,
    private val factory: () -> ReaderPlaceholderFactory
) {
    private data class Binding(
        val owner: ReaderPlaceholderFactory,
        val view: View,
        val state: PlaceholderState,
        val key: Any,
        val retryEnabled: Boolean
    )

    private var binding: Binding? = null
    private var serial = 0L
    private var updating = false

    private data class Pending(
        val state: PlaceholderState?,
        val key: Any,
        val actions: PlaceholderActions?
    )

    private var pending: Pending? = null
    private val fallback = DefaultReaderPlaceholderFactory()
    private var failedFactory: ReaderPlaceholderFactory? = null

    fun show(state: PlaceholderState?, key: Any, actions: PlaceholderActions) {
        if (updating) {
            serial++; pending = Pending(state, key, actions); return
        }
        if (state == null) {
            clear(); return
        }
        val requested = factory()
        val chosen = if (requested === failedFactory) fallback else requested
        if (binding?.let { it.owner === chosen && it.state == state && it.key == key && it.retryEnabled == actions.canRetry } == true) return
        updating = true
        val epoch = ++serial
        try {
            removeBinding()
            if (epoch != serial) return
            fun install(owner: ReaderPlaceholderFactory) {
                val retryEnabled = actions.canRetry
                val view = owner.create(root.context, state.kind)
                val next = Binding(owner, view, state, key, retryEnabled)
                require(
                    ReaderViewOwnership.acquire(
                        view,
                        next
                    )
                ) { "Placeholder factory must return a fresh unowned View" }
                binding = next
                if (epoch != serial) {
                    removeBinding(); return
                }
                var consumed = false
                val guarded = object : PlaceholderActions {
                    override val canRetry get() = android.os.Looper.myLooper() == android.os.Looper.getMainLooper() && !consumed && binding === next && epoch == serial && actions.canRetry
                    override fun retry(): Boolean {
                        if (!canRetry || updating || !view.isShown || !view.isEnabled) return false
                        consumed = true
                        val accepted = try {
                            actions.retry()
                        } catch (error: Exception) {
                            Log.w("iReadView", "Placeholder retry failed", error); false
                        }
                        if (!accepted && binding === next) consumed = false
                        return accepted
                    }
                }
                owner.bind(view, state, guarded)
                if (binding === next && epoch == serial) {
                    require(view.parent == null) { "Placeholder bind must not attach its View" }
                    root.addView(view, FrameLayout.LayoutParams(-1, -1))
                }
                if (binding === next && epoch != serial) removeBinding()
            }
            try {
                install(chosen)
            } catch (failure: Exception) {
                Log.w("iReadView", "Placeholder factory failed", failure)
                failedFactory = requested
                removeBinding()
                if (epoch == serial) install(fallback)
            }
        } finally {
            finishUpdate()
        }
    }

    fun clear() {
        serial++
        if (updating) {
            pending = Pending(null, Unit, null); return
        }
        updating = true
        pending = null
        try {
            removeBinding()
        } finally {
            finishUpdate()
        }
    }

    private fun finishUpdate() {
        updating = false
        val next = pending
        pending = null
        val stamp = serial
        if (next != null) root.post {
            if (serial == stamp) {
                if (next.state == null) clear() else show(
                    next.state,
                    next.key,
                    requireNotNull(next.actions)
                )
            }
        }
    }

    private fun removeBinding() {
        val old = binding ?: return
        binding = null
        try {
            try {
                root.removeView(old.view)
            } catch (error: Exception) {
                Log.w("iReadView", "Placeholder removal failed", error)
            }
            try {
                old.owner.unbind(old.view)
            } catch (error: Exception) {
                Log.w("iReadView", "Placeholder unbind failed", error)
            }
        } finally {
            ReaderViewOwnership.release(old.view, old)
        }
    }
}
