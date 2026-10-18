package org.peyilo.ireadview.internal.render

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import android.view.View
import android.widget.LinearLayout

/** Visibility hook belonging to the default template, not to the page SPI. */
internal class DefaultChromeRoot(context: Context) : LinearLayout(context) {
    var onVisibilityUpdated: (() -> Unit)? = null
    var onDetached: (() -> Unit)? = null
    override fun onAttachedToWindow() { super.onAttachedToWindow(); onVisibilityUpdated?.invoke() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); onDetached?.invoke() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility); onVisibilityUpdated?.invoke()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility); onVisibilityUpdated?.invoke()
    }
}

/** Battery subscription exists only for a visible default template displaying battery content. */
internal class DefaultChromeBatterySource(private val root: View, private val refresh: () -> Unit) {
    private var enabled = false
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(registered && enabled) {
                try { refresh() } catch(error: Exception) {
                    close()
                    Log.w("iReadView", "Default chrome battery refresh failed", error)
                }
            }
        }
    }
    fun text(): String {
        val intent = root.context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if(level >= 0 && scale > 0) "${(level * 100L / scale).coerceIn(0, 100)}%" else ""
    }
    fun setEnabled(value: Boolean) { enabled = value; updateVisibility() }
    fun updateVisibility() {
        val wanted = enabled && root.isAttachedToWindow && root.isShown && root.windowVisibility == View.VISIBLE
        if(wanted == registered) return
        if(wanted) {
            root.context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            registered = true
        } else {
            detach()
        }
    }
    fun detach() {
        if(registered) {
            registered = false
            root.context.unregisterReceiver(receiver)
        }
    }
    fun close() { setEnabled(false) }
}
