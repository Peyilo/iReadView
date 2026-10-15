package org.peyilo.iread.demo.ui.container

import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.peyilo.ireadview.api.effect.PageSurface
import org.peyilo.ireadview.api.effect.TurnDirection
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.spi.presentation.*
import org.peyilo.ireadview.widget.BaseReadView

/** Generic location is a card index. No book, chapter, text anchor or pagination model is needed. */
internal class CardReadView(context: Context, private val initialIndex: Int) : BaseReadView<Int>(context) {
    init { require(initialIndex in 0 until CARD_COUNT) }
    private val initialize = Runnable {
        if (!isReleased && isAttachedToWindow && navigationState == ReadNavigationState.Idle) {
            navigate(ReadNavigation.Position(currentLocation ?: initialIndex), NavigationOptions(animated = false), ReaderCallback { })
        }
    }
    override fun createContentAdapter(): ReadContentAdapter<Int> = CardAdapter(context)
    override fun onContainerAttached() { post(initialize) }
    override fun onContainerViewportChanged() {
        if (!isReleased && currentLocation == null && isAttachedToWindow) { removeCallbacks(initialize); post(initialize) }
    }
    override fun onContainerDetached() { removeCallbacks(initialize) }
    override fun onRelease() { removeCallbacks(initialize) }
    override fun turnDirection(target: ReadNavigation<Int>, from: Int?, to: Int) =
        if (from != null && to < from) TurnDirection.Backward else TurnDirection.Forward

    companion object { const val CARD_COUNT = 5 }
}

private class CardAdapter(private val context: Context) : ReadContentAdapter<Int> {
    private val root = FrameLayout(context)
    private var host: ReadContentHost<Int>? = null
    private var shown: CardSurface? = null
    private var shownIndex: Int? = null
    override val contentView: View get() = root
    override val currentSurface: PageSurface? get() = shown
    override fun attach(host: ReadContentHost<Int>) { check(this.host == null); this.host = host }
    override fun onViewportChanged(viewport: ReadViewport) { if (viewport.isValid) shown?.resize(viewport) }
    override fun navigationAvailability(target: ReadNavigation<Int>): NavigationAvailability {
        val host = host ?: return NavigationAvailability.NotReady
        val index = resolve(target, host.committedLocation) ?: return NavigationAvailability.NotReady
        return if (index in 0 until CardReadView.CARD_COUNT) NavigationAvailability.Available else NavigationAvailability.AtBoundary
    }
    override suspend fun prepare(target: ReadNavigation<Int>): ReadPreparation<Int> {
        currentCoroutineContext().ensureActive()
        val host = host ?: return ReadPreparation.Rejected(ReaderRejection.NotReady)
        val viewport = host.viewport
        if (!viewport.isValid) return ReadPreparation.Rejected(ReaderRejection.NotReady)
        val index = resolve(target, host.committedLocation) ?: return ReadPreparation.Rejected(ReaderRejection.NotReady)
        if (index !in 0 until CardReadView.CARD_COUNT) return ReadPreparation.Rejected(ReaderRejection.AtBoundary)
        if (index == shownIndex && shown != null) return ReadPreparation.Unchanged(index)
        val colors = intArrayOf(0xffe6edf5.toInt(), 0xffe4eddd.toInt(), 0xfff7e8d2.toInt(), 0xffebe4f1.toInt(), 0xffdcefee.toInt())
        val view = TextView(context).apply {
            gravity = Gravity.CENTER; textSize = 24f; setTextColor(0xff263442.toInt()); setBackgroundColor(colors[index])
            val margin = (24 * resources.displayMetrics.density).toInt(); setPadding(margin, margin, margin, margin)
            text = "卡片 ${index + 1} / ${CardReadView.CARD_COUNT}\n\n左右拖动、边缘点击\n或使用键盘翻页\n\n内容由宿主提供"
        }
        val surface = CardSurface(view).also { it.resize(viewport) }
        return ReadPreparation.Ready(Candidate(this, index, host.generation, surface))
    }
    override suspend fun show(candidate: PreparedRead<Int>): Int {
        currentCoroutineContext().ensureActive()
        val prepared = candidate as? Candidate ?: error("Unexpected card candidate")
        check(prepared.owner === this && !prepared.disposed && host?.generation == prepared.generation)
        val view = prepared.surface.view ?: error("Disposed card surface")
        root.removeAllViews(); root.addView(view, FrameLayout.LayoutParams(-1, -1))
        shown?.clear(); shown = prepared.surface; shownIndex = prepared.target; prepared.transferred = true
        return prepared.target
    }
    override fun detach() { host = null; root.removeAllViews(); shown?.clear(); shown = null; shownIndex = null }
    private fun resolve(target: ReadNavigation<Int>, current: Int?): Int? = when (target) {
        ReadNavigation.First -> 0
        ReadNavigation.Last -> CardReadView.CARD_COUNT - 1
        ReadNavigation.Next -> current?.plus(1)
        ReadNavigation.Previous -> current?.minus(1)
        is ReadNavigation.Position -> target.location
    }
    private class Candidate(val owner: CardAdapter, override val target: Int, override val generation: Long,
        override val surface: CardSurface) : PreparedRead<Int> {
        var transferred = false
        var disposed = false
        override fun dispose() { if (!disposed) { disposed = true; if (!transferred) surface.clear() } }
    }
}

private class CardSurface(var view: TextView?) : PageSurface {
    override val widthPx get() = view?.width ?: 0
    override val heightPx get() = view?.height ?: 0
    fun resize(viewport: ReadViewport) {
        view?.let {
            it.measure(View.MeasureSpec.makeMeasureSpec(viewport.widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(viewport.heightPx, View.MeasureSpec.EXACTLY))
            it.layout(0, 0, viewport.widthPx, viewport.heightPx)
        }
    }
    override fun draw(canvas: Canvas) { view?.draw(canvas) }
    fun clear() { view = null }
}
