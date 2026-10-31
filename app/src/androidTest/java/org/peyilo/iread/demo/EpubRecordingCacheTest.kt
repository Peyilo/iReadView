package org.peyilo.iread.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.effect.PageSurface
import org.peyilo.ireadview.api.effect.PageTurnEffects
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.spi.page.ReaderPageTemplate
import org.peyilo.ireadview.spi.presentation.*
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26, maxSdkVersion = 28)
class EpubRecordingCacheTest {
    @Test
    fun preparedWindowMatchesLiveDocument() = withReader { scenario ->
        withBook(scenario, "the-art-of-arm-assembly.epub") {
            request<NavigationResult<ReadingPosition>>(scenario) { r, c ->
                r.goToChapter("spine-9", callback = c)
            }
            val presentation = presentation(scenario)
            var preparedPages = 0
            for (turn in 0..2) {
                SystemClock.sleep(700)
                scenario.onActivity {
                    val page = pageRoot(requireNotNull(presentation.currentSurface))
                    val field =
                        Class.forName("org.peyilo.ireadview.epub.EpubPageDeck")
                            .getDeclaredField("preparedPixels")
                            .apply { isAccessible = true }
                    if ((field.get(page.parent) as Map<*, *>).containsKey(page)) preparedPages++
                }
                val prepared = window(scenario, presentation)
                scenario.onActivity {
                    val page = pageRoot(requireNotNull(presentation.currentSurface))
                    descendants(page)
                        .filterIsInstance<TextView>()
                        .first { it.text.isNotEmpty() }
                        .invalidate()
                }
                SystemClock.sleep(150)
                val live = window(scenario, presentation)
                try {
                    val a = IntArray(prepared.width * prepared.height)
                    val b = IntArray(a.size)
                    prepared.getPixels(a, 0, prepared.width, 0, 0, prepared.width, prepared.height)
                    live.getPixels(b, 0, live.width, 0, 0, live.width, live.height)
                    var sum = 0L
                    var bad = 0
                    for (i in a.indices) {
                        val delta =
                            listOf(0, 8, 16).map { shift ->
                                kotlin.math.abs((a[i] shr shift and 255) - (b[i] shr shift and 255))
                            }
                        sum += delta.sum()
                        if (delta.max() > 12) bad++
                    }
                    val mean = sum.toDouble() / (a.size * 3)
                    android.util.Log.i("PreparedWindow", "$turn mean=$mean outliers=$bad/${a.size}")
                    assertTrue("RGB error $mean", mean <= 1.1)
                    assertTrue("Outliers $bad/${a.size}", bad <= a.size * .002)
                } finally {
                    prepared.recycle()
                    live.recycle()
                }
                assertTrue(
                    request<NavigationResult<ReadingPosition>>(scenario) { r, c ->
                        r.nextPage(NavigationOptions(animated = false, respectInterval = false), c)
                    }
                        is NavigationResult.Moved
                )
            }
            assertTrue("The pixel comparison must exercise prepared playback", preparedPages > 0)
        }
    }

