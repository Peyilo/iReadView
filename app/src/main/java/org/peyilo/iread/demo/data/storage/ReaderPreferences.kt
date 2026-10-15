package org.peyilo.iread.demo.data.storage

import org.peyilo.iread.demo.reader.ReaderEffects

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.peyilo.ireadview.api.effect.PageTurnOptions
import org.peyilo.ireadview.api.input.PagingAxis
import org.peyilo.ireadview.api.input.ReaderInputOptions
import org.peyilo.ireadview.api.model.ReadingMode
import org.peyilo.ireadview.api.style.*

/** Host JSON schema, deliberately independent from the Widget's internal SavedState codec. */
internal class ReaderPreferences(context: Context) {
    private val resources = context.resources
    private val preferences = context.getSharedPreferences("reader-preferences", Context.MODE_PRIVATE)
    data class Settings(val style: ReaderStyle?, val input: ReaderInputOptions?, val turn: PageTurnOptions?,
        val mode: ReadingMode?, val effectId: String?, val customPage: Boolean?, val customPlaceholder: Boolean?, val customSelectionToolbar: Boolean?)

    fun load(base: ReaderStyle): Settings? = runCatching {
        val raw = preferences.getString("settings", null) ?: return null
        require(raw.length <= 65536)
        val data = JSONObject(raw)
        require(data.getInt("version") == 1)
        // Independent groups: one malformed group does not discard the others.
        val revision = data.optInt("typographyRevision")
        val savedStyle = if (revision == org.peyilo.iread.demo.reader.ReaderDefaults.TYPOGRAPHY_REVISION || revision == 2 || revision == 3)
            runCatching {
                val loaded = decodeStyle(data.getJSONObject("style"), base)
                val decoded = if (revision < 4) loaded.copy(selectionStyle = base.selectionStyle) else loaded
                if (revision == 2) decoded.copy(
                    header = decoded.header.copy(padding = decoded.header.padding.copy(topDp = base.header.padding.topDp)),
                    footer = decoded.footer.copy(padding = decoded.footer.padding.copy(bottomDp = base.footer.padding.bottomDp))
                ) else decoded
            }.getOrNull() else null
        Settings(savedStyle,
            runCatching {
                val input = data.getJSONObject("input")
                ReaderInputOptions(input.getBoolean("gesture"), input.getBoolean("tap"), input.getBoolean("keyboard"),
                    input.getBoolean("volume"), PagingAxis.valueOf(input.getString("axis")), input.getDouble("edge").toFloat())
            }.getOrNull(), runCatching {
                val turn = data.getJSONObject("turn")
                PageTurnOptions(turn.getLong("duration"), turn.getBoolean("reduceMotion"), turn.getLong("bitmapBudget"),
                    turn.getDouble("distance").toFloat(), turn.getDouble("velocity").toFloat(), turn.getLong("interval"))
            }.getOrNull(), when (data.optString("mode")) {
                ReadingMode.PAGED.id -> ReadingMode.PAGED
                ReadingMode.CONTINUOUS_SCROLL.id -> ReadingMode.CONTINUOUS_SCROLL
                else -> null
            }, data.optString("effect").takeIf { ReaderEffects.fromId(it) != null },
            runCatching { data.getBoolean("customPage") }.getOrNull(), runCatching { data.getBoolean("customPlaceholder") }.getOrNull(),
            runCatching { data.getBoolean("customSelectionToolbar") }.getOrNull())
    }.getOrNull()

    fun save(style: ReaderStyle, input: ReaderInputOptions, turn: PageTurnOptions, mode: ReadingMode,
        effectId: String, customPage: Boolean, customPlaceholder: Boolean, customSelectionToolbar: Boolean) {
        val data = JSONObject().put("version", 1).put("mode", mode.id)
            .put("typographyRevision", org.peyilo.iread.demo.reader.ReaderDefaults.TYPOGRAPHY_REVISION)
            .put("customPage", customPage).put("customPlaceholder", customPlaceholder)
            .put("customSelectionToolbar", customSelectionToolbar)
            .put("input", JSONObject().put("gesture", input.gesturePagingEnabled).put("tap", input.tapPagingEnabled)
                .put("keyboard", input.keyboardPagingEnabled).put("volume", input.volumeKeyPagingEnabled)
                .put("axis", input.pagingAxis.name).put("edge", input.tapEdgeFraction.toDouble()))
            .put("turn", JSONObject().put("duration", turn.durationMs).put("reduceMotion", turn.reduceMotion)
                .put("bitmapBudget", turn.bitmapBudgetBytes).put("distance", turn.commitDistanceFraction.toDouble())
                .put("velocity", turn.flingVelocityDpPerSecond.toDouble()).put("interval", turn.minimumTurnIntervalMs))
        // A host-known ID reconstructs a factory; arbitrary factory instances are never serialized.
        if (ReaderEffects.fromId(effectId) != null) data.put("effect", effectId)
        val encoded = encodeStyle(style)
        if (encoded.toString().length <= 60000) data.put("style", encoded)
        val raw = data.toString()
        if (preferences.getString("settings", null) != raw) preferences.edit().putString("settings", raw).apply()
    }

