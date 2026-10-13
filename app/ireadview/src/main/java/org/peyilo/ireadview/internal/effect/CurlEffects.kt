package org.peyilo.ireadview.internal.effect

import android.graphics.*
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.spi.presentation.ReadViewport
import kotlin.math.*

internal class EffectResourceUnavailable : RuntimeException("No bitmap lease available for page curl")

/** Capture both pages once so every animation frame uses the same prepared content. */
internal abstract class SnapshotCurlEffect : PageTurnEffect {
    protected var turn: PageTurnContext? = null
    private var host: PageTurnEffectHost? = null
    private val snapshots = PageTurnSnapshots()
    protected val texture = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    protected val pageRect = RectF()
    // Capture text in display orientation, then reflect material texture coordinates for RTL.
    protected val sheetBitmap: Bitmap? get() = if (turn?.direction == TurnDirection.Forward)
        snapshots.current else snapshots.target
    override fun attach(host: PageTurnEffectHost) { this.host = host }
    override fun supports(direction: TurnDirection, viewport: ReadViewport) = viewport.isValid
    override fun start(context: PageTurnContext) {
        clear()
        turn = context
        val width = context.viewport.widthPx
        val height = context.viewport.heightPx
        pageRect.set(0f, 0f, width.toFloat(), height.toFloat())
        if (!snapshots.capture(host ?: throw EffectResourceUnavailable(), context))
            throw EffectResourceUnavailable()
    }
    protected fun sheetProgress(frame: PageTurnFrame): Float =
        if (turn?.direction == TurnDirection.Forward) frame.progress else 1f - frame.progress
    final override fun update(frame: PageTurnFrame) { turn?.let { geometry(it, sheetProgress(frame), frame.touch) } }
    protected abstract fun geometry(context: PageTurnContext, progress: Float, touch: TurnPoint?)
    final override fun draw(canvas: Canvas, frame: PageTurnFrame) {
        val context = turn ?: return
        val bitmap = sheetBitmap ?: return
        val background = (if (context.direction == TurnDirection.Forward)
            snapshots.target else snapshots.current) ?: return
        fun flatSheet() {
            canvas.drawBitmap(bitmap, null, pageRect, texture)
        }
        if (frame.progress <= 0f) {
            if (context.direction == TurnDirection.Forward) flatSheet() else canvas.drawBitmap(background, null, pageRect, texture)
            return
        }
        if (frame.progress >= 1f) {
            if (context.direction == TurnDirection.Forward) canvas.drawBitmap(background, null, pageRect, texture) else flatSheet()
            return
        }
        canvas.drawBitmap(background, null, pageRect, texture)
        val save = canvas.save()
        try {
            if (context.viewport.rightToLeft) { canvas.translate(pageRect.width(), 0f); canvas.scale(-1f, 1f) }
            drawSheet(canvas, bitmap)
        } finally { canvas.restoreToCount(save) }
    }
    protected abstract fun drawSheet(canvas: Canvas, bitmap: Bitmap)
    override fun finish(outcome: TurnOutcome) { clear() }
    override fun abort(reason: TurnAbortReason) { clear() }
    override fun detach() { clear(); host = null }
    protected open fun clear() { snapshots.close(); turn = null; texture.shader = null; texture.colorFilter = null }
}

