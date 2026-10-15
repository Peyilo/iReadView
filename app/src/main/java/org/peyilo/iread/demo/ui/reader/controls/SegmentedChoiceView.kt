package org.peyilo.iread.demo.ui.reader.controls

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

/** Reference Demo's grey segmented track and white selected pill, with real focusable options. */
class SegmentedChoiceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    var onSelected: ((Int) -> Unit)? = null
    var selectedIndex = -1
        private set
    init {
        orientation = HORIZONTAL
        background = GradientDrawable().apply { setColor(0xffd3d3d3.toInt()); cornerRadius = 50f }
        setPadding(5, 5, 5, 5)
        minimumHeight = (30 * resources.displayMetrics.density).toInt()
    }
    fun setOptions(options: List<String>) {
        removeAllViews()
        options.forEachIndexed { index, name ->
            addView(TextView(context).apply {
                text = name; textSize = 14f; gravity = Gravity.CENTER; includeFontPadding = false
                isFocusable = true; isClickable = true
                accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = RadioButton::class.java.name; info.isCheckable = true; info.isChecked = isSelected
                    }
                }
                setOnClickListener { if (isEnabled && selectedIndex != index) { setSelectedIndex(index); onSelected?.invoke(index) } }
            }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        setSelectedIndex(0)
    }
    fun setSelectedIndex(index: Int) {
        selectedIndex = index.takeIf { it in 0 until childCount } ?: -1
        for (i in 0 until childCount) (getChildAt(i) as TextView).apply {
            isSelected = i == selectedIndex
            setTextColor(if (isSelected) 0xff000000.toInt() else 0xff444444.toInt())
            background = if (isSelected) GradientDrawable().apply { setColor(0xfffdfdfd.toInt()); cornerRadius = 45f } else null
        }
    }
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        for (i in 0 until childCount) getChildAt(i).isEnabled = enabled
    }
}
