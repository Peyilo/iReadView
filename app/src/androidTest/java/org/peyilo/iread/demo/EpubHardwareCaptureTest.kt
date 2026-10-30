package org.peyilo.iread.demo

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.spi.presentation.*
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

/** Checks the production capture adapter, including a target captured before it is revealed. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class EpubHardwareCaptureTest {
    @Test
    fun pendingWebViewCaptureCanBeCancelledBeforeTheBookCloses() {
        val draws = AtomicInteger()
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity {
                it.findViewById<ReaderView>(R.id.reader).apply {
                    setPageTurnEffect(PageTurnEffects.none())
                    setPageCacheMode(ReaderPageCacheMode.THREE_PAGE)
                }
            }
            request<ReaderResult<ModeChange>>(scenario) { r, c ->
                r.setReadingMode(ReadingMode.PAGED, c)
            }
            repeat(12) { iteration ->
                lateinit var source: EpubReaderBookSource
                assertTrue(
                    request<OpenResult>(scenario) { r, c ->
                        source =
                            EpubReaderBookSource(
                                "hardware-close-${System.nanoTime()}",
                                ReaderInput.fromAsset(r.context, "epubs/illustrated-flow.epub"),
                                File(r.context.cacheDir, "hardware-close"),
                            )
                        r.openBook(source, callback = c)
                    }
                        is OpenResult.Opened
                )
                val presentation = presentation(scenario)
                try {
                    val candidate =
                        runBlocking(Dispatchers.Main) {
                            (presentation.prepare(ReadNavigation.Next) as ReadPreparation.Ready)
                                .candidate
                        }
                    Pipe(presentation.contentView.width, presentation.contentView.height).use { pipe
                        ->
                        fun watched(source: PageSurface) =
                            object : PageSurface by source {
                                override fun draw(canvas: android.graphics.Canvas) {
                                    draws.incrementAndGet()
                                    source.draw(canvas)
                                }
                            }
                        pipe.start(
                            PageTurnContext(
                                iteration.toLong(),
                                watched(requireNotNull(presentation.currentSurface)),
                                watched(requireNotNull(candidate.surface)),
                                ReadViewport(pipe.width, pipe.height, 1f, 1f),
                                TurnDirection.Forward,
                            )
                        )
                        SystemClock.sleep(listOf(0L, 1L, 4L, 16L)[iteration % 4])
                        pipe.cancel()
                        runBlocking(Dispatchers.Main) { candidate.dispose() }
                        scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                    }
                } finally {
                    scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                    source.close()
                }
            }
        }
        assertTrue("Live capture was never exercised", draws.get() > 0)
        val deadline = SystemClock.uptimeMillis() + 10000
        fun workers() =
            Thread.getAllStackTraces().keys.count {
                it.isAlive && it.name in setOf("epub-hardware-check", "iRead-capture")
            }
        while (workers() > 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
        assertEquals("Capture workers retained after closing WebViews", 0, workers())
    }

    @Test
    fun parkedRichPagesMatchTheirActualWindowPixels() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity {
                it.findViewById<ReaderView>(R.id.reader).apply {
                    setPageTurnEffect(PageTurnEffects.none())
                    setPageCacheMode(ReaderPageCacheMode.THREE_PAGE)
                }
            }
            request<ReaderResult<ModeChange>>(scenario) { r, c ->
                r.setReadingMode(ReadingMode.PAGED, c)
            }
            for (asset in
                listOf(
                    "illustrated-flow.epub",
                    "fixed-layout.epub",
                    "the-art-of-arm-assembly.epub",
                )) {
                lateinit var source: EpubReaderBookSource
                assertTrue(
                    request<OpenResult>(scenario) { r, c ->
                        source =
                            EpubReaderBookSource(
                                "hardware-pixels-${System.nanoTime()}",
                                ReaderInput.fromAsset(r.context, "epubs/$asset"),
                                File(r.context.cacheDir, "hardware-pixels"),
                            )
                        r.openBook(source, callback = c)
                    }
                        is OpenResult.Opened
                )
                try {
                    if (asset.startsWith("the-art"))
                        request<NavigationResult<ReadingPosition>>(scenario) { r, c ->
                            r.goToChapter("spine-9", callback = c)
                        }
                    val presentation = presentation(scenario)
                    Pipe(presentation.contentView.width, presentation.contentView.height).use { pipe
                        ->
                        for (page in 0 until (if (asset == "fixed-layout.epub") 1 else 3)) {
                            SystemClock.sleep(750)
                            val prepared =
                                runBlocking(Dispatchers.Main) {
                                    presentation.prepare(ReadNavigation.Next)
                                }
                            if (
                                prepared is ReadPreparation.Rejected &&
                                    prepared.reason == ReaderRejection.AtBoundary
                            )
                                break
                            check(prepared is ReadPreparation.Ready) { "$asset/$page: $prepared" }
                            val candidate = prepared.candidate
                            var disposed = false
                            try {
                                val current = window(scenario, presentation)
                                val context =
                                    PageTurnContext(
                                        page.toLong(),
                                        requireNotNull(presentation.currentSurface),
                                        requireNotNull(candidate.surface),
                                        ReadViewport(pipe.width, pipe.height, 1f, 1f),
                                        TurnDirection.Forward,
                                    )
                                val images = pipe.capture(context)
                                try {
                                    compare("$asset/$page/current", current, images[0])
                                    runBlocking(Dispatchers.Main) { candidate.dispose() }
                                    disposed = true
                                    assertTrue(
                                        request<NavigationResult<ReadingPosition>>(scenario) { r, c
                                            ->
                                            r.nextPage(
                                                NavigationOptions(
                                                    animated = false,
                                                    respectInterval = false,
                                                ),
                                                c,
                                            )
                                        }
                                            is NavigationResult.Moved
                                    )
                                    SystemClock.sleep(80)
                                    val target = window(scenario, presentation)
                                    try {
                                        compare("$asset/$page/parked-target", target, images[1])
                                    } finally {
                                        target.recycle()
                                    }
                                } finally {
                                    current.recycle()
                                    images.forEach(Bitmap::recycle)
                                }
                            } finally {
                                if (!disposed) runBlocking(Dispatchers.Main) { candidate.dispose() }
                            }
                        }
                    }
                } finally {
                    scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                    source.close()
                }
            }
        }
    }

    private class Pipe(val width: Int, val height: Int) : AutoCloseable {
        private val thread = HandlerThread("epub-hardware-check").apply { start() }
        private val handler = Handler(thread.looper)
        private val failures = LinkedBlockingQueue<RuntimeException>()
        private val renderer: AutoCloseable
        private val capture: AutoCloseable
        private val captureType =
            Class.forName(
                "org.peyilo.ireadview.internal.effect.ioscurl.render.IosCurlCanvasCapture"
            )
        private val inputType =
            Class.forName(
                "org.peyilo.ireadview.internal.effect.ioscurl.render.IosCurlPageInput\$Canvas"
            )
        private val textures: IntArray
        @Volatile private var active = true

        init {
            val ready = CountDownLatch(1)
            val pair = gpu {
                val type =
                    Class.forName(
                        "org.peyilo.ireadview.internal.effect.ioscurl.render.IosCurlRenderer"
                    )
                val owner =
                    type.getDeclaredConstructor(Boolean::class.javaPrimitiveType).newInstance(true)
                        as AutoCloseable
                val adapter =
                    type
                        .getDeclaredMethod(
                            "createCanvasCapture",
                            Handler::class.java,
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            Function0::class.java,
                            Function0::class.java,
                            Function1::class.java,
                        )
                        .invoke(
                            owner,
                            handler,
                            width,
                            height,
                            { active },
                            {
                                ready.countDown()
                                Unit
                            },
                            { error: RuntimeException ->
                                failures.add(error)
                                Unit
                            },
                        ) as AutoCloseable
                val storage =
                    type.getDeclaredField("textures").apply { isAccessible = true }.get(owner)
                val names =
                    storage.javaClass
                        .getDeclaredField("names")
                        .apply { isAccessible = true }
                        .get(storage) as IntArray
                captureType.getDeclaredMethod("warm").invoke(adapter)
                Triple(owner, adapter, names)
            }
            renderer = pair.first
            capture = pair.second
            textures = pair.third
            check(ready.await(10, TimeUnit.SECONDS)) { "Capture did not warm: ${failures.peek()}" }
        }

        fun capture(context: PageTurnContext): List<Bitmap> {
            val images = LinkedBlockingQueue<List<Bitmap>>()
            gpu {
                val input =
                    inputType.declaredConstructors
                        .single()
                        .newInstance(context, width, height, null)
                captureType
                    .getDeclaredMethod(
                        "begin",
                        inputType,
                        Function0::class.java,
                        Function0::class.java,
                    )
                    .invoke(
                        capture,
                        input,
                        { active },
                        {
                            images.add(readPages())
                            Unit
                        },
                    )
            }
            return images.poll(10, TimeUnit.SECONDS) ?: error("Capture failed: ${failures.peek()}")
        }

        fun start(context: PageTurnContext) = gpu {
            val input =
                inputType.declaredConstructors.single().newInstance(context, width, height, null)
            captureType
                .getDeclaredMethod("begin", inputType, Function0::class.java, Function0::class.java)
                .invoke(capture, input, { active }, { Unit })
        }

        fun cancel() {
            active = false
            gpu { captureType.getDeclaredMethod("cancel").invoke(capture) }
        }

        private fun readPages(): List<Bitmap> {
            val framebuffer = IntArray(1)
            GLES20.glGenFramebuffers(1, framebuffer, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
            try {
                return textures.map { texture ->
                    GLES20.glFramebufferTexture2D(
                        GLES20.GL_FRAMEBUFFER,
                        GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D,
                        texture,
                        0,
                    )
                    check(
                        GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) ==
                            GLES20.GL_FRAMEBUFFER_COMPLETE
                    )
                    val pixels =
                        ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
                    GLES20.glReadPixels(
                        0,
                        0,
                        width,
                        height,
                        GLES20.GL_RGBA,
                        GLES20.GL_UNSIGNED_BYTE,
                        pixels,
                    )
                    check(GLES20.glGetError() == GLES20.GL_NO_ERROR)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                        pixels.position(0)
                        copyPixelsFromBuffer(pixels)
                    }
                }
            } finally {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glDeleteFramebuffers(1, framebuffer, 0)
            }
        }

        private fun <T> gpu(block: () -> T): T =
            FutureTask(java.util.concurrent.Callable(block))
                .also { handler.post(it) }
                .get(15, TimeUnit.SECONDS)

        override fun close() {
            cancel()
            val closed = CountDownLatch(1)
            gpu {
                captureType
                    .getDeclaredMethod("closeProducers", Function0::class.java)
                    .invoke(
                        capture,
                        {
                            try {
                                capture.close()
                                renderer.close()
                            } finally {
                                closed.countDown()
                            }
                        },
                    )
            }
            check(closed.await(15, TimeUnit.SECONDS))
            thread.quitSafely()
            thread.join(5000)
            assertFalse("Capture worker retained", thread.isAlive)
            assertNull("Capture reported a failure", failures.peek())
        }
    }

    private fun presentation(scenario: ActivityScenario<ReaderActivity>): BookPresentation {
        lateinit var result: BookPresentation
        scenario.onActivity {
            result =
                BookReadView::class
                    .java
                    .getDeclaredField("presentation")
                    .apply { isAccessible = true }
                    .get(it.findViewById<ReaderView>(R.id.reader)) as BookPresentation
        }
        return result
    }

    private fun compare(label: String, expected: Bitmap, actual: Bitmap) {
        val a = IntArray(expected.width * expected.height)
        val b = IntArray(a.size)
        expected.getPixels(a, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(b, 0, actual.width, 0, 0, actual.width, actual.height)
        var total = 0L
        var bad = 0
        for (i in a.indices) {
            val r = kotlin.math.abs(Color.red(a[i]) - Color.red(b[i]))
            val g = kotlin.math.abs(Color.green(a[i]) - Color.green(b[i]))
            val blue = kotlin.math.abs(Color.blue(a[i]) - Color.blue(b[i]))
            total += r + g + blue
            if (maxOf(r, g, blue) > 12) bad++
        }
        val average = total.toDouble() / (a.size * 3)
        if (average > 1.1 || bad > a.size * .002) {
            val directory =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .filesDir
            for ((name, image) in listOf("expected" to expected, "actual" to actual)) {
                File(directory, "hardware-$name.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        android.util.Log.i("EpubHardwareCapture", "$label mean=$average outliers=$bad/${a.size}")
        assertTrue("$label RGB error $average", average <= 1.1)
        assertTrue("$label outliers $bad/${a.size}", bad <= a.size * .002)
    }

    private fun window(
        scenario: ActivityScenario<ReaderActivity>,
        presentation: BookPresentation,
    ): Bitmap {
        val results = LinkedBlockingQueue<Pair<Int, Bitmap>>()
        scenario.onActivity { activity ->
            val view = presentation.contentView
            val at = IntArray(2)
            view.getLocationInWindow(at)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                activity.window,
                Rect(at[0], at[1], at[0] + view.width, at[1] + view.height),
                bitmap,
                { results.add(it to bitmap) },
                Handler(Looper.getMainLooper()),
            )
        }
        val result = requireNotNull(results.poll(10, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result.first)
        return result.second
    }

    private fun awaitReady(scenario: ActivityScenario<ReaderActivity>) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (true) {
            var ready = false
            scenario.onActivity {
                ready =
                    it.findViewById<ReaderView>(R.id.reader).readerState.phase == ReaderPhase.Ready
            }
            if (ready) return
            check(SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(25)
        }
    }

    private fun <T> request(
        scenario: ActivityScenario<ReaderActivity>,
        action: (ReaderView, ReaderCallback<T>) -> Unit,
    ): T {
        val results = LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity {
            action(it.findViewById(R.id.reader), ReaderCallback { result -> results.add(result) })
        }
        val result = results.poll(40, TimeUnit.SECONDS)
        return (result as? ReaderCompletion.Completed<T>)?.value ?: error("Request: $result")
    }
}
