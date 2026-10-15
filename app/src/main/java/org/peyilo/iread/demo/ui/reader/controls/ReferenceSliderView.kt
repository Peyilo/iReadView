package org.peyilo.iread.demo.ui.reader.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import kotlin.math.abs
import kotlin.math.roundToInt

/** Reusable reference-style slider; domain controllers map integer steps to values. */
open class ReferenceSliderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val trackHeight = 24 * density
    private val currentRadius = trackHeight / 1.8f
    private val referenceRadius = trackHeight / 2f
    private val completedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffd1d1d1.toInt() }
    private val remainingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffe9e9e9.toInt() }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xffffffff.toInt(); setShadowLayer(20f, 0f, 0f, 0xffd1d1d1.toInt())
    }
    private val referencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffbcbcbc.toInt() }
    private val track = RectF()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
    var thumbLabel: String? = null
        set(value) { field = value; invalidate() }
    var showReferenceMarker = true
        set(value) { field = value; invalidate() }
    var stepCount = 0
        set(value) { field = value.coerceAtLeast(0); stepIndex = clamp(stepIndex); referenceIndex = clamp(referenceIndex); invalidate() }
    var stepIndex = 0
        private set
    private var referenceIndex = 0
    var onInteractionStarted: (() -> Unit)? = null
    var onStepSelected: ((Int) -> Unit)? = null
    var onInteractionFinished: (() -> Unit)? = null
    var isInteracting = false
        private set
    private var referenceTouch = false
    private var downX = 0f
    private var downIndex = 0
    private var moved = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    init {
        isFocusable = true; isClickable = true
        contentDescription = "进度"
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setStepIndex(index: Int, resetReference: Boolean = false) {
        stepIndex = clamp(index)
        if (resetReference) referenceIndex = stepIndex
        invalidate()
    }
    private fun clamp(index: Int) = index.coerceIn(0, (stepCount - 1).coerceAtLeast(0))
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize((trackHeight + paddingTop + paddingBottom).roundToInt(), heightMeasureSpec))
    }
    private fun x(index: Int): Float {
        val start = paddingLeft + trackHeight / 2
        val end = (width - paddingRight - trackHeight / 2).coerceAtLeast(start)
        val fraction = if (stepCount > 1) clamp(index).toFloat() / (stepCount - 1) else 0f
        return start + fraction * (end - start)
    }
    override fun onDraw(canvas: Canvas) {
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()
        track.set(paddingLeft.toFloat(), top, (width - paddingRight).toFloat(), bottom)
        canvas.drawRoundRect(track, trackHeight / 2, trackHeight / 2, remainingPaint)
        track.right = (x(stepIndex) + trackHeight / 2).coerceAtMost(width - paddingRight.toFloat())
        canvas.drawRoundRect(track, trackHeight / 2, trackHeight / 2, completedPaint)
        val centerY = (top + bottom) / 2
        if (showReferenceMarker) canvas.drawCircle(x(referenceIndex), centerY, referenceRadius, referencePaint)
        canvas.drawCircle(x(stepIndex), centerY, currentRadius, currentPaint)
        thumbLabel?.let { label ->
            labelPaint.textSize = currentRadius
            val metrics = labelPaint.fontMetrics
            canvas.drawText(label, x(stepIndex) - labelPaint.measureText(label) / 2,
                centerY + (metrics.descent - metrics.ascent) / 4, labelPaint)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL && isInteracting) {
            cancelInteraction()
            return true
        }
        if ((!isEnabled || stepCount < 2) && isInteracting) cancelInteraction()
        if (!isEnabled || stepCount < 2) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                referenceTouch = showReferenceMarker && abs(event.x - x(stepIndex)) > currentRadius && abs(event.x - x(referenceIndex)) <= referenceRadius
                if (!referenceTouch && abs(event.x - x(stepIndex)) > currentRadius) return false
                downX = event.x; downIndex = stepIndex; moved = false; isInteracting = true
                onInteractionStarted?.invoke()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop) moved = true
                if (!referenceTouch) {
                    val start = paddingLeft + trackHeight / 2
                    val length = width - paddingRight - trackHeight / 2 - start
                    if (length > 0) setStepIndex(((event.x - start).coerceIn(0f, length) / length * (stepCount - 1)).toInt())
                }
            }
            MotionEvent.ACTION_UP -> {
                if (referenceTouch && !moved) setStepIndex(referenceIndex)
                if (stepIndex != downIndex) onStepSelected?.invoke(stepIndex)
                performClick(); finishInteraction()
            }
            MotionEvent.ACTION_CANCEL -> { setStepIndex(downIndex); finishInteraction() }
        }
        return true
    }
    private fun finishInteraction() {
        isInteracting = false; parent?.requestDisallowInterceptTouchEvent(false)
        onInteractionFinished?.invoke()
    }
    fun cancelInteraction() {
        if (isInteracting) { setStepIndex(downIndex); finishInteraction() }
    }
    override fun onDetachedFromWindow() {
        cancelInteraction()
        super.onDetachedFromWindow()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun select(index: Int): Boolean {
        if (!isEnabled || stepCount < 2) return false
        val selected = clamp(index)
        if (selected == stepIndex) return false
        onStepSelected?.invoke(selected)
        return true
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> select(stepIndex - 1)
        KeyEvent.KEYCODE_DPAD_RIGHT -> select(stepIndex + 1)
        else -> super.onKeyDown(keyCode, event)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        info.contentDescription = "${contentDescription} · $stepIndex / ${(stepCount - 1).coerceAtLeast(0)}"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
            0f, (stepCount - 1).coerceAtLeast(0).toFloat(), stepIndex.toFloat())
        if (isEnabled && stepIndex > 0) info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        if (isEnabled && stepIndex + 1 < stepCount) info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> select(stepIndex - 1)
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> select(stepIndex + 1)
        else -> super.performAccessibilityAction(action, arguments)
    }
}
