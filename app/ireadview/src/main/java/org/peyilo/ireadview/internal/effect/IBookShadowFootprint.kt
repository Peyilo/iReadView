package org.peyilo.ireadview.internal.effect

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import kotlin.math.abs

/** Union of actual paper triangles projected along the light onto z=0.
 * All contours have the same winding, so overlaps form a union, never holes. */
internal class IBookShadowFootprint {
    var coordinates = FloatArray(0)
        private set
    var valueCount = 0
        private set
    private var projected = FloatArray(0)

    fun reset() { valueCount = 0 }

    fun addMesh(g: IBookCurlGeometry, mesh: IBookCurlMesh, width: Float, height: Float,
        textureWidth: Int, textureHeight: Int) {
        reserve(mesh.valueCount, mesh.indexCount * 2)
        for (i in 0 until mesh.valueCount step 2) {
            val x = mesh.texture[i].toDouble() * width / textureWidth
            val y = mesh.texture[i + 1].toDouble() * height / textureHeight
            project(g, x, y, mesh.vertices, i)
        }
        for (i in 0 until mesh.indexCount step 3) triangle(
            mesh.indices[i].toInt() * 2, mesh.indices[i + 1].toInt() * 2, mesh.indices[i + 2].toInt() * 2)
    }

    fun outline(path: Path) {
        path.reset()
        path.fillType = Path.FillType.WINDING
        for (i in 0 until valueCount step 6) {
            path.moveTo(coordinates[i], coordinates[i + 1])
            path.lineTo(coordinates[i + 2], coordinates[i + 3])
            path.lineTo(coordinates[i + 4], coordinates[i + 5])
            path.close()
        }
    }

    private fun reserve(vertexValues: Int, triangleValues: Int) {
        if (projected.size < vertexValues) projected = FloatArray(vertexValues)
        if (coordinates.size < valueCount + triangleValues)
            coordinates = coordinates.copyOf(maxOf(valueCount + triangleValues, coordinates.size * 2))
    }

    private fun project(g: IBookCurlGeometry, x: Double, y: Double, vertices: FloatArray, i: Int) {
        val z = g.shadowHeight(x, y)
        projected[i] = (vertices[i] + IBookShadowLight.slopeX * z).toFloat()
        projected[i + 1] = (vertices[i + 1] + IBookShadowLight.slopeY * z).toFloat()
    }

    private fun triangle(a: Int, b: Int, c: Int) {
        val area = (projected[b] - projected[a]).toDouble() * (projected[c + 1] - projected[a + 1]) -
            (projected[b + 1] - projected[a + 1]).toDouble() * (projected[c] - projected[a])
        if (!area.isFinite() || abs(area) <= 1e-6) return
        val second = if (area > 0) b else c
        val third = if (area > 0) c else b
        coordinates[valueCount++] = projected[a]; coordinates[valueCount++] = projected[a + 1]
        coordinates[valueCount++] = projected[second]; coordinates[valueCount++] = projected[second + 1]
        coordinates[valueCount++] = projected[third]; coordinates[valueCount++] = projected[third + 1]
    }
}

/** Receiver visibility and light-space source support are separate constraints. */
@Suppress("DEPRECATION")
internal fun clipIBookNextShadow(canvas: Canvas, support: Path, front: Path, back: Path, page: RectF) {
    canvas.clipRect(page)
    canvas.clipPath(support)
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        canvas.clipOutPath(front); canvas.clipOutPath(back)
    } else {
        canvas.clipPath(front, Region.Op.DIFFERENCE)
        canvas.clipPath(back, Region.Op.DIFFERENCE)
    }
}
