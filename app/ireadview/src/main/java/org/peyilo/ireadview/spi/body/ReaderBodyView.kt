package org.peyilo.ireadview.spi.body

import android.content.Context
import android.graphics.RectF
import android.widget.FrameLayout

/**
 * Body slot shared by block text and document renderers. Main-thread only.
 * The owning presentation supplies content and owns page templates, navigation commits and effects.
 * Implementations keep their format-specific binding API; EPUB need not invent block text offsets.
 */
abstract class ReaderBodyView(context: Context) : FrameLayout(context) {
    private var renderer: ReaderBodyRenderer = DefaultReaderBodyRenderer
    protected var underlinePainter: ReaderUnderlineRenderer = ReaderUnderlinePainter()
        private set
    protected var selectionPainter: org.peyilo.ireadview.spi.selection.ReaderSelectionRenderer =
        org.peyilo.ireadview.spi.selection.ReaderSelectionPainter()
        private set
    private var componentsReleased = false
    private var componentsConfigured = false
    private var released = false

    /** Configure an unmounted body once. Replacing Widget components recreates bodies,
     * keeping cached/animated surfaces on their original component generation.
     */
    fun configureComponents(components: org.peyilo.ireadview.spi.presentation.ReaderPresentationComponents) {
        check(parent == null && !released && !componentsReleased && !componentsConfigured)
        val drawing = components.bodyRendererFactory.create(context)
        val selection = try {
            components.selectionRendererFactory.create(context)
        } catch (error: Throwable) {
            try {
                drawing.close()
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
        val underline = try {
            components.underlineRendererFactory.create(context)
        } catch (error: Throwable) {
            for (component in listOf(selection, drawing)) {
                try {
                    component.close()
                } catch (cleanup: Throwable) {
                    error.addSuppressed(cleanup)
                }
            }
            throw error
        }
        closeComponents(listOf(renderer, selectionPainter, underlinePainter))
        renderer = drawing; selectionPainter = selection; underlinePainter = underline
        componentsConfigured = true
    }

    protected fun drawBodyContent(
        canvas: android.graphics.Canvas,
        drawDefault: (android.graphics.Canvas) -> Unit
    ) {
        // Preserve the original drawing path for the default strategy, including software
        // WebView snapshots. Only custom renderers need the extra Canvas isolation scope.
        if (renderer === DefaultReaderBodyRenderer) {
            drawDefault(canvas); return
        }
        val save = canvas.save()
        try {
            renderer.draw(canvas, this, drawDefault)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun releaseComponents() {
        if (componentsReleased) return
        componentsReleased = true
        closeComponents(listOf(renderer, selectionPainter, underlinePainter))
    }

    private fun closeComponents(components: List<AutoCloseable>) {
        for (component in components) {
            try {
                component.close()
            } catch (error: Exception) {
                android.util.Log.w("iReadView", "Body extension cleanup failed", error)
            }
        }
    }

    /** Invoke the owned decoration renderer with isolated Canvas state. */
    protected fun drawUnderlines(
        canvas: android.graphics.Canvas,
        segments: List<org.peyilo.ireadview.spi.content.TextLineSegment>,
        style: org.peyilo.ireadview.api.style.TextLineStyle, density: Float
    ) {
        if (underlinePainter is ReaderUnderlinePainter) {
            underlinePainter.draw(canvas, segments, style, density)
            return
        }
        val save = canvas.save()
        try {
            underlinePainter.draw(canvas, segments, style, density)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    /** Visible selection bounds in body-local physical pixels, or null. */
    abstract fun selectionBounds(): RectF?

    /** Handle touch target in body-local physical pixels. Null if that endpoint is not displayed. */
    open fun selectionHandleBounds(start: Boolean): RectF? = null

    /** End a binding, invalidate pending callbacks and clear content. Safe to call repeatedly. */
    abstract fun clearContentBinding()

    /** Final, idempotent release. A temporarily recycled row stays reusable via clearContentBinding.
     * The template method always closes renderer components, even if backend cleanup fails.
     */
    fun release() {
        if (released) return
        released = true
        try {
            clearContentBinding()
        } finally {
            try {
                onRelease()
            } finally {
                releaseComponents()
            }
        }
    }

    /** Backend resources (WebView, callbacks, jobs). Called once after clearing the binding. */
    protected open fun onRelease() {}
}