    private fun encodeStyle(style: ReaderStyle): JSONObject {
        val body = style.body
        val title = style.title
        val data = JSONObject().put("page", JSONObject().put("color", style.page.backgroundColor)
            .put("padding", insets(style.page.padding)).put("viewport", insets(style.page.viewportPadding)).apply {
                background(style.page.readerBackground)?.let { put("background", it) }
            }).put("body", JSONObject().put("size", body.textSizeSp.toDouble()).put("color", body.textColor)
                .put("letterDp", body.letterSpacingDp?.toDouble() ?: JSONObject.NULL).put("letter", body.letterSpacingEm.toDouble()).put("indent", body.firstLineIndentEm.toDouble())
                .put("lineMultiplier", body.lineSpacingMultiplier.toDouble()).put("lineExtra", body.lineSpacingExtraDp.toDouble())
                .put("paragraph", body.paragraphSpacingDp.toDouble()).put("content", insets(body.contentPadding))
                .put("container", insets(body.containerPadding)).put("block", insets(body.blockPadding)).apply { font(body.readerFont)?.let { put("font", it) } })
            .put("title", JSONObject().put("size", title.textSizeSp.toDouble()).put("color", title.textColor)
                .put("alignment", title.alignment.name).put("after", title.spacingAfterDp.toDouble())
                .put("lineMultiplier", title.lineSpacingMultiplier.toDouble()).put("lineExtra", title.lineSpacingExtraDp.toDouble())
                .put("letterDp", title.letterSpacingDp?.toDouble() ?: JSONObject.NULL).put("letter", title.letterSpacingEm.toDouble()).put("padding", insets(title.padding)).apply { font(title.readerFont)?.let { put("font", it) } })
            .put("header", chrome(style.header)).put("footer", chrome(style.footer))
            .put("selection", JSONObject().put("fill", style.selectionStyle.fillColor).put("handle", style.selectionStyle.handleColor)
                .put("radius", style.selectionStyle.handleRadiusDp.toDouble()))
        val palette = style.highlightStyles.styles
        if (palette.size <= 256 && palette.keys.sumOf { it.length.toLong() } <= 16384) {
            data.put("highlights", JSONArray().apply { palette.forEach { (id, style) -> put(JSONObject().put("id", id).put("fill", style.fillColor)) } })
        }
        return data
    }

