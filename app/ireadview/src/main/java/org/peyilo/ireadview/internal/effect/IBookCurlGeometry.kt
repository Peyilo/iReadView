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
    private val requestedRadius = width * radiusFraction * envelope
    private val pageWidth = width.toDouble()
    private val pageHeight = height.toDouble()
    val radius = minOf(requestedRadius,
        maxOf(0.0, edgeDot - controlDot) / PI,
        2.0 * maxOf(0.0, midpoint - leftSupport) / PI)
    private val baseAxis = (midpoint - PI * radius * 0.5)
        .coerceIn(leftSupport, leftSupport + nx * width)
    // A shallow S bend connects the flat front to the raised roll. Its unit tangent
    // (cos(angle), sin(angle)) preserves material arc length, with horizontal tangents at both ends.
    val frontBandWidth = minOf(2.5 * radius, .75 * maxOf(0.0, baseAxis - leftSupport))
    private fun shoulderIntegral(u: Double, vertical: Boolean): Double {
        val table = if (vertical) shoulderZ else shoulderX
        val position = u.coerceIn(0.0, 1.0) * integralSteps
        val index = position.toInt().coerceAtMost(integralSteps - 1)
        return frontBandWidth * (table[index] + (table[index + 1] - table[index]) * (position - index))
    }

    private companion object {
        const val frontAngle = .65
        const val integralSteps = 512
        // The tangent profile is scale invariant. Integrate once for all frames
        // and instances; per-vertex projection only interpolates and scales.
        val shoulderX = integralTable(false)
        val shoulderZ = integralTable(true)
        fun integralTable(vertical: Boolean): DoubleArray = DoubleArray(integralSteps + 1).also { table ->
            for (i in 0 until integralSteps) {
                val angle = frontAngle * sin(PI * (i + .5) / integralSteps)
                table[i + 1] = table[i] + (if (vertical) sin(angle) else cos(angle)) / integralSteps
            }
        }
    }
    val frontShift = shoulderIntegral(1.0, false) - frontBandWidth
    private val frontHeight = shoulderIntegral(1.0, true)
    val axis = (baseAxis - frontShift * .5).coerceIn(leftSupport, leftSupport + nx * width)

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
        if (s <= 0.0) {
            val u = if (frontBandWidth > 0.0) ((s + frontBandWidth) / frontBandWidth).coerceIn(0.0, 1.0) else 0.0
            val shift = if (s <= -frontBandWidth) 0.0 else -frontBandWidth + shoulderIntegral(u, false) - s
            out[offset] = (x + nx * shift).toFloat(); out[offset + 1] = (y + ny * shift).toFloat()
            return (195 + 60 * cos(frontAngle * sin(PI * u))).roundToInt()
        }
        if (!conical || radius == 0.0) {
            val angle = if (radius == 0.0) PI else minOf(s / radius, PI)
            val projected = if (radius == 0.0 || s >= PI * radius) axis - s + PI * radius else axis + radius * sin(angle)
            out[offset] = (x + nx * (projected - axis - s + frontShift)).toFloat()
            out[offset + 1] = (y + ny * (projected - axis - s + frontShift)).toFloat()
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
        out[offset] = (x + gx * deltaT + nx * (mappedS - s + frontShift)).toFloat()
        out[offset + 1] = (y + gy * deltaT + ny * (mappedS - s + frontShift)).toFloat()
        return (195 + 60 * abs(normalZ)).roundToInt().coerceIn(0, 255)
    }

    fun projectRim(x: Double, y: Double, out: FloatArray, offset: Int) {
        val s = nx * x + ny * y - axis
        if (!conical || radius == 0.0) {
            out[offset] = (x + nx * (radius - s + frontShift)).toFloat()
            out[offset + 1] = (y + ny * (radius - s + frontShift)).toFloat()
            return
        }
        val t = gx * x + gy * y - apexT; val r = hypot(t, s)
        val beta = visible / sinAlpha
        val deltaT = s * s / (r + t) + r * sinSquared * (cos(beta) - 1.0)
        val mappedS = r * sinAlpha * sin(beta)
        out[offset] = (x + gx * deltaT + nx * (mappedS - s + frontShift)).toFloat()
        out[offset + 1] = (y + gy * deltaT + ny * (mappedS - s + frontShift)).toFloat()
    }

    /** Actual height of the silhouette above the receiving page, including the cylinder limit. */
    fun rimHeight(x: Double, y: Double): Double = frontHeight + if (!conical) radius else
        if (radius == 0.0) 0.0 else (hypot(gx * x + gy * y - apexT, nx * x + ny * y - axis) *
            sinAlpha * cos(alpha) * (1.0 - cos(visible / sinAlpha))).coerceAtLeast(0.0)
    fun materialHeight(x: Double, y: Double): Double {
        val s = nx * x + ny * y - axis
        if (s <= 0.0) return if (frontBandWidth == 0.0 || s <= -frontBandWidth) 0.0
            else shoulderIntegral((s + frontBandWidth) / frontBandWidth, true)
        if (!conical || radius == 0.0) return frontHeight + if (radius == 0.0) 0.0 else radius * (1 - cos(minOf(PI, s / radius)))
        val t = gx * x + gy * y - apexT
        val phi = atan2(s, t)
        return frontHeight + if (phi < arcEnd) hypot(t, s) * sinAlpha * cos(alpha) * (1 - cos(phi / sinAlpha))
            else 2 * sinAlpha * cos(alpha) * (t * endCos + s * endSin)
    }

    /** Shadow-only lift cue for a taut flap when binding constraints consume the
     * local roll radius. The screen projection is unchanged; this is a visual
     * height approximation, not a new exact paper surface/contact solution. */
    fun shadowHeight(x: Double, y: Double): Double {
        val s = maxOf(0.0, nx * x + ny * y - axis)
        val constrained = maxOf(0.0, requestedRadius - radius)
        // A continuous ramp from the hinge; no arbitrary minimum-height jump.
        val lift = if (requestedRadius > 0.0) 2.0 * constrained * s / (s + requestedRadius) else 0.0
        // Retain the material binding and vanish at both animation endpoints.
        return materialHeight(x, y) + lift * (x / pageWidth).coerceIn(0.0, 1.0)
    }

    /** Lift estimate sampled just behind the hinge, also valid for a taut flap. */
    fun shadowRimHeight(x: Double, y: Double): Double {
        val sampleX = (x + nx * requestedRadius).coerceIn(0.0, pageWidth)
        val sampleY = (y + ny * requestedRadius).coerceIn(0.0, pageHeight)
        return rimHeight(x, y) + maxOf(0.0, shadowHeight(sampleX, sampleY) - materialHeight(sampleX, sampleY))
    }

    /** Local concavity cue, zero on either tangent plane and at flat endpoints.
     * This is a small ambient-occlusion approximation, not a ray-traced shadow. */
    fun surfaceOcclusion(x: Double, y: Double): Double {
        if (radius <= 0.0) return 0.0
        val s = nx * x + ny * y - axis
        val coverage = radius / (radius + pageWidth * .0025)
        if (s <= 0.0) {
            if (frontBandWidth <= 0.0 || s <= -frontBandWidth) return 0.0
            val u = (s + frontBandWidth) / frontBandWidth
            val bend = sin(PI * u)
            return .5 * bend * bend * coverage * envelope
        }
        val beta = if (conical) parameter(x, y) / sinAlpha else s / radius
        if (beta >= PI) return 0.0
        val bend = sin(beta.coerceAtLeast(0.0))
        return bend * bend * coverage * envelope
    }

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
            projectMaterial(x.toDouble(), y.toDouble(), front, offset)
        } else {
            front[offset] = (x + nx * (frontNormal - dot + frontShift)).toFloat()
            front[offset + 1] = (y + ny * (frontNormal - dot + frontShift)).toFloat()
        }
        back[offset] = (x + nx * (backNormal - dot + frontShift)).toFloat()
        back[offset + 1] = (y + ny * (backNormal - dot + frontShift)).toFloat()
        return if (distance <= 0.0 || radius == 0.0) 255
        else (195 + 60 * abs(cos(minOf(angle, PI)))).toInt()
    }
}

