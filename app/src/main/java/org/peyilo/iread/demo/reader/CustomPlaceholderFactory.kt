package org.peyilo.iread.demo.reader

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.peyilo.ireadview.spi.page.*

/** Host-owned states and controls. Only supplied actions can retry the bound request. */
internal class CustomPlaceholderFactory : ReaderPlaceholderFactory {
    private class Card(context: Context) : LinearLayout(context) {
        val progress = ProgressBar(context)
        val message = TextView(context).apply { gravity = Gravity.CENTER }
        val retry = Button(context).apply { text = "再试一次" }
        init {
            orientation = VERTICAL; gravity = Gravity.CENTER
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(progress, LayoutParams((24 * resources.displayMetrics.density).toInt(), (24 * resources.displayMetrics.density).toInt()))
            addView(message, LayoutParams(-1, -2)); addView(retry, LayoutParams(-2, -2))
        }
    }
    override fun create(context: Context, kind: PlaceholderKind): View = Card(context)
    override fun bind(view: View, state: PlaceholderState, actions: PlaceholderActions) {
        val card = view as Card
        card.setBackgroundColor(state.style.page.backgroundColor)
        card.message.setTextColor(state.style.body.textColor)
        card.progress.visibility = if (state.kind == PlaceholderKind.BOOK_LOADING || state.kind == PlaceholderKind.CHAPTER_LOADING) View.VISIBLE else View.GONE
        card.message.text = when (state.kind) {
            PlaceholderKind.BOOK_LOADING -> "正在打开书籍"
            PlaceholderKind.CHAPTER_LOADING -> "正在准备这一章"
            PlaceholderKind.EMPTY_BOOK -> "这本书还没有章节"
            PlaceholderKind.EMPTY_CHAPTER -> "这一章留白了"
            PlaceholderKind.CLOSED -> "选择一本书开始阅读"
            else -> "暂时无法显示内容"
        }
        card.retry.visibility = if (actions.canRetry) View.VISIBLE else View.GONE
        card.retry.isEnabled = true
        card.retry.setOnClickListener { if (actions.retry()) card.retry.isEnabled = false }
    }
    override fun unbind(view: View) {
        (view as Card).apply { retry.setOnClickListener(null); progress.visibility = View.GONE; message.text = "" }
    }
}
