package org.peyilo.ireadview.api.style

/** Logical start/end respects layout direction; every padding value is in dp. */
enum class PaddingRegion { PAGE, HEADER, FOOTER, BODY, TITLE, CONTENT, BODY_VIEWPORT, PARAGRAPH }
enum class TextRole { BODY, TITLE, HEADER, FOOTER }

/** Java/Kotlin style editing API. Every with* function returns an immutable copy.
 * Submit the result through BookReadView.applyStyle; observe completion before persisting it.
 * These helpers do not mutate a Widget or bypass its anchor-preserving layout transaction.
 */
object ReaderStyles {
    /** ARGB per-line selection fill. Suggested alpha 0x20..0x55 to keep text readable. */
    @JvmStatic fun withSelectionBackgroundColor(style: ReaderStyle, color: Int): ReaderStyle =
        style.copy(selectionStyle = style.selectionStyle.copy(fillColor = color))
    /** ARGB for both endpoint stems and dots. Prefer an opaque color matching the selection fill. */
    @JvmStatic fun withSelectionHandleColor(style: ReaderStyle, color: Int): ReaderStyle =
        style.copy(selectionStyle = style.selectionStyle.copy(handleColor = color))
    @JvmStatic fun padding(style: ReaderStyle, region: PaddingRegion): EdgeInsets = when (region) {
        PaddingRegion.PAGE -> style.page.padding
        PaddingRegion.HEADER -> style.header.padding
        PaddingRegion.FOOTER -> style.footer.padding
        PaddingRegion.BODY -> style.body.containerPadding
        PaddingRegion.TITLE -> style.title.padding
        PaddingRegion.CONTENT -> style.body.contentPadding
        PaddingRegion.BODY_VIEWPORT -> style.page.viewportPadding
        PaddingRegion.PARAGRAPH -> style.body.blockPadding
    }
    /** PAGE surrounds the page; BODY/CONTENT are outer/inner body insets; PARAGRAPH is per block.
     * Suggested 0..32 dp; excessive padding can leave no usable viewport and fail applyStyle.
     */
    @JvmStatic fun withPadding(style: ReaderStyle, region: PaddingRegion, padding: EdgeInsets): ReaderStyle = when (region) {
        PaddingRegion.PAGE -> style.copy(page = style.page.copy(padding = padding))
        PaddingRegion.HEADER -> style.copy(header = style.header.copy(padding = padding))
        PaddingRegion.FOOTER -> style.copy(footer = style.footer.copy(padding = padding))
        PaddingRegion.BODY -> style.copy(body = style.body.copy(containerPadding = padding))
        PaddingRegion.TITLE -> style.copy(title = style.title.copy(padding = padding))
        PaddingRegion.CONTENT -> style.copy(body = style.body.copy(contentPadding = padding))
        PaddingRegion.BODY_VIEWPORT -> style.copy(page = style.page.copy(viewportPadding = padding))
        PaddingRegion.PARAGRAPH -> style.copy(body = style.body.copy(blockPadding = padding))
    }
    @JvmStatic fun textSizeSp(style: ReaderStyle, role: TextRole): Float = when (role) {
        TextRole.BODY -> style.body.textSizeSp; TextRole.TITLE -> style.title.textSizeSp
        TextRole.HEADER -> style.header.textSizeSp; TextRole.FOOTER -> style.footer.textSizeSp
    }
    /** Positive sp values respect font scale. Suggested body 16..24, title 22..32, chrome 10..16. */
    @JvmStatic fun withTextSizeSp(style: ReaderStyle, role: TextRole, value: Float): ReaderStyle = when (role) {
        TextRole.BODY -> style.copy(body = style.body.copy(textSizeSp = value))
        TextRole.TITLE -> style.copy(title = style.title.copy(textSizeSp = value))
        TextRole.HEADER -> style.copy(header = style.header.copy(textSizeSp = value))
        TextRole.FOOTER -> style.copy(footer = style.footer.copy(textSizeSp = value))
    }
    /** Null means em spacing is active. Fixed spacing applies to body and chapter headings. */
    @JvmStatic fun letterSpacingDp(style: ReaderStyle, role: TextRole): Float? = when (role) {
        TextRole.BODY -> style.body.letterSpacingDp; TextRole.TITLE -> style.title.letterSpacingDp
        else -> throw IllegalArgumentException("Fixed letter spacing supports BODY/TITLE")
    }
    @JvmStatic fun withLetterSpacingDp(style: ReaderStyle, role: TextRole, value: Float?): ReaderStyle = when (role) {
        TextRole.BODY -> style.copy(body = style.body.copy(letterSpacingDp = value))
        TextRole.TITLE -> style.copy(title = style.title.copy(letterSpacingDp = value))
        else -> throw IllegalArgumentException("Fixed letter spacing supports BODY/TITLE")
    }
    /** em spacing scales with text size; setting it explicitly clears the fixed dp override. */
    @JvmStatic fun withLetterSpacingEm(style: ReaderStyle, role: TextRole, value: Float): ReaderStyle = when (role) {
        TextRole.BODY -> style.copy(body = style.body.copy(letterSpacingEm = value, letterSpacingDp = null))
        TextRole.TITLE -> style.copy(title = style.title.copy(letterSpacingEm = value, letterSpacingDp = null))
        else -> throw IllegalArgumentException("Letter spacing supports BODY/TITLE")
    }
    /** Extra line spacing is dp, on top of a positive multiplier. Suggested 1.2..1.8 and 0..8 dp. */
    @JvmStatic fun withLineSpacing(style: ReaderStyle, multiplier: Float, extraDp: Float) =
        style.copy(body = style.body.copy(lineSpacingMultiplier = multiplier, lineSpacingExtraDp = extraDp))
    /** Heading line spacing: positive multiplier plus nonnegative dp. Suggested 1 and 0..10 dp. */
    @JvmStatic fun withTitleLineSpacing(style: ReaderStyle, multiplier: Float, extraDp: Float) =
        style.copy(title = style.title.copy(lineSpacingMultiplier = multiplier, lineSpacingExtraDp = extraDp))
    /** Gap after each body paragraph in dp; suggested 4..20. */
    @JvmStatic fun withParagraphSpacingDp(style: ReaderStyle, value: Float) = style.copy(body = style.body.copy(paragraphSpacingDp = value))
    /** First line indentation in em, suggested 0..2. Does not change source text or anchors. */
    @JvmStatic fun withFirstLineIndentEm(style: ReaderStyle, value: Float) = style.copy(body = style.body.copy(firstLineIndentEm = value))
    @JvmStatic fun withTitleAlignment(style: ReaderStyle, value: TitleAlignment) = style.copy(title = style.title.copy(alignment = value))
    /** Extra gap after a heading, in dp. Suggested 8..24. */
    @JvmStatic fun withTitleSpacingAfterDp(style: ReaderStyle, value: Float) = style.copy(title = style.title.copy(spacingAfterDp = value))
    @JvmStatic fun readerFont(style: ReaderStyle, role: TextRole): ReaderFont? = when (role) {
        TextRole.BODY -> style.body.readerFont; TextRole.TITLE -> style.title.readerFont
        TextRole.HEADER -> style.header.readerFont; TextRole.FOOTER -> style.footer.readerFont
    }
    @JvmStatic fun textColor(style: ReaderStyle, role: TextRole): Int = when (role) {
        TextRole.BODY -> style.body.textColor; TextRole.TITLE -> style.title.textColor
        TextRole.HEADER -> style.header.textColor; TextRole.FOOTER -> style.footer.textColor
    }
    /** Chrome helpers accept HEADER/FOOTER only; other roles fail explicitly. */
    @JvmStatic fun chrome(style: ReaderStyle, role: TextRole): ChromeStyle = when (role) {
        TextRole.HEADER -> style.header; TextRole.FOOTER -> style.footer
        else -> throw IllegalArgumentException("Chrome supports HEADER/FOOTER")
    }
    private fun withChrome(style: ReaderStyle, role: TextRole, value: ChromeStyle): ReaderStyle = when (role) {
        TextRole.HEADER -> style.copy(header = value); TextRole.FOOTER -> style.copy(footer = value)
        else -> throw IllegalArgumentException("Chrome supports HEADER/FOOTER")
    }
    /** Null measures text plus padding automatically; fixed positive dp values can clip text. */
    @JvmStatic fun withChromeHeightDp(style: ReaderStyle, role: TextRole, heightDp: Float?) =
        withChrome(style, role, chrome(style, role).copy(heightDp = heightDp))
    @JvmStatic fun withChromeVisibility(style: ReaderStyle, role: TextRole, visible: Boolean) =
        withChrome(style, role, chrome(style, role).copy(visible = visible))
    @JvmStatic fun withChromeAlignment(style: ReaderStyle, role: TextRole, alignment: ChromeAlignment) =
        withChrome(style, role, chrome(style, role).copy(alignment = alignment))
    @JvmStatic fun withChromeContent(style: ReaderStyle, role: TextRole, content: ChromeContent) =
        withChrome(style, role, chrome(style, role).copy(content = content))
    @JvmStatic fun withFont(style: ReaderStyle, role: TextRole, font: ReaderFont?): ReaderStyle = when (role) {
        TextRole.BODY -> style.copy(body = style.body.copy(readerFont = font, font = null))
        TextRole.TITLE -> style.copy(title = style.title.copy(readerFont = font, font = null))
        TextRole.HEADER -> style.copy(header = style.header.copy(readerFont = font, font = null))
        TextRole.FOOTER -> style.copy(footer = style.footer.copy(readerFont = font, font = null))
    }
    @JvmStatic fun withTextColor(style: ReaderStyle, role: TextRole, argb: Int): ReaderStyle = when (role) {
        TextRole.BODY -> style.copy(body = style.body.copy(textColor = argb))
        TextRole.TITLE -> style.copy(title = style.title.copy(textColor = argb))
        TextRole.HEADER -> style.copy(header = style.header.copy(textColor = argb))
        TextRole.FOOTER -> style.copy(footer = style.footer.copy(textColor = argb))
    }
}
