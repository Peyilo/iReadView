package org.peyilo.iread.demo.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.core.graphics.Insets
import androidx.core.view.*

/** Window policy only. The full-screen ReaderView paints its own page behind transparent bars. */
internal object ReaderSystemBars {
    private fun activity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return current as? Activity
    }
    private fun preferences(context: Context) = context.getSharedPreferences("reader-window", Context.MODE_PRIVATE)
    fun navigationHidden(context: Context) = preferences(context).getBoolean("navigationHidden", true)
    fun setNavigationHidden(context: Context, hidden: Boolean) {
        preferences(context).edit().putBoolean("navigationHidden", hidden).apply()
    }
    @Suppress("DEPRECATION")
    fun apply(context: Context, window: Window, backgroundColor: Int? = null, fullScreenContent: Boolean = true) {
        WindowCompat.setDecorFitsSystemWindows(window, !fullScreenContent)
        if (fullScreenContent && android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val content = window.findViewById<View>(android.R.id.content)
        val host = activity(context)
        val color = backgroundColor ?: (host?.window?.decorView?.getTag(org.peyilo.iread.demo.R.id.reader_window_color) as? Int)
            ?: 0xfff6f6f6.toInt()
        window.decorView.setTag(org.peyilo.iread.demo.R.id.reader_window_color, color)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false; window.isNavigationBarContrastEnforced = false
        }
        if (fullScreenContent && content != null) {
            content.fitsSystemWindows = false
            content.setPadding(0, 0, 0, 0)
            if (window !== host?.window && content.getTag(org.peyilo.iread.demo.R.id.reader_window_insets) == null) {
                content.setTag(org.peyilo.iread.demo.R.id.reader_window_insets, true)
                // Modal controls avoid system UI; this does not shrink the ReaderView beneath them.
                ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                    val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
                    val nav = if (navigationHidden(context)) Insets.NONE else insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    view.setPadding(top.left, top.top, top.right, maxOf(nav.bottom, ime.bottom))
                    WindowInsetsCompat.Builder(insets)
                        .setInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(), Insets.NONE)
                        .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                        .setInsets(WindowInsetsCompat.Type.systemGestures() or WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.NONE).build()
                }
            }
        }
        val light = Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 > 128
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (navigationHidden(context)) hide(WindowInsetsCompat.Type.navigationBars()) else show(WindowInsetsCompat.Type.navigationBars())
        }
        content?.let { ViewCompat.requestApplyInsets(it) }
    }
}