    private fun decodeStyle(data: JSONObject, base: ReaderStyle): ReaderStyle {
        val page = data.getJSONObject("page")
        val body = data.getJSONObject("body")
        val title = data.getJSONObject("title")
        val selection = data.getJSONObject("selection")
        val palette = if (!data.has("highlights")) base.highlightStyles else {
            val entries = data.getJSONArray("highlights")
            require(entries.length() in 1..256)
            val values = LinkedHashMap<String, HighlightStyle>()
            var characters = 0L
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val id = entry.getString("id")
                characters += id.length
                require(characters <= 16384 && !values.containsKey(id))
                values[id] = HighlightStyle(entry.getInt("fill"))
            }
            HighlightStyles(values)
        }
        return base.copy(
            page = base.page.copy(backgroundColor = page.getInt("color"), padding = page.insets("padding"), viewportPadding = page.insets("viewport"),
                readerBackground = decodeBackground(page.optionalObject("background"), base.page.readerBackground)),
            body = base.body.copy(textSizeSp = body.number("size"), textColor = body.getInt("color"), letterSpacingEm = body.number("letter"), letterSpacingDp = if (body.has("letterDp") && !body.isNull("letterDp")) body.number("letterDp") else null,
                firstLineIndentEm = body.number("indent"), lineSpacingMultiplier = body.number("lineMultiplier"), lineSpacingExtraDp = body.number("lineExtra"),
                paragraphSpacingDp = body.number("paragraph"), contentPadding = body.insets("content"), containerPadding = body.insets("container"),
                blockPadding = body.insets("block"), readerFont = decodeFont(body.optionalObject("font"), base.body.readerFont)),
            title = base.title.copy(textSizeSp = title.number("size"), textColor = title.getInt("color"), alignment = TitleAlignment.valueOf(title.getString("alignment")),
                lineSpacingMultiplier = title.optDouble("lineMultiplier", base.title.lineSpacingMultiplier.toDouble()).toFloat(),
                lineSpacingExtraDp = title.optDouble("lineExtra", base.title.lineSpacingExtraDp.toDouble()).toFloat(),
                spacingAfterDp = title.number("after"), letterSpacingEm = title.number("letter"), letterSpacingDp = if (title.has("letterDp") && !title.isNull("letterDp")) title.number("letterDp") else null, padding = title.insets("padding"),
                readerFont = decodeFont(title.optionalObject("font"), base.title.readerFont)),
            header = decodeChrome(data.getJSONObject("header"), base.header), footer = decodeChrome(data.getJSONObject("footer"), base.footer),
            selectionStyle = SelectionStyle(selection.getInt("fill"), selection.getInt("handle"), selection.number("radius")), highlightStyles = palette)
    }

    private fun chrome(style: ChromeStyle) = JSONObject().put("visible", style.visible).put("size", style.textSizeSp.toDouble())
        .put("color", style.textColor).put("padding", insets(style.padding)).put("alignment", style.alignment.name).put("content", style.content.name).apply {
            put("height", style.heightDp?.toDouble() ?: JSONObject.NULL)
            font(style.readerFont)?.let { put("font", it) }; background(style.background)?.let { put("background", it) }
        }
    private fun decodeChrome(data: JSONObject, base: ChromeStyle) = base.copy(visible = data.getBoolean("visible"), textSizeSp = data.number("size"),
        textColor = data.getInt("color"), padding = data.insets("padding"), alignment = ChromeAlignment.valueOf(data.getString("alignment")),
        content = ChromeContent.valueOf(data.getString("content")), heightDp = if (data.isNull("height")) null else data.number("height"),
        readerFont = decodeFont(data.optionalObject("font"), base.readerFont), background = decodeBackground(data.optionalObject("background"), base.background))

    private fun insets(value: EdgeInsets) = JSONArray(listOf(value.startDp.toDouble(), value.topDp.toDouble(), value.endDp.toDouble(), value.bottomDp.toDouble()))
    private fun JSONObject.insets(key: String): EdgeInsets {
        val values = getJSONArray(key).also { require(it.length() == 4) }
        return EdgeInsets(values.getDouble(0).toFloat(), values.getDouble(1).toFloat(), values.getDouble(2).toFloat(), values.getDouble(3).toFloat())
    }
    private fun JSONObject.number(key: String) = getDouble(key).toFloat()
    private fun JSONObject.optionalObject(key: String): JSONObject? = if (has(key)) getJSONObject(key) else null
    private fun font(value: ReaderFont?): JSONObject? = when (value) {
        null -> JSONObject().put("kind", "default")
        is ReaderFont.System -> if (value.family.length <= 4096) JSONObject().put("kind", "system").put("family", value.family).put("style", value.style) else null
        is ReaderFont.Resource -> resource("font", value.resourceId)
        is ReaderFont.Runtime -> null
    }
    private fun background(value: ReaderBackground?): JSONObject? = when (value) {
        null -> JSONObject().put("kind", "default")
        is ReaderBackground.Color -> JSONObject().put("kind", "color").put("color", value.color)
        is ReaderBackground.Resource -> resource("drawable", value.resourceId)
        is ReaderBackground.Factory -> null
    }
    private fun resource(kind: String, id: Int): JSONObject? = runCatching {
        JSONObject().put("kind", kind).put("resource", resources.getResourceName(id))
    }.getOrNull()
    private fun resourceId(data: JSONObject, types: Set<String>): Int {
        val id = resources.getIdentifier(data.getString("resource"), null, null)
        require(id != 0 && resources.getResourceTypeName(id) in types)
        return id
    }
    private fun decodeFont(data: JSONObject?, fallback: ReaderFont?): ReaderFont? = if (data == null) fallback else when (data.getString("kind")) {
        "default" -> null
        "system" -> ReaderFont.System(data.getString("family").also { require(it.length <= 4096) }, data.getInt("style"))
        "font" -> ReaderFont.Resource(resourceId(data, setOf("font")))
        else -> error("Unknown font preference")
    }
    private fun decodeBackground(data: JSONObject?, fallback: ReaderBackground?): ReaderBackground? = if (data == null) fallback else when (data.getString("kind")) {
        "default" -> null
        "color" -> ReaderBackground.Color(data.getInt("color"))
        "drawable" -> ReaderBackground.Resource(resourceId(data, setOf("drawable", "mipmap", "color")))
        else -> error("Unknown background preference")
    }
}
