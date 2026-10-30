package org.peyilo.ireadview

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.spi.presentation.ReadViewport

class CurlRenderTestActivity : Activity() {
    lateinit var stage: FrameLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        stage = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(stage)
    }
}

/** A real TextureView and a bitmap host render identical input into the same pixel grid. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class IosCurlGpuDeviceTest {
    private val width = 320
    private val height = 458

    private class Host : PageTurnEffectHost {
        val leases = AtomicInteger()
        var retains = 0
        var invalidate: () -> Unit = {}

        override fun requestRedraw() = invalidate()

        override fun borrowBitmap(widthPx: Int, heightPx: Int): BitmapLease {
            leases.incrementAndGet()
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val references = AtomicInteger(1)
            fun release() {
                if (references.decrementAndGet() == 0) {
                    bitmap.recycle()
                    leases.decrementAndGet()
                }
            }
            return object : RetainableBitmapLease {
                override val bitmap = bitmap
                private val closed = AtomicBoolean()

                override fun retain(): BitmapLease {
                    check(!closed.get())
                    retains++
                    references.incrementAndGet()
                    return object : BitmapLease {
                        override val bitmap = bitmap
                        private val closed = AtomicBoolean()

                        override fun close() {
                            if (closed.compareAndSet(false, true)) release()
                        }
                    }
                }

                override fun close() {
                    if (closed.compareAndSet(false, true)) release()
                }
            }
        }
    }

    private fun page(target: Boolean, opaque: Boolean = true) =
        object : PageSurface {
            override val isOpaque = opaque
            override val widthPx = width
            override val heightPx = height

            override fun draw(canvas: Canvas) {
                val pageColor = if (target) 0xFF183ECC.toInt() else 0xFFFFD06A.toInt()
                canvas.drawColor(
                    if (opaque) pageColor else (pageColor and 0x00FFFFFF) or 0x80000000.toInt()
                )
                val paint = Paint().apply { color = if (target) Color.GREEN else Color.RED }
                canvas.drawRect(0f, 0f, width * .5f, height * .5f, paint)
                paint.color = Color.BLACK
                paint.textSize = 22f
                canvas.drawText(if (target) "TARGET 02" else "CURRENT 01", 16f, 82f, paint)
                paint.color = Color.WHITE
                canvas.drawRect(24f, height - 62f, width - 24f, height - 26f, paint)
            }
        }

    @Test
    fun hardwareOutputMatchesBitmapForEveryBindingAxisDirectionAndRtl() {
        ActivityScenario.launch(CurlRenderTestActivity::class.java).use { scenario ->
            for (binding in IosCurlBinding.entries) {
                val direct = InstrumentationRegistry.getArguments().getString("output") == "surface"
                val factory =
                    PageTurnEffects.iosCurl(
                        IosCurlOptions(
                            binding,
                            output = if (direct) IosCurlOutput.Surface else IosCurlOutput.Texture,
                        )
                    )
                val gpu = factory.create()
                val reference = factory.create()
                val gpuHost = Host()
                val referenceHost = Host()
                lateinit var root: FrameLayout
                var texture: TextureView? = null
                var surface: SurfaceView? = null
                var frame: PageTurnFrame? = null
                var livePage: PageSurface = page(false)
                scenario.onActivity { activity ->
                    gpu.attach(gpuHost)
                    reference.attach(referenceHost)
                    root =
                        object : FrameLayout(activity) {
                            init {
                                setWillNotDraw(false)
                            }

                            override fun onDraw(canvas: Canvas) {
                                if (
                                    frame != null &&
                                        !(gpu as SnapshotPageTurnEffect).usesPageSnapshots
                                )
                                    livePage.draw(canvas)
                                frame?.let { gpu.draw(canvas, it) }
                            }
                        }
                    gpuHost.invalidate = { root.invalidate() }
                    val renderView = (gpu as ViewPageTurnEffect).createRenderView(activity)
                    texture = (renderView as? android.view.ViewGroup)?.getChildAt(0) as? TextureView
                    surface = renderView as? SurfaceView
                    root.addView(renderView, FrameLayout.LayoutParams(width, height))
                    activity.stage.addView(root, FrameLayout.LayoutParams(width, height))
                }
                try {
                    val until = android.os.SystemClock.uptimeMillis() + 5000
                    while (true) {
                        var ready = false
                        scenario.onActivity {
                            ready =
                                texture?.isAvailable ?: (surface?.holder?.surface?.isValid == true)
                        }
                        if (ready) break
                        check(android.os.SystemClock.uptimeMillis() < until)
                        android.os.SystemClock.sleep(16)
                    }
                    android.os.SystemClock.sleep(150)
                    var token = 0L
                    for (opaque in listOf(true, false)) for (vertical in
                        listOf(false, true)) for (rtl in listOf(false, true)) for (direction in
                        TurnDirection.entries) {
                        val expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        expected.eraseColor(Color.BLACK)
                        val actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val currentFrame =
                            PageTurnFrame(
                                .5f,
                                TurnPhase.Dragging,
                                TurnPoint(
                                    if (direction == TurnDirection.Forward) 100f else 220f,
                                    310f,
                                ),
                                1.0,
                            )
                        scenario.onActivity {
                            val context =
                                PageTurnContext(
                                    ++token,
                                    page(false, opaque).also { livePage = it },
                                    page(true, opaque),
                                    ReadViewport(width, height, 1f, 1f, rtl),
                                    direction,
                                    TurnPoint(
                                        if (direction == TurnDirection.Forward) 304f else 16f,
                                        110f,
                                    ),
                                    vertical,
                                )
                            gpu.start(context)
                            reference.start(context)
                            gpu.update(currentFrame)
                            reference.update(currentFrame)
                            reference.draw(Canvas(expected), currentFrame)
                            assertEquals(
                                "GPU input is retained for asynchronous upload",
                                token.toInt() * 2,
                                gpuHost.retains,
                            )
                            frame = currentFrame
                            root.invalidate()
                        }
                        val deadline = android.os.SystemClock.uptimeMillis() + 3000
                        while (true) {
                            var ready = false
                            scenario.onActivity {
                                ready =
                                    (gpu
                                            as
                                            org.peyilo.ireadview.internal.effect.ioscurl.IosCurlEffect)
                                        .hasRenderedFrame
                            }
                            if (ready) break
                            check(android.os.SystemClock.uptimeMillis() < deadline) {
                                "GPU did not present this turn"
                            }
                            android.os.SystemClock.sleep(16)
                        }
                        android.os.SystemClock.sleep(32)
                        val done = CountDownLatch(1)
                        var status = -1
                        scenario.onActivity { activity ->
                            val location = IntArray(2)
                            root.getLocationInWindow(location)
                            if (direct && opaque)
                                PixelCopy.request(
                                    requireNotNull(surface),
                                    actual,
                                    { result ->
                                        status = result
                                        done.countDown()
                                    },
                                    Handler(Looper.getMainLooper()),
                                )
                            else
                                PixelCopy.request(
                                    activity.window,
                                    Rect(
                                        location[0],
                                        location[1],
                                        location[0] + width,
                                        location[1] + height,
                                    ),
                                    actual,
                                    { result ->
                                        status = result
                                        done.countDown()
                                    },
                                    Handler(Looper.getMainLooper()),
                                )
                        }
                        assertTrue(done.await(5, TimeUnit.SECONDS))
                        assertEquals(PixelCopy.SUCCESS, status)
                        var error = 0L
                        var outliers = 0
                        val a = IntArray(width * height)
                        val b = IntArray(a.size)
                        expected.getPixels(a, 0, width, 0, 0, width, height)
                        actual.getPixels(b, 0, width, 0, 0, width, height)
                        for (index in a.indices) {
                            val red = abs(Color.red(a[index]) - Color.red(b[index]))
                            val green = abs(Color.green(a[index]) - Color.green(b[index]))
                            val blue = abs(Color.blue(a[index]) - Color.blue(b[index]))
                            error += red + green + blue
                            if (maxOf(red, green, blue) > 12) outliers++
                        }
                        val average = error.toDouble() / a.size / 3
                        val label = "$binding-opaque$opaque-v$vertical-rtl$rtl-$direction"
                        if (average > 1.1 || outliers > a.size / 500) {
                            val cache =
                                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
                            File(cache, "curl-gpu-expected.png").outputStream().use {
                                expected.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            File(cache, "curl-gpu-actual.png").outputStream().use {
                                actual.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        }
                        assertTrue("$label average RGB error $average", average <= 1.1)
                        assertTrue("$label outliers $outliers", outliers <= a.size / 500)
                        scenario.onActivity {
                            frame = null
                            gpu.finish(TurnOutcome.Committed)
                            reference.finish(TurnOutcome.Committed)
                            root.invalidate()
                        }
                        expected.recycle()
                        actual.recycle()
                        assertEquals(0, gpuHost.leases.get())
                        assertEquals(0, referenceHost.leases.get())
                    }
                } finally {
                    scenario.onActivity { activity ->
                        gpu.detach()
                        reference.detach()
                        activity.stage.removeView(root)
                    }
                }
            }
        }
    }
}
