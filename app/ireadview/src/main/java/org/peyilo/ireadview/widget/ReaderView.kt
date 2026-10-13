package org.peyilo.ireadview.widget

import android.content.Context
import android.util.AttributeSet
import org.peyilo.ireadview.R
import org.peyilo.ireadview.internal.style.ReaderStyleAttributes
import org.peyilo.ireadview.internal.presentation.paged.PagedBookPresentation
import org.peyilo.ireadview.internal.presentation.scroll.ScrollingBookPresentation
import org.peyilo.ireadview.api.model.ReadingMode
import org.peyilo.ireadview.api.input.PagingAxis
import org.peyilo.ireadview.api.input.ReaderInputOptions
import org.peyilo.ireadview.api.effect.PageTurnOptions
import org.peyilo.ireadview.api.effect.PageTurnEffects
import org.peyilo.ireadview.spi.presentation.BookPresentation
import org.peyilo.ireadview.spi.presentation.BookPresentationHost

/** Default XML/programmatic Widget, with independent paged and continuous presentations. */
open class ReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.ireadReaderViewStyle,
    defStyleRes: Int = R.style.Widget_IRead_ReaderView
) : BookReadView(context, attrs, defStyleAttr, defStyleRes) {
    override val supportedModes: Set<ReadingMode> = setOf(ReadingMode.PAGED, ReadingMode.CONTINUOUS_SCROLL)
    init {
        val values = context.obtainStyledAttributes(attrs, R.styleable.ReaderView, defStyleAttr, defStyleRes)
        try {
            setInitialReadingMode(if (values.getInt(R.styleable.ReaderView_ireadReadingMode, 0) == 1) ReadingMode.CONTINUOUS_SCROLL else ReadingMode.PAGED)
            val axis = values.getInt(R.styleable.ReaderView_ireadPagingAxis, 0)
            require(axis == 0 || axis == 1) { "Unknown ireadPagingAxis: $axis" }
            setInitialInputOptions(ReaderInputOptions(
                gesturePagingEnabled = values.getBoolean(R.styleable.ReaderView_ireadGesturePagingEnabled, true),
                tapPagingEnabled = values.getBoolean(R.styleable.ReaderView_ireadTapPagingEnabled, true),
                keyboardPagingEnabled = values.getBoolean(R.styleable.ReaderView_ireadKeyboardPagingEnabled, true),
                volumeKeyPagingEnabled = values.getBoolean(R.styleable.ReaderView_ireadVolumeKeyPagingEnabled, false),
                pagingAxis = if (axis == 1) PagingAxis.Vertical else PagingAxis.Horizontal,
                tapEdgeFraction = values.getFloat(R.styleable.ReaderView_ireadTapEdgeFraction, 0.3f)
            ))
            setInitialPageTurnOptions(PageTurnOptions(
                durationMs = values.getInt(R.styleable.ReaderView_ireadTurnDurationMs, 240).toLong(),
                reduceMotion = values.getBoolean(R.styleable.ReaderView_ireadReduceMotion, false),
                commitDistanceFraction = values.getFloat(R.styleable.ReaderView_ireadCommitDistanceFraction, 0.33f),
                flingVelocityDpPerSecond = values.getFloat(R.styleable.ReaderView_ireadFlingVelocityDpPerSecond, 600f),
                minimumTurnIntervalMs = values.getInt(R.styleable.ReaderView_ireadMinimumTurnIntervalMs, 0).toLong()
            ))
            val presetIds = arrayOf("none", "slide", "cover", "ibook_slide", "curl", "google_curl")
            val preset = values.getInt(R.styleable.ReaderView_ireadPageTurnEffect, 0)
            require(preset in presetIds.indices) { "Unknown ireadPageTurnEffect: $preset" }
            setInitialPageTurnEffect(requireNotNull(PageTurnEffects.fromId(presetIds[preset])))
            setInitialStyle(ReaderStyleAttributes.read(context, values, style))
        } finally { values.recycle() }
    }
    override fun createPresentation(mode: ReadingMode, host: BookPresentationHost): BookPresentation = when (mode) {
        ReadingMode.PAGED -> PagedBookPresentation(context, host)
        ReadingMode.CONTINUOUS_SCROLL -> ScrollingBookPresentation(context, host)
        else -> error("Unsupported reading mode: ${mode.id}")
    }
}
