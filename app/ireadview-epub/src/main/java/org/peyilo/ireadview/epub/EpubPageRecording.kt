package org.peyilo.ireadview.epub

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RenderNode
import android.os.Build
import android.view.View

internal interface EpubPageRecording {
    val canReplayHardware: Boolean get() = true
    /** False for software capture; the caller can draw the measured hierarchy directly. */
    fun draw(canvas:Canvas):Boolean
}

/** Software drawing commands for the pre-RenderNode path. Referenced Bitmap pixels can still
 * change: the recording itself is not an immutable pixel identity. Effects capture owned pixels.
 * Recorded during idle page preparation, so a gesture's bitmap snapshots do not synchronously
 * request two software draws from Chromium. Owned by the same three-page cache.
 */
internal class SoftwareEpubPageRecording(view: View) : EpubPageRecording {
    private val picture = Picture()
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
