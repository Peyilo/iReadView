package org.peyilo.iread.demo.ui.reader.menu

import android.content.Context
import android.util.AttributeSet
import org.peyilo.iread.demo.ui.reader.controls.ReferenceSliderView

/** Chapter-specific names around the shared reference-marker slider. */
class ChapterProgressView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ReferenceSliderView(context, attrs) {
    var chapterCount: Int
        get() = stepCount
        set(value) { stepCount = value }
    val chapterIndex get() = stepIndex
    var onChapterSelected: ((Int) -> Unit)?
        get() = onStepSelected
        set(value) { onStepSelected = value }
    init { contentDescription = "章节进度" }
    fun setChapterIndex(index: Int, resetReference: Boolean = false) = setStepIndex(index, resetReference)
}