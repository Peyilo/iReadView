package org.peyilo.ireadview.internal.container

import kotlinx.coroutines.*
import org.peyilo.ireadview.api.event.PositionChangeReason
import org.peyilo.ireadview.api.event.ReadLocationChange
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.spi.presentation.*

/** A single commit source. It has no knowledge of books, Android Views or animation geometry. */
internal class NavigationController<L : Any>(
    private val generation: () -> Long,
    private val onCommit: (ReadLocationChange<L>) -> Unit,
    private val onState: (ReadNavigationState) -> Unit,
    private val onCleanupError: (ReaderError, () -> Boolean) -> Unit
) {
    var location: L? = null
        private set
    var state: ReadNavigationState = ReadNavigationState.Idle
        private set
    private var token = 0L
    private var pending: Deferred<NavigationResult<L>>? = null

    suspend fun navigate(
        adapter: ReadContentAdapter<L>,
        target: ReadNavigation<L>,
        options: NavigationOptions,
        reason: PositionChangeReason,
        transitionState: () -> ReadNavigationState = { ReadNavigationState.Settling }
    ): NavigationResult<L> = coroutineScope {
        if (pending?.isActive == true && !options.force) return@coroutineScope NavigationResult.Rejected(ReaderRejection.Busy)
        val operationGeneration = generation()
        val previous = pending
        pending = null
        val operationToken = ++token
        previous?.cancel(SupersededNavigation())
        if (previous != null) changeState(ReadNavigationState.Idle)
        // Reserve ownership before notifying cancellation; an Idle listener may start newer work.
        if (operationToken != token || operationGeneration != generation()) return@coroutineScope NavigationResult.Superseded
        val from = location
        val operation = async(start = CoroutineStart.LAZY) {
            var candidate: PreparedRead<L>? = null
            try {
                changeState(ReadNavigationState.Preparing)
                currentCoroutineContext().ensureActive()
                ensureCurrent(operationToken, operationGeneration, operationGeneration)
                val preparation = adapter.prepare(target)
                // Register Ready resources before checking cancellation so finally always disposes them.
                candidate = (preparation as? ReadPreparation.Ready)?.candidate
                currentCoroutineContext().ensureActive()
                ensureCurrent(operationToken, operationGeneration, candidate?.generation ?: operationGeneration)
                when (preparation) {
                    is ReadPreparation.Unchanged -> NavigationResult.Unchanged(preparation.location)
                    is ReadPreparation.Rejected -> NavigationResult.Rejected(preparation.reason)
                    is ReadPreparation.Failed -> NavigationResult.Failed(preparation.error)
                    is ReadPreparation.Ready -> {
                        val ready = preparation.candidate
                        val readyState = transitionState()
                        currentCoroutineContext().ensureActive()
                        ensureCurrent(operationToken, operationGeneration, ready.generation)
                        require(readyState == ReadNavigationState.Dragging || readyState == ReadNavigationState.Settling)
                        changeState(readyState)
                        currentCoroutineContext().ensureActive()
                        ensureCurrent(operationToken, operationGeneration, ready.generation)
                        val actual = adapter.show(ready)
                        currentCoroutineContext().ensureActive()
                        ensureCurrent(operationToken, operationGeneration, ready.generation)
                        location = actual
                        if (from != actual) onCommit(ReadLocationChange(from, actual, reason))
                        if (from == actual) NavigationResult.Unchanged(actual) else NavigationResult.Moved(from, actual)
                    }
                }
            } catch (_: NavigationReset) {
                if (operationToken == token && operationGeneration == generation()) NavigationResult.Unchanged(from)
                else NavigationResult.Superseded
            } catch (_: SupersededNavigation) {
                NavigationResult.Superseded
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (operationToken != token || operationGeneration != generation()) NavigationResult.Superseded
                else NavigationResult.Failed(ReaderError(ReaderStage.NAVIGATION, "NavigationFailed", error.message ?: "Navigation failed", cause = error))
            } finally {
                try {
                    candidate?.dispose()
                } catch (error: Exception) {
                    onCleanupError(ReaderError(ReaderStage.NAVIGATION, "CandidateDisposeFailed",
                        "Navigation candidate cleanup failed", retryable = false, cause = error)) {
                        operationToken == token && operationGeneration == generation()
                    }
                } finally {
                    // Fatal errors propagate, but cannot leave this owner's navigation state busy.
                    if (operationToken == token) { pending = null; changeState(ReadNavigationState.Idle) }
                }
            }
        }
        pending = operation
        try { operation.await() } catch (_: SupersededNavigation) { NavigationResult.Superseded }
    }

    fun interrupt(superseded: Boolean = false): Boolean {
        val old = pending ?: return false
        pending = null
        token++
        if (superseded) old.cancel(SupersededNavigation()) else old.cancel()
        changeState(ReadNavigationState.Idle)
        return true
    }

    fun reset(reason: PositionChangeReason = PositionChangeReason.Close) {
        interrupt()
        val old = location
        location = null
        if (old != null) onCommit(ReadLocationChange(old, null, reason))
    }

    /** Called only by the active adapter.show path, after candidate generation validation. */
    fun reportTransitionState(value: ReadNavigationState) {
        require(value == ReadNavigationState.Dragging || value == ReadNavigationState.Settling)
        if (pending?.isActive == true && state != value) changeState(value)
    }

    fun reportVisible(location: L, expectedGeneration: Long): ReaderResult<Unit> {
        if (expectedGeneration != generation()) return ReaderResult.Superseded
        if (state != ReadNavigationState.Idle) return ReaderResult.Rejected(ReaderRejection.Busy)
        val old = this.location
        this.location = location
        if (old != location) onCommit(ReadLocationChange(old, location, PositionChangeReason.Scroll))
        return ReaderResult.Success(Unit)
    }

    private fun ensureCurrent(operationToken: Long, operationGeneration: Long, candidateGeneration: Long) {
        if (operationToken != token || operationGeneration != generation() || candidateGeneration != operationGeneration) throw SupersededNavigation()
    }

    private fun changeState(value: ReadNavigationState) { state = value; onState(value) }
}

internal class SupersededNavigation : CancellationException("Replaced by a newer operation")
internal class NavigationReset : RuntimeException("Gesture returned to the committed page")
