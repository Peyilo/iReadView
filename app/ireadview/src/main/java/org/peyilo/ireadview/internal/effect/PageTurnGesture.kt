package org.peyilo.ireadview.internal.effect

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import org.peyilo.ireadview.api.effect.TurnPoint

/** Main-thread input mailbox. It never owns candidates, effects or committed locations. */
internal class PageTurnGesture(val origin: TurnPoint, val forward: Boolean, val vertical: Boolean, private val sign: Float, val extent: Float) {
    var touch: TurnPoint = origin
        private set
    var progress = 0f
        private set
    var started = false
    var decision: Boolean? = null
        private set
    var releaseVelocity: TurnPoint? = null
        private set
    private val released = CompletableDeferred<Boolean>()
    private val changes = Channel<Unit>(Channel.CONFLATED)

    fun move(point: TurnPoint) {
        if (decision != null || point == touch) return
        touch = point
        val delta = if (vertical) point.yPx - origin.yPx else point.xPx - origin.xPx
        progress = (delta * sign / extent).coerceIn(0f, 1f)
        wake()
    }
    fun signedVelocity(velocity: Float) = velocity * sign
    fun release(commit: Boolean, velocity: TurnPoint? = null) {
        if (decision != null) return
        releaseVelocity = velocity
        decision = commit
        released.complete(commit)
        wake()
    }
    /** Input, abort and renderer failure share one cancellable, conflated wake-up. */
    fun wake() { changes.trySend(Unit) }
    suspend fun awaitChange() { changes.receive() }
    suspend fun awaitRelease(): Boolean = released.await()
}
