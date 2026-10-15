package org.peyilo.ireadview.internal.effect

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.*

/** Shared cast-shadow distance field for the next page and visible current front. */
internal class IBookFrontShadow {
    private val columns = 48
    private val rows = 96
    private val stride = columns + 1
    private val maximum = 64
    private val material = FloatArray(16)
    private val plane = DoubleArray(3)
    private val points = FloatArray(maximum * 2)
    private val heights = DoubleArray(maximum)
    private val edgeX = DoubleArray(maximum)
    private val edgeY = DoubleArray(maximum)
    private val inverseLength = DoubleArray(maximum)
    private val left = DoubleArray(maximum)
    private val right = DoubleArray(maximum)
    private val top = DoubleArray(maximum)
    private val bottom = DoubleArray(maximum)
    private val profileSteps = 512
    private val profile = DoubleArray(profileSteps + 1) { i ->
        // Lookup by squared distance avoids a square root for every edge sample.
        val q = sqrt(9.0 * i / profileSteps)
        // Subtract the cutoff value so a finite three-sigma support is continuous.
        IBookShadowLight.softProfile(q)
    }
    private val vertices = FloatArray(stride * (rows + 1) * 2)
    private val colors = IntArray(vertices.size)
    private val indices = ShortArray(columns * rows * 6).also { out ->
        var i = 0
        for (row in 0 until rows) for (column in 0 until columns) {
            val a = row * stride + column; val b = a + stride
            out[i++] = a.toShort(); out[i++] = (a + 1).toShort(); out[i++] = b.toShort()
            out[i++] = (a + 1).toShort(); out[i++] = (b + 1).toShort(); out[i++] = b.toShort()
        }
    }
    private val paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
    private var nodes = 0
    private var indexCount = 0

    fun update(g: IBookCurlGeometry, width: Float, height: Float, softness: Float, strength: Float, density: Float) {
        clear()
        if (g.travel <= 0f || g.travel >= 1f || softness <= 0f || strength <= 0f) return
        g.cutPlane(g.visible, plane)
        val count = clipIBookPlane(plane[0], plane[1], width, height, plane[2], false, material) / 2
        if (count < 3) return
        var minX = Double.POSITIVE_INFINITY; var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        for (edge in 0 until count) for (step in 0 until 8) {
            val end = (edge + 1) % count; val t = step / 8.0
            val x = material[edge * 2] * (1 - t) + material[end * 2] * t
            val y = material[edge * 2 + 1] * (1 - t) + material[end * 2 + 1] * t
            g.projectMaterial(x, y, points, nodes * 2)
            val z = g.shadowHeight(x, y).coerceAtLeast(0.0)
            if (!z.isFinite() || !points[nodes * 2].isFinite() || !points[nodes * 2 + 1].isFinite()) { clear(); return }
            heights[nodes] = z
            points[nodes * 2] += (IBookShadowLight.slopeX * z).toFloat()
            points[nodes * 2 + 1] += (IBookShadowLight.slopeY * z).toFloat()
            minX = minOf(minX, points[nodes * 2].toDouble()); maxX = maxOf(maxX, points[nodes * 2].toDouble())
            minY = minOf(minY, points[nodes * 2 + 1].toDouble()); maxY = maxOf(maxY, points[nodes * 2 + 1].toDouble())
            nodes++
        }
        val fade = maxOf(0.0, sin(PI * g.travel))
        val heightFade = maxOf(1.0, 2.0 * density)
        var support = 0.0
        for (i in 0 until nodes) support = maxOf(support, 3 * softness * heights[i] / (heights[i] + softness))
        minX = (minX - support).coerceIn(0.0, width.toDouble())
        maxX = (maxX + support).coerceIn(0.0, width.toDouble())
        minY = (minY - support).coerceIn(0.0, height.toDouble())
        maxY = (maxY + support).coerceIn(0.0, height.toDouble())
        if (maxX <= minX || maxY <= minY) { clear(); return }
        val cell = maxOf((maxX - minX) / columns, (maxY - minY) / rows)
        for (edge in 0 until nodes) {
            val next = (edge + 1) % nodes
            edgeX[edge] = (points[next * 2] - points[edge * 2]).toDouble()
            edgeY[edge] = (points[next * 2 + 1] - points[edge * 2 + 1]).toDouble()
            val lengthSquared = edgeX[edge] * edgeX[edge] + edgeY[edge] * edgeY[edge]
            inverseLength[edge] = if (lengthSquared > 0) 1.0 / lengthSquared else 0.0
            val z = maxOf(heights[edge], heights[next])
            val extent = 3 * softness * z / (z + softness)
            left[edge] = minOf(points[edge * 2], points[next * 2]) - extent
            right[edge] = maxOf(points[edge * 2], points[next * 2]) + extent
            top[edge] = minOf(points[edge * 2 + 1], points[next * 2 + 1]) - extent
            bottom[edge] = maxOf(points[edge * 2 + 1], points[next * 2 + 1]) + extent
        }
        for (row in 0..rows) for (column in 0..columns) {
            val i = row * stride + column
            val x = minX + (maxX - minX) * column / columns
            val y = minY + (maxY - minY) * row / rows
            vertices[i * 2] = x.toFloat(); vertices[i * 2 + 1] = y.toFloat()
            var alpha = 0.0
            for (edge in 0 until nodes) {
                if (x < left[edge] || x > right[edge] || y < top[edge] || y > bottom[edge]) continue
                val dx = x - points[edge * 2]; val dy = y - points[edge * 2 + 1]
                val t = ((dx * edgeX[edge] + dy * edgeY[edge]) * inverseLength[edge]).coerceIn(0.0, 1.0)
                val rx = dx - t * edgeX[edge]; val ry = dy - t * edgeY[edge]
                val distanceSquared = rx * rx + ry * ry
                val z = heights[edge] + (heights[(edge + 1) % nodes] - heights[edge]) * t
                val sigma = softness * z / (z + softness)
                if (sigma <= 0 || distanceSquared >= 9 * sigma * sigma) continue
                val position = distanceSquared / (9 * sigma * sigma) * profileSteps
                val sample = position.toInt().coerceAtMost(profileSteps - 1)
                val weight = profile[sample] + (profile[sample + 1] - profile[sample]) * (position - sample)
                // A subpixel penumbra fades with its coverage instead of jumping
                // between grid vertices. Max avoids dark seams at edge joins.
                alpha = maxOf(alpha, strength * fade * z / (z + heightFade) *
                    sigma / (sigma + cell * .5) * weight)
            }
            colors[i] = (255 * alpha).roundToInt().coerceIn(0, 255) shl 24
        }
        indexCount = indices.size
    }

    fun draw(canvas: Canvas) {
        if (indexCount > 0) canvas.drawVertices(Canvas.VertexMode.TRIANGLES, vertices.size, vertices, 0,
            null, 0, colors, 0, indices, 0, indexCount, paint)
    }
    fun clear() { nodes = 0; indexCount = 0 }
}
