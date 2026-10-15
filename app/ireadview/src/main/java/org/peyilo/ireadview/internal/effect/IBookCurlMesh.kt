package org.peyilo.ireadview.internal.effect

import kotlin.math.*

/** One face: a tangent plane and a clipped developable band, in a single indexed mesh. */
internal class IBookCurlMesh(private val segments: Int) {
    // Angular sampling alone leaves page-height triangles at the tapered rim.
    // A small generator subdivision bounds texture interpolation error there.
    private val generatorSegments = 4
    private val sectionValues = (generatorSegments + 1) * 2
    private val cuts = DoubleArray(segments + 7)
    val vertices = FloatArray(cuts.size * sectionValues * 2 + 16)
    val texture = FloatArray(vertices.size)
    // Older software Canvas JNI validates colors against the float count, before
    // converting to vertex count. Pad the array; only one color per vertex is used.
    val colors = IntArray(vertices.size)
    val occlusion = IntArray(vertices.size)
    val indices = ShortArray(cuts.size * generatorSegments * 12 + 48)
    private val polygon = FloatArray(16)
    private val plane = DoubleArray(3)
    private val line = DoubleArray(4)
    private var stripValueCount = 0
    private var flatValueCount = 0
    var valueCount = 0
        private set
    var indexCount = 0
        private set

    fun update(g: IBookCurlGeometry, width: Float, height: Float,
        textureWidth: Int, textureHeight: Int, back: Boolean) {
        valueCount = 0
        indexCount = 0
        if (!back) updateFrontShoulder(g, width, height, textureWidth, textureHeight)
        connectBand(0, valueCount)
        val arcStart = valueCount
        if (g.conical) updateConicalArc(g, width, height, textureWidth, textureHeight, back)
        else updateArc(g, width, height, textureWidth, textureHeight, back)
        connectBand(arcStart, valueCount)
        if (back) g.cutPlane(g.arcEnd, plane)
        else { plane[0] = g.nx; plane[1] = g.ny; plane[2] = g.axis - g.frontBandWidth }
        val size = clipIBookPlane(plane[0], plane[1], width, height, plane[2], !back, polygon)
        stripValueCount = valueCount
        flatValueCount = size
        val base = valueCount / 2
        for (i in 0 until size step 2) {
            val x = polygon[i].toDouble(); val y = polygon[i + 1].toDouble()
            if (g.conical) {
                val brightness = g.projectMaterial(x, y, vertices, valueCount)
                colors[valueCount / 2] = (0xff shl 24) or (brightness shl 16) or (brightness shl 8) or brightness
            } else {
                val shift = if (back) 2.0 * g.axis + PI * g.radius - 2.0 * (g.nx * x + g.ny * y) + g.frontShift else 0.0
                vertices[valueCount] = (x + g.nx * shift).toFloat()
                vertices[valueCount + 1] = (y + g.ny * shift).toFloat()
                colors[valueCount / 2] = -1
            }
            occlusion[valueCount / 2] = (255 * g.surfaceOcclusion(x, y)).roundToInt().coerceIn(0, 255) shl 24
            texture[valueCount] = (x * textureWidth / width).toFloat()
            texture[valueCount + 1] = (y * textureHeight / height).toFloat()
            valueCount += 2
        }
        for (i in 1 until size / 2 - 1) {
            triangle(base, base + i, base + i + 1)
        }
    }
    private fun appendSection(g: IBookCurlGeometry, width: Float, height: Float, textureWidth: Int, textureHeight: Int) {
        for (sample in 0..generatorSegments) {
            val fraction = sample.toDouble() / generatorSegments
            val x = line[0] + (line[2] - line[0]) * fraction
            val y = line[1] + (line[3] - line[1]) * fraction
            val brightness = g.projectMaterial(x, y, vertices, valueCount)
            occlusion[valueCount / 2] = (255 * g.surfaceOcclusion(x, y)).roundToInt().coerceIn(0, 255) shl 24
            texture[valueCount] = (x.coerceIn(0.0, width.toDouble()) * textureWidth / width).toFloat()
            texture[valueCount + 1] = (y.coerceIn(0.0, height.toDouble()) * textureHeight / height).toFloat()
            colors[valueCount / 2] = (0xff shl 24) or (brightness shl 16) or (brightness shl 8) or brightness
            valueCount += 2
        }
    }

