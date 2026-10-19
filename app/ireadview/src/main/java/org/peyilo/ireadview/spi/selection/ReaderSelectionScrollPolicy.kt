package org.peyilo.ireadview.spi.selection

import android.view.View
import org.peyilo.ireadview.internal.render.awaitAnimationFrame

/** Shared continuous-selection edge dwell, velocity and frame clock. */
object ReaderSelectionScrollPolicy {
    const val dwellMillis = 350L
    fun speed(y: Float, top: Float, bottom: Float, density: Float): Float {
        val edge = 28f * density
        if (bottom - top < edge * 2f) return 0f
        val depth = when { y < top + edge -> -(top + edge - y) / edge
            y > bottom - edge -> (y - bottom + edge) / edge; else -> 0f }
        return depth.coerceIn(-1f, 1f) * 420f * density
    }
    suspend fun nextFrame(view: View): Long = view.awaitAnimationFrame()
}
