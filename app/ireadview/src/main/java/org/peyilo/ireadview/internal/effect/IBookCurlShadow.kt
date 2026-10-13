package org.peyilo.ireadview.internal.effect

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

/** Soft silhouette cue for the legacy iBook surface, not a light-space ray solver.
 * One height-based ribbon handles cone/cylinder and finite round ends continuously.
 */
internal class IBookCurlShadow {
    private val across = 17
    private val along = 25
    private val vertices = FloatArray(across * along * 2)
    private val colors = IntArray(across * along)
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var visible = false

    fun update(g: IBookCurlGeometry, width: Float, height: Float,
        maximumWidth: Float, maximumAlpha: Float, density: Float) {
        visible = false
        if (maximumWidth <= 0f || maximumAlpha <= 0f || g.radius <= 0.0) return
        g.cutPlane(g.visible,plane)
        if (!intersectIBookPlane(plane[0],plane[1],plane[2],width,height,material)) return
        for (i in 0..1) g.projectRim(material[i*2],material[i*2+1],rim,i*2)
        val dx = (rim[2]-rim[0]).toDouble(); val dy = (rim[3]-rim[1]).toDouble()
        val length = hypot(dx,dy)
        if (!length.isFinite() || length <= 0.0) return
        val h0 = g.rimHeight(material[0],material[1])
        val h1 = g.rimHeight(material[2],material[3])
        if (!h0.isFinite() || !h1.isFinite()) return
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
        for (row in 0 until along) {
            // Explicit cap samples retain their density even on a very long silhouette.
            val distance = when {
                row < 8 -> -3*sigma0*(1-row/8.0)
                row <= 16 -> length*(row-8)/8.0
                else -> length+3*sigma1*(row-16)/8.0
            }
            val fraction = (distance/length).coerceIn(0.0,1.0)
            val h = h0+(h1-h0)*fraction
            val spread = sigma(h)
            val capDistance = when { distance<0 -> -distance; distance>length -> distance-length; else -> 0.0 }
            val cap = if (spread>0.0) exp(-.5*(capDistance/spread).pow(2)) else 0.0
            val amplitude = maximumAlpha * envelope * coverage * h/(h+heightFadeScale)
            for (column in 0 until across) {
                val normal = -3.0+6.0*column/(across-1)
                val index = row*across+column
                vertices[index*2] = (rim[0]+tx*distance+nx*normal*spread).toFloat()
                vertices[index*2+1] = (rim[1]+ty*distance+ny*normal*spread).toFloat()
                val alpha = if (row==0 || row==along-1 || column==0 || column==across-1) 0
                    else (255*amplitude*cap*exp(-.5*normal*normal)).roundToInt().coerceIn(0,255)
                colors[index] = Color.argb(alpha,0,0,0)
            }
        }
        visible = true
    }
    fun draw(canvas: Canvas, width: Float, height: Float) {
        if (!visible) return
        val save = canvas.save()
        try {
            canvas.clipRect(0f,0f,width,height)
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES,vertices.size,vertices,0,
                null,0,colors,0,indices,0,indices.size,paint)
        } finally { canvas.restoreToCount(save) }
    }
    fun clear() { visible = false }
}
