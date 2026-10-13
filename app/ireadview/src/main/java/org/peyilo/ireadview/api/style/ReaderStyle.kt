package org.peyilo.ireadview.api.style

import android.graphics.Typeface
import java.util.Collections

data class HighlightStyle(val fillColor: Int = 0x66ffd54f)
data class SelectionStyle(val fillColor: Int = 0x6657b9ff, val handleColor: Int = 0xff2786c5.toInt(), val handleRadiusDp: Float = 7f) {
    init { require(handleRadiusDp.isFinite() && handleRadiusDp > 0) }
}

/** Copies host entries so a style cannot change behind its layout/decorations transaction. */
class HighlightStyles(styles: Map<String, HighlightStyle> = mapOf("default" to HighlightStyle())) {
    val styles: Map<String, HighlightStyle> = Collections.unmodifiableMap(LinkedHashMap(styles))
    init { require("default" in styles && styles.keys.all { it.isNotBlank() }) }
    operator fun get(id: String): HighlightStyle = styles[id] ?: styles.getValue("default")
    override fun equals(other: Any?): Boolean = other is HighlightStyles && styles == other.styles
    override fun hashCode(): Int = styles.hashCode()
}

data class EdgeInsets(
    val startDp: Float = 0f, val topDp: Float = 0f,
    val endDp: Float = 0f, val bottomDp: Float = 0f
) {
    init { require(listOf(startDp, topDp, endDp, bottomDp).all { it.isFinite() && it >= 0f }) }
}

data class BodyStyle(
    val textSizeSp: Float = 18f,
    val textColor: Int = 0xff302c28.toInt(),
    val font: Typeface? = null,
    val letterSpacingEm: Float = 0f,
    val firstLineIndentEm: Float = 2f,
    val lineSpacingMultiplier: Float = 1.4f,
    val lineSpacingExtraDp: Float = 0f,
    val paragraphSpacingDp: Float = 12f,
    val contentPadding: EdgeInsets = EdgeInsets(),
    /** When non-null this takes precedence over the legacy Typeface field. */
    val readerFont: ReaderFont? = null,
    /** Body-only outer inset, added to page.viewportPadding and contentPadding. */
    val containerPadding: EdgeInsets = EdgeInsets(),
    /** Applied by the layout engine to every non-heading block; not repeated on split pages. */
    val blockPadding: EdgeInsets = EdgeInsets()
) {
    init {
        require(textSizeSp.isFinite() && textSizeSp > 0)
        require(letterSpacingEm.isFinite() && firstLineIndentEm.isFinite() && firstLineIndentEm >= 0)
        require(lineSpacingMultiplier.isFinite() && lineSpacingMultiplier > 0)
        require(lineSpacingExtraDp.isFinite() && lineSpacingExtraDp >= 0)
        require(paragraphSpacingDp.isFinite() && paragraphSpacingDp >= 0)
    }
}

enum class TitleAlignment { START, CENTER, END }
data class TitleStyle(
    val textSizeSp: Float = 24f,
    val textColor: Int = 0xff302c28.toInt(),
    val font: Typeface? = null,
    val alignment: TitleAlignment = TitleAlignment.CENTER,
    val spacingAfterDp: Float = 20f,
    val readerFont: ReaderFont? = null,
    /** Applied to HeadingBlock by the engine instead of body.blockPadding. */
    val padding: EdgeInsets = EdgeInsets(),
    val letterSpacingEm: Float = 0f
) {
    init { require(textSizeSp.isFinite() && textSizeSp > 0); require(spacingAfterDp.isFinite() && spacingAfterDp >= 0); require(letterSpacingEm.isFinite()) }
}

enum class ChromeAlignment { START, CENTER, END }
enum class ChromeContent { DEFAULT, BOOK_TITLE, CHAPTER_TITLE, PAGE_NUMBER, PAGE_COUNT, PAGE_PROGRESS, NONE, TIME, PAGE_PROGRESS_AND_TIME }
data class ChromeStyle(
    val visible: Boolean = true,
    val textSizeSp: Float = 12f,
    val textColor: Int = 0xff81756b.toInt(),
    /** Null lets text and padding determine height; fixed heights are in dp. */
    val heightDp: Float? = null,
    val padding: EdgeInsets = EdgeInsets(),
    val alignment: ChromeAlignment = ChromeAlignment.START,
    val content: ChromeContent = ChromeContent.DEFAULT,
    val background: ReaderBackground? = null,
    val font: Typeface? = null,
    val readerFont: ReaderFont? = null
) {
    init {
        require(textSizeSp.isFinite() && textSizeSp > 0)
        require(heightDp == null || heightDp.isFinite() && heightDp > 0)
    }
}

data class PageStyle(
    val backgroundColor: Int = 0xfff7f0e5.toInt(),
    val padding: EdgeInsets = EdgeInsets(20f, 12f, 20f, 12f),
    /** Null uses backgroundColor; transparent drawable pixels have backgroundColor underneath. */
    val readerBackground: ReaderBackground? = null,
    /** Insets of the library-owned body viewport, excluding header and footer. */
    val viewportPadding: EdgeInsets = EdgeInsets()
)

data class ReaderStyle(
    val page: PageStyle = PageStyle(),
    val body: BodyStyle = BodyStyle(),
    val title: TitleStyle = TitleStyle(),
    val header: ChromeStyle = ChromeStyle(padding = EdgeInsets(bottomDp = 12f)),
    val footer: ChromeStyle = ChromeStyle(padding = EdgeInsets(topDp = 12f), alignment = ChromeAlignment.END),
    val highlightStyles: HighlightStyles = HighlightStyles(),
    val selectionStyle: SelectionStyle = SelectionStyle()
) {
    companion object { @JvmStatic fun default(): ReaderStyle = ReaderStyle() }
}
