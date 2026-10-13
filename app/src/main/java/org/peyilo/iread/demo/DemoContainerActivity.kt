package org.peyilo.iread.demo

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import org.peyilo.ireadview.api.event.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.widget.BaseReadView

/** An independent generic Widget demo, with all animation/gesture ownership in BaseReadView. */
class DemoContainerActivity : Activity() {
    private lateinit var reader: DemoCardReadView
    private lateinit var status: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private var effectsDialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val index = (savedInstanceState?.getInt("card", 0) ?: 0).coerceIn(0, DemoCardReadView.CARD_COUNT - 1)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        status = TextView(this).apply { text = "独立容器效果演示"; setPadding(16, 16, 16, 16) }
        reader = DemoCardReadView(this, index).apply { id = R.id.effect_container }
        layout.addView(status); layout.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() } }
        previous = button("上一张") { reader.previousPage(NavigationOptions(), callback()) }
        next = button("下一张") { reader.nextPage(NavigationOptions(), callback()) }
        layout.addView(LinearLayout(this).apply {
            listOf(previous, button("效果") { showEffects() }, next, button("退出") { finish() }).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
        setContentView(layout)
        savedInstanceState?.getString("effect")?.let { DemoReaderEffects.fromId(it) }?.let { reader.setPageTurnEffect(it) }
        reader.addReadViewListener(object : ReadViewListener<Int> {
            override fun onLocationChanged(view: BaseReadView<Int>, change: ReadLocationChange<Int>) { refresh() }
            override fun onNavigationStateChanged(view: BaseReadView<Int>, state: ReadNavigationState) { refresh() }
            override fun onNavigationCapabilitiesChanged(view: BaseReadView<Int>, capabilities: ReadNavigationCapabilities) { refresh() }
            override fun onEffectFallback(view: BaseReadView<Int>, requestedId: String, appliedId: String, reason: org.peyilo.ireadview.api.effect.EffectFallbackReason) {
                Toast.makeText(this@DemoContainerActivity, "效果已回退为无动画", Toast.LENGTH_SHORT).show(); refresh()
            }
        })
        refresh()
    }
    private fun refresh() {
        if (!::reader.isInitialized || isDestroyed) return
        val state = when (reader.navigationState) {
            ReadNavigationState.Idle -> "已就绪"
            ReadNavigationState.Preparing -> "准备中"
            ReadNavigationState.Settling -> "翻页中"
            ReadNavigationState.Dragging -> "拖动中"
        }
        status.text = "独立容器 · 卡片 ${reader.currentLocation?.plus(1) ?: "…"}/${DemoCardReadView.CARD_COUNT} · $state"
        previous.isEnabled = reader.navigationCapabilities.previous == NavigationAvailability.Available
        next.isEnabled = reader.navigationCapabilities.next == NavigationAvailability.Available
    }
    private fun callback() = ReaderCallback<NavigationResult<Int>> { completion ->
        if (isFinishing || isDestroyed) return@ReaderCallback
        when (val result = (completion as? ReaderCompletion.Completed)?.value) {
            is NavigationResult.Failed -> status.text = result.error.message
            is NavigationResult.Rejected -> status.text = "当前无法翻页：${result.reason}"
            else -> refresh()
        }
    }
    private fun showEffects() {
        val factories = DemoReaderEffects.factories()
        val dialog = AlertDialog.Builder(this).setTitle("容器翻页效果").setSingleChoiceItems(DemoReaderEffects.labels,
            factories.indexOfFirst { it.id == reader.pageTurnEffectId }) { selected, index -> reader.setPageTurnEffect(factories[index]); selected.dismiss() }
            .setNeutralButton(if (reader.pageTurnOptions.reduceMotion) "启用动画" else "减少动画") { _, _ ->
                reader.setPageTurnOptions(reader.pageTurnOptions.copy(reduceMotion = !reader.pageTurnOptions.reduceMotion))
            }.setNegativeButton("取消", null).create()
        effectsDialog?.dismiss(); effectsDialog = dialog
        dialog.setOnDismissListener { if (effectsDialog === dialog) effectsDialog = null }
        dialog.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("card", reader.currentLocation ?: 0); outState.putString("effect", reader.pageTurnEffectId)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() { effectsDialog?.dismiss(); effectsDialog = null; super.onStop() }
    override fun onDestroy() { if (::reader.isInitialized) reader.release(); super.onDestroy() }
}
