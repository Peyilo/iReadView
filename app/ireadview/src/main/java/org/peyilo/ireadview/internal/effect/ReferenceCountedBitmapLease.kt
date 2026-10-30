package org.peyilo.ireadview.internal.effect

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.peyilo.ireadview.api.effect.BitmapLease
import org.peyilo.ireadview.api.effect.RetainableBitmapLease

/** Keeps a pool allocation reserved until the transaction and all async readers finish. */
internal class ReferenceCountedBitmapLease(
    private val owned: Bitmap,
    private val onClosed: (BitmapLease) -> Unit,
    private val onReleased: (Bitmap) -> Unit,
) : RetainableBitmapLease {
    private val main = Handler(Looper.getMainLooper())
    private val references = AtomicInteger(1)
    private val closed = AtomicBoolean()
    override val bitmap
        get(): Bitmap {
            check(!closed.get()) { "Bitmap lease has ended" }
            return owned
        }

    override fun retain(): BitmapLease {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Retain bitmaps on the Widget thread" }
        check(!closed.get()) { "Bitmap lease has ended" }
        references.incrementAndGet()
        return object : BitmapLease {
            private val released = AtomicBoolean()
            override val bitmap
                get(): Bitmap {
                    check(!released.get()) { "Bitmap lease has ended" }
                    return owned
                }

            override fun close() {
                if (released.compareAndSet(false, true)) releaseReference()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        onMain { onClosed(this) }
        releaseReference()
    }

    private fun releaseReference() {
        if (references.decrementAndGet() == 0) onMain { onReleased(owned) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
