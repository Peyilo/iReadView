package org.peyilo.iread.demo.ui.reader.contents

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.peyilo.iread.demo.ui.reader.menu.ReaderBottomSheets

/** Reference catalogue: rounded bottom sheet, heading, and a recycled chapter list. */
internal class ContentsSheet(private val activity: Activity) {
    private var dialog: BottomSheetDialog? = null
    fun dismiss() { dialog?.dismiss(); dialog = null }
    fun show(titles: List<String>, currentIndex: Int = 0, enabled: (Int) -> Boolean = { true }, onSelected: (Int) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        dismiss()
        val labels = titles.toList()
        val context = ReaderBottomSheets.context(activity, rounded = true)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(24)) }
        panel.addView(TextView(context).apply {
            text = "目录"; textSize = 18f; setTextColor(0xff000000.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(32), dp(32), 0, dp(16))
        })
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setPaddingRelative(dp(38), 0, 0, 0)
        }
        list.adapter = object : RecyclerView.Adapter<Row>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Row(LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView)
            override fun getItemCount() = labels.size
            override fun onBindViewHolder(holder: Row, position: Int) {
                holder.label.text = labels[position]
                holder.label.isEnabled = enabled(position)
                holder.label.setOnClickListener {
                    if (dialog?.isShowing == true && enabled(position) && !activity.isFinishing) {
                        dismiss(); onSelected(position)
                    }
                }
            }
        }
        panel.addView(list, LinearLayout.LayoutParams(-1, dp(400)))
        val shown = ReaderBottomSheets.show(panel, dimAmount = 0.01f)
        dialog = shown
        shown.setOnDismissListener { if (dialog === shown) dialog = null }
        if (currentIndex in labels.indices) list.scrollToPosition(currentIndex)
    }
    private class Row(val label: TextView) : RecyclerView.ViewHolder(label)
}
