package org.peyilo.ireadview.internal.render

import android.view.View
import android.view.ViewTreeObserver
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** View-owned frame wait; cancellation removes the scheduled callback. */
internal suspend fun View.awaitAnimationFrame(): Long =
    suspendCancellableCoroutine { continuation ->
        val callback =
            Runnable { if (continuation.isActive) continuation.resume(SystemClock.uptimeMillis()) }
        postOnAnimation(callback)
        continuation.invokeOnCancellation { removeCallbacks(callback) }
    }

internal suspend fun View.awaitMeasured() {
    if (isLaidOut && !isLayoutRequested && width > 0 && height > 0) return
    suspendCancellableCoroutine<Unit> { continuation ->
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (isLaidOut && !isLayoutRequested && width > 0 && height > 0 && continuation.isActive) {
                    viewTreeObserver.removeOnPreDrawListener(this)
                    continuation.resume(Unit)
                }
                return true
            }
        }
        viewTreeObserver.addOnPreDrawListener(listener)
        continuation.invokeOnCancellation {
            if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(
                listener
            )
        }
    }
}