    private fun connectBand(start: Int, end: Int) {
        val stride = generatorSegments + 1
        for (section in start / 2 until end / 2 - stride step stride) {
            for (sample in 0 until generatorSegments) {
                val a = section + sample
                val b = a + stride
                triangle(a, a + 1, b)
                triangle(a + 1, b + 1, b)
            }
        }
    }

    private fun triangle(a: Int, b: Int, c: Int) {
        // At an exact page corner a whole generator can collapse. Such
        // triangles carry no visible area and have an unstable texture inverse.
        val ax = vertices[a * 2]; val ay = vertices[a * 2 + 1]
        val area = (vertices[b * 2] - ax).toDouble() * (vertices[c * 2 + 1] - ay) -
            (vertices[b * 2 + 1] - ay).toDouble() * (vertices[c * 2] - ax)
        if (!area.isFinite() || abs(area) <= 1e-6) return
        indices[indexCount++] = a.toShort()
        indices[indexCount++] = b.toShort()
        indices[indexCount++] = c.toShort()
    }

    private fun updateFrontShoulder(g: IBookCurlGeometry, width: Float, height: Float, textureWidth: Int, textureHeight: Int) {
        if (g.frontBandWidth <= 0.0) return
        var count = 0
        for (i in 0..segments) cuts[count++] = g.axis - g.frontBandWidth + g.frontBandWidth * i / segments
        for (corner in 0..3) {
            val x = if (corner == 1 || corner == 2) width.toDouble() else 0.0
            val y = if (corner >= 2) height.toDouble() else 0.0
            val dot = g.nx * x + g.ny * y
            if (dot > g.axis - g.frontBandWidth && dot < g.axis) cuts[count++] = dot
        }
        java.util.Arrays.sort(cuts, 0, count)
        var previous = Double.NaN
        for (i in 0 until count) {
            if (cuts[i] == previous) continue
            previous = cuts[i]
            if (!intersectIBookPlane(g.nx, g.ny, cuts[i], width, height, line)) continue
            appendSection(g, width, height, textureWidth, textureHeight)

        }
    }
    fun outline(path: android.graphics.Path) {
        path.reset()
        // The two disjoint face regions need only their perimeter, not one
        // clip contour per triangle. Both use the material rectangle's winding.
        if (stripValueCount >= sectionValues) {
            path.moveTo(vertices[0], vertices[1])
            for (i in sectionValues until stripValueCount step sectionValues) path.lineTo(vertices[i], vertices[i + 1])
            for (i in stripValueCount - 2 downTo sectionValues - 2 step sectionValues) path.lineTo(vertices[i], vertices[i + 1])
            path.close()
        }
        if (flatValueCount >= 6) {
            path.moveTo(vertices[stripValueCount], vertices[stripValueCount + 1])
            for (i in stripValueCount + 2 until valueCount step 2) path.lineTo(vertices[i], vertices[i + 1])
            path.close()
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
            appendSection(g, width, height, textureWidth, textureHeight)

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
            line[0] = g.nx * dot - g.ny * low
            line[1] = g.ny * dot + g.nx * low
            line[2] = g.nx * dot - g.ny * high
            line[3] = g.ny * dot + g.nx * high
            appendSection(g, width, height, textureWidth, textureHeight)

        }
    }
}

/** Clip the material rectangle to one half-plane, without per-frame polygon allocations. */
internal fun clipIBookMaterial(g: IBookCurlGeometry, width: Float, height: Float,
    threshold: Double, less: Boolean, out: FloatArray): Int =
    clipIBookPlane(g.nx, g.ny, width, height, threshold, less, out)

internal fun clipIBookPlane(nx: Double, ny: Double, width: Float, height: Float,
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

