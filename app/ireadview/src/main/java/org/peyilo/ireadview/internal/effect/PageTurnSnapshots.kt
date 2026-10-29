package org.peyilo.ireadview.internal.effect

import android.graphics.Bitmap
import android.graphics.Canvas
import org.peyilo.ireadview.api.effect.*

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
        for (scale in SCALES) {
            if (fullResolutionOnly && scale != 1f) break
            val first = host.borrowBitmap(
                maxOf(1, (width * scale).toInt()),
                maxOf(1, (height * scale).toInt())
            ) ?: continue
            val second = try {
                host.borrowBitmap(first.bitmap.width, first.bitmap.height)
            } catch (failure: Exception) {
                first.close(); throw failure
            }
            if (second == null) {
                first.close(); continue
            }
            val forward = context.direction == TurnDirection.Forward
            currentLease = if (forward) first else second
            targetLease = if (forward) second else first
            try {
                fun capture(surface: PageSurface, bitmap: Bitmap) {
                    val start = System.nanoTime()
                    val canvas = Canvas(bitmap)
                    canvas.scale(bitmap.width.toFloat() / width, bitmap.height.toFloat() / height)
                    surface.draw(canvas)
                    android.util.Log.i(
                        "EpubStage",
                        "capture ${(System.nanoTime() - start) / 1e6}ms"
                    )
                }
                // Preserve the curl capture order: turning sheet, then exposed page.
                capture(if (forward) context.current else context.target, first.bitmap)
                capture(if (forward) context.target else context.current, second.bitmap)
                return true
            } catch (failure: Exception) {
                close()
                throw failure
            }
        }
        return false
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

    private companion object {
        val SCALES = floatArrayOf(1f, .75f, .5f, .25f)
    }
}
