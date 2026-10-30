package org.peyilo.iread.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.effect.PageTurnEffects
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.epub.EpubReaderBodyView
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.widget.ReaderView

/** The optimized capture must match the platform's original software child rendering exactly. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class EpubSnapshotRenderingTest {
    @Test
    fun hardwareLayerCapturesMatchUncachedSoftwareAcrossPublicationsAndColumns() {
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
                val reader = it.findViewById<ReaderView>(R.id.reader)
                reader.setPageTurnEffect(PageTurnEffects.none())
                reader.setPageCacheMode(ReaderPageCacheMode.SINGLE_PAGE)
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
                                "software-pixels-${System.nanoTime()}",
                                ReaderInput.fromAsset(r.context, "epubs/$asset"),
                                File(r.context.cacheDir, "software-pixels"),
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
                    for (page in 0..2) {
                        scenario.onActivity { activity ->
                            val reader = activity.findViewById<ReaderView>(R.id.reader)
                            val body = bodies(reader).single()
                            val web =
                                EpubReaderBodyView::class
                                    .java
                                    .getDeclaredField("web")
                                    .apply { isAccessible = true }
                                    .get(body) as WebView
                            fun capture(layer: Int): Bitmap {
                                web.setLayerType(layer, null)
                                return Bitmap.createBitmap(
                                        body.width,
                                        body.height,
                                        Bitmap.Config.ARGB_8888,
                                    )
                                    .also { body.draw(Canvas(it)) }
                            }
                            val original = capture(View.LAYER_TYPE_NONE)
                            val optimized = capture(View.LAYER_TYPE_HARDWARE)
                            try {
                                val reference = IntArray(original.width * original.height)
                                val actual = IntArray(reference.size)
                                original.getPixels(
                                    reference,
                                    0,
                                    original.width,
                                    0,
                                    0,
                                    original.width,
                                    original.height,
                                )
                                optimized.getPixels(
                                    actual,
                                    0,
                                    optimized.width,
                                    0,
                                    0,
                                    optimized.width,
                                    optimized.height,
                                )
                                if (!reference.contentEquals(actual)) {
                                    File(activity.filesDir, "software-reference.png")
                                        .outputStream()
                                        .use {
                                            original.compress(Bitmap.CompressFormat.PNG, 100, it)
                                        }
                                    File(activity.filesDir, "software-optimized.png")
                                        .outputStream()
                                        .use {
                                            optimized.compress(Bitmap.CompressFormat.PNG, 100, it)
                                        }
                                }
                                assertArrayEquals(
                                    "$asset page=$page scroll=${web.scrollX},${web.scrollY}",
                                    reference,
                                    actual,
                                )
                            } finally {
                                original.recycle()
                                optimized.recycle()
                            }
                        }
                        val liveOriginal = liveCapture(scenario, View.LAYER_TYPE_NONE)
                        val liveOptimized = liveCapture(scenario, View.LAYER_TYPE_HARDWARE)
                        try {
                            val originalPixels = IntArray(liveOriginal.width * liveOriginal.height)
                            val optimizedPixels = IntArray(originalPixels.size)
                            liveOriginal.getPixels(
                                originalPixels,
                                0,
                                liveOriginal.width,
                                0,
                                0,
                                liveOriginal.width,
                                liveOriginal.height,
                            )
                            liveOptimized.getPixels(
                                optimizedPixels,
                                0,
                                liveOptimized.width,
                                0,
                                0,
                                liveOptimized.width,
                                liveOptimized.height,
                            )
                            var total = 0L
                            var outliers = 0
                            for (i in originalPixels.indices) {
                                val a = originalPixels[i]
                                val b = optimizedPixels[i]
                                val r = kotlin.math.abs(Color.red(a) - Color.red(b))
                                val g = kotlin.math.abs(Color.green(a) - Color.green(b))
                                val blue = kotlin.math.abs(Color.blue(a) - Color.blue(b))
                                total += r + g + blue
                                if (maxOf(r, g, blue) > 12) outliers++
                            }
                            val average = total.toDouble() / (originalPixels.size * 3)
                            if (average > 1.1 || outliers > originalPixels.size * .002)
                                scenario.onActivity { activity ->
                                    File(activity.filesDir, "live-reference.png")
                                        .outputStream()
                                        .use {
                                            liveOriginal.compress(
                                                Bitmap.CompressFormat.PNG,
                                                100,
                                                it,
                                            )
                                        }
                                    File(activity.filesDir, "live-optimized.png")
                                        .outputStream()
                                        .use {
                                            liveOptimized.compress(
                                                Bitmap.CompressFormat.PNG,
                                                100,
                                                it,
                                            )
                                        }
                                }
                            assertTrue(
                                "$asset live page=$page average=$average outliers=$outliers",
                                average <= 1.1 && outliers <= originalPixels.size * .002,
                            )
                        } finally {
                            liveOriginal.recycle()
                            liveOptimized.recycle()
                        }
                        val result =
                            request<NavigationResult<ReadingPosition>>(scenario) { r, c ->
                                r.nextPage(
                                    NavigationOptions(animated = false, respectInterval = false),
                                    c,
                                )
                            }
                        if (
                            result is NavigationResult.Rejected &&
                                result.reason == ReaderRejection.AtBoundary
                        )
                            break
                        assertTrue(result.toString(), result is NavigationResult.Moved)
                    }
                } finally {
                    scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                    source.close()
                }
            }
        }
    }

    @Test
    fun cachedWebViewLayersKeepCssAnimationLive() {
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
                val reader = it.findViewById<ReaderView>(R.id.reader)
                reader.setPageTurnEffect(PageTurnEffects.none())
                reader.setPageCacheMode(ReaderPageCacheMode.SINGLE_PAGE)
            }
            request<ReaderResult<ModeChange>>(scenario) { r, c ->
                r.setReadingMode(ReadingMode.PAGED, c)
            }
            lateinit var source: EpubReaderBookSource
            assertTrue(
                request<OpenResult>(scenario) { r, c ->
                    source =
                        EpubReaderBookSource(
                            "animated-layers-${System.nanoTime()}",
                            ReaderInput { animatedPublication().inputStream() },
                            File(r.context.cacheDir, "animated-layers"),
                        )
                    r.openBook(source, callback = c)
                }
                    is OpenResult.Opened
            )
            try {
                for (layer in listOf(View.LAYER_TYPE_NONE, View.LAYER_TYPE_HARDWARE)) {
                    liveCapture(scenario, layer).recycle()
                    val states = mutableListOf<Int>()
                    repeat(20) {
                        SystemClock.sleep(100)
                        val result = LinkedBlockingQueue<Pair<Int, Bitmap>>()
                        scenario.onActivity { activity ->
                            val body = bodies(activity.findViewById(R.id.reader)).single()
                            val rect = Rect()
                            assertTrue(body.getGlobalVisibleRect(rect))
                            val bitmap = Bitmap.createBitmap(96, 160, Bitmap.Config.ARGB_8888)
                            PixelCopy.request(
                                activity.window,
                                rect,
                                bitmap,
                                { result.add(it to bitmap) },
                                Handler(Looper.getMainLooper()),
                            )
                        }
                        val capture = requireNotNull(result.poll(5, TimeUnit.SECONDS))
                        assertEquals(PixelCopy.SUCCESS, capture.first)
                        try {
                            val color = capture.second.getPixel(40, 60)
                            states.add(
                                if (Color.red(color) > Color.blue(color) + 40) 1
                                else if (Color.blue(color) > Color.red(color) + 40) 2 else 0
                            )
                        } finally {
                            capture.second.recycle()
                        }
                    }
                    val colored = states.filter { it != 0 }
                    val transitions = colored.zipWithNext().count { (a, b) -> a != b }
                    assertTrue(
                        "Layer $layer stopped the document's animation: $states",
                        colored.contains(1) && colored.contains(2) && transitions >= 3,
                    )
                }
            } finally {
                scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                source.close()
            }
        }
    }

    private fun animatedPublication(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                val bytes = text.toByteArray()
                val entry = java.util.zip.ZipEntry(name)
                if (name == "mimetype") {
                    entry.method = java.util.zip.ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = entry.size
                    entry.crc = java.util.zip.CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            entry(
                "book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book-id">animated-layers</dc:identifier><dc:title>Animation</dc:title><dc:language>en</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest><item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="c"/></spine></package>""",
            )
            entry(
                "nav.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">Animation</a></li></ol></nav></body></html>""",
            )
            entry(
                "chapter.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Animation</title><style>@keyframes pulse {0%,49% {background:#c72525} 50%,100% {background:#255bc7}} #pulse {height:80vh;width:80%;animation:pulse .6s infinite}</style></head><body><div id="pulse">Live animation</div></body></html>""",
            )
        }
        return out.toByteArray()
    }

    private var serial = 0L

    private fun liveCapture(scenario: ActivityScenario<ReaderActivity>, layer: Int): Bitmap {
        val result = LinkedBlockingQueue<Pair<Int, Bitmap>>()
        scenario.onActivity { activity ->
            val body = bodies(activity.findViewById(R.id.reader)).single()
            val web =
                EpubReaderBodyView::class
                    .java
                    .getDeclaredField("web")
                    .apply { isAccessible = true }
                    .get(body) as WebView
            web.setLayerType(layer, null)
            web.invalidate()
            web.postVisualStateCallback(
                ++serial,
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        web.postOnAnimation {
                            web.postOnAnimation {
                                val rect = Rect()
                                assertTrue(body.getGlobalVisibleRect(rect))
                                val bitmap =
                                    Bitmap.createBitmap(
                                        body.width,
                                        body.height,
                                        Bitmap.Config.ARGB_8888,
                                    )
                                PixelCopy.request(
                                    activity.window,
                                    rect,
                                    bitmap,
                                    { result.add(it to bitmap) },
                                    Handler(Looper.getMainLooper()),
                                )
                            }
                        }
                    }
                },
            )
        }
        val capture =
            requireNotNull(result.poll(10, TimeUnit.SECONDS)) { "Live viewport was not presented" }
        assertEquals(PixelCopy.SUCCESS, capture.first)
        return capture.second
    }

    private fun bodies(view: View): List<EpubReaderBodyView> =
        when (view) {
            is EpubReaderBodyView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { bodies(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun <T> request(
        scenario: ActivityScenario<ReaderActivity>,
        action: (ReaderView, ReaderCallback<T>) -> Unit,
    ): T {
        val result = LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity {
            action(it.findViewById(R.id.reader), ReaderCallback { result.add(it) })
        }
        val completed = result.poll(40, TimeUnit.SECONDS)
        return (completed as? ReaderCompletion.Completed<T>)?.value ?: error("Request: $completed")
    }
}
