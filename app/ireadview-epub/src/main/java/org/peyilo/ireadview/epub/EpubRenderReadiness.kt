package org.peyilo.ireadview.epub

import android.os.Build
import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import org.peyilo.ireadview.spi.selection.ReaderSelectionScrollPolicy

/** One render fence per DOM mutation, owned by the body rather than a prefetch
 * waiter. Cancelling read-ahead must not discard a loaded document or restart
 * Chromium's visual-state round trip when navigation takes the same page.
 */
internal class EpubRenderReadiness(
    private val scope: CoroutineScope,
    private val barrier: suspend () -> Unit
) {
    private var pending: Deferred<Unit>? = null
    fun changed() {
        clear()
        pending = scope.async(start = CoroutineStart.UNDISPATCHED) { barrier() }
    }
    suspend fun awaitReady() { pending?.await() }
    fun clear() { pending?.cancel(); pending = null }
}

/** Platform differences stay at the WebView boundary, out of page/navigation code. */
internal class EpubVisualState(private val web: WebView) {
    private var serial = 0L
    suspend fun awaitReady() = withTimeout(5000) {
        if (Build.VERSION.SDK_INT >= 23) suspendCancellableCoroutine<Unit> { continuation ->
            web.postVisualStateCallback(++serial, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
        } else {
            ReaderSelectionScrollPolicy.nextFrame(web)
            ReaderSelectionScrollPolicy.nextFrame(web)
        }
    }
    suspend fun awaitPresented() {
        awaitReady()
        ReaderSelectionScrollPolicy.nextFrame(web)
    }
}
