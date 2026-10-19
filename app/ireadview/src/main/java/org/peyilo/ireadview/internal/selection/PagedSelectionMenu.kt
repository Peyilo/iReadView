package org.peyilo.ireadview.internal.selection

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import org.peyilo.ireadview.api.model.ReaderSelection
import org.peyilo.ireadview.spi.selection.*
import kotlin.math.min
import kotlin.math.roundToInt

/** Fixed-width pages instead of clipping or shrinking items when the window is narrow. */
internal class PagedSelectionMenu(context: Context, private val items: List<SelectionMenuItem>,
    private val options: SelectionMenuOptions, selection: ReaderSelection, actions: SelectionActions
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private var selected: ReaderSelection? = selection
    private var activeActions: SelectionActions? = actions
    private var page = 0
    private var capacity = -1
    private var paging = false
    private var arrowWidth = 0
    /** Stable viewport budget; a narrow last page must not reduce pagination capacity. */
    var availableWidthPx: Int? = null
    var onPageChanged: (() -> Unit)? = null
    private val itemHeight get() = dp(52f * resources.configuration.fontScale.coerceAtLeast(1f))
    private fun dp(value: Float) = (value * density).roundToInt()
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply { setColor(options.backgroundColor); cornerRadius = dp(12f).toFloat() }
        setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
        elevation = dp(4f).toFloat()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (items.isEmpty()) { setMeasuredDimension(0, 0); return }
        val available = availableWidthPx ?: if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) dp(options.maxWidthDp)
            else MeasureSpec.getSize(widthMeasureSpec)
        val limit = min(dp(options.maxWidthDp), available).coerceAtLeast(1)
        val natural = dp(options.itemWidthDp) * items.size + paddingLeft + paddingRight
        val nextPaging = natural > limit
        val nextArrowWidth = min(dp(32f), ((limit - paddingLeft - paddingRight) / 4).coerceAtLeast(1))
        val arrows = if (nextPaging) nextArrowWidth * 2 else 0
        val nextCapacity = ((limit - paddingLeft - paddingRight - arrows) / dp(options.itemWidthDp)).coerceAtLeast(1)
        if (nextCapacity != capacity || nextPaging != paging || nextArrowWidth != arrowWidth) {
            capacity = nextCapacity; paging = nextPaging; arrowWidth = nextArrowWidth
            page = page.coerceAtMost((items.size - 1) / capacity)
            rebuild()
        }
        val visibleCount = min(capacity, items.size - page * capacity)
        val visibleArrows = (if (page > 0) 1 else 0) + (if ((page + 1) * capacity < items.size) 1 else 0)
        val packedWidth = visibleCount * dp(options.itemWidthDp) + visibleArrows * arrowWidth + paddingLeft + paddingRight
        super.onMeasure(MeasureSpec.makeMeasureSpec(packedWidth.coerceAtMost(limit), MeasureSpec.EXACTLY), heightMeasureSpec)
    }
    private fun rebuild() {
        clearListeners(); removeAllViews()
        if (paging && page > 0) addArrow(false)
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER }
        val visible = items.drop(page * capacity).take(capacity)
        visible.forEach { item ->
            val cell = LinearLayout(context).apply {
                orientation = VERTICAL; gravity = Gravity.CENTER
                isClickable = true; isFocusable = true; contentDescription = item.title
                setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
            }
            cell.addView(ImageView(context).apply {
                setImageResource(item.iconResource); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(dp(24f), dp(24f)))
            cell.addView(TextView(context).apply {
                text = item.title; textSize = 12f; setTextColor(options.textColor); gravity = Gravity.CENTER
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(-1, -2).apply { topMargin = dp(3f) })
            cell.setOnClickListener {
                val current = activeActions ?: return@setOnClickListener
                val selection = selected ?: return@setOnClickListener
                if (!current.isValid()) return@setOnClickListener
                try { item.action.onClick(selection, current) }
                catch (error: Exception) { Log.w("iReadView", "Selection menu action ${item.id} failed", error) }
            }
            row.addView(cell, LayoutParams(dp(options.itemWidthDp), -1))
        }
        addView(row, LayoutParams(visible.size * dp(options.itemWidthDp), itemHeight))
        if (paging && (page + 1) * capacity < items.size) addArrow(true)
    }
    private fun addArrow(next: Boolean) {
        val canMove = if (next) (page + 1) * capacity < items.size else page > 0
        addView(TextView(context).apply {
            text = if (next) "›" else "‹"; textSize = 28f; gravity = Gravity.CENTER
            setTextColor(options.textColor); isFocusable = canMove; isEnabled = canMove
            visibility = if (canMove) VISIBLE else INVISIBLE
            contentDescription = if (next) "下一组操作" else "上一组操作"
            setOnClickListener {
                if (activeActions?.isValid() != true) return@setOnClickListener
                page += if (next) 1 else -1
                rebuild(); requestLayout()
                onPageChanged?.invoke()
                announceForAccessibility("操作菜单第 ${page + 1} / ${(items.size + capacity - 1) / capacity} 页")
            }
        }, LayoutParams(arrowWidth, itemHeight))
    }
    private fun clearListeners(view: View = this) {
        view.setOnClickListener(null)
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) clearListeners(view.getChildAt(i))
    }
    fun release() { onPageChanged = null; activeActions = null; selected = null; clearListeners(); removeAllViews() }
}
