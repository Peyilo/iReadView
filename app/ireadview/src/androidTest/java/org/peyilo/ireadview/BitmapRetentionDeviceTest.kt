package org.peyilo.ireadview

import android.graphics.Bitmap
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.effect.BitmapLease
import org.peyilo.ireadview.internal.effect.ReferenceCountedBitmapLease

@RunWith(AndroidJUnit4::class)
class BitmapRetentionDeviceTest {
    @Test
    fun cancellationDoesNotRecycleBitmapWhileWorkerReadsIt() {
        val released = CountDownLatch(1)
        lateinit var root: ReferenceCountedBitmapLease
        lateinit var first: BitmapLease
        lateinit var second: BitmapLease
        var closed = 0
        var returns = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root =
                ReferenceCountedBitmapLease(
                    Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888),
                    { closed++ },
                ) { bitmap ->
                    assertEquals(Looper.getMainLooper(), Looper.myLooper())
                    bitmap.recycle()
                    returns++
                    released.countDown()
                }
            first = root.retain()
            second = root.retain()
            root.close()
            root.close()
            assertEquals(1, closed)
            assertFalse(first.bitmap.isRecycled)
        }
        // Simulate cancellation followed by two asynchronous readers completing.
        assertFalse(second.bitmap.isRecycled)
        first.close()
        first.close()
        assertEquals(0, returns)
        assertFalse(second.bitmap.isRecycled)
        second.close()
        second.close()
        assertTrue(released.await(3, TimeUnit.SECONDS))
        assertEquals(1, returns)
    }

    @Test
    fun closedOrOffThreadHandlesCannotCreateMoreReaders() {
        lateinit var root: ReferenceCountedBitmapLease
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root =
                ReferenceCountedBitmapLease(
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                    {},
                    Bitmap::recycle,
                )
        }
        assertThrows(IllegalStateException::class.java) { root.retain() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root.close()
            assertThrows(IllegalStateException::class.java) { root.retain() }
        }
    }
}
