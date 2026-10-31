package org.peyilo.ireadview.epub

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RenderNode
import android.os.Build
import android.os.Trace
import android.view.View

internal interface EpubPageRecording : AutoCloseable {
    override fun close() = Unit
    val canReplayHardware: Boolean get() = true
    val isIndependent: Boolean get() = false
    /** False for software capture; the caller can draw the measured hierarchy directly. */
    fun draw(canvas:Canvas):Boolean
    companion object {
        fun prepare(view: View, raster: Boolean, budget: EpubBitmapBudget): EpubPageRecording {
            Trace.beginSection("iRead.epub.record")
            try {
                return if (Build.VERSION.SDK_INT >= 29) HardwareEpubPageRecording(view)
                else {
                    val allocation = if (raster && Build.VERSION.SDK_INT >= 26) budget.allocate(view.width, view.height) else null
                    if (allocation == null) SoftwareEpubPageRecording(view)
                    else try { BitmapEpubPageRecording(view, allocation) }
                    catch (error: Throwable) { allocation.close(); throw error }
                }
            } finally { Trace.endSection() }
        }
    }
}

/** Validity follows content changes, independently of a page's visibility or recording identity. */
internal class EpubRecordingCache(private val budget: EpubBitmapBudget) {
    private var revision = 0L
    var onChanged: (EpubPageRecording?) -> Unit = {}
    var current: EpubPageRecording? = null
        private set(value) {
            field = value
            onChanged(value)
        }

    fun invalidate() {
        revision++
        current?.close()
        current = null
    }

    fun capture(view: View, raster: Boolean) {
        val observedRevision = revision
        current?.close()
        current = null
        val next = EpubPageRecording.prepare(view, raster, budget)
        if (revision == observedRevision) current = next else next.close()
    }

    fun visibilityChanged(tracksContentChanges: Boolean) {
        if (!tracksContentChanges || current?.isIndependent != true) invalidate()
    }
}

internal class BitmapEpubPageRecording(view: View, private val allocation: EpubBitmapBudget.Allocation) : EpubPageRecording {
    private val bitmap = allocation.bitmap.also { view.draw(Canvas(it)) }
    override val isIndependent get() = true
    override fun close() = allocation.close()
    override fun draw(canvas: Canvas): Boolean {
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return true
    }
}

/** Software drawing commands for the pre-RenderNode path. Referenced Bitmap pixels can still
 * change: the recording itself is not an immutable pixel identity. Effects capture owned pixels.
 * Recorded during idle page preparation, so a gesture's bitmap snapshots do not synchronously
 * request two software draws from Chromium. Owned by the same three-page cache.
 */
internal class SoftwareEpubPageRecording(view: View) : EpubPageRecording {
    private val picture = Picture()
    override val isIndependent get() = true
    override val canReplayHardware get() = Build.VERSION.SDK_INT >= 23
    init {
        val canvas = picture.beginRecording(view.width, view.height)
        try { canvas.clipRect(0, 0, view.width, view.height); view.draw(canvas) }
        finally { picture.endRecording() }
    }
    override fun draw(canvas: Canvas): Boolean {
        if (canvas.isHardwareAccelerated && !canReplayHardware) return false
        canvas.drawPicture(picture)
        return true
    }
}

/** Hardware recording keeps idle work short and preserves full-resolution glyphs.
 * Child RenderNodes remain hierarchy-owned: rebuild after the page changes visibility
 * state, rather than reusing a parked recording as the next turn's current page.
 */
@TargetApi(29)
internal class HardwareEpubPageRecording(view:View):EpubPageRecording {
    private val node=RenderNode("iRead EPUB page").apply { setPosition(0,0,view.width,view.height) }
    init {
        val canvas=node.beginRecording(view.width,view.height)
        try { canvas.clipRect(0,0,view.width,view.height);view.draw(canvas) }
        finally { node.endRecording() }
    }
    override fun draw(canvas:Canvas):Boolean {
        if(!canvas.isHardwareAccelerated)return false
        canvas.drawRenderNode(node)
        return true
    }
}
