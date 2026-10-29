package org.peyilo.ireadview.internal.render

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.peyilo.ireadview.R
import org.peyilo.ireadview.spi.page.*

internal class DefaultReaderPlaceholderFactory : ReaderPlaceholderFactory {
    private class Card(context: Context) : LinearLayout(context) {
        val message = TextView(context).apply { gravity = Gravity.CENTER }
        val retry = Button(context)

        init {
            orientation = VERTICAL; gravity = Gravity.CENTER
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(retry, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }

    override fun create(context: Context, kind: PlaceholderKind): View = Card(context)
    override fun bind(view: View, state: PlaceholderState, actions: PlaceholderActions) {
        val card = view as Card
        card.setBackgroundColor(state.style.page.backgroundColor)
        card.message.setTextColor(state.style.body.textColor)
        card.message.text = state.error?.message ?: card.context.getString(
            when (state.kind) {
                PlaceholderKind.CLOSED -> R.string.iread_placeholder_closed
                PlaceholderKind.BOOK_LOADING -> R.string.iread_placeholder_book_loading
                PlaceholderKind.EMPTY_BOOK -> R.string.iread_placeholder_empty_book
                PlaceholderKind.EMPTY_CHAPTER -> R.string.iread_placeholder_empty_chapter
                PlaceholderKind.CHAPTER_LOADING -> R.string.iread_placeholder_chapter_loading
                else -> R.string.iread_placeholder_failed
            }
        )
        card.retry.setText(R.string.iread_placeholder_retry)
        card.retry.visibility = if (actions.canRetry) View.VISIBLE else View.GONE
        card.retry.setOnClickListener { if (actions.retry()) card.retry.isEnabled = false }
        card.retry.isEnabled = true
    }

    override fun unbind(view: View) {
        (view as Card).apply { retry.setOnClickListener(null); message.text = "" }
    }
}
