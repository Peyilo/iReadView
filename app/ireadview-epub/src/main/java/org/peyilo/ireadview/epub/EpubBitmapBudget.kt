package org.peyilo.ireadview.epub

import android.graphics.Bitmap

/** Bounds bitmap ownership in the page cache. Existing readers keep immutable pixels via GC. */
internal class EpubBitmapBudget(private val capacity: Long = 32L * 1024 * 1024) {
    private var used = 0L

    fun allocate(width: Int, height: Int): Allocation? {
        if (width.toLong() * height * 4 > capacity - used) return null
        val bitmap =
            try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                return null
            }
        val bytes = bitmap.allocationByteCount.toLong()
        if (bytes > capacity - used) {
            bitmap.recycle()
            return null
        }
        used += bytes
        return Allocation(bitmap) { used -= bytes }
    }

    class Allocation(val bitmap: Bitmap, private val release: () -> Unit) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            release()
        }
    }
}
