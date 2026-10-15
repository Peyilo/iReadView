package org.peyilo.ireadview.internal.style

import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import android.util.TypedValue
import org.peyilo.ireadview.R
import org.peyilo.ireadview.api.style.*

/** Static XML values are converted once into the same immutable style used by runtime callers. */
internal object ReaderStyleAttributes {
    fun read(context: Context, values: TypedArray, style: ReaderStyle): ReaderStyle {
        val metrics = context.resources.displayMetrics
        fun dp(index: Int, fallback: Float) = if (values.hasValue(index)) values.getDimension(index, 0f) / metrics.density else fallback
        fun insets(base: EdgeInsets, start: Int, top: Int, end: Int, bottom: Int) =
            EdgeInsets(dp(start, base.startDp), dp(top, base.topDp), dp(end, base.endDp), dp(bottom, base.bottomDp))
        fun sp(index: Int, fallback: Float): Float {
            if (!values.hasValue(index)) return fallback
            val pixels = values.getDimension(index, 0f)
            return if (Build.VERSION.SDK_INT >= 34) TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, pixels, metrics)
            else pixels / (metrics.density * context.resources.configuration.fontScale)
        }
        fun font(index: Int, fallback: ReaderFont?): ReaderFont? {
            if (!values.hasValue(index)) return fallback
            val resourceId = values.getResourceId(index, 0)
            if (resourceId != 0) {
                val type = context.resources.getResourceTypeName(resourceId)
                if (type == "font") return ReaderFont.Resource(resourceId)
                require(type == "string") { "Reader font family expects a name, @string or @font" }
            }
            val family = values.getString(index)
            require(!family.isNullOrBlank()) { "Reader font family must not be blank" }
            return ReaderFont.System(family)
        }
        val alignment = values.getInt(R.styleable.ReaderView_ireadTitleAlignment, style.title.alignment.ordinal)
        require(alignment in TitleAlignment.entries.indices) { "Unknown ireadTitleAlignment: $alignment" }
        val highlights = LinkedHashMap(style.highlightStyles.styles)
        val backgroundId = values.getResourceId(R.styleable.ReaderView_ireadPageBackground, 0)
        val drawableBackground = backgroundId != 0 && context.resources.getResourceTypeName(backgroundId) in setOf("drawable", "mipmap")
        fun chrome(base: ChromeStyle, visible: Int, size: Int, color: Int, height: Int, padding: IntArray, alignmentIndex: Int, contentIndex: Int, backgroundIndex: Int, fontIndex: Int): ChromeStyle {
            val align = values.getInt(alignmentIndex, base.alignment.ordinal)
            val content = values.getInt(contentIndex, base.content.ordinal)
            require(align in ChromeAlignment.entries.indices && content in ChromeContent.entries.indices) { "Unknown Reader chrome alignment/content" }
            val id = values.getResourceId(backgroundIndex, 0)
            val background = if (!values.hasValue(backgroundIndex)) base.background else
                if (id != 0 && context.resources.getResourceTypeName(id) in setOf("drawable", "mipmap")) ReaderBackground.Resource(id)
                else ReaderBackground.Color(values.getColor(backgroundIndex, 0))
            return base.copy(visible = values.getBoolean(visible, base.visible), textSizeSp = sp(size, base.textSizeSp), textColor = values.getColor(color, base.textColor),
                heightDp = if (values.hasValue(height)) dp(height, 0f) else base.heightDp,
                padding = EdgeInsets(dp(padding[0], base.padding.startDp), dp(padding[1], base.padding.topDp), dp(padding[2], base.padding.endDp), dp(padding[3], base.padding.bottomDp)),
                alignment = ChromeAlignment.entries[align], content = ChromeContent.entries[content], background = background,
                readerFont = font(fontIndex, base.readerFont))
        }
        highlights["default"] = HighlightStyle(values.getColor(R.styleable.ReaderView_ireadDefaultHighlightColor, highlights.getValue("default").fillColor))
        return style.copy(
            page = style.page.copy(
                backgroundColor = if (drawableBackground) style.page.backgroundColor else values.getColor(R.styleable.ReaderView_ireadPageBackground, style.page.backgroundColor),
                readerBackground = if (drawableBackground) ReaderBackground.Resource(backgroundId) else style.page.readerBackground,
                viewportPadding = insets(style.page.viewportPadding, R.styleable.ReaderView_ireadViewportPaddingStart,
                    R.styleable.ReaderView_ireadViewportPaddingTop, R.styleable.ReaderView_ireadViewportPaddingEnd, R.styleable.ReaderView_ireadViewportPaddingBottom),
                padding = EdgeInsets(dp(R.styleable.ReaderView_ireadPagePaddingStart, style.page.padding.startDp),
                    dp(R.styleable.ReaderView_ireadPagePaddingTop, style.page.padding.topDp),
                    dp(R.styleable.ReaderView_ireadPagePaddingEnd, style.page.padding.endDp),
                    dp(R.styleable.ReaderView_ireadPagePaddingBottom, style.page.padding.bottomDp))
            ),
            body = style.body.copy(
                textSizeSp = sp(R.styleable.ReaderView_ireadBodyTextSize, style.body.textSizeSp),
                textColor = values.getColor(R.styleable.ReaderView_ireadBodyTextColor, style.body.textColor),
                readerFont = font(R.styleable.ReaderView_ireadBodyFontFamily, style.body.readerFont),
                letterSpacingEm = values.getFloat(R.styleable.ReaderView_ireadBodyLetterSpacing, style.body.letterSpacingEm),
                letterSpacingDp = if (values.hasValue(R.styleable.ReaderView_ireadBodyLetterSpacingDp)) dp(R.styleable.ReaderView_ireadBodyLetterSpacingDp, 0f) else style.body.letterSpacingDp,
                firstLineIndentEm = values.getFloat(R.styleable.ReaderView_ireadFirstLineIndent, style.body.firstLineIndentEm),
                lineSpacingMultiplier = values.getFloat(R.styleable.ReaderView_ireadLineSpacingMultiplier, style.body.lineSpacingMultiplier),
                lineSpacingExtraDp = dp(R.styleable.ReaderView_ireadLineSpacingExtra, style.body.lineSpacingExtraDp),
                paragraphSpacingDp = dp(R.styleable.ReaderView_ireadParagraphSpacing, style.body.paragraphSpacingDp),
                containerPadding = insets(style.body.containerPadding, R.styleable.ReaderView_ireadBodyContainerPaddingStart,
                    R.styleable.ReaderView_ireadBodyContainerPaddingTop, R.styleable.ReaderView_ireadBodyContainerPaddingEnd, R.styleable.ReaderView_ireadBodyContainerPaddingBottom),
                blockPadding = insets(style.body.blockPadding, R.styleable.ReaderView_ireadBodyBlockPaddingStart,
                    R.styleable.ReaderView_ireadBodyBlockPaddingTop, R.styleable.ReaderView_ireadBodyBlockPaddingEnd, R.styleable.ReaderView_ireadBodyBlockPaddingBottom),
                contentPadding = EdgeInsets(dp(R.styleable.ReaderView_ireadBodyPaddingStart, style.body.contentPadding.startDp),
                    dp(R.styleable.ReaderView_ireadBodyPaddingTop, style.body.contentPadding.topDp),
                    dp(R.styleable.ReaderView_ireadBodyPaddingEnd, style.body.contentPadding.endDp),
                    dp(R.styleable.ReaderView_ireadBodyPaddingBottom, style.body.contentPadding.bottomDp))
            ),
            title = style.title.copy(
                textSizeSp = sp(R.styleable.ReaderView_ireadTitleTextSize, style.title.textSizeSp),
                textColor = values.getColor(R.styleable.ReaderView_ireadTitleTextColor, style.title.textColor),
                readerFont = font(R.styleable.ReaderView_ireadTitleFontFamily, style.title.readerFont),
                alignment = TitleAlignment.entries[alignment],
                padding = insets(style.title.padding, R.styleable.ReaderView_ireadTitlePaddingStart,
                    R.styleable.ReaderView_ireadTitlePaddingTop, R.styleable.ReaderView_ireadTitlePaddingEnd, R.styleable.ReaderView_ireadTitlePaddingBottom),
                letterSpacingEm = values.getFloat(R.styleable.ReaderView_ireadTitleLetterSpacing, style.title.letterSpacingEm),
                letterSpacingDp = if (values.hasValue(R.styleable.ReaderView_ireadTitleLetterSpacingDp)) dp(R.styleable.ReaderView_ireadTitleLetterSpacingDp, 0f) else style.title.letterSpacingDp,
                spacingAfterDp = dp(R.styleable.ReaderView_ireadTitleSpacingAfter, style.title.spacingAfterDp),
                lineSpacingMultiplier = values.getFloat(R.styleable.ReaderView_ireadTitleLineSpacingMultiplier, style.title.lineSpacingMultiplier),
                lineSpacingExtraDp = dp(R.styleable.ReaderView_ireadTitleLineSpacingExtra, style.title.lineSpacingExtraDp)
            ),
            header = chrome(style.header, R.styleable.ReaderView_ireadHeaderVisible, R.styleable.ReaderView_ireadHeaderTextSize, R.styleable.ReaderView_ireadHeaderTextColor,
                R.styleable.ReaderView_ireadHeaderHeight, intArrayOf(R.styleable.ReaderView_ireadHeaderPaddingStart, R.styleable.ReaderView_ireadHeaderPaddingTop,
                    R.styleable.ReaderView_ireadHeaderPaddingEnd, R.styleable.ReaderView_ireadHeaderPaddingBottom),
                R.styleable.ReaderView_ireadHeaderAlignment, R.styleable.ReaderView_ireadHeaderContent, R.styleable.ReaderView_ireadHeaderBackground, R.styleable.ReaderView_ireadHeaderFontFamily),
            footer = chrome(style.footer, R.styleable.ReaderView_ireadFooterVisible, R.styleable.ReaderView_ireadFooterTextSize, R.styleable.ReaderView_ireadFooterTextColor,
                R.styleable.ReaderView_ireadFooterHeight, intArrayOf(R.styleable.ReaderView_ireadFooterPaddingStart, R.styleable.ReaderView_ireadFooterPaddingTop,
                    R.styleable.ReaderView_ireadFooterPaddingEnd, R.styleable.ReaderView_ireadFooterPaddingBottom),
                R.styleable.ReaderView_ireadFooterAlignment, R.styleable.ReaderView_ireadFooterContent, R.styleable.ReaderView_ireadFooterBackground, R.styleable.ReaderView_ireadFooterFontFamily),
            highlightStyles = HighlightStyles(highlights),
            selectionStyle = style.selectionStyle.copy(
                fillColor = values.getColor(R.styleable.ReaderView_ireadSelectionColor, style.selectionStyle.fillColor),
                handleColor = values.getColor(R.styleable.ReaderView_ireadSelectionHandleColor, style.selectionStyle.handleColor),
                handleRadiusDp = dp(R.styleable.ReaderView_ireadSelectionHandleRadius, style.selectionStyle.handleRadiusDp)
            )
        )
    }
}
