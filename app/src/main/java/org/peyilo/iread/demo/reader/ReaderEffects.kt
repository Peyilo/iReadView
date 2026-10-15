package org.peyilo.iread.demo.reader

import android.graphics.Canvas
import android.graphics.Paint
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.spi.presentation.ReadViewport

/** Host catalogue. Custom IDs are resolved here, never by the Widget's built-in resolver. */
internal object ReaderEffects {
    val labels = arrayOf("无动画", "滑动", "覆盖", "iBook 风格滑动", "Apple / iBook 仿真翻页", "Google / MoonReader 仿真翻页", "卷页", "宿主擦除示例")
    fun factories() = listOf(PageTurnEffects.none(), PageTurnEffects.slide(), PageTurnEffects.cover(), PageTurnEffects.iBookSlide(),
        PageTurnEffects.curl(), PageTurnEffects.googleCurl(), PageTurnEffects.pageCurl(), WipeFactory)
    fun fromId(id: String): PageTurnEffectFactory? = if (id == WipeFactory.id) WipeFactory else PageTurnEffects.fromId(id)
    fun isCustom(id: String) = id == WipeFactory.id

    private object WipeFactory : PageTurnEffectFactory {
        override val id = "demo.wipe"
        override fun create(): PageTurnEffect = WipeEffect()
    }
}

/** No timer, navigation or snapshots: only the two transaction surfaces and normalized progress. */
private class WipeEffect : PageTurnEffect {
    private var attached = false
    private var context: PageTurnContext? = null
    private val shadow = Paint().apply { color = 0x22000000 }
    override fun attach(host: PageTurnEffectHost) { check(!attached); attached = true }
    override fun supports(direction: TurnDirection, viewport: ReadViewport) = attached && viewport.isValid
    override fun start(context: PageTurnContext) {
        check(attached && this.context == null)
        require(context.current.widthPx == context.target.widthPx && context.current.heightPx == context.target.heightPx)
        this.context = context
    }
    override fun update(frame: PageTurnFrame) = Unit // Widget owns redraws, drag sampling and settling.
    override fun draw(canvas: Canvas, frame: PageTurnFrame) {
        val transaction = context ?: return
        val progress = frame.progress
        val width = transaction.current.widthPx.toFloat()
        val height = transaction.current.heightPx.toFloat()
        val save = canvas.save()
        try {
            canvas.clipRect(0f, 0f, width, height)
            if (progress == 1f) { transaction.target.draw(canvas); return }
            transaction.current.draw(canvas)
            if (progress == 0f) return
            val fromRight = (transaction.direction == TurnDirection.Forward) != transaction.viewport.rightToLeft
            val edge = if (fromRight) width * (1f - progress) else width * progress
            val reveal = canvas.save()
            try {
                if (fromRight) canvas.clipRect(edge, 0f, width, height) else canvas.clipRect(0f, 0f, edge, height)
                transaction.target.draw(canvas)
            } finally { canvas.restoreToCount(reveal) }
            val thickness = minOf(8f * transaction.viewport.density, width)
            if (fromRight) canvas.drawRect(maxOf(0f, edge - thickness), 0f, edge, height, shadow)
            else canvas.drawRect(edge, 0f, minOf(width, edge + thickness), height, shadow)
        } finally { canvas.restoreToCount(save) }
    }
    override fun finish(outcome: TurnOutcome) { context = null }
    override fun abort(reason: TurnAbortReason) { context = null }
    override fun detach() { context = null; attached = false }
}

