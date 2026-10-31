package org.peyilo.ireadview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.internal.effect.PageTurnEffectManager
import org.peyilo.ireadview.internal.effect.PageTurnSnapshots
import org.peyilo.ireadview.spi.presentation.ReadViewport

@RunWith(AndroidJUnit4::class)
class PreparedSnapshotDeviceTest {
    private var cancelTurn: () -> Unit = {}

    private class Source(
        val color: Int,
        size: Int = 16,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    ) : BitmapPageSurface, SnapshotPreferredSurface {
        override val widthPx = 16
        override val heightPx = 16
        override val preferSnapshot = true
        override val isOpaque
            get() = Color.alpha(color) == 255

        val pixels = Bitmap.createBitmap(size, size, config).apply { eraseColor(color) }
        val closed = AtomicInteger()
        val released = CountDownLatch(1)
        var draws = 0
        var acquired = 0
        var recycleOnClose = false
        var failAcquire = false
        var onAcquire: (() -> Unit)? = null

        override fun draw(canvas: Canvas) {
            draws++
            canvas.drawColor(color)
        }

        override fun acquireSnapshot(widthPx: Int, heightPx: Int): BitmapLease? {
            if (failAcquire) error("provider failed")
            if (widthPx != this.widthPx || heightPx != this.heightPx) return null
            acquired++
            return object : BitmapLease {
                    override val bitmap = pixels

                    override fun close() {
                        assertEquals(Looper.getMainLooper(), Looper.myLooper())
                        closed.incrementAndGet()
                        if (recycleOnClose) pixels.recycle()
                        released.countDown()
                    }
                }
                .also { onAcquire?.invoke() }
        }
    }

    /**
     * Enters the real manager's transaction, then cancels before any unattached View frame clock.
     */
    private fun transaction(
        budget: Long,
        block: (PageTurnEffectHost) -> Unit,
    ): PageTurnEffectManager =
        runBlocking(Dispatchers.Main) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val manager =
                PageTurnEffectManager(
                    context,
                    { false },
                    { _, reason -> error("Fallback: $reason") },
                    { { true } },
                    { error, _ -> throw AssertionError(error) },
                )
            cancelTurn = { manager.abort(TurnAbortReason.Cancelled) }
            manager.setOptions(PageTurnOptions(bitmapBudgetBytes = budget))
            manager.install(
                object : PageTurnEffectFactory {
                    override val id = "prepared-pixels-check"

                    override fun create() =
                        object : PageTurnEffect {
                            lateinit var host: PageTurnEffectHost

                            override fun attach(host: PageTurnEffectHost) {
                                this.host = host
                            }

                            override fun supports(
                                direction: TurnDirection,
                                viewport: ReadViewport,
                            ) = true

                            override fun start(context: PageTurnContext) {
                                block(host)
                                throw CancellationException("probe completed")
                            }

                            override fun update(frame: PageTurnFrame) = Unit

                            override fun draw(canvas: Canvas, frame: PageTurnFrame) = Unit

                            override fun finish(outcome: TurnOutcome) = Unit

                            override fun abort(reason: TurnAbortReason) = Unit

                            override fun detach() = Unit
                        }
                },
                true,
            )
            val surface =
                object : PageSurface {
                    override val widthPx = 16
                    override val heightPx = 16

