package org.peyilo.ireadview.spi.selection

import android.content.Context
import android.view.View
import androidx.annotation.DrawableRes
import org.peyilo.ireadview.R
import org.peyilo.ireadview.api.model.TextSelection

/** Called on the main thread for a still-valid selection. Clear selection explicitly if desired. */
fun interface SelectionMenuAction {
    fun onClick(selection: TextSelection, actions: SelectionActions)
}

/** Stable unique id, label, packaged drawable icon and runtime handler. Icons retain their colors. */
data class SelectionMenuItem(val id: String, val title: String, @DrawableRes val iconResource: Int,
    val action: SelectionMenuAction,
    val activeTitle: String? = null,
    val state: SelectionMenuState? = null) {
    constructor(id: String, title: String, @DrawableRes iconResource: Int, action: SelectionMenuAction) :
        this(id, title, iconResource, action, null, null)
    init { require(id.isNotBlank() && title.isNotBlank() && iconResource != 0) }
}
/** Optional live menu state; evaluated for the current selection on the main thread. */
fun interface SelectionMenuState { fun isActive(selection: TextSelection, actions: SelectionActions): Boolean }

/** Width is also constrained to the visible window. Suggested maximum 280..400 dp; item 56..80 dp. */
data class SelectionMenuOptions @JvmOverloads constructor(
    val maxWidthDp: Float = 360f,
    val itemWidthDp: Float = 56f,
    val backgroundColor: Int = 0xff303030.toInt(),
    val textColor: Int = 0xffffffff.toInt()
) {
    init {
        require(maxWidthDp.isFinite() && maxWidthDp >= 144f)
        require(itemWidthDp.isFinite() && itemWidthDp >= 48f && itemWidthDp <= maxWidthDp - 96f)
    }
}

/** Convenience factories; full custom View toolbars remain available via SelectionToolbarFactory. */
object SelectionToolbars {
    @JvmStatic fun defaultItems(): List<SelectionMenuItem> = listOf(
        SelectionMenuItem("copy", "复制", R.drawable.iread_selection_copy) { _, actions -> actions.copyText() },
        SelectionMenuItem("highlight", "高亮", R.drawable.iread_selection_highlight,
            SelectionMenuAction { _, actions -> actions.toggleHighlight() }, "取消高亮", SelectionMenuState { _, actions -> actions.hasHighlight() }),
        SelectionMenuItem("underline", "划线", R.drawable.iread_selection_underline,
            SelectionMenuAction { _, actions -> actions.toggleUnderline() }, "取消划线", SelectionMenuState { _, actions -> actions.hasUnderline() })
    )
    /** Copies the list. Items must have unique ids; an empty list intentionally hides the menu. */
    @JvmStatic @JvmOverloads fun menu(items: List<SelectionMenuItem>, options: SelectionMenuOptions = SelectionMenuOptions()): SelectionToolbarFactory {
        val snapshot = items.toList()
        require(snapshot.map { it.id }.distinct().size == snapshot.size) { "Selection menu item ids must be unique" }
        return object : SelectionToolbarFactory {
            override fun create(context: Context, selection: TextSelection, actions: SelectionActions): View =
                org.peyilo.ireadview.internal.selection.PagedSelectionMenu(context, snapshot.map { item ->
                    if (item.state?.isActive(selection, actions) == true && item.activeTitle != null) item.copy(title = item.activeTitle) else item
                }, options, selection, actions)
            override fun unbind(view: View) { (view as org.peyilo.ireadview.internal.selection.PagedSelectionMenu).release() }
        }
    }
}
