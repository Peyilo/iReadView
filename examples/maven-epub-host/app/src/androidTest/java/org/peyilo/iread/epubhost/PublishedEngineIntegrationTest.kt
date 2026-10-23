package org.peyilo.iread.epubhost

import android.os.SystemClock
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.model.ReadingPosition
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.spi.body.ReaderBodyRenderer
import org.peyilo.ireadview.spi.body.ReaderBodyRendererFactory
import org.peyilo.ireadview.spi.body.ReaderBodyView
import org.peyilo.ireadview.spi.presentation.ReaderPresentationComponents
import org.peyilo.ireadview.widget.ReaderView
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PublishedEngineIntegrationTest {
    @Test fun switchPublishedEnginesAndRestoreUsingOneWidget() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val draws = AtomicInteger()
            await(scenario) { it.readerState.phase == ReaderPhase.Ready }
            var widget: ReaderView? = null
            scenario.onActivity { activity ->
                widget = activity.reader
                assertEquals(ReaderSelectionModel.Document, activity.reader.contentCapabilities.selectionModel)
                assertTrue(activity.reader.setPresentationComponents(ReaderPresentationComponents(
                    bodyRendererFactory = ReaderBodyRendererFactory {
                        object : ReaderBodyRenderer {
                            override fun draw(canvas: android.graphics.Canvas, body: ReaderBodyView, drawDefault: (android.graphics.Canvas) -> Unit) {
                                draws.incrementAndGet(); drawDefault(canvas)
                            }
                        }
                    })) is ReaderResult.Success)
            }
            await(scenario) { it.readerState.phase == ReaderPhase.Ready && draws.get() > 0 }
            for ((button, model) in listOf(R.id.text_source to ReaderSelectionModel.Text, R.id.epub_source to ReaderSelectionModel.Document)) {
                scenario.onActivity { activity ->
                    draws.set(0)
                    activity.findViewById<Button>(button).performClick()
                    assertSame(widget, activity.reader)
                }
                await(scenario) { it.readerState.phase == ReaderPhase.Ready && it.contentCapabilities.selectionModel == model && draws.get() > 0 }
                val results = LinkedBlockingQueue<ReaderCompletion<NavigationResult<ReadingPosition>>>()
                scenario.onActivity { it.reader.nextPage(NavigationOptions(animated = false, respectInterval = false), ReaderCallback { results.add(it) }) }
                val moved = results.poll(30, TimeUnit.SECONDS) as? ReaderCompletion.Completed<*>
                assertTrue("$moved", moved?.value is NavigationResult.Moved<*>)
            }
            var before: ReadingPosition? = null
            scenario.onActivity { before = it.reader.currentPosition }
            scenario.recreate()
            await(scenario) { it.readerState.phase == ReaderPhase.Ready }
            scenario.onActivity {
                assertEquals(ReaderSelectionModel.Document, it.reader.contentCapabilities.selectionModel)
                assertEquals(before?.chapterId, it.reader.currentPosition?.chapterId)
                assertEquals(before?.pageNumberHint, it.reader.currentPosition?.pageNumberHint)
            }
        }
    }
    private fun await(scenario: ActivityScenario<MainActivity>, condition: (ReaderView) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        do {
            var ready = false
            scenario.onActivity { ready = condition(it.reader) }
            if (ready) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Published engine did not become ready")
    }
}
