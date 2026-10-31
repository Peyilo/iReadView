package org.peyilo.ireadview.internal.effect

import android.graphics.Bitmap
import android.os.Build
import org.peyilo.ireadview.api.effect.BitmapLease
import org.peyilo.ireadview.api.effect.BitmapPageSurface
import org.peyilo.ireadview.api.effect.PageSurface

/** Provider validation stays at the ownership boundary, outside renderers and navigation. */
internal class PreparedPageSnapshot
private constructor(
    val bitmap: Bitmap,
    private val owner: BitmapLease,
) : AutoCloseable {
    override fun close() = owner.close()

    companion object {
        fun acquire(surface: PageSurface, width: Int, height: Int): PreparedPageSnapshot? {
            val owner =
                (surface as? BitmapPageSurface)?.acquireSnapshot(width, height) ?: return null
            var accepted = false
            try {
                val bitmap = owner.bitmap
                if (
                    bitmap.isRecycled ||
                        bitmap.width != width ||
                        bitmap.height != height ||
                        bitmap.config != Bitmap.Config.ARGB_8888 ||
                        bitmap.rowBytes.toLong() != width.toLong() * 4L ||
                        bitmap.allocationByteCount.toLong() != bitmap.rowBytes.toLong() * height ||
                        bitmap.hasAlpha() && !bitmap.isPremultiplied ||
                        Build.VERSION.SDK_INT >= 26 && bitmap.colorSpace?.isSrgb == false
                )
                    return null
                accepted = true
                return PreparedPageSnapshot(bitmap, owner)
            } finally {
                if (!accepted) owner.close()
            }
        }
    }
}
