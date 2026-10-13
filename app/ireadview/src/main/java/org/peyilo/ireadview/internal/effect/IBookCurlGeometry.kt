package org.peyilo.ireadview.internal.effect

import kotlin.math.*

/** Developable cone between tangent planes; the central-origin limit is a cylinder. */
internal class IBookCurlGeometry(width: Float, height: Float, controlX: Float,
    controlY: Float, originY: Float, radiusFraction: Float) {
    val travel = ((width - controlX) / (2f * width)).coerceIn(0f, 1f)
    private val envelope = if (travel == 0f || travel == 1f) 0.0 else sin(PI * travel)
    // The pull vector determines the fold direction. Its angle stays bounded while the
    // envelope removes the direction ambiguity when the control reaches either edge.
    private val theta = atan2(originY.toDouble() - controlY,
        maxOf(0.0, width.toDouble() - controlX)) * envelope
    val nx = cos(theta)
    val ny = sin(theta)
    private val leftSupport = maxOf(0.0, ny * height)
    private val edgeDot = nx * width + ny * originY
    private val controlDot = nx * width * (1.0 - 2.0 * travel) + ny * controlY
    private val midpoint = (edgeDot + controlDot) * 0.5
    // The cylinder limit projects reversed material to 2*axis + PI*r - q.
    // Retain that reference arc-length compensation for positioning the cone;
    // this does not solve an exact grabbed-material contact constraint.
    val radius = minOf(width * radiusFraction * envelope,
        maxOf(0.0, edgeDot - controlDot) / PI,
        2.0 * maxOf(0.0, midpoint - leftSupport) / PI)
    val axis = (midpoint - PI * radius * 0.5)
        .coerceIn(leftSupport, leftSupport + nx * width)

    val conical = originY != height * .5f
    private val sign = if (originY > height * .5f) 1.0 else -1.0
    val gx = -sign * ny
    val gy = sign * nx
    private val minimumT = minOf(0.0, gx * width, gy * height, gx * width + gy * height)
    private val asymmetry = abs(2.0 * originY / height - 1.0)
    private val apexT = if (conical) minimumT - maxOf(2.0 * radius, maxOf(width, height) * .12 / asymmetry) else 0.0
    private val alpha = if (conical) atan(radius / (gx * width + gy * originY - apexT)) else 0.0
    private val sinAlpha = sin(alpha)
    private val sinSquared = sinAlpha * sinAlpha
    private val cosSquared = 1.0 - sinSquared
    private val cosTwice = 1.0 - 2.0 * sinSquared
    val arcEnd = if (conical) PI * sinAlpha else PI * radius
    val visible = if (conical) sinAlpha * acos(-(tan(alpha) * tan(alpha)).coerceIn(-1.0, 1.0)) else arcEnd * .5
    private val endSin = sin(arcEnd)
    private val endCos = cos(arcEnd)
    private val endShift = -2.0 * sinSquared * endCos - 2.0 * sin(arcEnd * .5).pow(2)
    val materialBandWidth = if (conical) {
        var largest = 0.0
        for (corner in 0..3) {
            val x = if (corner == 1 || corner == 2) width.toDouble() else 0.0
            val y = if (corner >= 2) height.toDouble() else 0.0
            largest = maxOf(largest, hypot(gx * x + gy * y - apexT, nx * x + ny * y - axis))
        }
        largest * arcEnd
    } else arcEnd

    /** Material parameter of a generator, not a screen-space coordinate. */
    fun parameter(x: Double, y: Double): Double = if (conical)
        atan2(nx * x + ny * y - axis, gx * x + gy * y - apexT) else nx * x + ny * y - axis

    fun cutPlane(parameter: Double, out: DoubleArray) {
        val slope = if (conical) tan(parameter) else 0.0
        out[0] = nx - slope * gx; out[1] = ny - slope * gy
        out[2] = if (conical) axis - slope * apexT else axis + parameter
    }

    /** Visible material projection, used by the clipped indexed mesh. */
    fun projectMaterial(x: Double, y: Double, out: FloatArray, offset: Int): Int {
        val s = nx * x + ny * y - axis
        if (s <= 0.0) { out[offset] = x.toFloat(); out[offset + 1] = y.toFloat(); return 255 }
        if (!conical || radius == 0.0) {
            val angle = if (radius == 0.0) PI else minOf(s / radius, PI)
            val projected = if (radius == 0.0 || s >= PI * radius) axis - s + PI * radius else axis + radius * sin(angle)
            out[offset] = (x + nx * (projected - axis - s)).toFloat()
            out[offset + 1] = (y + ny * (projected - axis - s)).toFloat()
            return if (s >= PI * radius) 255 else (195 + 60 * abs(cos(angle))).toInt()
        }
        val t = gx * x + gy * y - apexT
        val phi = atan2(s, t)
        val deltaT: Double; val mappedS: Double; val normalZ: Double
        if (phi >= arcEnd) {
            deltaT = t * endShift + s * cosTwice * endSin
            mappedS = -s * endCos + t * endSin
            normalZ = -cosTwice
        } else {
            val r = hypot(t, s); val beta = phi / sinAlpha
            // Avoid subtracting the far-away apex when tending to a cylinder.
            deltaT = s * s / (r + t) + r * sinSquared * (cos(beta) - 1.0)
            mappedS = r * sinAlpha * sin(beta)
            normalZ = sinSquared + cosSquared * cos(beta)
        }
        out[offset] = (x + gx * deltaT + nx * (mappedS - s)).toFloat()
        out[offset + 1] = (y + gy * deltaT + ny * (mappedS - s)).toFloat()
        return (195 + 60 * abs(normalZ)).roundToInt().coerceIn(0, 255)
    }

    fun projectRim(x: Double, y: Double, out: FloatArray, offset: Int) {
        val s = nx * x + ny * y - axis
        if (!conical || radius == 0.0) {
            out[offset] = (x + nx * (radius - s)).toFloat()
            out[offset + 1] = (y + ny * (radius - s)).toFloat()
            return
        }
        val t = gx * x + gy * y - apexT; val r = hypot(t, s)
        val beta = visible / sinAlpha
        val deltaT = s * s / (r + t) + r * sinSquared * (cos(beta) - 1.0)
        val mappedS = r * sinAlpha * sin(beta)
        out[offset] = (x + gx * deltaT + nx * (mappedS - s)).toFloat()
        out[offset + 1] = (y + gy * deltaT + ny * (mappedS - s)).toFloat()
    }

    /** Actual height of the silhouette above the receiving page, including the cylinder limit. */
    fun rimHeight(x: Double, y: Double): Double = if (!conical) radius else
        if (radius == 0.0) 0.0 else (hypot(gx * x + gy * y - apexT, nx * x + ny * y - axis) *
            sinAlpha * cos(alpha) * (1.0 - cos(visible / sinAlpha))).coerceAtLeast(0.0)

    /** Project both visible faces; hidden material collapses onto a unit-normal rim line. */
    fun project(x: Float, y: Float, front: FloatArray, back: FloatArray, offset: Int): Int {
        if (conical) {
            val brightness = projectMaterial(x.toDouble(), y.toDouble(), front, offset)
            back[offset] = front[offset]; back[offset + 1] = front[offset + 1]
            if (parameter(x.toDouble(), y.toDouble()) <= visible) projectRim(x.toDouble(), y.toDouble(), back, offset)
            else projectRim(x.toDouble(), y.toDouble(), front, offset)
            return brightness
        }
        val dot = nx * x + ny * y
        val distance = dot - axis
        val angle = if (radius == 0.0) 0.0 else maxOf(0.0, distance / radius)
        val normal = when {
            distance <= 0.0 -> dot
            radius == 0.0 -> axis - distance
            angle <= PI -> axis + radius * sin(angle)
            else -> axis - (distance - PI * radius)
        }
        val rim = axis + radius
        val frontNormal = if (distance <= PI * radius / 2.0) normal else rim
        val backNormal = if (distance >= PI * radius / 2.0) normal else rim
        if (distance <= 0.0 || x == 0f) {
            front[offset] = x; front[offset + 1] = y
        } else {
            front[offset] = (x + nx * (frontNormal - dot)).toFloat()
            front[offset + 1] = (y + ny * (frontNormal - dot)).toFloat()
        }
        back[offset] = (x + nx * (backNormal - dot)).toFloat()
        back[offset + 1] = (y + ny * (backNormal - dot)).toFloat()
        return if (distance <= 0.0 || radius == 0.0) 255
        else (195 + 60 * abs(cos(minOf(angle, PI)))).toInt()
    }
}