/** Apple/iBook: a developable cone/cylinder limit, with a flat material binding. */
internal class CornerCurlEffect(private val options: CurlOptions) : SnapshotCurlEffect() {
    private val columns = options.meshColumns
    private val rows = options.meshRows
    private val indexed = android.os.Build.VERSION.SDK_INT >= 29
    private val front = FloatArray(if (indexed) 0 else (columns + 1) * (rows + 1) * 2)
    private val back = FloatArray(front.size)
    private val colors = IntArray(front.size / 2)
    private var meshRows = rows
    private val frontMesh = if (indexed) IBookCurlMesh(columns) else null
    private val backMesh = if (indexed) IBookCurlMesh(columns) else null
    private val meshPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val silhouetteShadow = IBookCurlShadow()
    private val backFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        options.backContentAlpha, 0f, 0f, 0f, Color.red(options.backTintColor) * (1f - options.backContentAlpha),
        0f, options.backContentAlpha, 0f, 0f, Color.green(options.backTintColor) * (1f - options.backContentAlpha),
        0f, 0f, options.backContentAlpha, 0f, Color.blue(options.backTintColor) * (1f - options.backContentAlpha),
        0f, 0f, 0f, 1f, 0f
    )))
    override fun start(context: PageTurnContext) {
        super.start(context)
        if (indexed) sheetBitmap?.let {
            meshPaint.shader = BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
    }
    override fun geometry(context: PageTurnContext, progress: Float, touch: TurnPoint?) {
        val width = pageRect.width()
        val height = pageRect.height()
        val origin = canonical(context.touchOrigin, context)
        val point = control(context, progress, touch, options)
        val originY = (origin?.yPx ?: height * .65f).coerceIn(0f, height)
        val geometry = IBookCurlGeometry(width, height, point.xPx, point.yPx, originY, options.radiusFraction)
        if (indexed) {
            val bitmap = sheetBitmap ?: return
            requireNotNull(frontMesh).update(geometry, width, height, bitmap.width, bitmap.height, false)
            requireNotNull(backMesh).update(geometry, width, height, bitmap.width, bitmap.height, true)
            if (context.viewport.rightToLeft) {
                fun reflect(mesh: IBookCurlMesh) {
                    for (i in 0 until mesh.valueCount step 2) mesh.texture[i] = bitmap.width - mesh.texture[i]
                }
                reflect(frontMesh); reflect(backMesh)
            }
        } else {
            // Exactly horizontal projection and texture-v are affine along y.
            // All nonzero tilt retains the requested regular material grid.
            meshRows = if (!geometry.conical && geometry.ny == 0.0) 1 else rows
            // A regular mesh interpolates one vertex's light across a whole cell.
            // As the cylinder shrinks below that cell, point-sampled lighting would
            // leave a dark cell at the flat endpoint. Weight the light by the
            // cylinder band's coverage of the cell's extent along the fold normal.
            val normalCellExtent = geometry.nx * width / columns + abs(geometry.ny) * height / meshRows
            val lightCoverage = (geometry.materialBandWidth / normalCellExtent).coerceIn(0.0, 1.0)
            for (row in 0..meshRows) for (column in 0..columns) {
                val materialColumn = if (context.viewport.rightToLeft) columns - column else column
                val index = row * (columns + 1) + column
                val brightness = geometry.project(width * materialColumn / columns,
                    height * row / meshRows, front, back, index * 2)
                val light = 255 - ((255 - brightness) * lightCoverage).roundToInt()
                colors[index] = Color.rgb(light, light, light)
            }
        }
        silhouetteShadow.update(geometry,width,height,options.shadowWidthDp*context.viewport.density,
            options.shadowAlpha,context.viewport.density)
    }
    override fun drawSheet(canvas: Canvas, bitmap: Bitmap) {
        silhouetteShadow.draw(canvas,pageRect.width(),pageRect.height())
        if (indexed) {
            fun face(mesh: IBookCurlMesh) {
                if (mesh.indexCount == 0) return
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mesh.valueCount,
                    mesh.vertices, 0, mesh.texture, 0, mesh.colors, 0,
                    mesh.indices, 0, mesh.indexCount, meshPaint)
            }
            face(requireNotNull(frontMesh))
            meshPaint.colorFilter = backFilter
            try { face(requireNotNull(backMesh)) }
            finally { meshPaint.colorFilter = null }
        } else {
            canvas.drawBitmapMesh(bitmap, columns, meshRows, front, 0, colors, 0, texture)
            texture.colorFilter = backFilter
            try { canvas.drawBitmapMesh(bitmap, columns, meshRows, back, 0, colors, 0, texture) }
            finally { texture.colorFilter = null }
        }
    }
    override fun clear() {
        super.clear()
        meshPaint.shader = null; meshPaint.colorFilter = null
        silhouetteShadow.clear()
    }
}
/** Google/MoonReader: PlayLikeCurl PageFront travelling wave with perspective compression; no reverse-face fold. */
internal class EdgeCurlEffect(private val options: CurlOptions) : SnapshotCurlEffect() {
    private val wave = GoogleCurlGeometry(options.radiusFraction)
    private val columns = options.meshColumns
    private val rows = options.meshRows
    private val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val colors = IntArray(vertices.size / 2)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowRect = RectF()
    override fun geometry(context: PageTurnContext, progress: Float, touch: TurnPoint?) {
        val width = pageRect.width()
        val height = pageRect.height()
        val point = control(context, progress, touch, options)
        wave.update(((width - point.xPx) / (2f * width)).coerceIn(0f, 1f))
        // Follow the actual mesh edge; fitting travel must not pin a shadow to the binding.
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
        val size = maxOf(0.001f, options.shadowWidthDp * context.viewport.density)
        shadowRect.set(edge, 0f, edge + size, height)
        // The projected sheet now reaches the binding exactly at the endpoint. Fade its
        // shadow with the remaining visible width so the endpoint cannot drop a dark strip.
        val visibleShadow = (edge / size).coerceIn(0f, 1f)
        shadow.shader = LinearGradient(edge, 0f, edge + size, 0f,
            Color.argb((255 * options.shadowAlpha * visibleShadow).toInt(), 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    override fun drawSheet(canvas: Canvas, bitmap: Bitmap) {
        if (options.shadowWidthDp > 0f) canvas.drawRect(shadowRect, shadow)
        canvas.drawBitmapMesh(bitmap, columns, rows, vertices, 0, colors, 0, texture)
    }
    override fun clear() { super.clear(); shadow.shader = null }
}

private fun canonical(point: TurnPoint?, context: PageTurnContext): TurnPoint? = point?.let {
    TurnPoint(if (context.viewport.rightToLeft) context.viewport.widthPx - it.xPx else it.xPx, it.yPx)
}

private fun control(context: PageTurnContext, progress: Float, touch: TurnPoint?, options: CurlOptions): TurnPoint {
    val width = context.viewport.widthPx.toFloat()
    val height = context.viewport.heightPx.toFloat()
    val origin = canonical(context.touchOrigin, context)
    val current = canonical(touch, context)
    val initialY = (origin?.yPx ?: height * 0.65f).coerceIn(0f, height)
    val vertical = if (origin != null && current != null) current.yPx - origin.yPx else 0f
    val defaultPoint = TurnPoint(width * (1f - 2f * progress),
        (initialY + vertical * sin(PI.toFloat() * progress)).coerceIn(-height, 2f * height))
    return options.controlPointPolicy.resolve(CurlControlContext(defaultPoint, origin, current, progress,
        context.direction, context.viewport))
}


