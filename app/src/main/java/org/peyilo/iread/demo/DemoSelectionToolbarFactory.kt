package org.peyilo.iread.demo

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.peyilo.ireadview.api.model.TextSelection
import org.peyilo.ireadview.api.state.ReaderResult
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.spi.selection.SelectionActions
import org.peyilo.ireadview.spi.selection.SelectionToolbarFactory

/** Public SPI example: fresh views, revision-bound actions, explicit listener cleanup. */
internal class DemoSelectionToolbarFactory(private val style: () -> ReaderStyle) : SelectionToolbarFactory {
    private class Toolbar(context: Context) : LinearLayout(context) {
        val summary = TextView(context)
        val copy = Button(context).apply { text = "复制"; minWidth = 0 }
        val highlight = Button(context).apply { text = "高亮"; minWidth = 0 }
        val cancel = Button(context).apply { text = "取消选择"; minWidth = 0 }
        init {
            orientation = VERTICAL
            val padding = (8 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(summary, LayoutParams(-1, -2))
            val row = LinearLayout(context).apply {
                addView(copy, LayoutParams(0, -2, 1f))
                addView(highlight, LayoutParams(0, -2, 1f))
            }
            addView(row, LayoutParams(-1, -2))
            addView(cancel, LayoutParams(-1, -2))
            minimumWidth = (200 * resources.displayMetrics.density).toInt()
        }
    }

    override fun create(context: Context, selection: TextSelection, actions: SelectionActions): View {
        val appearance = style()
        return Toolbar(context).apply {
            setBackgroundColor(appearance.page.backgroundColor)
            summary.setTextColor(appearance.body.textColor)
            summary.text = "已选择 ${selection.text.codePointCount(0, selection.text.length)} 个字符"
            copy.setOnClickListener { report(context, actions.copyText()) }
            highlight.setOnClickListener { report(context, actions.highlight()) }
            cancel.setOnClickListener { actions.clearSelection() }
        }
    }

    private fun report(context: Context, result: ReaderResult<*>) {
        val message = when (result) {
            is ReaderResult.Success -> return
            is ReaderResult.Failure -> result.error.message
            is ReaderResult.Rejected -> "当前无法操作：${result.reason}"
            ReaderResult.Superseded -> "选区已变化，请重新选择"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun unbind(view: View) {
        (view as Toolbar).apply {
            copy.setOnClickListener(null)
            highlight.setOnClickListener(null)
            cancel.setOnClickListener(null)
            summary.text = ""
        }
    }
}