                    override fun draw(canvas: Canvas) = Unit
                }
            try {
                manager.transition(
                    surface,
                    surface,
                    ReadViewport(16, 16, 1f, 1f),
                    TurnDirection.Forward,
                    {},
                    {},
                )
                fail("The probe must cancel its transaction")
            } catch (cancelled: CancellationException) {
                assertEquals("probe completed", cancelled.message)
            } finally {
                manager.detach()
            }
            manager
        }

    private fun capture(
        snapshots: PageTurnSnapshots,
        host: PageTurnEffectHost,
        current: PageSurface,
        target: PageSurface,
        direction: TurnDirection = TurnDirection.Forward,
    ) =
        snapshots.capture(
            host,
            PageTurnContext(1L, current, target, ReadViewport(16, 16, 1f, 1f), direction),
        )

    @Test
    fun translucentSharedPixelsMatchRenderedSnapshotsAcrossEffects() {
        val current = Source(Color.argb(153, 240, 60, 30))
        val target = Source(Color.argb(102, 30, 80, 220))
        fun copied(source: Source) =
            object : SnapshotPreferredSurface {
                override val widthPx = source.widthPx
                override val heightPx = source.heightPx
                override val preferSnapshot = true

                override fun draw(canvas: Canvas) = source.draw(canvas)
            }
        val factories =
            listOf(
                PageTurnEffects.slide(),
                PageTurnEffects.cover(),
                PageTurnEffects.iBookSlide(),
                PageTurnEffects.googleCurl(),
                PageTurnEffects.iosCurl(),
            )
        try {
            for (factory in factories) transaction(8192) { host ->
                fun rendered(first: PageSurface, second: PageSurface): IntArray {
                    val effect = factory.create()
                    val output = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                    effect.attach(host)
                    try {
                        effect.start(
                            PageTurnContext(
                                1L,
                                first,
                                second,
                                ReadViewport(16, 16, 1f, 1f),
                                TurnDirection.Forward,
                                TurnPoint(15f, 8f),
                            )
                        )
                        val frame = PageTurnFrame(.4f, TurnPhase.Dragging, TurnPoint(8f, 12f), .15)
                        effect.update(frame)
                        output.eraseColor(Color.DKGRAY)
                        effect.draw(Canvas(output), frame)
                        return IntArray(256).also { output.getPixels(it, 0, 16, 0, 0, 16, 16) }
                    } finally {
                        effect.finish(TurnOutcome.Cancelled)
                        effect.detach()
                        output.recycle()
                    }
                }
                val shared = rendered(current, target)
                val reference = rendered(copied(current), copied(target))
                assertArrayEquals(factory.id, reference, shared)
                assertTrue(current.pixels.hasAlpha())
                assertTrue(target.pixels.hasAlpha())
            }
        } finally {
            current.pixels.recycle()
            target.pixels.recycle()
        }
    }

    @Test
    fun retainedProviderPixelsStayChargedUntilTheLastWorkerFinishes() {
        for (direction in TurnDirection.values()) {
            val current = Source(Color.RED).apply { recycleOnClose = true }
            val target = Source(Color.BLUE).apply { recycleOnClose = true }
            lateinit var retained: PageTurnSnapshots.Retained
            val generation = current.pixels.generationId
            val alpha = current.pixels.hasAlpha()
            val manager =
                transaction(2048) { host ->
                    PageTurnSnapshots().use { snapshots ->
                        assertTrue(capture(snapshots, host, current, target, direction))
                        assertSame(current.pixels, snapshots.current)
                        assertSame(target.pixels, snapshots.target)
                        retained = requireNotNull(snapshots.retainForUpload())
                    }
                    assertEquals(0, current.closed.get())
                    assertNull("Readers must keep their reservation", host.borrowBitmap(16, 16))
                    assertEquals(generation, current.pixels.generationId)
                    assertEquals(alpha, current.pixels.hasAlpha())
                }
            assertEquals(0, current.draws + target.draws)
            assertEquals(Color.RED, retained.current.bitmap.getPixel(0, 0))
            assertEquals(Color.BLUE, retained.target.bitmap.getPixel(0, 0))
            retained.current.close()
            retained.current.close()
            assertTrue(current.released.await(3, TimeUnit.SECONDS))
            assertFalse(retained.target.bitmap.isRecycled)
            retained.target.close()
            retained.target.close()
            assertTrue(target.released.await(3, TimeUnit.SECONDS))
            assertEquals(1, current.closed.get())
            assertEquals(1, target.closed.get())
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertEquals(
                    0L,
                    PageTurnEffectManager::class
                        .java
                        .getDeclaredField("allocatedBytes")
                        .apply { isAccessible = true }
                        .getLong(manager),
                )
            }
        }
    }

    @Test
    fun aSmallBudgetKeepsTheOriginalResolutionFallback() {
        val current = Source(Color.RED)
        val target = Source(Color.BLUE)
        try {
            transaction(512) { host ->
                PageTurnSnapshots().use { snapshots ->
                    assertTrue(capture(snapshots, host, current, target, TurnDirection.Backward))
                    assertEquals(8, snapshots.current!!.width)
                    assertEquals(8, snapshots.target!!.height)
                    assertNotSame(current.pixels, snapshots.current)
                    assertEquals(Color.RED, snapshots.current!!.getPixel(0, 0))
                    assertEquals(Color.BLUE, snapshots.target!!.getPixel(0, 0))
                    assertEquals(1, current.draws)
                    assertEquals(1, target.draws)
                }
            }
            assertEquals(
                0,
                current.acquired + target.acquired,
            ) // A pair that cannot fit is skipped.
        } finally {
            current.pixels.recycle()
            target.pixels.recycle()
        }
    }

    @Test
    fun incompatiblePixelsAreClosedAndDrawnThroughTheOriginalPath() {
        for ((size, config) in
            listOf(
                8 to Bitmap.Config.ARGB_8888,
                16 to Bitmap.Config.RGB_565,
                32 to Bitmap.Config.ARGB_8888,
            )) {
            val current = Source(Color.RED, size, config)
            val target = Source(Color.BLUE, size, config)
            if (size == 32) {
                current.pixels.reconfigure(16, 16, config)
                target.pixels.reconfigure(16, 16, config)
            }
            try {
                transaction(if (size == 32) 4096 else 2048) { host ->
                    PageTurnSnapshots().use { snapshots ->
                        assertTrue(capture(snapshots, host, current, target))
                        assertEquals(1, current.closed.get())
                        assertEquals(1, target.closed.get())
                        assertNotSame(current.pixels, snapshots.current)
                        assertEquals(
                            "Sharing must not reduce the original capture resolution",
                            16,
                            snapshots.current!!.width,
                        )
                        assertEquals(Color.RED, snapshots.current!!.getPixel(0, 0))
                        assertEquals(Color.BLUE, snapshots.target!!.getPixel(0, 0))
                    }
                }
            } finally {
                current.pixels.recycle()
                target.pixels.recycle()
            }
        }
    }

    @Test
    fun aFailingSecondProviderReleasesTheFirstReservation() {
        val current = Source(Color.RED)
        val target = Source(Color.BLUE).apply { failAcquire = true }
        try {
            transaction(2048) { host ->
                PageTurnSnapshots().use { snapshots ->
                    assertThrows(IllegalStateException::class.java) {
                        capture(snapshots, host, current, target)
                    }
                    assertEquals(1, current.closed.get())
                    assertNull(snapshots.current)
                    assertNull(snapshots.target)
                    val first = requireNotNull(host.borrowBitmap(16, 16))
                    val second = requireNotNull(host.borrowBitmap(16, 16))
                    first.close()
                    second.close()
                }
            }
        } finally {
            current.pixels.recycle()
            target.pixels.recycle()
        }
    }

    @Test
    fun foreignTokensUseDrawingAndCancelledProvidersReleaseTheirLease() {
        val current = Source(Color.RED)
        val target = Source(Color.BLUE)
        try {
            transaction(2048) { host ->
                PageTurnSnapshots().use { snapshots ->
                    assertTrue(
                        snapshots.capture(
                            host,
                            PageTurnContext(
                                2L,
                                current,
                                target,
                                ReadViewport(16, 16, 1f, 1f),
                                TurnDirection.Forward,
                            ),
                        )
                    )
                    assertEquals(0, current.acquired)
                    assertNotSame(current.pixels, snapshots.current)
                    current.onAcquire = { cancelTurn() }
                    assertThrows(CancellationException::class.java) {
                        capture(snapshots, host, current, target)
                    }
                    assertEquals(1, current.closed.get())
                    assertNull(host.borrowBitmap(16, 16))
                    assertNull(snapshots.current)
                }
            }
        } finally {
            current.pixels.recycle()
            target.pixels.recycle()
        }
    }
}
