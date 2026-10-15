package org.peyilo.ireadview.internal.style

import android.os.Bundle
import org.peyilo.ireadview.api.style.*

/** Primitive, versioned state only. Typeface instances are supplied again by XML/the host. */
internal object ReaderStyleStateCodec {
    private const val MAX_HIGHLIGHTS = 256
    private const val MAX_PALETTE_CHARACTERS = 16384
    private fun EdgeInsets.values() = floatArrayOf(startDp, topDp, endDp, bottomDp)
    private fun FloatArray.insets() = EdgeInsets(this[0], this[1], this[2], this[3])

    fun encode(style: ReaderStyle): Bundle = Bundle().apply {
        putInt("version", 1)
        putInt("pageColor", style.page.backgroundColor)
        encodeBackground(style.page.readerBackground)?.let { putBundle("background", it) }
        putFloatArray("pagePadding", style.page.padding.values())
        putFloatArray("viewportPadding", style.page.viewportPadding.values())
        putFloatArray("body", with(style.body) { floatArrayOf(textSizeSp, letterSpacingEm, firstLineIndentEm, lineSpacingMultiplier, lineSpacingExtraDp, paragraphSpacingDp) })
        putFloatArray("bodyPadding", style.body.contentPadding.values())
        putFloatArray("containerPadding", style.body.containerPadding.values())
        putFloatArray("blockPadding", style.body.blockPadding.values())
        putInt("bodyColor", style.body.textColor)
        style.body.letterSpacingDp?.let { putFloat("bodyLetterSpacingDp", it) }
        style.title.letterSpacingDp?.let { putFloat("titleLetterSpacingDp", it) }
        putFloatArray("title", floatArrayOf(style.title.textSizeSp, style.title.spacingAfterDp))
        putFloatArray("titlePadding", style.title.padding.values())
        putFloat("titleLetterSpacing", style.title.letterSpacingEm)
        putFloat("titleLineMultiplier", style.title.lineSpacingMultiplier)
        putFloat("titleLineExtra", style.title.lineSpacingExtraDp)
        putIntArray("titleValues", intArrayOf(style.title.textColor, style.title.alignment.ordinal))
        putFloatArray("chromeSizes", floatArrayOf(style.header.textSizeSp, style.footer.textSizeSp))
        putIntArray("chromeColors", intArrayOf(style.header.textColor, style.footer.textColor))
        putBooleanArray("chromeVisibility", booleanArrayOf(style.header.visible, style.footer.visible))
        putBundle("headerOptions", encodeChrome(style.header))
        putBundle("footerOptions", encodeChrome(style.footer))
        putIntArray("selectionColors", intArrayOf(style.selectionStyle.fillColor, style.selectionStyle.handleColor))
        putFloat("handleRadius", style.selectionStyle.handleRadiusDp)
        encodeFont(style.body.readerFont)?.let { putBundle("bodyFont", it) }
        encodeFont(style.title.readerFont)?.let { putBundle("titleFont", it) }
        if (style.highlightStyles.styles.size <= MAX_HIGHLIGHTS && style.highlightStyles.styles.keys.sumOf { it.length.toLong() } <= MAX_PALETTE_CHARACTERS) {
            putStringArrayList("highlightIds", ArrayList(style.highlightStyles.styles.keys))
            putIntArray("highlightColors", style.highlightStyles.styles.values.map { it.fillColor }.toIntArray())
        }
    }

