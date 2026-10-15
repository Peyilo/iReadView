package org.peyilo.iread.demo

import org.peyilo.iread.demo.ui.reader.ReaderActivity

import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.widget.ReaderView
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReaderWidgetIntegrationTest {
    @Test fun xmlWidgetNavigatesSafelyAndInvalidTargetsPreservePosition() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            awaitReady(scenario)
            val opened = request<OpenResult>(scenario) { view, callback -> view.openBook(book(), OpenBookRequest(), callback) }
            assertTrue(value(opened) is OpenResult.Opened)
            var before: ReadingPosition? = null
            scenario.onActivity { before = it.findViewById<ReaderView>(R.id.reader).currentPosition }
            assertEquals(NavigationResult.Rejected(ReaderRejection.AtBoundary), value(request<NavigationResult<ReadingPosition>>(scenario) { view, callback -> view.previousPage(NavigationOptions(), callback) }))
            assertEquals(NavigationResult.Rejected(ReaderRejection.UnknownChapter), value(request<NavigationResult<ReadingPosition>>(scenario) { view, callback -> view.goToChapter("missing", 1, callback) }))
            assertEquals(NavigationResult.Rejected(ReaderRejection.InvalidPage), value(request<NavigationResult<ReadingPosition>>(scenario) { view, callback -> view.goToPage(99999, callback) }))
            scenario.onActivity { assertEquals(before, it.findViewById<ReaderView>(R.id.reader).currentPosition) }
            val last = value(request<NavigationResult<ReadingPosition>>(scenario) { view, callback -> view.lastPage(callback) }) as NavigationResult.Moved
            assertEquals("two", last.to.chapterId)
            assertEquals(NavigationResult.Rejected(ReaderRejection.AtBoundary), value(request<NavigationResult<ReadingPosition>>(scenario) { view, callback -> view.nextPage(NavigationOptions(), callback) }))
        }
    }

    @Test fun invalidInitialPositionReturnsAnObservableFallback() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            awaitReady(scenario)
            val result = value(request<OpenResult>(scenario) { view, callback -> view.openBook(book(), OpenBookRequest(initialChapterNumber = 500), callback) }) as OpenResult.Opened
            assertTrue(result.restoration is Restoration.Fallback)
            assertEquals("one", result.position.chapterId)
            assertEquals(1, result.position.pageNumberHint)
        }
    }

    @Test fun changingFontRestoresThePageContainingTheOldContentAnchor() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            awaitReady(scenario)
            value(request<OpenResult>(scenario) { view, callback -> view.openBook(book(), OpenBookRequest(), callback) })
            val jumped = value(request<NavigationResult<ReadingPosition>>(scenario) { view, callback -> view.goToPage(3, callback) }) as NavigationResult.Moved
            val original = jumped.to
            val changed = value(request<ReaderResult<StyleApplication>>(scenario) { view, callback -> view.applyStyle(view.style.copy(body = view.style.body.copy(textSizeSp = 26f)), callback) })
            assertTrue(changed is ReaderResult.Success)
            var after: ReadingPosition? = null
            scenario.onActivity { after = it.findViewById<ReaderView>(R.id.reader).currentPosition }
            assertEquals(original.chapterId, after?.chapterId)
            val restored = value(request<NavigationResult<ReadingPosition>>(scenario) { view, callback -> view.goToPosition(original, callback) })
            assertTrue(restored is NavigationResult.Unchanged)
            scenario.onActivity { assertEquals(after, it.findViewById<ReaderView>(R.id.reader).currentPosition) }
        }
    }

    @Test fun twoInstancesCanOpenBeforeTheirFirstMeasurementWithoutSharingState() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            awaitReady(scenario)
            val firstQueue = LinkedBlockingQueue<ReaderCompletion<OpenResult>>()
            val secondQueue = LinkedBlockingQueue<ReaderCompletion<OpenResult>>()
            var first: ReaderView? = null
            var second: ReaderView? = null
            scenario.onActivity { activity ->
                activity.findViewById<ReaderView>(R.id.reader).release()
                first = ReaderView(activity)
                second = ReaderView(activity)
                first!!.openBook(book("first"), OpenBookRequest(), ReaderCallback { firstQueue.add(it) })
                second!!.openBook(book("second"), OpenBookRequest(initialChapterNumber = 2), ReaderCallback { secondQueue.add(it) })
                val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                root.addView(first, LinearLayout.LayoutParams(-1, 0, 1f))
                root.addView(second, LinearLayout.LayoutParams(-1, 0, 1f))
                activity.setContentView(root)
            }
            assertTrue(value(await(firstQueue)) is OpenResult.Opened)
            assertTrue(value(await(secondQueue)) is OpenResult.Opened)
            scenario.onActivity {
                assertEquals("first", first!!.currentBook?.bookId)
                assertEquals("one", first!!.currentPosition?.chapterId)
                assertEquals("second", second!!.currentBook?.bookId)
                assertEquals("two", second!!.currentPosition?.chapterId)
                first!!.release(); second!!.release()
            }
        }
    }

    @Test fun cancellingACallbackRequestDeliversExactlyOneCancellation() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            awaitReady(scenario)
            val queue = LinkedBlockingQueue<ReaderCompletion<OpenResult>>()
            scenario.onActivity { activity ->
                val reader = activity.findViewById<ReaderView>(R.id.reader)
                val handle = reader.openBook(GatedSource(), OpenBookRequest(), ReaderCallback { queue.add(it) })
                handle.cancel()
            }
            assertEquals(ReaderCompletion.Cancelled, await(queue))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertNull(queue.poll(250, TimeUnit.MILLISECONDS))
        }
    }

    @Test fun activityRecreationRestoresSemanticReadingPositionAfterSourceRebinding() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            awaitReady(scenario)
            val navigation = value(request<NavigationResult<ReadingPosition>>(scenario) { view, callback -> view.goToChapter("chapter-2", 2, callback) })
            assertTrue("Expected a move, got $navigation", navigation is NavigationResult.Moved)
            val moved = navigation as NavigationResult.Moved
            scenario.recreate()
            awaitReady(scenario)
            scenario.onActivity {
                val actual = it.findViewById<ReaderView>(R.id.reader).currentPosition
                assertEquals(moved.to.chapterId, actual?.chapterId)
                assertEquals(moved.to.anchor, actual?.anchor)
            }
        }
    }

    private fun <T> request(scenario: ActivityScenario<ReaderActivity>, invoke: (ReaderView, ReaderCallback<T>) -> Unit): ReaderCompletion<T> {
        val queue = LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity { invoke(it.findViewById(R.id.reader), ReaderCallback { result -> queue.add(result) }) }
        return await(queue)
    }

    private fun <T> await(queue: LinkedBlockingQueue<ReaderCompletion<T>>): ReaderCompletion<T> =
        queue.poll(15, TimeUnit.SECONDS) ?: throw AssertionError("Widget operation did not complete")

    private fun <T> value(completion: ReaderCompletion<T>): T {
        assertTrue("Expected completion, got $completion", completion is ReaderCompletion.Completed)
        return (completion as ReaderCompletion.Completed).value
    }

    private fun awaitReady(scenario: ActivityScenario<ReaderActivity>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var observed = ""
        do {
            var ready = false
            scenario.onActivity {
                val reader = it.findViewById<ReaderView>(R.id.reader)
                ready = reader.readerState.phase == ReaderPhase.Ready && reader.navigationState == ReadNavigationState.Idle
                observed = "${reader.readerState} navigation=${reader.navigationState}"
            }
            if (ready) return
            Thread.sleep(50)
        } while (System.nanoTime() < deadline)
        throw AssertionError("Demo reader did not become ready: $observed")
    }

    private fun book(id: String = "test-book"): MemoryBookSource {
        val chapters = listOf(ChapterDescriptor("one", "Chapter one", "1"), ChapterDescriptor("two", "Chapter two", "1"))
        val descriptor = BookDescriptor(id, "Test book", "1", chapters.map { TocEntry.Chapter(it) })
        val paragraphs = "A paragraph with stable content identity. 中文与 English 在同一段落中排版，字号和窗口改变时以正文锚点恢复。 ".repeat(8)
        return MemoryBookSource(descriptor, chapters.map { chapter ->
            ChapterContent(id, chapter.chapterId, "1", (0 until 30).map { TextBlock("p-$it", paragraphs) })
        })
    }

    private class GatedSource : BookSource {
        override val bookId = "gated"
        private val gate = CompletableDeferred<Unit>()
        override suspend fun loadBook(): BookDescriptor { gate.await(); return BookDescriptor(bookId, "Pending", tableOfContents = emptyList()) }
        override suspend fun loadChapter(chapterId: String): ChapterContent = error("Never requested")
    }
}
