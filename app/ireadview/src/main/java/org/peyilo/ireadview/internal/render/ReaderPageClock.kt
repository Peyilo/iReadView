package org.peyilo.ireadview.internal.render

import android.util.Log
import android.view.View
import org.peyilo.ireadview.spi.presentation.BookPresentation

/** One wall-clock callback per Widget. It never calls navigation or publishes reader state. */
internal class ReaderPageClock(
    private val owner: View,
    private val current: () -> BookPresentation?,
    private val generation: () -> Long,
    private val enabled: () -> Boolean
) {
    private var scheduled = false
    private var updating = false
    private var failed: BookPresentation? = null
    private var failedGeneration = -1L
    private val tick = Runnable { scheduled = false; update() }

    fun update() {
        if (updating) return
        updating = true
        try {
            updateInternal()
        } finally {
            updating = false
        }
    }

    private fun updateInternal() {
        val content = current()
        val stamp = generation()
        if (!enabled() || !owner.isAttachedToWindow || !owner.isShown || owner.windowVisibility != View.VISIBLE ||
            content == null || failed === content && failedGeneration == stamp
        ) {
            stop(); return
        }
        try {
            if (!content.needsTimeUpdates) {
                stop(); return
            }
            if (scheduled) return
            content.onTimeChanged(System.currentTimeMillis())
            // A custom callback may close, replace or release the Widget.
            if (!enabled() || current() !== content || generation() != stamp || !owner.isShown || !owner.isAttachedToWindow || owner.windowVisibility != View.VISIBLE) return
            val remainder = ((System.currentTimeMillis() % 60000L) + 60000L) % 60000L
            scheduled = owner.postDelayed(tick, 60000L - remainder)
        } catch (failure: Exception) {
            failed = content
            failedGeneration = stamp
            stop()
            Log.w("iReadView", "Page time update failed", failure)
        }
    }

    fun stop() {
        owner.removeCallbacks(tick); scheduled = false
    }

    fun clear() {
        stop(); failed = null; failedGeneration = -1L
    }
}
