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
    private val frontMesh = IBookCurlMesh(columns)
    private val backMesh = IBookCurlMesh(columns)
    private val meshPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val silhouetteShadow = IBookCurlShadow()
    private val frontShadow = IBookFrontShadow()
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = (255 * options.shadowAlpha * .2f).roundToInt().coerceIn(0, 255)
    }
    private val surfaceColors = IntArray(front.size)
    private val surfaceIndices = if (indexed) ShortArray(0) else ShortArray(columns * rows * 6).also { out ->
        var i = 0
        for (row in 0 until rows) for (column in 0 until columns) {
            val a = row * (columns + 1) + column; val b = a + columns + 1
            out[i++] = a.toShort(); out[i++] = (a + 1).toShort(); out[i++] = b.toShort()
            out[i++] = (a + 1).toShort(); out[i++] = (b + 1).toShort(); out[i++] = b.toShort()
        }
    }
    private val frontReceiver = Path()
    private val backReceiver = Path()
    private val shadowFootprint = IBookShadowFootprint()
    private val nextShadowSupport = Path()
    private var backFilter: ColorMatrixColorFilter? = null
    private fun backFilter(color: Int) = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        options.backContentAlpha, 0f, 0f, 0f, Color.red(color) * (1f - options.backContentAlpha),
        0f, options.backContentAlpha, 0f, 0f, Color.green(color) * (1f - options.backContentAlpha),
        0f, 0f, options.backContentAlpha, 0f, Color.blue(color) * (1f - options.backContentAlpha),
        0f, 0f, 0f, 1f, 0f
    )))
    override fun start(context: PageTurnContext) {
        super.start(context)
        val sheet = if (context.direction == TurnDirection.Forward) context.current else context.target
        sheetBitmap?.let { backFilter = backFilter(options.backTintColor ?: PageBackTint.resolve(sheet, it)) }
        if (indexed) sheetBitmap?.let {
            // Grazing-angle text is strongly minified. Let the GPU use reduced
            // texture levels rather than stretch high-frequency glyph strokes.
            it.setHasMipMap(true)
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
        shadowFootprint.reset()
        val bitmap = sheetBitmap ?: return
        frontMesh.update(geometry, width, height, bitmap.width, bitmap.height, false)
        backMesh.update(geometry, width, height, bitmap.width, bitmap.height, true)
        // Use actual material faces even on the bitmap-mesh path: its hidden
        // vertices are collapsed for display and cannot define an occluder.
        // Consume material coordinates before RTL texture reflection.
        shadowFootprint.addMesh(geometry, frontMesh, width, height, bitmap.width, bitmap.height)
        shadowFootprint.addMesh(geometry, backMesh, width, height, bitmap.width, bitmap.height)
        if (indexed) {
            frontMesh.outline(frontReceiver)
            backMesh.outline(backReceiver)
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
                surfaceColors[index] = (255 * geometry.surfaceOcclusion(
                    (width * materialColumn / columns).toDouble(), (height * row / meshRows).toDouble()) *
                    lightCoverage).roundToInt().coerceIn(0, 255) shl 24
            }
            traceBoundary(front, frontReceiver)
            traceBoundary(back, backReceiver)
        }
        shadowFootprint.outline(nextShadowSupport)
        silhouetteShadow.update(geometry,width,height,options.shadowWidthDp*context.viewport.density,
            options.shadowAlpha,context.viewport.density)
        frontShadow.update(geometry,width,height,options.shadowWidthDp*context.viewport.density,
            options.shadowAlpha,context.viewport.density)
    }
    private fun traceBoundary(vertices: FloatArray, path: Path) {
        path.reset()
        path.moveTo(vertices[0], vertices[1])
        fun edge(row: Int, column: Int) {
            val i = (row * (columns + 1) + column) * 2
            path.lineTo(vertices[i], vertices[i + 1])
        }
        for (column in 1..columns) edge(0, column)
        for (row in 1..meshRows) edge(row, columns)
        for (column in columns - 1 downTo 0) edge(meshRows, column)
        for (row in meshRows - 1 downTo 1) edge(row, 0)
        path.close()
    }

    override fun drawSheet(canvas: Canvas, bitmap: Bitmap) {
        // The exposed next page receives the roll silhouette; the visible
        // current front receives the separate back-boundary projection.
        val nextSave = canvas.save()
        try {
            clipIBookNextShadow(canvas, nextShadowSupport, frontReceiver, backReceiver, pageRect)
            silhouetteShadow.draw(canvas, pageRect.width(), pageRect.height())
        } finally { canvas.restoreToCount(nextSave) }
        if (indexed) {
            fun face(mesh: IBookCurlMesh) {
                if (mesh.indexCount == 0) return
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mesh.valueCount,
                    mesh.vertices, 0, mesh.texture, 0, mesh.colors, 0,
                    mesh.indices, 0, mesh.indexCount, meshPaint)
            }
            fun surface(mesh: IBookCurlMesh) {
                if (mesh.indexCount == 0 || surfacePaint.alpha == 0) return
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mesh.valueCount, mesh.vertices, 0,
                    null, 0, mesh.occlusion, 0, mesh.indices, 0, mesh.indexCount, surfacePaint)
            }
            val frontSave = canvas.save()
            try { canvas.clipPath(frontReceiver); face(requireNotNull(frontMesh)); surface(frontMesh) }
            finally { canvas.restoreToCount(frontSave) }
            val save = canvas.save()
            canvas.clipPath(frontReceiver); frontShadow.draw(canvas); canvas.restoreToCount(save)
            meshPaint.colorFilter = backFilter
            val backSave = canvas.save()
            try {
                canvas.clipPath(backReceiver); face(requireNotNull(backMesh))
                meshPaint.colorFilter = null
                surface(backMesh)
            }
            finally { canvas.restoreToCount(backSave); meshPaint.colorFilter = null }
        } else {
            fun surface(vertices: FloatArray) {
                if (surfacePaint.alpha == 0) return
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, (columns + 1) * (meshRows + 1) * 2,
                    vertices, 0, null, 0, surfaceColors, 0, surfaceIndices, 0,
                    columns * meshRows * 6, surfacePaint)
            }
            val frontSave = canvas.save()
            try { canvas.clipPath(frontReceiver); canvas.drawBitmapMesh(bitmap, columns, meshRows, front, 0, colors, 0, texture); surface(front) }
            finally { canvas.restoreToCount(frontSave) }
            val save = canvas.save()
            canvas.clipPath(frontReceiver); frontShadow.draw(canvas); canvas.restoreToCount(save)
            texture.colorFilter = backFilter
            val backSave = canvas.save()
            try {
                canvas.clipPath(backReceiver); canvas.drawBitmapMesh(bitmap, columns, meshRows, back, 0, colors, 0, texture)
                texture.colorFilter = null
                surface(back)
            }
            finally { canvas.restoreToCount(backSave); texture.colorFilter = null }
        }
    }
    override fun detach() {
        super.detach()
        silhouetteShadow.release()
    }
    override fun clear() {
        super.clear()
        meshPaint.shader = null; meshPaint.colorFilter = null
        backFilter = null
        silhouetteShadow.clear()
        frontShadow.clear(); frontReceiver.reset(); backReceiver.reset()
        shadowFootprint.reset(); nextShadowSupport.reset()
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


