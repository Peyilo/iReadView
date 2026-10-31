package org.peyilo.ireadview.internal.effect

import android.graphics.Bitmap
import android.graphics.Canvas
import org.peyilo.ireadview.api.effect.*

/** Internal hint for captures that overwrite every pixel; public host leases stay cleared. */
internal interface OpaqueBitmapHost {
    fun borrowOpaqueBitmap(widthPx: Int, heightPx: Int): BitmapLease?
}

internal interface SnapshotBudgetHost {
    val snapshotBudgetBytes: Long
}

/** Transaction-scoped, display-oriented page images shared by built-in animated effects. */
internal class PageTurnSnapshots : AutoCloseable {
    private var currentLease: BitmapLease? = null
    private var targetLease: BitmapLease? = null
    val current: Bitmap? get() = currentLease?.bitmap
    val target: Bitmap? get() = targetLease?.bitmap

    fun capture(
        host: PageTurnEffectHost, context: PageTurnContext,
        fullResolutionOnly: Boolean = false
    ): Boolean {
        close()
        val width = context.viewport.widthPx
        val height = context.viewport.heightPx
        val forward = context.direction == TurnDirection.Forward
        val firstSurface = if (forward) context.current else context.target
        val secondSurface = if (forward) context.target else context.current
        fun borrow(surface: PageSurface, w: Int, h: Int): BitmapLease? =
            if (surface.isOpaque && host is OpaqueBitmapHost) host.borrowOpaqueBitmap(w, h)
            else host.borrowBitmap(w, h)
        for (scale in SCALES) {
            if (fullResolutionOnly && scale != 1f) break
            val first = borrow(firstSurface,
                maxOf(1, (width * scale).toInt()),
                maxOf(1, (height * scale).toInt())
            ) ?: continue
            val second = try {
                borrow(secondSurface, first.bitmap.width, first.bitmap.height)
            } catch (failure: Exception) {
                first.close(); throw failure
            }
            if (second == null) {
                first.close(); continue
            }
            currentLease = if (forward) first else second
            targetLease = if (forward) second else first
            try {
                fun capture(surface: PageSurface, bitmap: Bitmap) {
                    val start = System.nanoTime()
                    val canvas = Canvas(bitmap)
                    canvas.scale(bitmap.width.toFloat() / width, bitmap.height.toFloat() / height)
                    surface.draw(canvas)
                    bitmap.setHasAlpha(!surface.isOpaque)
                    android.util.Log.i(
                        "EpubStage",
                        "capture ${(System.nanoTime() - start) / 1e6}ms"
                    )
                }
                // Preserve the curl capture order: turning sheet, then exposed page.
                capture(firstSurface, first.bitmap)
                capture(secondSurface, second.bitmap)
                return true
            } catch (failure: Exception) {
                close()
                throw failure
            }
        }
        return false
    }

    class Retained(val current: BitmapLease, val target: BitmapLease) : AutoCloseable {
        override fun close() { try { current.close() } finally { target.close() } }
    }

    fun retainForUpload(): Retained? {
        val current = currentLease as? RetainableBitmapLease ?: return null
        val target = targetLease as? RetainableBitmapLease ?: return null
        val first = current.retain()
        return try { Retained(first, target.retain()) }
        catch (failure: Throwable) { first.close(); throw failure }
    }

    override fun close() {
        val current = currentLease
        val target = targetLease
        currentLease = null
        targetLease = null
        try {
            current?.close()
        } finally {
            target?.close()
        }
    }

    internal companion object {
        val SCALES = floatArrayOf(1f, .75f, .5f, .25f)

        fun sizeWithinBudget(width: Int, height: Int, bytes: Long): Size? {
            if (width <= 0 || height <= 0) return null
            for (scale in SCALES) {
                val w = maxOf(1, (width * scale).toInt())
                val h = maxOf(1, (height * scale).toInt())
                if (w.toLong() * h * 8 <= bytes) return Size(w, h)
            }
            return null
        }
    }

    data class Size(val width: Int, val height: Int)
}
