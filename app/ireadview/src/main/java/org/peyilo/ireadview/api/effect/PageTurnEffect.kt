package org.peyilo.ireadview.api.effect

import android.graphics.Bitmap
import android.graphics.Canvas
import org.peyilo.ireadview.spi.presentation.ReadViewport

interface PageSurface {
    val widthPx: Int
    val heightPx: Int
    fun draw(canvas: Canvas)
}
/** Optional rendering policy for surfaces with expensive live compositor work.
 * Effects may take transaction-scoped snapshots within their existing bitmap budget.
 */
interface SnapshotPreferredSurface : PageSurface { val preferSnapshot: Boolean }
/** Stable recorded drawing commands; draw does not depend on live hierarchy visibility.
 * The adapter keeps referenced render resources alive until candidate disposal/turn completion.
 */
interface RecordedPageSurface : PageSurface { val isRecorded: Boolean }
/** Optional background-only metadata, excluding text. Null requests snapshot-based estimation.
 * Read once on the UI thread when a turn starts; existing PageSurface implementations need no changes.
 */
interface PageBackgroundSurface : PageSurface { val backgroundColor: Int? }

interface BitmapLease : AutoCloseable { val bitmap: Bitmap; override fun close() }
interface PageTurnEffectHost {
    /** Invalidates the current visual without advancing the manager's turn frame. */
    fun requestRedraw()
    /** Transaction-owned lease; null when dimensions/budget cannot be satisfied. */
    fun borrowBitmap(widthPx: Int, heightPx: Int): BitmapLease?
}
/** Optional host capability; software/older hosts retain their existing renderer. */
interface HardwarePageTurnEffectHost : PageTurnEffectHost { val hardwareAccelerated: Boolean }
/** Stable nonblank ID; each creation supplies a separately owned renderer with idempotent cleanup. */
interface PageTurnEffectFactory { val id: String; fun create(): PageTurnEffect }
interface PageTurnEffect {
    fun attach(host: PageTurnEffectHost)
    fun supports(direction: TurnDirection, viewport: ReadViewport): Boolean
    fun start(context: PageTurnContext)
    /** Initial state, changed drag input coalesced per display frame, and timed settling frames.
     * A stationary drag does not receive a continuous update clock.
     */
    fun update(frame: PageTurnFrame)
    fun draw(canvas: Canvas, frame: PageTurnFrame)
    fun finish(outcome: TurnOutcome)
    fun abort(reason: TurnAbortReason)
    /** Releases creation/attachment resources, including a failed or superseded attachment. */
    fun detach()
}
/** True only after start has captured everything needed to draw the turn.
 * The Widget may suspend the live content hierarchy until commit/cancel completes.
 * Effects falling back to live surfaces must report false.
 */
interface SnapshotPageTurnEffect : PageTurnEffect { val usesPageSnapshots: Boolean }
enum class TurnDirection { Forward, Backward }
enum class TurnOutcome { Committed, Cancelled }
enum class TurnAbortReason { Cancelled, Superseded, EffectChanged, Detached, Failed }
enum class TurnPhase { Dragging, SettlingCommit, SettlingReset }
data class TurnPoint(val xPx: Float, val yPx: Float) {
    init { require(xPx.isFinite() && yPx.isFinite()) }
}
data class PageTurnContext(val token: Long, val current: PageSurface, val target: PageSurface,
    val viewport: ReadViewport, val direction: TurnDirection, val touchOrigin: TurnPoint? = null,
    val vertical: Boolean = false)
data class PageTurnFrame(val progress: Float, val phase: TurnPhase, val touch: TurnPoint? = null,
    /** Seconds since this visual transaction started, supplied by the Widget's frame clock. */
    val elapsedSeconds: Double = 0.0) {
    init { require(progress.isFinite() && progress in 0f..1f); require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0) }
}
/** Gestures use a viewport-extent fraction and density-independent fling speed.
 * A fast reverse release resets even when the distance threshold was reached.
 * Reduced motion keeps gesture commit policy while skipping visual interpolation.
 */
data class PageTurnOptions(
    val durationMs: Long = 240,
    val reduceMotion: Boolean = false,
    /** Shared cap for both page snapshots and the reusable pool. The 16 MiB default
     * fits two full-size 1080 x 1900 ARGB pages; larger viewports or explicit smaller
     * budgets can still downsample curls, changing text sharpness at live endpoints.
     */
    val bitmapBudgetBytes: Long = 16L * 1024 * 1024,
    val commitDistanceFraction: Float = 0.33f,
    val flingVelocityDpPerSecond: Float = 600f,
    val minimumTurnIntervalMs: Long = 0
) {
    init {
        require(durationMs >= 0 && bitmapBudgetBytes > 0)
        require(minimumTurnIntervalMs >= 0)
        require(commitDistanceFraction.isFinite() && commitDistanceFraction > 0f && commitDistanceFraction <= 1f)
        require(flingVelocityDpPerSecond.isFinite() && flingVelocityDpPerSecond > 0f)
    }
}
enum class EffectFallbackReason { CreationFailed, AttachFailed, UnsupportedDirection, UnsupportedSurface, ResourcesUnavailable, RuntimeFailed }
sealed interface EffectSelection {
    data class Active(val id: String) : EffectSelection
    data class DeferredUntilPaged(val id: String) : EffectSelection
    data class Fallback(val requestedId: String, val appliedId: String, val reason: EffectFallbackReason) : EffectSelection
}
