package org.peyilo.iread.demo.ui.reader.settings

import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.peyilo.iread.demo.reader.ReaderThemes
import org.peyilo.iread.demo.ui.reader.controls.SegmentedChoiceView
import org.peyilo.iread.demo.ui.reader.menu.ReaderBottomSheets
import org.peyilo.ireadview.api.effect.PageTurnEffectFactory
import org.peyilo.ireadview.api.effect.PageTurnEffects
import org.peyilo.ireadview.api.model.ReadingMode
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.widget.ReaderView

/** Reference settings: segmented turn modes and themes, using current public Widget APIs. */
internal class ReadingSettingsSheet(
    private val activity: Activity,
    private val reader: ReaderView,
    private val applyStyle: (ReaderStyle) -> Unit,
    private val onCommitted: () -> Unit,
    private val message: (String) -> Unit,
    private val books: () -> Unit,
    private val input: () -> Unit,
    private val bookmarks: () -> Unit,
    private val pageIndex: () -> Unit,
    private val details: () -> Unit
) {
    private var dialog: BottomSheetDialog? = null
    private val choices = ArrayList<SegmentedChoiceView>()
    private var pending = false
    private var turn: SegmentedChoiceView? = null
    private var curl: SegmentedChoiceView? = null
    private var themes: SegmentedChoiceView? = null
    private var motion: SegmentedChoiceView? = null
    private var navigation: SegmentedChoiceView? = null
    fun dismiss() { dialog?.dismiss(); dialog = null; choices.clear() }
    fun refresh() {
        if (dialog?.isShowing != true) return
        val id = reader.pageTurnEffectId
        turn?.setSelectedIndex(if (reader.readingMode == ReadingMode.CONTINUOUS_SCROLL) 4 else when (id) {
            PageTurnEffects.none().id -> 0; PageTurnEffects.cover().id -> 1; PageTurnEffects.slide().id -> 2
            PageTurnEffects.iBookSlide().id -> 5; else -> 3
        })
        curl?.setSelectedIndex(when (id) { PageTurnEffects.googleCurl().id -> 1; PageTurnEffects.pageCurl().id -> 2; PageTurnEffects.iBookCurl().id, "curl" -> 0; else -> -1 })
        themes?.setSelectedIndex(ReaderThemes.indexOf(reader.style))
        motion?.setSelectedIndex(if (reader.pageTurnOptions.reduceMotion) 1 else 0)
        navigation?.setSelectedIndex(if (org.peyilo.iread.demo.reader.ReaderSystemBars.navigationHidden(activity)) 1 else 0)
        choices.forEach { it.isEnabled = !pending }
        dialog?.window?.let { org.peyilo.iread.demo.reader.ReaderSystemBars.apply(activity, it) }
    }
    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        dismiss()
        val context = ReaderBottomSheets.context(activity)
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xfff6f6f6.toInt()); setPadding(dp(20), dp(16), dp(20), dp(24)) }
        fun row(label: String, labels: List<String>, action: (Int) -> Unit): SegmentedChoiceView {
            panel.addView(TextView(context).apply { text = label; setTextColor(0xff000000.toInt()); setPadding(0, 0, 0, dp(15)) })
            val control = SegmentedChoiceView(context).apply { setOptions(labels); onSelected = action }
            panel.addView(control, LinearLayout.LayoutParams(-1, dp(30)).apply { bottomMargin = dp(20) })
            choices.add(control); return control
        }
        turn = row("翻页效果", listOf("无动画", "覆盖", "滑动", "仿真", "滚动", "视差")) { index ->
            when (index) {
                0 -> selectEffect(PageTurnEffects.none())
                1 -> selectEffect(PageTurnEffects.cover())
                2 -> selectEffect(PageTurnEffects.slide())
                3 -> selectEffect(PageTurnEffects.iBookCurl())
                4 -> changeMode(ReadingMode.CONTINUOUS_SCROLL, null)
                5 -> selectEffect(PageTurnEffects.iBookSlide())
            }
        }
        themes = row("阅读主题", ReaderThemes.labels.toList()) { applyStyle(ReaderThemes.apply(it, reader.style)) }
        curl = row("仿真效果", listOf("iBook", "Google", "卷页")) {
            selectEffect(when (it) { 0 -> PageTurnEffects.iBookCurl(); 1 -> PageTurnEffects.googleCurl(); else -> PageTurnEffects.pageCurl() })
        }
        motion = row("动态效果", listOf("正常动画", "减少动态")) { selected ->
            when (val outcome = reader.setPageTurnOptions(reader.pageTurnOptions.copy(reduceMotion = selected == 1))) {
                is ReaderResult.Success -> onCommitted()
                is ReaderResult.Rejected -> message("当前无法修改动画：${outcome.reason}")
                is ReaderResult.Failure -> message(outcome.error.message)
                else -> Unit
            }
            refresh()
        }
        navigation = row("系统底部导航栏", listOf("显示", "隐藏")) { selected ->
            org.peyilo.iread.demo.reader.ReaderSystemBars.setNavigationHidden(activity, selected == 1)
            org.peyilo.iread.demo.reader.ReaderSystemBars.apply(activity, activity.window)
            dialog?.window?.let { org.peyilo.iread.demo.reader.ReaderSystemBars.apply(activity, it) }
            refresh()
        }
        val actions = LinearLayout(context)
        listOf("书籍" to books, "阅读输入" to input, "书签" to bookmarks, "全书页" to pageIndex).forEach { (label, action) ->
            actions.addView(Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
                text = label; textSize = 13f; setOnClickListener { dismiss(); action() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        panel.addView(actions)
        panel.addView(Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            text = "书籍信息"; setOnClickListener { dismiss(); details() }
        }, LinearLayout.LayoutParams(-1, -2))
        val shown = ReaderBottomSheets.show(ScrollView(context).apply { addView(panel) })
        dialog = shown
        shown.setOnDismissListener { if (dialog === shown) { dialog = null; choices.clear() } }
        refresh()
    }
    private fun selectEffect(factory: PageTurnEffectFactory) = changeMode(ReadingMode.PAGED, factory)
    private fun changeMode(mode: ReadingMode, factory: PageTurnEffectFactory?) {
        if (pending || activity.isFinishing || activity.isDestroyed) return
        pending = true; refresh()
        reader.setReadingMode(mode) { completion ->
            if (activity.isFinishing || activity.isDestroyed) return@setReadingMode
            when (val result = (completion as? ReaderCompletion.Completed)?.value) {
                is ReaderResult.Success -> {
                    val effect = factory?.let { reader.setPageTurnEffect(it) }
                    if (effect is ReaderResult.Rejected) message("当前无法切换效果：${effect.reason}")
                    else if (effect is ReaderResult.Failure) message(effect.error.message)
                    onCommitted()
                }
                is ReaderResult.Failure -> message(result.error.message)
                is ReaderResult.Rejected -> message("当前无法切换模式：${result.reason}")
                else -> Unit
            }
            pending = false; refresh()
        }
    }
}
