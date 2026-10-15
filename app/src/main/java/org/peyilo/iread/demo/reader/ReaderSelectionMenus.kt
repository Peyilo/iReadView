package org.peyilo.iread.demo.reader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import org.peyilo.iread.demo.R
import org.peyilo.ireadview.spi.selection.*

/** Host-defined actions exercise icon/text items and the menu's next/previous page controls. */
internal object ReaderSelectionMenus {
    fun factory(activity: Activity): SelectionToolbarFactory {
        val delegate = SelectionToolbars.menu(
        SelectionToolbars.defaultItems() + listOf(
            SelectionMenuItem("share", "分享", R.drawable.ic_selection_share) { selection, actions ->
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, selection.text)
                activity.startActivity(Intent.createChooser(intent, "分享选中文字"))
                actions.clearSelection()
            },
            SelectionMenuItem("preview", "查看", R.drawable.ic_selection_preview) { selection, _ ->
                AlertDialog.Builder(activity).setTitle("选中文字").setMessage(selection.text).setPositiveButton("关闭", null).show()
            },
            SelectionMenuItem("count", "字数", R.drawable.ic_selection_info) { selection, _ ->
                Toast.makeText(activity, "已选 ${selection.text.codePointCount(0, selection.text.length)} 个字符", Toast.LENGTH_SHORT).show()
            },
            SelectionMenuItem("clear", "取消", R.drawable.ic_selection_close) { _, actions -> actions.clearSelection() }
        )
        )
        return delegate
    }
}
