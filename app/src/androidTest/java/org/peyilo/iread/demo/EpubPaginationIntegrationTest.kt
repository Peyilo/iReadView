package org.peyilo.iread.demo

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.effect.PageTurnEffects
import org.peyilo.ireadview.api.effect.PageTurnOptions
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.widget.ReaderView
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real publication: long chapter, tables, illustrations, and a fractional CSS viewport. */
@RunWith(AndroidJUnit4::class)
class EpubPaginationIntegrationTest {
    @Test fun armChapterAdvancesEveryPageAndReturnsAcrossChapterBoundary() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            val readyUntil = SystemClock.uptimeMillis() + 30000
            while (true) {
                var ready = false
                scenario.onActivity { ready = it.findViewById<ReaderView>(R.id.reader).readerState.phase == ReaderPhase.Ready }
                if (ready) break
                check(SystemClock.uptimeMillis() < readyUntil)
                SystemClock.sleep(50)
            }
            lateinit var source: EpubReaderBookSource
            assertTrue(request<OpenResult>(scenario) { reader, callback ->
                source = EpubReaderBookSource("pagination-${System.nanoTime()}",
                    ReaderInput.fromAsset(reader.context, "epubs/the-art-of-arm-assembly.epub"), File(reader.context.cacheDir, "pagination-test"))
                reader.openBook(source, callback = callback)
            } is OpenResult.Opened)
            try {
                request<ReaderResult<ModeChange>>(scenario) { r, c -> r.setReadingMode(ReadingMode.PAGED, c) }
                request<ReaderResult<StyleApplication>>(scenario) { r, c -> r.applyStyle(ReaderStyle.default(), c) }
                request<NavigationResult<ReadingPosition>>(scenario) { r, c -> r.goToChapter("spine-9", callback = c) }
                var previous = position(scenario)
                assertEquals("spine-9", previous.chapterId)
                assertEquals(1, previous.pageNumberHint)
                val timings = mutableListOf<Long>()
                val documentViews = mutableSetOf<android.webkit.WebView>()
                var pages = 0
                while (previous.chapterId == "spine-9") {
                    check(++pages < 500) { "Unbounded pagination" }
                    val start = SystemClock.elapsedRealtime()
                    val result = request<NavigationResult<ReadingPosition>>(scenario) { r, c -> r.nextPage(NavigationOptions(animated = false, respectInterval = false), c) }
                    timings += SystemClock.elapsedRealtime() - start
                    if (result !is NavigationResult.Moved) {
                        val diagnostics = LinkedBlockingQueue<String>()
                        scenario.onActivity { activity ->
                            fun inspect(view: android.view.View) {
                                if (view is android.webkit.WebView) view.evaluateJavascript("JSON.stringify({x:pageXOffset,inner:innerWidth,client:document.documentElement.clientWidth,root:document.documentElement.scrollWidth,body:document.body.scrollWidth,rect:document.body.getBoundingClientRect().toJSON(),state:window.__iread.state()})") { diagnostics.add(it) }
                                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) inspect(view.getChildAt(i))
                            }
                            inspect(activity.findViewById(R.id.reader))
                        }
                        repeat(2) { Log.e("EpubPagination", "layout=" + diagnostics.poll(3, TimeUnit.SECONDS)) }
                    }
                    assertTrue("page ${previous.pageNumberHint}: $result", result is NavigationResult.Moved)
                    val next = position(scenario)
                    if (next.chapterId == "spine-9") scenario.onActivity { activity ->
                        documentViews.clear()
                        fun collect(view: android.view.View) {
                            if (view is android.webkit.WebView) documentViews.add(view)
                            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
                        }
                        collect(activity.findViewById(R.id.reader))
                        assertTrue("Unbounded document read-ahead: ${documentViews.size} WebViews", documentViews.size <= 3)
                    }
                    if (next.chapterId == previous.chapterId) assertEquals(previous.pageNumberHint!! + 1, next.pageNumberHint)
                    else assertEquals(1, next.pageNumberHint)
                    previous = next
                }
                request<NavigationResult<ReadingPosition>>(scenario) { r, c -> r.previousPage(NavigationOptions(animated = false, respectInterval = false), c) }
                assertEquals("spine-9", position(scenario).chapterId)
                assertEquals(pages, position(scenario).pageNumberHint)
                repeat(pages - 1) {
                    val before = position(scenario)
                    assertTrue(request<NavigationResult<ReadingPosition>>(scenario) { r, c -> r.previousPage(NavigationOptions(animated = false, respectInterval = false), c) } is NavigationResult.Moved)
                    assertEquals(before.pageNumberHint!! - 1, position(scenario).pageNumberHint)
                }
                Log.i("EpubPagination", "pages=$pages forwardMs=$timings median=${timings.sorted()[timings.size / 2]}")
                // Exercise the snapshot/animation path after repeated reuse of both documents.
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    reader.setPageTurnEffect(PageTurnEffects.slide())
                    reader.setPageTurnOptions(PageTurnOptions(durationMs = 80))
                }
                repeat(3) {
                    val before = position(scenario)
                    assertTrue(request<NavigationResult<ReadingPosition>>(scenario) { r, c -> r.nextPage(NavigationOptions(respectInterval = false), c) } is NavigationResult.Moved)
                    assertEquals(before.pageNumberHint!! + 1, position(scenario).pageNumberHint)
                }
                val beforeCancel = position(scenario)
                val cancelled = LinkedBlockingQueue<ReaderCompletion<NavigationResult<ReadingPosition>>>()
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    val request = reader.nextPage(NavigationOptions(respectInterval = false), ReaderCallback { cancelled.add(it) })
                    reader.postDelayed({ request.cancel() }, 1)
                }
                assertEquals(ReaderCompletion.Cancelled, cancelled.poll(10, TimeUnit.SECONDS))
                assertEquals(beforeCancel, position(scenario))
                assertTrue(request<NavigationResult<ReadingPosition>>(scenario) { r, c -> r.nextPage(NavigationOptions(respectInterval = false), c) } is NavigationResult.Moved)
            } finally {
                scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                source.close()
            }
        }
    }
    private fun position(scenario: ActivityScenario<ReaderActivity>): ReadingPosition {
        var value: ReadingPosition? = null
        scenario.onActivity { value = it.findViewById<ReaderView>(R.id.reader).currentPosition }
        return requireNotNull(value)
    }
    private fun <T> request(scenario: ActivityScenario<ReaderActivity>, call: (ReaderView, ReaderCallback<T>) -> Unit): T {
        val queue = LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity { call(it.findViewById(R.id.reader), ReaderCallback { queue.add(it) }) }
        val result = queue.poll(40, TimeUnit.SECONDS)
        assertTrue("operation: $result", result is ReaderCompletion.Completed)
        return (result as ReaderCompletion.Completed).value
    }
}
