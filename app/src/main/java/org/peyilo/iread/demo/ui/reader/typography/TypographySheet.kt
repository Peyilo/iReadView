package org.peyilo.iread.demo.ui.reader.typography

import android.app.AlertDialog
import org.peyilo.iread.demo.reader.PageChromeSettings
import org.peyilo.iread.demo.reader.PageChromePosition
import android.app.Activity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.peyilo.iread.demo.ui.reader.controls.ReferenceSliderView
import org.peyilo.iread.demo.ui.reader.controls.SegmentedChoiceView
import org.peyilo.iread.demo.ui.reader.menu.ReaderBottomSheets
import org.peyilo.ireadview.api.style.*
import kotlin.math.roundToInt

/** Reference typography controls, using the public style API and default-template configuration API. */
internal class TypographySheet(
    private val activity: Activity,
    private val current: () -> ReaderStyle,
    private val apply: (ReaderStyle) -> Unit,
    private val currentChrome: () -> PageChromeSettings,
    private val applyChrome: (PageChromeSettings) -> Unit
) {
    private var dialog: BottomSheetDialog? = null
    private val bindings = ArrayList<() -> Unit>()
    private var context = ReaderBottomSheets.context(activity)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
    fun dismiss() { dialog?.dismiss(); dialog = null; bindings.clear() }
    fun refresh() { if (dialog?.isShowing == true) bindings.toList().forEach { it() } }

    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        dismiss(); context = ReaderBottomSheets.context(activity)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xffffffff.toInt())
            setPadding(dp(20), dp(15), dp(20), dp(24))
        }
        choices(panel, "标题对齐", listOf("起始对齐", "居中", "末尾对齐"), { current().title.alignment.ordinal }) {
            apply(ReaderStyles.withTitleAlignment(current(), TitleAlignment.entries[it]))
        }
        pair(panel, "文字大小（sp）",
            Value("标题", 10f, 60f, { it.title.textSizeSp }) { s, v -> ReaderStyles.withTextSizeSp(s, TextRole.TITLE, v) },
            Value("正文", 10f, 60f, { it.body.textSizeSp }) { s, v -> ReaderStyles.withTextSizeSp(s, TextRole.BODY, v) })
        pair(panel, "文字间距（dp）",
            Value("标题", 0f, 20f, { spacing(it, TextRole.TITLE) }) { s, v -> ReaderStyles.withLetterSpacingDp(s, TextRole.TITLE, v) },
            Value("正文", 0f, 20f, { spacing(it, TextRole.BODY) }) { s, v -> ReaderStyles.withLetterSpacingDp(s, TextRole.BODY, v) })
        pair(panel, "行间距 / 段间距（dp）",
            Value("行距", 0f, 100f, { it.body.lineSpacingExtraDp }) { s, v -> ReaderStyles.withLineSpacing(s, s.body.lineSpacingMultiplier, v) },
            Value("段距", 0f, 100f, { it.body.paragraphSpacingDp }) { s, v -> ReaderStyles.withParagraphSpacingDp(s, v) })
        padding(panel)
        pair(panel, "标题行距（dp） / 标题行距倍率",
            Value("行距", 0f, 100f, { it.title.lineSpacingExtraDp }) { s, v -> ReaderStyles.withTitleLineSpacing(s, s.title.lineSpacingMultiplier, v) },
            Value("倍率", 0.5f, 3f, { it.title.lineSpacingMultiplier }, 0.1f) { s, v -> ReaderStyles.withTitleLineSpacing(s, v, s.title.lineSpacingExtraDp) })
        pair(panel, "页眉 / 页脚字号（sp）",
            Value("页眉", 8f, 30f, { it.header.textSizeSp }) { s, v -> ReaderStyles.withTextSizeSp(s, TextRole.HEADER, v) },
            Value("页脚", 8f, 30f, { it.footer.textSizeSp }) { s, v -> ReaderStyles.withTextSizeSp(s, TextRole.FOOTER, v) })
        pair(panel, "首行缩进 / 行距倍率",
            Value("缩进", 0f, 4f, { it.body.firstLineIndentEm }, 0.1f) { s, v -> ReaderStyles.withFirstLineIndentEm(s, v) },
            Value("倍率", 0.5f, 3f, { it.body.lineSpacingMultiplier }, 0.1f) { s, v -> ReaderStyles.withLineSpacing(s, v, s.body.lineSpacingExtraDp) })
        pair(panel, "标题后间距 / 页眉高度（dp，0为自动）",
            Value("标题", 0f, 100f, { it.title.spacingAfterDp }) { s, v -> ReaderStyles.withTitleSpacingAfterDp(s, v) },
            Value("页眉", 0f, 80f, { it.header.heightDp ?: 0f }) { s, v -> ReaderStyles.withChromeHeightDp(s, TextRole.HEADER, v.takeIf { it > 0 }) })
        pair(panel, "页脚高度（dp） / 正文段落内边距（dp）",
            Value("页脚", 0f, 80f, { it.footer.heightDp ?: 0f }) { s, v -> ReaderStyles.withChromeHeightDp(s, TextRole.FOOTER, v.takeIf { it > 0 }) },
            Value("段落", 0f, 32f, { it.body.blockPadding.topDp }) { s, v -> ReaderStyles.withPadding(s, PaddingRegion.PARAGRAPH, s.body.blockPadding.copy(topDp = v, bottomDp = v)) })
        font(panel, "标题字体", TextRole.TITLE)
        font(panel, "正文字体", TextRole.BODY)
        font(panel, "页眉字体", TextRole.HEADER)
        font(panel, "页脚字体", TextRole.FOOTER)
        pageChrome(panel)
        choices(panel, "页眉 / 页脚", listOf("全部显示", "仅页眉", "仅页脚", "全部隐藏"), {
            when { current().header.visible && current().footer.visible -> 0; current().header.visible -> 1; current().footer.visible -> 2; else -> 3 }
        }) { index ->
            val next = ReaderStyles.withChromeVisibility(current(), TextRole.HEADER, index < 2)
            apply(ReaderStyles.withChromeVisibility(next, TextRole.FOOTER, index == 0 || index == 2))
        }
        val scroll = ScrollView(context).apply { addView(panel); isFillViewport = false }
        val shown = ReaderBottomSheets.show(scroll)
        dialog = shown
        shown.setOnDismissListener { if (dialog === shown) { dialog = null; bindings.clear() } }
        refresh()
    }

    private data class Value(val label: String, val min: Float, val max: Float,
        val get: (ReaderStyle) -> Float, val step: Float = 1f, val set: (ReaderStyle, Float) -> ReaderStyle)
    private fun spacing(style: ReaderStyle, role: TextRole): Float = ReaderStyles.letterSpacingDp(style, role)
        ?: (if (role == TextRole.TITLE) style.title.letterSpacingEm else style.body.letterSpacingEm) * ReaderStyles.textSizeSp(style, role) * activity.resources.displayMetrics.scaledDensity / activity.resources.displayMetrics.density
    private fun caption(panel: LinearLayout, text: String): TextView = TextView(context).apply {
        this.text = text; setTextColor(0xff000000.toInt()); setPadding(0, 0, 0, dp(15))
    }.also { panel.addView(it) }
    private fun section(panel: LinearLayout): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        .also { panel.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) }) }
    private fun pair(panel: LinearLayout, text: String, first: Value, second: Value) {
        val group = section(panel); caption(group, text)
        val row = LinearLayout(context); group.addView(row)
        slider(row, first); slider(row, second)
    }
    private fun slider(row: LinearLayout, value: Value) {
        val control = ReferenceSliderView(context).apply {
            stepCount = ((value.max - value.min) / value.step).roundToInt() + 1
            thumbLabel = value.label; showReferenceMarker = false
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setStepIndex(((value.get(current()) - value.min) / value.step).roundToInt(), resetReference = true)
            onStepSelected = { index ->
                if (dialog?.isShowing == true && !activity.isFinishing) apply(value.set(current(), value.min + index * value.step))
            }
        }
        row.addView(control, LinearLayout.LayoutParams(0, -2, 1f))
        bindings.add {
            if (!control.isInteracting) control.setStepIndex(((value.get(current()) - value.min) / value.step).roundToInt())
            control.contentDescription = "${value.label} · ${value.get(current())}"
        }
    }
    private fun choices(panel: LinearLayout, text: String, options: List<String>, selected: () -> Int, action: (Int) -> Unit) {
        val group = section(panel); caption(group, text)
        val choice = SegmentedChoiceView(context).apply { setOptions(options); setSelectedIndex(selected()); onSelected = action }
        group.addView(choice, LinearLayout.LayoutParams(-1, dp(30)))
        bindings.add { choice.setSelectedIndex(selected()) }
    }
    private fun padding(panel: LinearLayout) {
        val group = section(panel); caption(group, "边距（dp）")
        val regions = listOf(PaddingRegion.PAGE, PaddingRegion.HEADER, PaddingRegion.FOOTER, PaddingRegion.BODY, PaddingRegion.TITLE, PaddingRegion.CONTENT)
        var region = regions.first()
        val choice = SegmentedChoiceView(context).apply { setOptions(listOf("页面", "页眉", "页脚", "内容", "标题", "正文")) }
        group.addView(choice, LinearLayout.LayoutParams(-1, dp(30)))
        val extraRegions = listOf(PaddingRegion.BODY_VIEWPORT, PaddingRegion.PARAGRAPH)
        val extraChoice = SegmentedChoiceView(context).apply { setOptions(listOf("正文视口", "段落内边距")); setSelectedIndex(-1) }
        group.addView(extraChoice, LinearLayout.LayoutParams(-1, dp(30)).apply { topMargin = dp(10) })
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }; group.addView(grid)
        var paddingBindings = emptyList<() -> Unit>()
        fun bind() {
            bindings.removeAll(paddingBindings.toSet()); grid.removeAllViews()
            val before = bindings.size
            val sides = listOf("始", "末", "上", "下")
            for (rowIndex in 0..1) {
                val row = LinearLayout(context); grid.addView(row)
                for (side in rowIndex * 2..rowIndex * 2 + 1) {
                    slider(row, Value(sides[side], 0f, if (region == PaddingRegion.TITLE && side == 3) 300f else 100f, {
                        val p = ReaderStyles.padding(it, region)
                        when (side) { 0 -> p.startDp; 1 -> p.endDp; 2 -> p.topDp; else -> p.bottomDp }
                    }) { style, value ->
                        val p = ReaderStyles.padding(style, region)
                        ReaderStyles.withPadding(style, region, when (side) { 0 -> p.copy(startDp = value); 1 -> p.copy(endDp = value); 2 -> p.copy(topDp = value); else -> p.copy(bottomDp = value) })
                    })
                }
            }
            paddingBindings = bindings.drop(before)
        }
        choice.onSelected = { region = regions[it]; extraChoice.setSelectedIndex(-1); bind() }
        extraChoice.onSelected = { region = extraRegions[it]; choice.setSelectedIndex(-1); bind() }
        bind()
    }
    private fun font(panel: LinearLayout, text: String, role: TextRole) {
        val fonts = listOf(org.peyilo.iread.demo.reader.ReaderDefaults.font(role), ReaderFont.System("sans-serif"), ReaderFont.System("serif"), ReaderFont.System("monospace"))
        choices(panel, text, listOf("默认", "无衬线", "衬线", "等宽"), {
            fonts.indexOf(ReaderStyles.readerFont(current(), role))
        }) { apply(ReaderStyles.withFont(current(), role, fonts[it])) }
    }
    private fun pageChrome(panel: LinearLayout) {
        val contents = listOf(ChromeContent.NONE, ChromeContent.CHAPTER_TITLE, ChromeContent.BOOK_TITLE,
            ChromeContent.PAGE_NUMBER, ChromeContent.PAGE_COUNT, ChromeContent.PAGE_PROGRESS,
            ChromeContent.TIME, ChromeContent.BATTERY_LEVEL, ChromeContent.PAGE_PROGRESS_AND_TIME)
        val labels = listOf("不显示", "章节名", "书籍名", "章内页号", "章内页数", "章内页码 / 页数", "时间", "电池电量", "页码与时间")
        val group = section(panel)
        caption(group, "页眉页脚模板")
        PageChromePosition.entries.forEach { position ->
            val item = TextView(context).apply {
                setTextColor(0xff222222.toInt()); setPadding(dp(4), dp(12), dp(4), dp(12))
            }
            val update = {
                item.text = "${position.label}：${labels[contents.indexOf(currentChrome().content(position))]}  ›"
            }
            update(); bindings += update
            item.setOnClickListener {
                AlertDialog.Builder(activity).setTitle(position.label)
                    .setSingleChoiceItems(labels.toTypedArray(), contents.indexOf(currentChrome().content(position))) { picker, index ->
                        applyChrome(currentChrome().withContent(position, contents[index]))
                        refresh(); picker.dismiss()
                    }.setNegativeButton("取消", null).show()
            }
            group.addView(item, LinearLayout.LayoutParams(-1, -2))
        }
        choices(panel, "模板返回按钮", listOf("显示", "隐藏"), { if(currentChrome().showBackButton) 0 else 1 }) {
            applyChrome(currentChrome().copy(showBackButton = it == 0))
            refresh()
        }
    }
}
