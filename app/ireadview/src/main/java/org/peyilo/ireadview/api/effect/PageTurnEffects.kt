package org.peyilo.ireadview.api.effect

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import org.peyilo.ireadview.spi.presentation.ReadViewport
import org.peyilo.ireadview.internal.effect.googlecurl.GoogleCurlEffect
import org.peyilo.ireadview.internal.effect.PageTurnSnapshots
import org.peyilo.ireadview.internal.effect.ioscurl.IosCurlEffect

/** Each factory creates a fresh renderer; the Widget owns progress and resource lifetime. */
object PageTurnEffects {
    /** iOS12.5.7 recovered control/gesture/mesh/shadow algorithms, pure Kotlin + GLES2, API21+. */
    @JvmStatic @JvmOverloads fun iosCurl(options: IosCurlOptions = IosCurlOptions()): PageTurnEffectFactory =
        PresetFactory("ios_curl", options == IosCurlOptions()) { IosCurlEffect(options) }
    @JvmStatic fun none(): PageTurnEffectFactory = factory("none", 0)
    @JvmStatic fun slide(): PageTurnEffectFactory = factory("slide", 1)
    @JvmStatic fun cover(): PageTurnEffectFactory = factory("cover", 2)
    @JvmStatic fun iBookSlide(): PageTurnEffectFactory = factory("ibook_slide", 3)
    /** Google/MoonReader wave and perspective deformation, without a reflected back face. */
    @JvmStatic @JvmOverloads fun googleCurl(options: GoogleCurlOptions = GoogleCurlOptions()): PageTurnEffectFactory =
        PresetFactory("google_curl", options == GoogleCurlOptions()) { GoogleCurlEffect(options) }
    /** Resolves only the stable built-in IDs; custom factories must be supplied by their owner. */
    @JvmStatic fun fromId(id: String): PageTurnEffectFactory? = when (id) {
        "ios_curl" -> iosCurl()
        "none" -> none()
        "slide" -> slide()
        "cover" -> cover()
        "ibook_slide" -> iBookSlide()
        "google_curl" -> googleCurl()
        else -> null
    }
    internal fun builtInId(factory: PageTurnEffectFactory): String? = (factory as? PresetFactory)?.takeIf { it.restorable }?.id
    private fun factory(name: String, kind: Int) = PresetFactory(name) { SlidingEffect(kind) }
    private class PresetFactory(override val id: String, val restorable: Boolean = true, private val renderer: () -> PageTurnEffect) : PageTurnEffectFactory {
        override fun create() = renderer()
    }
}

private class SlidingEffect(private val kind: Int) : SnapshotPageTurnEffect {
    private var context: PageTurnContext? = null
    private var host: PageTurnEffectHost? = null
    private val snapshots = PageTurnSnapshots()
    override val usesPageSnapshots get() = snapshots.current != null && snapshots.target != null ||
        ((context?.current as? RecordedPageSurface)?.isRecorded == true &&
            (context?.target as? RecordedPageSurface)?.isRecorded == true)
    private val texture = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pageRect = RectF()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun attach(host: PageTurnEffectHost) { this.host = host }
    override fun supports(direction: TurnDirection, viewport: ReadViewport) = viewport.isValid
    override fun start(context: PageTurnContext) {
        clear()
        this.context = context
        pageRect.set(0f, 0f, context.viewport.widthPx.toFloat(), context.viewport.heightPx.toFloat())
        // Native modern views keep their cheap display-list path. External renderers
        // request bounded snapshots, including the same budgeted scale ladder as curls.
        // Legacy native views retain live drawing unless full-resolution images fit.
        val preferSnapshot = (context.current as? SnapshotPreferredSurface)?.preferSnapshot == true ||
            (context.target as? SnapshotPreferredSurface)?.preferSnapshot == true
        if (kind != 0 && (android.os.Build.VERSION.SDK_INT < 29 || preferSnapshot))
            host?.let { snapshots.capture(it, context, fullResolutionOnly = !preferSnapshot) }
    }
    override fun update(frame: PageTurnFrame) {}
    override fun draw(canvas: Canvas, frame: PageTurnFrame) {
        val turn = context ?: return
        val width = turn.viewport.widthPx.toFloat()
        val progress = frame.progress
        val sign = if (turn.direction == TurnDirection.Forward) {
            if (turn.viewport.rightToLeft) -1f else 1f
        } else if (turn.viewport.rightToLeft) 1f else -1f
        fun draw(surface: PageSurface, x: Float) {
            val save = canvas.save()
            try {
                canvas.translate(x, 0f)
                val bitmap = if (surface === turn.current) snapshots.current else snapshots.target
                if (bitmap != null) canvas.drawBitmap(bitmap, null, pageRect, texture) else surface.draw(canvas)
            } finally { canvas.restoreToCount(save) }
        }
        when (kind) {
            0 -> draw(if (progress < 1f) turn.current else turn.target, 0f)
            1 -> { draw(turn.current, -sign * width * progress); draw(turn.target, sign * width * (1f - progress)) }
            2, 3 -> {
                val parallax = if (kind == 3) 0.22f else 0f
                if (turn.direction == TurnDirection.Forward) {
                    draw(turn.target, sign * width * (1f - progress) * parallax)
                    draw(turn.current, -sign * width * progress)
                    edgeShadow(canvas, if (sign > 0) width * (1f - progress) else width * progress, sign, progress, turn)
                } else {
                    draw(turn.current, -sign * width * progress * parallax)
                    draw(turn.target, sign * width * (1f - progress))
                    edgeShadow(canvas, if (sign > 0) width * (1f - progress) else width * progress, -sign, progress, turn)
                }
            }
        }
    }
    private fun edgeShadow(canvas: Canvas, edge: Float, direction: Float, progress: Float, turn: PageTurnContext) {
        if (progress <= 0f || progress >= 1f) return
        val width = 18f * turn.viewport.density
        val end = edge + direction * width
        // The shadow belongs to the moving top sheet. Its disappearance must follow
        // the sheet's visible width for forward completion and backward appearance.
        val movingFraction = if (turn.direction == TurnDirection.Forward) 1f - progress else progress
        val fade = (turn.viewport.widthPx * movingFraction / width).coerceIn(0f, 1f)
        val alpha = (0x55 * fade).toInt()
        shadow.shader = LinearGradient(edge, 0f, end, 0f, alpha shl 24, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(minOf(edge, end), 0f, maxOf(edge, end), turn.viewport.heightPx.toFloat(), shadow)
        shadow.shader = null
    }
    private fun clear() { snapshots.close(); context = null; shadow.shader = null }
    override fun finish(outcome: TurnOutcome) { clear() }
    override fun abort(reason: TurnAbortReason) { clear() }
    override fun detach() { clear(); host = null }
}
