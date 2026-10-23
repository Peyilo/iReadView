package org.peyilo.iread.demo

import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.spi.body.*
import org.peyilo.ireadview.spi.presentation.ReaderPresentationComponents
import org.peyilo.ireadview.spi.selection.*

@RunWith(AndroidJUnit4::class)
class ReaderBodyComponentLifecycleTest {
    @Test fun failedFactoryClosesCandidatesAndAllowsRetry() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val body = body()
            var drawingClosed = 0
            var selectionClosed = 0
            val failure = IllegalStateException("underline factory failed")
            val components = ReaderPresentationComponents(
                bodyRendererFactory = ReaderBodyRendererFactory {
                    object : ReaderBodyRenderer {
                        override fun draw(canvas: Canvas, body: ReaderBodyView, drawDefault: (Canvas) -> Unit) = drawDefault(canvas)
                        override fun close() { drawingClosed++ }
                    }
                },
                selectionRendererFactory = ReaderSelectionRendererFactory {
                    object : ReaderSelectionPainter() {
                        override fun close() { selectionClosed++; error("cleanup failure") }
                    }
                },
                underlineRendererFactory = ReaderUnderlineRendererFactory { throw failure }
            )
            try { body.configureComponents(components); fail("Factory must fail") }
            catch (error: IllegalStateException) { assertSame(failure, error) }
            assertEquals(1, drawingClosed)
            assertEquals(1, selectionClosed)
            assertEquals(1, failure.suppressed.size)
            // Failed installation must leave the body configurable; failed candidates are gone.
            body.configureComponents(ReaderPresentationComponents.DEFAULT)
            body.release()
            body.release()
            assertEquals(1, drawingClosed)
            assertEquals(1, selectionClosed)
        }
    }

    @Test fun finalReleaseClosesEveryComponentDespiteCleanupFailure() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var drawingClosed = 0
            var selectionClosed = 0
            var underlineClosed = 0
            val body = body()
            body.configureComponents(ReaderPresentationComponents(
                bodyRendererFactory = ReaderBodyRendererFactory {
                    object : ReaderBodyRenderer {
                        override fun draw(canvas: Canvas, body: ReaderBodyView, drawDefault: (Canvas) -> Unit) = drawDefault(canvas)
                        override fun close() { drawingClosed++; error("cleanup failure") }
                    }
                },
                selectionRendererFactory = ReaderSelectionRendererFactory {
                    object : ReaderSelectionPainter() { override fun close() { selectionClosed++ } }
                },
                underlineRendererFactory = ReaderUnderlineRendererFactory {
                    object : ReaderUnderlineRenderer by ReaderUnderlinePainter() {
                        override fun close() { underlineClosed++ }
                    }
                }
            ))
            body.clearContentBinding()
            assertEquals(0, underlineClosed)
            body.release()
            body.release()
            assertEquals(1, drawingClosed)
            assertEquals(1, selectionClosed)
            assertEquals(1, underlineClosed)
        }
    }

    private fun body() = object : ReaderBodyView(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun selectionBounds(): RectF? = null
        override fun clearContentBinding() {}
    }
}
