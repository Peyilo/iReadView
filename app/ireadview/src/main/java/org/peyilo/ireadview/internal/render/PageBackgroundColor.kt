package org.peyilo.ireadview.internal.render

import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.View

/** Samples only the root background Drawable. No page text and no full-size extra bitmap. */
internal fun pageBackgroundColor(view: View): Int? {
    val background = view.background ?: return null
    if (background is ColorDrawable) return background.color.takeIf { Color.alpha(it) > 0 }
    val clone = background.constantState?.newDrawable(view.resources)?.mutate() ?: return null
    clone.state = background.state
    clone.level = background.level
    val sample = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    return try {
        clone.setBounds(0, 0, 4, 4); clone.draw(Canvas(sample))
        var r = 0L; var g = 0L; var b = 0L; var alpha = 0L
        for (y in 0..3) for (x in 0..3) {
            val color = sample.getPixel(x, y); val a = Color.alpha(color)
            r += Color.red(color) * a; g += Color.green(color) * a; b += Color.blue(color) * a; alpha += a
        }
        if (alpha == 0L) null else Color.rgb((r / alpha).toInt(), (g / alpha).toInt(), (b / alpha).toInt())
    } catch (_: Exception) { null }
    finally { sample.recycle() }
}
