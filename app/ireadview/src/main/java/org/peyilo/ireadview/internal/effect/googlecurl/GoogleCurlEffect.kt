package org.peyilo.ireadview.internal.effect.googlecurl

import android.graphics.*
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.internal.effect.EffectResourceUnavailable
import org.peyilo.ireadview.internal.effect.PageTurnSnapshots
import org.peyilo.ireadview.spi.presentation.ReadViewport
import kotlin.math.PI
import kotlin.math.sin

/** Google/MoonReader travelling front wave. Owns one transaction's two page snapshots. */
internal class GoogleCurlEffect(private val options: GoogleCurlOptions) : SnapshotPageTurnEffect {
    private var turn: PageTurnContext? = null
    private var host: PageTurnEffectHost? = null
    private val snapshots = PageTurnSnapshots()
    private val texture = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pageRect = RectF()
    private val wave = GoogleCurlGeometry(options.radiusFraction)
    private val columns = options.meshColumns
    private val rows = options.meshRows
    private val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val colors = IntArray(vertices.size / 2)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowRect = RectF()
    override val usesPageSnapshots get() = snapshots.current != null && snapshots.target != null

    override fun attach(host: PageTurnEffectHost) { this.host = host }
    override fun supports(direction: TurnDirection, viewport: ReadViewport) = viewport.isValid
    override fun start(context: PageTurnContext) {
        clear()
        turn = context
        pageRect.set(0f, 0f, context.viewport.widthPx.toFloat(), context.viewport.heightPx.toFloat())
        if (!snapshots.capture(host ?: throw EffectResourceUnavailable(), context)) throw EffectResourceUnavailable()
    }
    override fun update(frame: PageTurnFrame) {
        val context = turn ?: return
        val progress = if (context.direction == TurnDirection.Forward) frame.progress else 1f - frame.progress
        val width = pageRect.width()
        val height = pageRect.height()
        val point = control(context, progress, frame.touch)
        wave.update(((width - point.xPx) / (2f * width)).coerceIn(0f, 1f))
        var edge = Float.NEGATIVE_INFINITY
        for (column in 0..columns) {
            val materialColumn = if (context.viewport.rightToLeft) columns - column else column
            val u = materialColumn.toFloat() / columns
            val brightness = wave.project(u, 0f, width, height, vertices, column * 2)
            val color = Color.rgb(brightness, brightness, brightness)
            colors[column] = color
            edge = maxOf(edge, vertices[column * 2])
            for (row in 1..rows) {
                val index = row * (columns + 1) + column
                wave.project(u, row.toFloat() / rows, width, height, vertices, index * 2)
                colors[index] = color
            }
        }
        val size = maxOf(.001f, options.shadowWidthDp * context.viewport.density)
        shadowRect.set(edge, 0f, edge + size, height)
        val visibleShadow = (edge / size).coerceIn(0f, 1f)
        shadow.shader = LinearGradient(edge, 0f, edge + size, 0f,
            Color.argb((255 * options.shadowAlpha * visibleShadow).toInt(), 0, 0, 0),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    override fun draw(canvas: Canvas, frame: PageTurnFrame) {
        val context = turn ?: return
        if (frame.progress <= 0f) { context.current.draw(canvas); return }
        if (frame.progress >= 1f) { context.target.draw(canvas); return }
        val forward = context.direction == TurnDirection.Forward
        val bitmap = (if (forward) snapshots.current else snapshots.target) ?: return
        val background = (if (forward) snapshots.target else snapshots.current) ?: return
        canvas.drawBitmap(background, null, pageRect, texture)
        val save = canvas.save()
        try {
            if (context.viewport.rightToLeft) { canvas.translate(pageRect.width(), 0f); canvas.scale(-1f, 1f) }
            if (options.shadowWidthDp > 0f) canvas.drawRect(shadowRect, shadow)
            canvas.drawBitmapMesh(bitmap, columns, rows, vertices, 0, colors, 0, texture)
        } finally { canvas.restoreToCount(save) }
    }
    private fun canonical(point: TurnPoint?, context: PageTurnContext) = point?.let {
        TurnPoint(if (context.viewport.rightToLeft) context.viewport.widthPx - it.xPx else it.xPx, it.yPx)
    }
    private fun control(context: PageTurnContext, progress: Float, touch: TurnPoint?): TurnPoint {
        val width = context.viewport.widthPx.toFloat()
        val height = context.viewport.heightPx.toFloat()
        val origin = canonical(context.touchOrigin, context)
        val current = canonical(touch, context)
        val initialY = (origin?.yPx ?: height * .65f).coerceIn(0f, height)
        val vertical = if (origin != null && current != null) current.yPx - origin.yPx else 0f
        val point = TurnPoint(width * (1f - 2f * progress),
            (initialY + vertical * sin(PI.toFloat() * progress)).coerceIn(-height, 2f * height))
        return options.controlPointPolicy.resolve(GoogleCurlControlContext(point, origin, current,
            progress, context.direction, context.viewport))
    }
    private fun clear() { snapshots.close(); turn = null; shadow.shader = null }
    override fun finish(outcome: TurnOutcome) = clear()
    override fun abort(reason: TurnAbortReason) = clear()
    override fun detach() { clear(); host = null }
}
