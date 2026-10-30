package org.peyilo.ireadview.internal.effect

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout

/** Keeps a compositor attached while idle without retaining a navigation transaction. */
internal class PageTurnEffectOverlay(context: Context, private val render: (Canvas) -> Unit) :
    FrameLayout(context) {
    var renderView: View? = null
        private set

    var turning = false
        set(value) {
            field = value
            updateVisibility()
            invalidate()
        }

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        updateVisibility()
    }

    fun bindRenderView(view: View?) {
        if (renderView === view) return
        require(view?.parent == null) { "Effect render views must be unattached" }
        renderView?.let(::removeView)
        renderView = view
        view?.let {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        updateVisibility()
    }

    override fun onDraw(canvas: Canvas) {
        if (!turning) return
        val saved = canvas.save()
        try {
            canvas.clipRect(0, 0, width, height)
            render(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun updateVisibility() {
        visibility = if (turning || renderView != null) View.VISIBLE else View.GONE
    }
}
