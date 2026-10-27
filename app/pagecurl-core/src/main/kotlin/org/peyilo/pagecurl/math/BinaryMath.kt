package org.peyilo.pagecurl.math

import kotlin.math.abs

/** Software fused operations: no JNI, Android API level or host libm rounding dependency.
 * Curl inputs stay far from overflow/underflow. The exact product residual and sum residual
 * are retained until the final rounding, instead of replacing FMA by a*b+c.
 */
internal object BinaryMath {
    fun fma(a: Double, b: Double, c: Double): Double {
        val p = a * b
        val sum = p + c
        if (!p.isFinite() || !sum.isFinite()) return sum
        val aa = a * 134217729.0
        val ah = aa - (aa - a)
        val al = a - ah
        val bb = b * 134217729.0
        val bh = bb - (bb - b)
        val bl = b - bh
        val productError = ((ah * bh - p) + ah * bl + al * bh) + al * bl
        val virtual = sum - p
        val sumError = (p - (sum - virtual)) + (c - virtual)
        val tail = productError + sumError
        return sum + tail
    }
    fun fma(a: Float, b: Float, c: Float): Float {
        val product = a.toDouble() * b.toDouble()
        val sum = product + c.toDouble()
        // Round to odd before conversion, preventing double rounding at a float midpoint.
        val virtual = sum - product
        val error = (product - (sum - virtual)) + (c.toDouble() - virtual)
        if (error == 0.0 || !sum.isFinite()) return sum.toFloat()
        var bits = sum.toRawBits()
        if (bits and 1L == 0L) {
            bits += if ((error > 0) == (sum > 0)) 1L else -1L
        }
        return Double.fromBits(bits).toFloat()
    }
    fun copySign(value: Double, sign: Double): Double =
        Double.fromBits((value.toRawBits() and Long.MAX_VALUE) or (sign.toRawBits() and Long.MIN_VALUE))
    fun copySign(value: Float, sign: Float): Float =
        Float.fromBits((value.toRawBits() and Int.MAX_VALUE) or (sign.toRawBits() and Int.MIN_VALUE))

    fun halfToFloat(h: Int): Float {
        val sign = (h and 0x8000) shl 16
        val exponent = h ushr 10 and 31
        var mantissa = h and 1023
        val bits = if (exponent == 0) {
            if (mantissa == 0) sign else {
                var shift = 0
                while (mantissa and 1024 == 0) { mantissa = mantissa shl 1; shift++ }
                sign or ((113 - shift) shl 23) or ((mantissa and 1023) shl 13)
            }
        } else sign or ((if (exponent == 31) 255 else exponent + 112) shl 23) or (mantissa shl 13)
        return Float.fromBits(bits)
    }
    fun floatToHalf(value: Float): Int {
        val bits = value.toRawBits()
        val sign = bits ushr 16 and 0x8000
        var mantissa = bits and 0x7fffff
        var exponent = (bits ushr 23 and 255) - 127 + 15
        if (exponent >= 31) return sign or 0x7c00 or (if (mantissa != 0 && bits ushr 23 and 255 == 255) 0x200 else 0)
        if (exponent <= 0) {
            if (exponent < -10) return sign
            mantissa = mantissa or 0x800000
            val shift = 14 - exponent
            var q = mantissa ushr shift
            val r = mantissa and ((1 shl shift) - 1)
            val half = 1 shl (shift - 1)
            if (r > half || r == half && q and 1 != 0) q++
            return sign or q
        }
        var q = mantissa ushr 13
        val r = mantissa and 8191
        if (r > 4096 || r == 4096 && q and 1 != 0) q++
        if (q == 1024) { q = 0; exponent++ }
        return sign or (exponent shl 10) or q
    }
}
