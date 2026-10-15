package org.peyilo.ireadview.internal.effect

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

internal object IBookShadowLight {
    const val slopeX = .18
    const val slopeY = .28
    fun softProfile(q: Double): Double =
        ((exp(-.5 * q * q) - exp(-4.5)) / (1.0 - exp(-4.5))).coerceAtLeast(0.0)
}

/** Soft silhouette cue for the legacy iBook surface, not a light-space ray solver.
 * One height-based ribbon handles cone/cylinder and finite round ends continuously.
 */
internal class IBookCurlShadow {
    private val across = 2
    private val along = 25
    private val offsets = DoubleArray(across) { -3.0 + 6.0 * it / (across - 1) }
    private val vertices = FloatArray(across * along * 2)
    private val colors = IntArray(vertices.size)
    private val texture = FloatArray(vertices.size)
    private val lutWidth = 1024
    private var gradient: Bitmap? = null
    private val indices = ShortArray((across - 1) * (along - 1) * 6).also { out ->
        var offset = 0
        for (row in 0 until along - 1) for (column in 0 until across - 1) {
            val a = row * across + column; val b = a + across
            for (index in intArrayOf(a,b,a+1,a+1,b,b+1)) out[offset++] = index.toShort()
        }
    }
    private val plane = DoubleArray(3)
    private val material = DoubleArray(4)
    private val rim = FloatArray(4)
    private val paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG) }
    private var visible = false

    fun update(g: IBookCurlGeometry, width: Float, height: Float,
        maximumWidth: Float, maximumAlpha: Float, density: Float) {
        visible = false
        if (maximumWidth <= 0f || maximumAlpha <= 0f || g.travel <= 0f || g.travel >= 1f) return
        g.cutPlane(g.visible,plane)
        if (!intersectIBookPlane(plane[0],plane[1],plane[2],width,height,material)) return
        for (i in 0..1) g.projectRim(material[i*2],material[i*2+1],rim,i*2)
        val h0 = g.shadowRimHeight(material[0],material[1])
        val h1 = g.shadowRimHeight(material[2],material[3])
        if (!h0.isFinite() || !h1.isFinite()) return
        rim[0] += (IBookShadowLight.slopeX * h0).toFloat(); rim[1] += (IBookShadowLight.slopeY * h0).toFloat()
        rim[2] += (IBookShadowLight.slopeX * h1).toFloat(); rim[3] += (IBookShadowLight.slopeY * h1).toFloat()
        val dx = (rim[2]-rim[0]).toDouble(); val dy = (rim[3]-rim[1]).toDouble()
        val length = hypot(dx,dy)
        if (!length.isFinite() || length <= 0.0) return
        // Width tends to height as the fold flattens; it never stays fixed then vanishes.
        fun sigma(h: Double) = maximumWidth * h / (h + maximumWidth)
        val sigma0 = sigma(h0); val sigma1 = sigma(h1)
        val largest = max(sigma0,sigma1)
        if (largest <= 0.0) return
        val coverage = length / (length + 2*largest)
        val envelope = max(0.0,sin(PI*g.travel))
        val heightFadeScale = max(1.0,2.0*density)
        val tx = dx/length; val ty = dy/length
        val nx = -ty; val ny = tx
        val cap0 = minOf(3 * sigma0, length * .25)
        val cap1 = minOf(3 * sigma1, length * .25)
        fun endFade(distance: Double, extent: Double): Double {
            if (extent <= 0.0) return 1.0
            val t = (distance / extent).coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }
        for (row in 0 until along) {
            // Finite sheet edge: soften inward, never extend the ribbon past
            // either material endpoint into unrelated exposed-page regions.
            val distance = when {
                row < 8 -> cap0 * row / 8.0
                row <= 16 -> cap0 + (length - cap0 - cap1) * (row - 8) / 8.0
                else -> length - cap1 + cap1 * (row - 16) / 8.0
            }
            val fraction = (distance/length).coerceIn(0.0,1.0)
            val h = h0+(h1-h0)*fraction
            val spread = sigma(h)
            val cap = endFade(distance, cap0) * endFade(length - distance, cap1)
            val amplitude = maximumAlpha * envelope * coverage * h/(h+heightFadeScale)
            for (column in 0 until across) {
                val normal = offsets[column]
                val index = row*across+column
                vertices[index*2] = (rim[0]+tx*distance+nx*normal*spread).toFloat()
                vertices[index*2+1] = (rim[1]+ty*distance+ny*normal*spread).toFloat()
                // Sample the cached profile in texture space, rather than
                // approximating it with a few bands of vertex colors.
                texture[index * 2] = .5f + column * (lutWidth - 1)
                texture[index * 2 + 1] = .5f
                val alpha = (255 * amplitude * cap).roundToInt().coerceIn(0, 255)
                colors[index] = (alpha shl 24) or 0x00ffffff
            }
        }
        visible = true
    }
    fun draw(canvas: Canvas, width: Float, height: Float) {
        if (!visible) return
        if (gradient == null) {
            val pixels = IntArray(lutWidth) { i ->
                val q = -3.0 + 6.0 * i / (lutWidth - 1)
                (255 * IBookShadowLight.softProfile(q)).roundToInt().coerceIn(0,255) shl 24
            }
            val bitmap = Bitmap.createBitmap(lutWidth, 1, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, lutWidth, 0, 0, lutWidth, 1)
            gradient = bitmap
            paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val save = canvas.save()
        try {
            canvas.clipRect(0f,0f,width,height)
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES,vertices.size,vertices,0,
                texture,0,colors,0,indices,0,indices.size,paint)
        } finally { canvas.restoreToCount(save) }
    }
    fun clear() { visible = false }
    fun release() {
        clear()
        paint.shader = null
        gradient?.recycle()
        gradient = null
    }
}
