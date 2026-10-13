package org.peyilo.ireadview.internal.effect

import kotlin.math.*

/** One face: a tangent plane and a clipped developable band, in a single indexed mesh. */
internal class IBookCurlMesh(private val segments: Int) {
    private val cuts = DoubleArray(segments + 7)
    val vertices = FloatArray(cuts.size * 4 + 16)
    val texture = FloatArray(vertices.size)
    // Older software Canvas JNI validates colors against the float count, before
    // converting to vertex count. Pad the array; only one color per vertex is used.
    val colors = IntArray(vertices.size)
    val indices = ShortArray(cuts.size * 6 + 48)
    private val polygon = FloatArray(16)
    private val plane = DoubleArray(3)
    private val line = DoubleArray(4)
    var valueCount = 0
        private set
    var indexCount = 0
        private set

    fun update(g: IBookCurlGeometry, width: Float, height: Float,
        textureWidth: Int, textureHeight: Int, back: Boolean) {
        valueCount = 0
        indexCount = 0
        if (g.conical) updateConicalArc(g, width, height, textureWidth, textureHeight, back)
        else updateArc(g, width, height, textureWidth, textureHeight, back)
        for (section in 0 until valueCount / 4 - 1) {
            val base = section * 2
            indices[indexCount++] = base.toShort()
            indices[indexCount++] = (base + 1).toShort()
            indices[indexCount++] = (base + 2).toShort()
            indices[indexCount++] = (base + 1).toShort()
            indices[indexCount++] = (base + 3).toShort()
            indices[indexCount++] = (base + 2).toShort()
        }
        g.cutPlane(if (back) g.arcEnd else 0.0, plane)
        val size = clipIBookPlane(plane[0], plane[1], width, height, plane[2], !back, polygon)
        val base = valueCount / 2
        for (i in 0 until size step 2) {
            val x = polygon[i].toDouble(); val y = polygon[i + 1].toDouble()
            if (g.conical) {
                val brightness = g.projectMaterial(x, y, vertices, valueCount)
                colors[valueCount / 2] = (0xff shl 24) or (brightness shl 16) or (brightness shl 8) or brightness
            } else {
                val shift = if (back) 2.0 * g.axis + PI * g.radius - 2.0 * (g.nx * x + g.ny * y) else 0.0
                vertices[valueCount] = (x + g.nx * shift).toFloat()
                vertices[valueCount + 1] = (y + g.ny * shift).toFloat()
                colors[valueCount / 2] = -1
            }
            texture[valueCount] = (x * textureWidth / width).toFloat()
            texture[valueCount + 1] = (y * textureHeight / height).toFloat()
            valueCount += 2
        }
        for (i in 1 until size / 2 - 1) {
            indices[indexCount++] = base.toShort()
            indices[indexCount++] = (base + i).toShort()
            indices[indexCount++] = (base + i + 1).toShort()
        }
    }

    private fun updateConicalArc(g: IBookCurlGeometry, width: Float, height: Float,
        textureWidth: Int, textureHeight: Int, back: Boolean) {
        if (g.radius == 0.0) return
        var minimum = Double.POSITIVE_INFINITY; var maximum = Double.NEGATIVE_INFINITY
        for (corner in 0..3) {
            val x = if (corner == 1 || corner == 2) width.toDouble() else 0.0
            val y = if (corner >= 2) height.toDouble() else 0.0
            val parameter = g.parameter(x, y)
            minimum = minOf(minimum, parameter); maximum = maxOf(maximum, parameter)
        }
        val base = if (back) g.visible else 0.0
        val limit = if (back) g.arcEnd else g.visible
        val start = maxOf(base, minimum); val end = minOf(limit, maximum)
        if (end <= start) return
        var count = 0
        cuts[count++] = start; cuts[count++] = end
        for (i in 1 until segments) {
            val parameter = base + (limit - base) * i / segments
            if (parameter > start && parameter < end) cuts[count++] = parameter
        }
        for (corner in 0..3) {
            val x = if (corner == 1 || corner == 2) width.toDouble() else 0.0
            val y = if (corner >= 2) height.toDouble() else 0.0
            val parameter = g.parameter(x, y)
            if (parameter > start && parameter < end) cuts[count++] = parameter
        }
        java.util.Arrays.sort(cuts, 0, count)
        var previous = Double.NaN
        for (i in 0 until count) {
            if (cuts[i] == previous) continue
            previous = cuts[i]
            g.cutPlane(cuts[i], plane)
            if (!intersectIBookPlane(plane[0], plane[1], plane[2], width, height, line)) continue
            for (endpoint in 0..1) {
                val x = line[endpoint * 2]; val y = line[endpoint * 2 + 1]
                val brightness = g.projectMaterial(x, y, vertices, valueCount)
                texture[valueCount] = (x * textureWidth / width).toFloat()
                texture[valueCount + 1] = (y * textureHeight / height).toFloat()
                colors[valueCount / 2] = (0xff shl 24) or (brightness shl 16) or (brightness shl 8) or brightness
                valueCount += 2
            }
        }
    }

