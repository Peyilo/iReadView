package org.peyilo.iread.demo

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.peyilo.ireadview.api.style.*

/** Host UI only: each editor patches the latest immutable style through one public transaction. */
internal class DemoReaderStylePanel(
    private val activity: Activity,
    private val current: () -> ReaderStyle,
    private val apply: (ReaderStyle) -> Unit
) {
    private var dialog: AlertDialog? = null
    fun dismiss() { dialog?.dismiss(); dialog = null }

    fun show() = menu("排版设置", listOf(
        "页面与正文区域" to ::page,
        "正文" to ::body,
        "章节标题" to ::title,
        "页眉" to { chrome(true) },
        "页脚" to { chrome(false) }
    ))

    private fun page() = menu("页面与正文区域", listOf(
        "页面边距" to { insets("页面边距", { it.page.padding }) { style, value -> style.copy(page = style.page.copy(padding = value)) } },
        "正文区域边距" to { insets("正文区域边距", { it.page.viewportPadding }) { style, value -> style.copy(page = style.page.copy(viewportPadding = value)) } },
        "正文容器边距" to { insets("正文容器边距", { it.body.containerPadding }) { style, value -> style.copy(body = style.body.copy(containerPadding = value)) } },
        "正文内容边距" to { insets("正文内容边距", { it.body.contentPadding }) { style, value -> style.copy(body = style.body.copy(contentPadding = value)) } }
    ))

    private fun body() = menu("正文", listOf(
        "字号（sp）" to { number("正文字号（sp）", { it.body.textSizeSp }, positive = true) { style, value -> style.copy(body = style.body.copy(textSizeSp = value)) } },
        "字体" to { font("正文字体", { it.body.readerFont }) { style, value -> style.copy(body = style.body.copy(readerFont = value, font = null)) } },
        "字距（em）" to { number("正文字距（em）", { it.body.letterSpacingEm }, signed = true) { style, value -> style.copy(body = style.body.copy(letterSpacingEm = value)) } },
        "首行缩进（em）" to { number("首行缩进（em）", { it.body.firstLineIndentEm }) { style, value -> style.copy(body = style.body.copy(firstLineIndentEm = value)) } },
        "行距倍率" to { number("行距倍率", { it.body.lineSpacingMultiplier }, positive = true) { style, value -> style.copy(body = style.body.copy(lineSpacingMultiplier = value)) } },
        "额外行距（dp）" to { number("额外行距（dp）", { it.body.lineSpacingExtraDp }) { style, value -> style.copy(body = style.body.copy(lineSpacingExtraDp = value)) } },
        "段落间距（dp）" to { number("段落间距（dp）", { it.body.paragraphSpacingDp }) { style, value -> style.copy(body = style.body.copy(paragraphSpacingDp = value)) } },
        "段落边距" to { insets("段落边距", { it.body.blockPadding }) { style, value -> style.copy(body = style.body.copy(blockPadding = value)) } }
    ))

    private fun title() = menu("章节标题", listOf(
        "字号（sp）" to { number("标题字号（sp）", { it.title.textSizeSp }, positive = true) { style, value -> style.copy(title = style.title.copy(textSizeSp = value)) } },
        "字体" to { font("标题字体", { it.title.readerFont }) { style, value -> style.copy(title = style.title.copy(readerFont = value, font = null)) } },
        "字距（em）" to { number("标题字距（em）", { it.title.letterSpacingEm }, signed = true) { style, value -> style.copy(title = style.title.copy(letterSpacingEm = value)) } },
        "标题边距" to { insets("标题边距", { it.title.padding }) { style, value -> style.copy(title = style.title.copy(padding = value)) } },
        "标题后间距（dp）" to { number("标题后间距（dp）", { it.title.spacingAfterDp }) { style, value -> style.copy(title = style.title.copy(spacingAfterDp = value)) } },
        "对齐" to { choices("标题对齐", arrayOf("起始", "居中", "末尾"), current().title.alignment.ordinal) { index ->
            val style = current(); apply(style.copy(title = style.title.copy(alignment = TitleAlignment.entries[index])))
        } }
    ))

    private fun chrome(header: Boolean) {
        fun get(style: ReaderStyle) = if (header) style.header else style.footer
        fun patch(style: ReaderStyle, value: ChromeStyle) = if (header) style.copy(header = value) else style.copy(footer = value)
        val name = if (header) "页眉" else "页脚"
        menu(name, listOf(
            "显示或隐藏" to { val style = current(); val chrome = get(style); apply(patch(style, chrome.copy(visible = !chrome.visible))) },
            "字号（sp）" to { number("${name}字号（sp）", { get(it).textSizeSp }, positive = true) { style, value -> patch(style, get(style).copy(textSizeSp = value)) } },
            "字体" to { font("${name}字体", { get(it).readerFont }) { style, value -> patch(style, get(style).copy(readerFont = value, font = null)) } },
            "边距" to { insets("${name}边距", { get(it).padding }) { style, value -> patch(style, get(style).copy(padding = value)) } },
            "高度（dp，0为自动）" to { number("${name}高度（dp，0为自动）", { get(it).heightDp ?: 0f }) { style, value -> patch(style, get(style).copy(heightDp = value.takeIf { it > 0f })) } },
            "对齐" to { choices("${name}对齐", arrayOf("起始", "居中", "末尾"), get(current()).alignment.ordinal) { index ->
                val style = current(); apply(patch(style, get(style).copy(alignment = ChromeAlignment.entries[index])))
            } },
            "显示内容" to { choices("${name}内容", arrayOf("默认", "书名", "章节名", "页号", "章节页数", "章节页进度", "无文字", "时间", "章节页进度与时间"), get(current()).content.ordinal) { index ->
                val style = current(); apply(patch(style, get(style).copy(content = ChromeContent.entries[index])))
            } }
        ))
    }

    private fun menu(title: String, entries: List<Pair<String, () -> Unit>>) {
        display(AlertDialog.Builder(activity).setTitle(title).setItems(entries.map { it.first }.toTypedArray()) { selectedDialog, index ->
            if (active() && dialog === selectedDialog) entries[index].second()
        }.setNegativeButton("取消", null).create())
    }

    private fun choices(title: String, labels: Array<String>, selected: Int, choose: (Int) -> Unit) {
        display(AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(labels, selected) { selectedDialog, index ->
            if (!active() || dialog !== selectedDialog) return@setSingleChoiceItems
            selectedDialog.dismiss()
            choose(index)
        }.setNegativeButton("取消", null).create())
    }

    private fun font(title: String, get: (ReaderStyle) -> ReaderFont?, patch: (ReaderStyle, ReaderFont?) -> ReaderStyle) {
        val fonts = listOf(null, ReaderFont.System("sans-serif"), ReaderFont.System("serif"), ReaderFont.System("monospace"))
        val value = get(current())
        choices(title, arrayOf("系统默认", "无衬线", "衬线", "等宽"), fonts.indexOf(value)) { index -> apply(patch(current(), fonts[index])) }
    }

    private fun number(title: String, get: (ReaderStyle) -> Float, positive: Boolean = false, signed: Boolean = false,
        patch: (ReaderStyle, Float) -> ReaderStyle) {
        val input = input(get(current()), signed)
        input.contentDescription = title
        val editor = AlertDialog.Builder(activity).setTitle(title).setView(input).setPositiveButton("应用", null).setNegativeButton("取消", null).create()
        display(editor)
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!active() || dialog !== editor) return@setOnClickListener
            val value = input.text.toString().toFloatOrNull()
            if (value == null || !value.isFinite() || positive && value <= 0f || !signed && value < 0f) {
                input.error = if (positive) "请输入大于零的数字" else if (signed) "请输入有效数字" else "请输入不小于零的数字"
            } else { editor.dismiss(); apply(patch(current(), value)) }
        }
    }

    private fun insets(title: String, get: (ReaderStyle) -> EdgeInsets, patch: (ReaderStyle, EdgeInsets) -> ReaderStyle) {
        val value = get(current())
        val values = listOf(value.startDp, value.topDp, value.endDp, value.bottomDp)
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val margin = (16 * resources.displayMetrics.density).toInt()
            setPadding(margin, margin, margin, margin)
        }
        val inputs = arrayOf("起始", "顶部", "末尾", "底部").mapIndexed { index, label ->
            val field = input(values[index])
            val caption = TextView(activity).apply { text = "$label（dp）"; labelFor = android.view.View.generateViewId().also { field.id = it } }
            panel.addView(caption); panel.addView(field); field
        }
        val scroll = ScrollView(activity).apply { addView(panel) }
        val editor = AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("应用", null).setNegativeButton("取消", null).create()
        display(editor)
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!active() || dialog !== editor) return@setOnClickListener
            val entered = inputs.map { it.text.toString().toFloatOrNull() }
            val invalid = entered.indexOfFirst { it == null || !it.isFinite() || it < 0f }
            if (invalid >= 0) { inputs[invalid].error = "请输入不小于零的数字"; return@setOnClickListener }
            val padding = EdgeInsets(entered[0]!!, entered[1]!!, entered[2]!!, entered[3]!!)
            editor.dismiss(); apply(patch(current(), padding))
        }
    }

    private fun input(value: Float, signed: Boolean = false) = EditText(activity).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
        setText(value.toString()); selectAll(); isSingleLine = true
    }
    private fun active() = !activity.isFinishing && !activity.isDestroyed
    private fun display(next: AlertDialog) {
        if (!active()) return
        dismiss(); dialog = next
        next.setOnDismissListener { if (dialog === next) dialog = null }
        next.show()
    }
}
