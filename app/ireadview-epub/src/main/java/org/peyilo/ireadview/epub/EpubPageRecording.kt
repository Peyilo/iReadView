package org.peyilo.ireadview.epub

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.RenderNode
import android.view.View

internal interface EpubPageRecording {
    /** False for software capture; the caller can draw the measured hierarchy directly. */
    fun draw(canvas:Canvas):Boolean
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