    private fun updateArc(g: IBookCurlGeometry, width: Float, height: Float,
        textureWidth: Int, textureHeight: Int, back: Boolean) {
        if (g.radius == 0.0) return
        val corner0 = 0.0
        val corner1 = g.nx * width
        val corner2 = g.nx * width + g.ny * height
        val corner3 = g.ny * height
        val half = PI * g.radius / 2.0
        val start = maxOf(if (back) half else 0.0, minOf(corner0, corner1, corner2, corner3) - g.axis)
        val end = minOf(if (back) 2.0 * half else half, maxOf(corner0, corner1, corner2, corner3) - g.axis)
        if (end <= start) return
        var count = 0
        cuts[count++] = start
        cuts[count++] = end
        // Keep the angular density even when the page clips away most of a band.
        val base = if (back) half else 0.0
        for (i in 1 until segments) {
            val distance = base + half * i / segments
            if (distance > start && distance < end) cuts[count++] = distance
        }
        fun corner(dot: Double) {
            val distance = dot - g.axis
            if (distance > start && distance < end) cuts[count++] = distance
        }
        // These exact crossings keep the page boundary piecewise linear in material space.
        corner(corner0); corner(corner1); corner(corner2); corner(corner3)
        java.util.Arrays.sort(cuts, 0, count)
        var previous = Double.NaN
        for (i in 0 until count) {
            val distance = cuts[i]
            if (distance == previous) continue
            previous = distance
            val dot = g.axis + distance
            var low = -g.ny * dot / g.nx
            var high = (height - g.ny * dot) / g.nx
            if (g.ny > 0.0) {
                low = maxOf(low, (g.nx * dot - width) / g.ny)
                high = minOf(high, g.nx * dot / g.ny)
            } else if (g.ny < 0.0) {
                low = maxOf(low, g.nx * dot / g.ny)
                high = minOf(high, (g.nx * dot - width) / g.ny)
            }
            // A corner can differ by one floating-point ulp; collapse that endpoint.
            high = maxOf(low, high)
            val angle = distance / g.radius
            val projectedDot = g.axis + g.radius * sin(angle)
            val brightness = (195 + 60 * abs(cos(angle))).toInt().coerceIn(0, 255)
            val color = (0xff shl 24) or (brightness shl 16) or (brightness shl 8) or brightness
            fun endpoint(tangent: Double) {
                val index = valueCount
                vertices[index] = (g.nx * projectedDot - g.ny * tangent).toFloat()
                vertices[index + 1] = (g.ny * projectedDot + g.nx * tangent).toFloat()
                texture[index] = ((g.nx * dot - g.ny * tangent).coerceIn(0.0, width.toDouble()) * textureWidth / width).toFloat()
                texture[index + 1] = ((g.ny * dot + g.nx * tangent).coerceIn(0.0, height.toDouble()) * textureHeight / height).toFloat()
                colors[index / 2] = color
                valueCount += 2
            }
            endpoint(low); endpoint(high)
        }
    }
}

/** Clip the material rectangle to one half-plane, without per-frame polygon allocations. */
internal fun clipIBookMaterial(g: IBookCurlGeometry, width: Float, height: Float,
    threshold: Double, less: Boolean, out: FloatArray): Int =
    clipIBookPlane(g.nx, g.ny, width, height, threshold, less, out)

private fun clipIBookPlane(nx: Double, ny: Double, width: Float, height: Float,
    threshold: Double, less: Boolean, out: FloatArray): Int {
    var count = 0
    var previousX = 0.0
    var previousY = height.toDouble()
    var previousDistance = nx * previousX + ny * previousY - threshold
    for (corner in 0..3) {
        val x = if (corner == 1 || corner == 2) width.toDouble() else 0.0
        val y = if (corner >= 2) height.toDouble() else 0.0
        val distance = nx * x + ny * y - threshold
        val previousInside = if (less) previousDistance <= 0.0 else previousDistance >= 0.0
        val inside = if (less) distance <= 0.0 else distance >= 0.0
        if (inside != previousInside) {
            val fraction = previousDistance / (previousDistance - distance)
            out[count++] = (previousX + (x - previousX) * fraction).toFloat()
            out[count++] = (previousY + (y - previousY) * fraction).toFloat()
        }
        if (inside) { out[count++] = x.toFloat(); out[count++] = y.toFloat() }
        previousX = x; previousY = y; previousDistance = distance
    }
    return count
}

/** Intersect a generator with the rectangle; output both endpoints without allocations. */
internal fun intersectIBookPlane(nx: Double, ny: Double, threshold: Double,
    width: Float, height: Float, out: DoubleArray): Boolean {
    val minimum = minOf(0.0, nx * width, ny * height, nx * width + ny * height)
    val maximum = maxOf(0.0, nx * width, ny * height, nx * width + ny * height)
    val slack = 16.0 * Math.ulp(maxOf(abs(minimum), abs(maximum), abs(threshold)))
    if (threshold < minimum - slack || threshold > maximum + slack) return false
    val lengthSquared = nx * nx + ny * ny
    val px = nx * threshold / lengthSquared; val py = ny * threshold / lengthSquared
    val tx = -ny; val ty = nx
    var low = Double.NEGATIVE_INFINITY; var high = Double.POSITIVE_INFINITY
    if (tx != 0.0) {
        val first = -px / tx; val last = (width - px) / tx
        low = maxOf(low, minOf(first, last)); high = minOf(high, maxOf(first, last))
    }
    if (ty != 0.0) {
        val first = -py / ty; val last = (height - py) / ty
        low = maxOf(low, minOf(first, last)); high = minOf(high, maxOf(first, last))
    }
    if (high < low) { val corner = (low + high) * .5; low = corner; high = corner }
    out[0] = (px + tx * low).coerceIn(0.0, width.toDouble())
    out[1] = (py + ty * low).coerceIn(0.0, height.toDouble())
    out[2] = (px + tx * high).coerceIn(0.0, width.toDouble())
    out[3] = (py + ty * high).coerceIn(0.0, height.toDouble())
    return true
}

