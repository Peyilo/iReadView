package org.peyilo.ireadview.internal.effect

import android.graphics.Bitmap
import android.graphics.Color
import org.peyilo.ireadview.api.effect.*

/** Shared automatic reverse tint. Prefer background-only metadata, never average text into it. */
internal object PageBackTint {
    fun resolve(surface: PageSurface, snapshot: Bitmap): Int {
        (surface as? PageBackgroundSurface)?.backgroundColor?.takeIf { Color.alpha(it) > 0 }
            ?.let { return it }
        // Custom surfaces without background metadata: dominant RGB bin avoids isolated glyphs.
        val sums = HashMap<Int, LongArray>()
        for (row in 0 until 8) for (column in 0 until 8) {
            val x = ((column + .5f) * snapshot.width / 8).toInt().coerceIn(0, snapshot.width - 1)
            val y = ((row + .5f) * snapshot.height / 8).toInt().coerceIn(0, snapshot.height - 1)
            val pixel = snapshot.getPixel(x, y)
            if (Color.alpha(pixel) == 0) continue
            val r = Color.red(pixel);
            val g = Color.green(pixel);
            val b = Color.blue(pixel)
            val key = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
            val bucket = sums.getOrPut(key) { LongArray(4) }
            bucket[0] += r; bucket[1] += g; bucket[2] += b; bucket[3]++
        }
        val dominant = sums.values.maxByOrNull { it[3] } ?: return Color.WHITE
        return Color.rgb(
            (dominant[0] / dominant[3]).toInt(),
            (dominant[1] / dominant[3]).toInt(),
            (dominant[2] / dominant[3]).toInt()
        )
    }
}