    @Test
    fun invalidationDuringCaptureIsDiscardedAndAnExhaustedBudgetUsesPicture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val budgetType = Class.forName("org.peyilo.ireadview.epub.EpubBitmapBudget")
            val budget = budgetType.getConstructor(Long::class.javaPrimitiveType).newInstance(256L)
            val cacheType = Class.forName("org.peyilo.ireadview.epub.EpubRecordingCache")
            val cache = cacheType.getConstructor(budgetType).newInstance(budget)
            val invalidate = cacheType.getMethod("invalidate")
            val capture =
                cacheType.getMethod("capture", View::class.java, Boolean::class.javaPrimitiveType)
            val current = cacheType.getMethod("getCurrent")
            var changeDuringDraw = true
            val view =
                object : View(instrumentation.targetContext) {
                        override fun onDraw(canvas: Canvas) {
                            canvas.drawColor(Color.BLUE)
                            if (changeDuringDraw) invalidate.invoke(cache)
                        }
                    }
                    .apply { layout(0, 0, 8, 8) }
            capture.invoke(cache, view, true)
            assertNull(
                "A reentrant invalidation cannot publish stale pixels",
                current.invoke(cache),
            )
            val allocate =
                budgetType.getMethod(
                    "allocate",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
            val held = requireNotNull(allocate.invoke(budget, 8, 8)) as AutoCloseable
            changeDuringDraw = false
            capture.invoke(cache, view, true)
            assertEquals(
                "SoftwareEpubPageRecording",
                requireNotNull(current.invoke(cache)).javaClass.simpleName,
            )
            held.close()
            invalidate.invoke(cache)
        }
    }

    private fun window(
        scenario: ActivityScenario<ReaderActivity>,
        presentation: BookPresentation,
    ): Bitmap {
        val result = LinkedBlockingQueue<Pair<Int, Bitmap>>()
        scenario.onActivity {
            val view = presentation.contentView
            val at = IntArray(2)
            view.getLocationInWindow(at)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                it.window,
                Rect(at[0], at[1], at[0] + view.width, at[1] + view.height),
                bitmap,
                { code -> result.add(code to bitmap) },
                Handler(Looper.getMainLooper()),
            )
        }
        val pixels = requireNotNull(result.poll(10, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, pixels.first)
        return pixels.second
    }

    @Test
    fun cacheBudgetFallsBackAndDoesNotRecyclePixelsHeldByAnEffect() {
        val budgetType = Class.forName("org.peyilo.ireadview.epub.EpubBitmapBudget")
        val budget = budgetType.getConstructor(Long::class.javaPrimitiveType).newInstance(256L)
        val allocate =
            budgetType.getMethod(
                "allocate",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
        assertNull(allocate.invoke(budget, 16, 16))
        val first = requireNotNull(allocate.invoke(budget, 8, 8))
        val pixels = first.javaClass.getMethod("getBitmap").invoke(first) as Bitmap
        pixels.eraseColor(Color.MAGENTA)
        assertNull(allocate.invoke(budget, 1, 1))
        (first as AutoCloseable).close()
        first.close()
        val second = requireNotNull(allocate.invoke(budget, 8, 8)) as AutoCloseable
        assertFalse(pixels.isRecycled)
        assertEquals(Color.MAGENTA, pixels.getPixel(0, 0))
        assertNull(allocate.invoke(budget, 1, 1))
        second.close()
        pixels.recycle()
    }

    @Test
    fun reusedPagesMatchFreshSoftwareRecordingsAcrossPublications() = withReader { scenario ->
        for (asset in
            listOf("illustrated-flow.epub", "fixed-layout.epub", "the-art-of-arm-assembly.epub")) {
            withBook(scenario, asset) {
                if (asset.startsWith("the-art"))
                    request<NavigationResult<ReadingPosition>>(scenario) { r, c ->
                        r.goToChapter("spine-9", callback = c)
                    }
                for (forward in listOf(true, false)) {
                    repeat(if (asset == "fixed-layout.epub") 1 else 3) {
                        val presentation = presentation(scenario)
                        val prepared =
                            runBlocking(Dispatchers.Main) {
                                presentation.prepare(
                                    if (forward) ReadNavigation.Next else ReadNavigation.Previous
                                )
                            }
                        if (
                            prepared is ReadPreparation.Rejected &&
                                prepared.reason == ReaderRejection.AtBoundary
                        )
                            return@repeat
                        check(prepared is ReadPreparation.Ready) { "$asset: $prepared" }
                        try {
                            assertFresh(scenario, requireNotNull(presentation.currentSurface))
                            assertFresh(scenario, requireNotNull(prepared.candidate.surface))
                        } finally {
                            runBlocking(Dispatchers.Main) { prepared.candidate.dispose() }
                        }
                        assertTrue(
                            request<NavigationResult<ReadingPosition>>(scenario) { r, c ->
                                if (forward)
                                    r.nextPage(
                                        NavigationOptions(
                                            animated = false,
                                            respectInterval = false,
                                        ),
                                        c,
                                    )
                                else
                                    r.previousPage(
                                        NavigationOptions(
                                            animated = false,
                                            respectInterval = false,
                                        ),
                                        c,
                                    )
                            }
                                is NavigationResult.Moved
                        )
                        SystemClock.sleep(150)
                    }
                }
            }
        }
    }

    @Test
    fun webContentAndNativeChromeInvalidatePreparedRecordings() = withReader { scenario ->
        withBook(scenario, "illustrated-flow.epub") {
            val presentation = presentation(scenario)
            fun preparedPixels(): IntArray {
                val prepared =
                    runBlocking(Dispatchers.Main) { presentation.prepare(ReadNavigation.Next) }
                check(prepared is ReadPreparation.Ready)
                try {
                    return assertFresh(scenario, requireNotNull(presentation.currentSurface))
                } finally {
                    runBlocking(Dispatchers.Main) { prepared.candidate.dispose() }
                }
            }
            val before = preparedPixels()
            scenario.onActivity {
                val page = pageRoot(requireNotNull(presentation.currentSurface))
                descendants(page)
                    .filterIsInstance<TextView>()
                    .first { it.text.isNotEmpty() }
                    .setTextColor(Color.MAGENTA)
            }
            SystemClock.sleep(150)
            val chrome = preparedPixels()
            assertFalse(
                "The chrome mutation must change rendered pixels",
                before.contentEquals(chrome),
            )
            val changed = LinkedBlockingQueue<String>()
            scenario.onActivity {
                val page = pageRoot(requireNotNull(presentation.currentSurface))
                descendants(page)
                    .filterIsInstance<WebView>()
                    .single()
                    .evaluateJavascript(
                        "(function(){document.body.style.setProperty('background-color','#ffe060','important');document.querySelectorAll('h1,h2,p,li,pre,span').forEach(function(v){v.style.setProperty('color','#008000','important')});return true})()",
                        changed::add,
                    )
            }
            assertEquals("true", changed.poll(5, TimeUnit.SECONDS))
            SystemClock.sleep(250)
            val content = preparedPixels()
            assertFalse(
                "The DOM mutation must change rendered pixels",
                chrome.contentEquals(content),
            )
        }
    }

    private fun assertFresh(
        scenario: ActivityScenario<ReaderActivity>,
        surface: PageSurface,
    ): IntArray {
        lateinit var actual: IntArray
        scenario.onActivity {
            val root = pageRoot(surface)
            val cached = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val fresh = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val live = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try {
                surface.draw(Canvas(cached))
                root.draw(Canvas(live))
                val picture = Picture()
                val canvas = picture.beginRecording(root.width, root.height)
                try {
                    canvas.clipRect(0, 0, root.width, root.height)
                    root.draw(canvas)
                } finally {
                    picture.endRecording()
                }
                Canvas(fresh).drawPicture(picture)
                actual = IntArray(root.width * root.height)
                val expected = IntArray(actual.size)
                val direct = IntArray(actual.size)
                cached.getPixels(actual, 0, root.width, 0, 0, root.width, root.height)
                fresh.getPixels(expected, 0, root.width, 0, 0, root.width, root.height)
                live.getPixels(direct, 0, root.width, 0, 0, root.width, root.height)
                // PageSurface has always supported both Picture replay and a live software draw.
                val matchesFresh = expected.contentEquals(actual) || direct.contentEquals(actual)
                if (!matchesFresh) {
                    for ((name, bitmap) in
                        listOf("fresh" to fresh, "cached" to cached, "live" to live)) {
                        File(it.filesDir, "record-cache-$name.png").outputStream().use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        }
                    }
                }
                assertTrue(
                    "Prepared pixels differ from both original software rendering paths",
                    matchesFresh,
                )
            } finally {
                cached.recycle()
                fresh.recycle()
                live.recycle()
            }
        }
        return actual
    }

    private fun pageRoot(surface: PageSurface): View {
        val page =
            surface.javaClass.declaredFields
                .single { it.type.simpleName == "Page" }
                .apply { isAccessible = true }
                .get(surface)
        return (page.javaClass.getDeclaredField("template").apply { isAccessible = true }.get(page)
                as ReaderPageTemplate)
            .root
    }

    private fun descendants(view: View): List<View> =
        listOf(view) +
            if (view is ViewGroup)
                (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
            else emptyList()

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

    private fun withReader(action: (ActivityScenario<ReaderActivity>) -> Unit) {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 30000
            while (true) {
                var ready = false
                scenario.onActivity {
                    ready =
                        it.findViewById<ReaderView>(R.id.reader).readerState.phase ==
                            ReaderPhase.Ready
                }
                if (ready) break
                check(SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(25)
            }
            scenario.onActivity {
                it.findViewById<ReaderView>(R.id.reader).apply {
                    setPageTurnEffect(PageTurnEffects.none())
                    setPageCacheMode(ReaderPageCacheMode.THREE_PAGE)
                }
            }
            request<ReaderResult<ModeChange>>(scenario) { r, c ->
                r.setReadingMode(ReadingMode.PAGED, c)
            }
            action(scenario)
        }
    }

    private fun withBook(
        scenario: ActivityScenario<ReaderActivity>,
        asset: String,
        action: () -> Unit,
    ) {
        lateinit var source: EpubReaderBookSource
        assertTrue(
            request<OpenResult>(scenario) { r, c ->
                source =
                    EpubReaderBookSource(
                        "record-cache-${System.nanoTime()}",
                        ReaderInput.fromAsset(r.context, "epubs/$asset"),
                        File(r.context.cacheDir, "record-cache"),
                    )
                r.openBook(source, callback = c)
            }
                is OpenResult.Opened
        )
        try {
            action()
        } finally {
            scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
            source.close()
        }
    }

    private fun <T> request(
        scenario: ActivityScenario<ReaderActivity>,
        action: (ReaderView, ReaderCallback<T>) -> Unit,
    ): T {
        val result = LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity {
            action(it.findViewById(R.id.reader), ReaderCallback { result.add(it) })
        }
        val completion = result.poll(40, TimeUnit.SECONDS)
        return (completion as? ReaderCompletion.Completed<T>)?.value
            ?: error("Request: $completion")
    }
}
