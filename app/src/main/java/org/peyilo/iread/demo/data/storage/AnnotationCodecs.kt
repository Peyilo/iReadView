package org.peyilo.iread.demo.data.storage

import org.json.JSONObject
import org.peyilo.ireadview.api.model.*

/** One envelope, versioned per kind. Adding a kind adds a codec, not another storage collection. */
internal object AnnotationCodecs {
    private class Codec(val type: String, val accepts: (ReaderAnnotation) -> Boolean,
        val write: (ReaderAnnotation) -> JSONObject, val read: (String, String, JSONObject) -> ReaderAnnotation)
    private val codecs = listOf(
        Codec("bookmark", { it is Bookmark }, {
            it as Bookmark; JSONObject().put("position", position(it.position)).put("label", it.label)
        }, { book, id, data -> Bookmark(id, parsePosition(book, data.getJSONObject("position")), data.optionalString("label")) }),
        Codec("highlight", { it is Highlight }, {
            it as Highlight; range(it).put("style", it.styleId)
        }, { book, id, data -> Highlight(id, parseRange(book, data), data.getString("quote"), data.getString("style")) }),
        Codec("underline", { it is Underline }, {
            it as Underline; range(it).put("appearance", TextLineStyleJson.encode(it.style))
        }, { book, id, data -> Underline(id, parseRange(book, data), data.getString("quote"), TextLineStyleJson.decode(data.getJSONObject("appearance"))) }),
        Codec("document-highlight", { it is DocumentHighlight }, {
            it as DocumentHighlight; documentRange(it.selection).put("style", it.styleId)
        }, { book, id, data -> DocumentHighlight(id, parseDocumentRange(book, data), data.getString("style")) }),
        Codec("document-underline", { it is DocumentUnderline }, {
            it as DocumentUnderline; documentRange(it.selection).put("appearance", TextLineStyleJson.encode(it.style))
        }, { book, id, data -> DocumentUnderline(id, parseDocumentRange(book, data), TextLineStyleJson.decode(data.getJSONObject("appearance"))) })
    )
    fun encode(item: ReaderAnnotation): JSONObject {
        val codec = codecs.single { it.accepts(item) }
        return JSONObject().put("id", item.id).put("type", codec.type).put("schema", 1).put("payload", codec.write(item))
    }
    fun decode(book: String, record: JSONObject): ReaderAnnotation? {
        if (record.optInt("schema") != 1) return null
        val codec = codecs.firstOrNull { it.type == record.optString("type") } ?: return null
        return codec.read(book, record.getString("id"), record.getJSONObject("payload"))
    }
    fun legacy(book: String, type: String, data: JSONObject): ReaderAnnotation =
        codecs.single { it.type == type }.read(book, data.getString("id"), data)
    private fun range(item: RangedAnnotation) = JSONObject().put("chapter", item.range.chapterId)
        .put("revision", item.range.contentRevision).put("quote", item.quote)
        .put("start", anchor(item.range.start)).put("end", anchor(item.range.endExclusive))
    private fun documentRange(value: DocumentTextSelection) = JSONObject().put("chapter", value.chapterId)
        .put("revision", value.contentRevision).put("locator", value.documentLocator).put("quote", value.text)
    private fun parseDocumentRange(book: String, data: JSONObject) = DocumentTextSelection(book, data.getString("chapter"),
        data.optionalString("revision"), data.getString("locator"), data.getString("quote"))
    private fun parseRange(book: String, data: JSONObject) = TextRange(book, data.getString("chapter"), data.optionalString("revision"),
        parseAnchor(data.getJSONObject("start")), parseAnchor(data.getJSONObject("end")))
    private fun anchor(value: ContentAnchor) = JSONObject().put("block", value.blockId).put("offset", value.offset)
    private fun parseAnchor(data: JSONObject) = ContentAnchor(data.getString("block"), data.getInt("offset"))
    private fun position(value: ReadingPosition) = JSONObject().put("chapter", value.chapterId).put("revision", value.contentRevision)
        .put("page", value.pageNumberHint).put("chapterNumber", value.chapterNumberHint).put("anchor", value.anchor?.let(::anchor))
        .put("documentLocator", value.documentLocator)
    private fun parsePosition(book: String, data: JSONObject) = ReadingPosition(book, data.getString("chapter"),
        data.optJSONObject("anchor")?.let(::parseAnchor), data.optionalString("revision"),
        if (data.has("page") && !data.isNull("page")) data.getInt("page") else null,
        if (data.has("chapterNumber") && !data.isNull("chapterNumber")) data.getInt("chapterNumber") else null, data.optionalString("documentLocator"))
    private fun JSONObject.optionalString(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
