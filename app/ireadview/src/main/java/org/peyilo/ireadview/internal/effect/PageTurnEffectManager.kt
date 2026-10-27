package org.peyilo.ireadview.internal.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.state.ReaderError
import org.peyilo.ireadview.api.state.ReaderStage
import org.peyilo.ireadview.api.state.ReadNavigationState
import org.peyilo.ireadview.internal.container.NavigationReset
import org.peyilo.ireadview.internal.container.SupersededNavigation
import org.peyilo.ireadview.internal.render.awaitAnimationFrame
import org.peyilo.ireadview.internal.effect.input.EffectInputProvider
import org.peyilo.ireadview.spi.presentation.ReadViewport

/** Owns the clock and resource leases; renderers cannot commit a reading position. */
internal class PageTurnEffectManager(
    context: Context,
    private val hardwareAccelerated: () -> Boolean,
    private val fallback: (String, EffectFallbackReason) -> Unit,
    private val captureConfigurationOwnership: () -> (() -> Boolean),
    private val diagnostic: (ReaderError, Boolean) -> Unit
) {
    var options = PageTurnOptions()
        private set
    var factory: PageTurnEffectFactory = PageTurnEffects.none()
        private set
    var effectId: String = "none"
        private set
    private var effect = factory.create()
    val inputProvider get() = effect as? EffectInputProvider
    private var attached = false
    private var installationSequence = 0L
    private var serial = 0L
    private var active: Turn? = null
    private val pool = ArrayDeque<Bitmap>()
    private var allocatedBytes = 0L
    val overlay: View = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val turn = active ?: return
            if (turn.terminal || turn.failure != null) return
            val save = canvas.save()
            try { canvas.clipRect(0, 0, width, height); turn.effect.draw(canvas, turn.frame) }
            catch (error: Exception) { turn.failure = error; turn.gesture?.wake() }
            finally { canvas.restoreToCount(save) }
        }
    }.apply { visibility = View.GONE; setWillNotDraw(false) }
    private val host = object : HardwarePageTurnEffectHost {
        override val hardwareAccelerated get() = this@PageTurnEffectManager.hardwareAccelerated()
        override fun requestRedraw() { if (active != null) overlay.invalidate() }
        override fun borrowBitmap(widthPx: Int, heightPx: Int): BitmapLease? {
            val turn = active?.takeIf { !it.terminal } ?: return null
            if (widthPx <= 0 || heightPx <= 0) return null
            val rowBytes = widthPx.toLong() * 4L
            if (heightPx.toLong() > options.bitmapBudgetBytes / rowBytes) return null
            val bytes = rowBytes * heightPx
            if (bytes <= 0 || bytes > options.bitmapBudgetBytes) return null
            var bitmap = pool.firstOrNull { it.width == widthPx && it.height == heightPx }
            if (bitmap != null) pool.remove(bitmap)
            else {
                while (allocatedBytes + bytes > options.bitmapBudgetBytes && pool.isNotEmpty()) recycle(pool.removeFirst())
                if (allocatedBytes + bytes > options.bitmapBudgetBytes) return null
                bitmap = try { Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) { return null }
                allocatedBytes += bitmap.allocationByteCount
            }
            val leased = bitmap
            leased.eraseColor(0)
            val lease = Lease(leased, turn)
            turn.leases.add(lease)
            return lease
        }
    }
    fun setOptions(value: PageTurnOptions) {
        options = value
        while (allocatedBytes > value.bitmapBudgetBytes && pool.isNotEmpty()) recycle(pool.removeFirst())
    }
    /** Null means a newer installation/lifecycle or caller configuration superseded this attempt. */
    fun install(value: PageTurnEffectFactory, enabled: Boolean, stillCurrent: () -> Boolean = { true }): EffectSelection? {
        val sequence = ++installationSequence
        fun current() = sequence == installationSequence && stillCurrent()
        val requestedId = value.id
        if (!current()) return null
        require(requestedId.isNotBlank())
        abort(TurnAbortReason.EffectChanged)
        if (!current()) return null
        detachInstalled(replaceWithNone = true, notificationAllowed = ::current)
        if (!current()) return null
        var reason = EffectFallbackReason.CreationFailed
        var proposed: PageTurnEffect? = null
        fun discard() {
            proposed?.let { candidate -> cleanup(requestedId, "Detach", null, ::current) { candidate.detach() } }
        }
        return try {
            proposed = value.create()
            if (!current()) { discard(); return null }
            reason = EffectFallbackReason.AttachFailed
            proposed.attach(host)
            if (!current()) { discard(); return null }
            factory = value; effectId = requestedId; effect = proposed
            attached = true
            if (enabled) EffectSelection.Active(requestedId) else EffectSelection.DeferredUntilPaged(requestedId)
        } catch (_: Exception) {
            discard()
            if (!current()) return null
            fallback(requestedId, reason)
            if (!current()) null else EffectSelection.Fallback(requestedId, "none", reason)
        }
    }
    suspend fun <L> transition(current: PageSurface?, target: PageSurface?, viewport: ReadViewport,
        direction: TurnDirection, showOverlay: (Boolean) -> Unit, show: suspend () -> L,
        gesture: PageTurnGesture? = null, animated: Boolean = true, vertical: Boolean = false,
        phaseChanged: (ReadNavigationState) -> Unit = {}): L {
        val sequence = installationSequence
        val owner = captureConfigurationOwnership()
        val renderer = effect
        val requested = effectId
        suspend fun ensureOperationCurrent() {
            currentCoroutineContext().ensureActive()
            if (sequence != installationSequence || !owner()) throw SupersededNavigation()
        }
        if (active != null) abort(TurnAbortReason.Superseded)
        ensureOperationCurrent()
        gesture?.started = true
        if (gesture != null && gesture.decision == null) phaseChanged(ReadNavigationState.Dragging)
        ensureOperationCurrent()
        suspend fun withoutEffect(): L {
            if (gesture != null && !gesture.awaitRelease()) throw NavigationReset()
            ensureOperationCurrent()
            phaseChanged(ReadNavigationState.Settling)
            ensureOperationCurrent()
            val result = show()
            ensureOperationCurrent()
            return result
        }
        if (gesture?.decision == false) throw NavigationReset()
        if (!animated || effectId == "none" || options.reduceMotion || options.durationMs == 0L) return withoutEffect()
        val surfacesSupported = current != null && target != null && current.widthPx == viewport.widthPx && target.widthPx == viewport.widthPx &&
            current.heightPx == viewport.heightPx && target.heightPx == viewport.heightPx
        ensureOperationCurrent()
        if (!surfacesSupported) {
            degrade(requested, EffectFallbackReason.UnsupportedSurface)
            return withoutEffect()
        }
        val supported = try { renderer.supports(direction, viewport) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        ensureOperationCurrent()
        if (!supported) { degrade(requested, EffectFallbackReason.UnsupportedDirection); return withoutEffect() }
        val turn = Turn(renderer, requested, ++serial, gesture)
        active = turn
        try {
            var failure: Exception? = null
            try {
                turn.started = true
                turn.effect.start(PageTurnContext(turn.token, requireNotNull(current), requireNotNull(target), viewport, direction, gesture?.origin, vertical))
                turn.resetClock()
                ensureTurnActive(turn)
                turn.frame = PageTurnFrame(gesture?.progress ?: 0f, if (gesture == null) TurnPhase.SettlingCommit else TurnPhase.Dragging, gesture?.touch)
                turn.effect.update(turn.frame)
                ensureTurnActive(turn)
                showOverlay((turn.effect as? SnapshotPageTurnEffect)?.usesPageSnapshots == true)
                ensureTurnActive(turn)
                overlay.visibility = View.VISIBLE
                while (gesture != null && gesture.decision == null) {
                    val warming = (turn.effect as? PageTurnDynamics)?.warmingUp == true
                    if (!warming) gesture.awaitChange()
                    ensureTurnActive(turn)
                    if (gesture.decision != null) break
                    // Coalesce input to the latest state for this display frame.
                    overlay.awaitAnimationFrame()
                    ensureTurnActive(turn)
                    if (gesture.decision != null) break
                    val frame = PageTurnFrame(gesture.progress, TurnPhase.Dragging, gesture.touch, turn.elapsedSeconds())
                    if (frame != turn.frame) {
                        turn.frame = frame
                        turn.effect.update(frame)
                        ensureTurnActive(turn)
                        overlay.invalidate()
                    }
                }
                ensureTurnActive(turn)
                val commit = gesture?.decision != false
                phaseChanged(ReadNavigationState.Settling)
                ensureTurnActive(turn)
                animate(turn, gesture?.progress ?: 0f, if (commit) 1f else 0f,
                    if (commit) TurnPhase.SettlingCommit else TurnPhase.SettlingReset, gesture)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error }
            ensureOperationCurrent()
            if (failure != null) {
                terminate(turn, TurnAbortReason.Failed)
                ensureOperationCurrent()
                if (effect === turn.effect) {
                    degrade(requested, if (failure is EffectResourceUnavailable) EffectFallbackReason.ResourcesUnavailable else EffectFallbackReason.RuntimeFailed)
                }
            }
            // A renderer failure changes the visual, never the user's commit/reset decision.
            if (gesture != null && !gesture.awaitRelease()) {
                if (!turn.terminal) finish(turn, TurnOutcome.Cancelled)
                throw NavigationReset()
            }
            ensureOperationCurrent()
            phaseChanged(ReadNavigationState.Settling)
            ensureOperationCurrent()
            val result = show()
            ensureOperationCurrent()
            if (!turn.terminal) finish(turn, TurnOutcome.Committed)
            ensureOperationCurrent()
            return result
        } finally {
            if (!turn.terminal) terminate(turn, TurnAbortReason.Cancelled)
            turn.leases.toList().forEach { it.close() }
            if (active === turn) { active = null; overlay.visibility = View.GONE }
        }
    }
    private suspend fun ensureTurnActive(turn: Turn) {
        currentCoroutineContext().ensureActive()
        if (turn.terminal || active !== turn) throw CancellationException("Turn aborted")
        turn.failure?.let { throw it }
    }
    private suspend fun animate(turn: Turn, from: Float, to: Float, phase: TurnPhase, gesture: PageTurnGesture?) {
        val dynamics = turn.effect as? PageTurnDynamics
        if (from == to && dynamics == null) {
            ensureTurnActive(turn)
            turn.frame = PageTurnFrame(to, phase, gesture?.origin)
            turn.effect.update(turn.frame)
            ensureTurnActive(turn)
            overlay.invalidate()
            return
        }
        // A release can arrive before the coalesced last MOVE is rendered.
        // Hand off that exact state before advancing the settling clock.
        val initial = PageTurnFrame(from, phase, gesture?.touch, turn.elapsedSeconds())
        val releaseTouch = initial.touch
        if (initial != turn.frame) {
            ensureTurnActive(turn)
            turn.frame = initial
            turn.effect.update(initial)
            ensureTurnActive(turn)
            overlay.invalidate()
        }
        val duration = (dynamics?.beginSettling(to == 1f, gesture?.releaseVelocity)
            ?: (options.durationMs * kotlin.math.abs(to - from)).toLong()).coerceAtLeast(1L)
        val start = overlay.awaitAnimationFrame()
        while (true) {
            ensureTurnActive(turn)
            val now = overlay.awaitAnimationFrame()
            ensureTurnActive(turn)
            val fraction = ((now - start).toDouble() / duration).coerceIn(0.0, 1.0)
            val f = fraction.toFloat()
            val eased = f * f * (3f - 2f * f)
            val touch = gesture?.let {
                val released = requireNotNull(releaseTouch)
                TurnPoint(it.origin.xPx + (released.xPx - it.origin.xPx) * (1f - eased),
                    it.origin.yPx + (released.yPx - it.origin.yPx) * (1f - eased))
            }
            turn.frame = PageTurnFrame((from + (to - from) * eased).coerceIn(0f, 1f), phase, touch, turn.elapsedSeconds())
            if (dynamics != null) dynamics.updateSettling(turn.frame, fraction) else turn.effect.update(turn.frame)
            ensureTurnActive(turn)
            overlay.invalidate()
            if (fraction >= 1f) break
        }
    }
    private fun finish(turn: Turn, outcome: TurnOutcome) {
        turn.terminal = true
        if (!cleanup(turn.effectId, "Finish", turn.token) { turn.effect.finish(outcome) } && effect === turn.effect) {
            degrade(turn.effectId, EffectFallbackReason.RuntimeFailed)
        }
    }
    fun abort(reason: TurnAbortReason) {
        val turn = active
        // End ownership before plugin callbacks so a nested turn is not cleared afterwards.
        active = null; overlay.visibility = View.GONE
        turn?.let {
            terminate(turn, reason)
            turn.leases.toList().forEach { it.close() }
        }
    }
    fun detach() {
        val sequence = ++installationSequence
        abort(TurnAbortReason.Detached)
        if (sequence != installationSequence) return
        detachInstalled()
        while (pool.isNotEmpty()) recycle(pool.removeFirst())
    }
    fun reattach(enabled: Boolean) { install(factory, enabled) }
    private fun terminate(turn: Turn, reason: TurnAbortReason) {
        if (turn.terminal) return
        turn.terminal = true
        turn.gesture?.wake()
        if (turn.started && !cleanup(turn.effectId, "Abort", turn.token) { turn.effect.abort(reason) } && effect === turn.effect) {
            degrade(turn.effectId, EffectFallbackReason.RuntimeFailed)
        }
        turn.leases.toList().forEach { it.close() }
    }
    private fun degrade(requested: String, reason: EffectFallbackReason) {
        val sequence = installationSequence
        val failed = effect
        detachInstalled()
        if (sequence != installationSequence || effect !== failed) return
        factory = PageTurnEffects.none(); effectId = "none"; effect = factory.create()
        fallback(requested, reason)
    }
    private fun detachInstalled(replaceWithNone: Boolean = false, notificationAllowed: () -> Boolean = { true }) {
        val previous = effect
        val id = effectId
        val wasAttached = attached
        // End ownership before invoking plugin code, including when detach throws.
        attached = false
        if (replaceWithNone) {
            // Options-only reentry must not leave an already detached renderer selected.
            factory = PageTurnEffects.none(); effectId = "none"; effect = factory.create()
        }
        if (wasAttached) cleanup(id, "Detach", active?.token, notificationAllowed) { previous.detach() }
    }
    private inline fun cleanup(id: String, operation: String, token: Long?,
        notificationAllowed: () -> Boolean = { true }, block: () -> Unit): Boolean {
        val sequence = installationSequence
        val owner = captureConfigurationOwnership()
        return try { block(); true } catch (error: Exception) {
            diagnostic(ReaderError(
                ReaderStage.EFFECT, "Effect${operation}Failed",
                "Effect '$id' failed during ${operation.lowercase()}" + (token?.let { " (turn $it)" } ?: ""),
                retryable = false, cause = error
            ), sequence == installationSequence && owner() && notificationAllowed())
            false
        }
    }
    private fun recycle(bitmap: Bitmap) { allocatedBytes -= bitmap.allocationByteCount; bitmap.recycle() }
    private inner class Lease(private val owned: Bitmap, private val turn: Turn) : BitmapLease {
        private val bytes = owned.allocationByteCount.toLong()
        private var closed = false
        override val bitmap get(): Bitmap { check(!closed) { "Bitmap lease has ended" }; return owned }
        override fun close() {
            if (closed) return
            closed = true; turn.leases.remove(this)
            if (owned.isRecycled) allocatedBytes -= bytes
            else if (allocatedBytes > options.bitmapBudgetBytes) recycle(owned) else pool.addLast(owned)
        }
    }
    private inner class Turn(val effect: PageTurnEffect, val effectId: String, val token: Long, val gesture: PageTurnGesture?) {
        private var beganAt = android.os.SystemClock.uptimeMillis()
        fun resetClock() { beganAt = android.os.SystemClock.uptimeMillis() }
        fun elapsedSeconds() = if (effect is PageTurnDynamics) (android.os.SystemClock.uptimeMillis() - beganAt) / 1000.0 else 0.0
        var started = false
        var terminal = false
        var failure: Exception? = null
        var frame = PageTurnFrame(0f, TurnPhase.SettlingCommit)
        val leases = HashSet<Lease>()
    }
}