    /** Invalid/unknown state returns null, preserving the entire current style. */
    fun decode(data: Bundle?, current: ReaderStyle): ReaderStyle? = runCatching {
        if (data == null || data.getInt("version") != 1) return null
        fun floats(key: String, count: Int) = requireNotNull(data.getFloatArray(key)).also { require(it.size == count) }
        fun ints(key: String, count: Int) = requireNotNull(data.getIntArray(key)).also { require(it.size == count) }
        fun optionalInsets(key: String, fallback: EdgeInsets) = if (data.containsKey(key)) floats(key, 4).insets() else fallback
        require(listOf("pageColor", "bodyColor", "handleRadius").all { data.containsKey(it) })
        val body = floats("body", 6)
        val title = floats("title", 2)
        val titleValues = ints("titleValues", 2)
        require(titleValues[1] in TitleAlignment.entries.indices)
        val sizes = floats("chromeSizes", 2)
        val colors = ints("chromeColors", 2)
        val visibility = requireNotNull(data.getBooleanArray("chromeVisibility")).also { require(it.size == 2) }
        val selection = ints("selectionColors", 2)
        val palette = if (data.containsKey("highlightIds") || data.containsKey("highlightColors")) {
            val ids = requireNotNull(data.getStringArrayList("highlightIds"))
            val fills = requireNotNull(data.getIntArray("highlightColors"))
            require(ids.size in 1..MAX_HIGHLIGHTS && ids.size == fills.size && ids.toSet().size == ids.size)
            require(ids.sumOf { it.length.toLong() } <= MAX_PALETTE_CHARACTERS)
            HighlightStyles(ids.indices.associate { ids[it] to HighlightStyle(fills[it]) })
        } else current.highlightStyles
        current.copy(
            page = PageStyle(data.getInt("pageColor"), floats("pagePadding", 4).insets(), decodeBackground(data.getBundle("background"), current.page.readerBackground),
                viewportPadding = optionalInsets("viewportPadding", current.page.viewportPadding)),
            body = current.body.copy(textSizeSp = body[0], textColor = data.getInt("bodyColor"), letterSpacingEm = body[1],
                letterSpacingDp = if (data.containsKey("bodyLetterSpacingDp")) data.getFloat("bodyLetterSpacingDp") else null,
                firstLineIndentEm = body[2], lineSpacingMultiplier = body[3], lineSpacingExtraDp = body[4], paragraphSpacingDp = body[5],
                contentPadding = floats("bodyPadding", 4).insets(), readerFont = decodeFont(data.getBundle("bodyFont"), current.body.readerFont),
                containerPadding = optionalInsets("containerPadding", current.body.containerPadding), blockPadding = optionalInsets("blockPadding", current.body.blockPadding)),
            title = current.title.copy(textSizeSp = title[0], spacingAfterDp = title[1], textColor = titleValues[0], alignment = TitleAlignment.entries[titleValues[1]],
                letterSpacingDp = if (data.containsKey("titleLetterSpacingDp")) data.getFloat("titleLetterSpacingDp") else null,
                readerFont = decodeFont(data.getBundle("titleFont"), current.title.readerFont), padding = optionalInsets("titlePadding", current.title.padding),
                letterSpacingEm = if (data.containsKey("titleLetterSpacing")) data.getFloat("titleLetterSpacing") else current.title.letterSpacingEm,
                lineSpacingMultiplier = if (data.containsKey("titleLineMultiplier")) data.getFloat("titleLineMultiplier") else current.title.lineSpacingMultiplier,
                lineSpacingExtraDp = if (data.containsKey("titleLineExtra")) data.getFloat("titleLineExtra") else current.title.lineSpacingExtraDp),
            header = decodeChrome(data.getBundle("headerOptions"), current.header.copy(visible = visibility[0], textSizeSp = sizes[0], textColor = colors[0])),
            footer = decodeChrome(data.getBundle("footerOptions"), current.footer.copy(visible = visibility[1], textSizeSp = sizes[1], textColor = colors[1])),
            highlightStyles = palette,
            selectionStyle = SelectionStyle(selection[0], selection[1], data.getFloat("handleRadius"))
        )
    }.getOrNull()

    private fun encodeFont(font: ReaderFont?): Bundle? = when (font) {
        is ReaderFont.System -> Bundle().apply { putString("kind", "system"); putString("family", font.family); putInt("style", font.style) }
        is ReaderFont.Resource -> Bundle().apply { putString("kind", "resource"); putInt("id", font.resourceId) }
        else -> null
    }

    private fun encodeBackground(background: ReaderBackground?): Bundle? = when (background) {
        null -> Bundle().apply { putString("kind", "fallback") }
        is ReaderBackground.Color -> Bundle().apply { putString("kind", "color"); putInt("color", background.color) }
        is ReaderBackground.Resource -> Bundle().apply { putString("kind", "resource"); putInt("id", background.resourceId) }
        is ReaderBackground.Factory -> null
    }

    private fun encodeChrome(style: ChromeStyle) = Bundle().apply {
        putFloatArray("padding", style.padding.values())
        style.heightDp?.let { putFloat("height", it) }
        putInt("alignment", style.alignment.ordinal)
        putInt("content", style.content.ordinal)
        encodeBackground(style.background)?.let { putBundle("background", it) }
        encodeFont(style.readerFont)?.let { putBundle("font", it) }
    }

    private fun decodeChrome(data: Bundle?, current: ChromeStyle): ChromeStyle {
        if (data == null) return current
        val padding = requireNotNull(data.getFloatArray("padding")).also { require(it.size == 4) }
        require(data.containsKey("alignment") && data.containsKey("content"))
        val alignment = data.getInt("alignment")
        val content = data.getInt("content")
        require(alignment in ChromeAlignment.entries.indices && content in ChromeContent.entries.indices)
        return current.copy(padding = padding.insets(), heightDp = if (data.containsKey("height")) data.getFloat("height") else null,
            alignment = ChromeAlignment.entries[alignment], content = ChromeContent.entries[content],
            background = decodeBackground(data.getBundle("background"), current.background),
            readerFont = decodeFont(data.getBundle("font"), current.readerFont))
    }

    private fun decodeBackground(data: Bundle?, fallback: ReaderBackground?): ReaderBackground? {
        if (data == null) return fallback
        return when (data.getString("kind")) {
            "fallback" -> null
            "color" -> { require(data.containsKey("color")); ReaderBackground.Color(data.getInt("color")) }
            "resource" -> ReaderBackground.Resource(data.getInt("id"))
            else -> error("Unknown reader background state")
        }
    }

    private fun decodeFont(data: Bundle?, fallback: ReaderFont?): ReaderFont? {
        if (data == null) return fallback
        return when (data.getString("kind")) {
            "system" -> ReaderFont.System(requireNotNull(data.getString("family")), data.getInt("style"))
            "resource" -> ReaderFont.Resource(data.getInt("id"))
            else -> error("Unknown reader font state")
        }
    }
}
