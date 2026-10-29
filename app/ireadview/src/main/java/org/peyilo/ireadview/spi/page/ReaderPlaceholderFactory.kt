package org.peyilo.ireadview.spi.page

import android.content.Context
import android.view.View
import org.peyilo.ireadview.api.state.ReaderError
import org.peyilo.ireadview.api.style.ReaderStyle

enum class PlaceholderKind { CLOSED, BOOK_LOADING, BOOK_FAILED, CHAPTER_LOADING, CHAPTER_FAILED, EMPTY_BOOK, EMPTY_CHAPTER, LAYOUT_FAILED }
data class PlaceholderState(
    val kind: PlaceholderKind,
    val bookId: String?,
    val chapterId: String?,
    val error: ReaderError?,
    val style: ReaderStyle
)

interface PlaceholderActions {
    val canRetry: Boolean

    /** Accepts one retry of this binding. Old/hidden/replaced bindings return false. */
    fun retry(): Boolean
}

interface ReaderPlaceholderFactory {
    /** Returns a fresh unowned View for each call, including overlapping binds across Widgets. */
    fun create(context: Context, kind: PlaceholderKind): View

    /** Updates the View without attaching it; the Widget owns mounting and removal. */
    fun bind(view: View, state: PlaceholderState, actions: PlaceholderActions)
    fun unbind(view: View)
}

object ReaderPlaceholderFactories {
    @JvmStatic
    fun default(): ReaderPlaceholderFactory =
        org.peyilo.ireadview.internal.render.DefaultReaderPlaceholderFactory()
}
