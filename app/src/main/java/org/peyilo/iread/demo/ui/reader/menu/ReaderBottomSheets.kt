package org.peyilo.iread.demo.ui.reader.menu

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.peyilo.iread.demo.R

/** Shared reference-style window policy for menu, contents, typography and settings. */
internal object ReaderBottomSheets {
    fun context(activity: Activity, rounded: Boolean = false) = ContextThemeWrapper(activity,
        if (rounded) R.style.ReaderContentsTheme else R.style.ReaderMenuTheme)
    fun show(content: View, dimAmount: Float = 0f): BottomSheetDialog {
        val dialog = BottomSheetDialog(content.context)
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setDimAmount(dimAmount)
        dialog.window?.let { org.peyilo.iread.demo.reader.ReaderSystemBars.apply(content.context, it) }
        dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.fitsSystemWindows = false
            (sheet.parent as? View)?.fitsSystemWindows = false
            BottomSheetBehavior.from(sheet).isGestureInsetBottomIgnored = true
            ViewCompat.setOnApplyWindowInsetsListener(sheet) { _, insets -> insets }
            sheet.setPadding(0, 0, 0, 0)
        }
        return dialog
    }
}
