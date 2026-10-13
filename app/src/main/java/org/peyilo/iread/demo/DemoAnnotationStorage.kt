package org.peyilo.iread.demo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.peyilo.ireadview.api.model.*

/** Example host persistence. The Widget imposes no JSON or storage format. */
internal class DemoAnnotationStorage(context: Context) {
    private val preferences = context.getSharedPreferences("reader-annotations", Context.MODE_PRIVATE)

    fun save(snapshot: AnnotationSnapshot) {
        val bookmarks = JSONArray()
        snapshot.bookmarks.forEach { item -> bookmarks.put(JSONObject().put("id", item.id).put("label", item.label).put("position", positionJson(item.position))) }
        val highlights = JSONArray()
        snapshot.highlights.forEach { item ->
            val range = item.range
            highlights.put(JSONObject().put("id", item.id).put("quote", item.quote).put("style", item.styleId)
                .put("chapter", range.chapterId).put("revision", range.contentRevision)
                .put("start", anchorJson(range.start)).put("end", anchorJson(range.endExclusive)))
        }
        val data = JSONObject().put("version", 1).put("book", snapshot.bookId).put("bookmarks", bookmarks).put("highlights", highlights)
        preferences.edit().putString(snapshot.bookId, data.toString()).apply()
    }

    fun load(bookId: String): AnnotationSnapshot? = runCatching {
        val value = preferences.getString(bookId, null) ?: return null
        val data = JSONObject(value)
        require(data.getInt("version") == 1 && data.getString("book") == bookId)
        val bookmarks = data.getJSONArray("bookmarks")
        val highlights = data.getJSONArray("highlights")
        AnnotationSnapshot(bookId,
            (0 until bookmarks.length()).map { index ->
                val item = bookmarks.getJSONObject(index)
                Bookmark(item.getString("id"), parsePosition(bookId, item.getJSONObject("position")), item.optionalString("label"))
            },
            (0 until highlights.length()).map { index ->
                val item = highlights.getJSONObject(index)
                Highlight(item.getString("id"), TextRange(bookId, item.getString("chapter"), item.optionalString("revision"), parseAnchor(item.getJSONObject("start")), parseAnchor(item.getJSONObject("end"))), item.getString("quote"), item.getString("style"))
            })
    }.getOrNull()

    private fun positionJson(value: ReadingPosition): JSONObject = JSONObject().put("chapter", value.chapterId)
        .put("revision", value.contentRevision).put("page", value.pageNumberHint).put("chapterNumber", value.chapterNumberHint)
        .put("anchor", value.anchor?.let { anchorJson(it) })
    private fun parsePosition(bookId: String, value: JSONObject) = ReadingPosition(bookId, value.getString("chapter"),
        if (value.has("anchor")) parseAnchor(value.getJSONObject("anchor")) else null,
        value.optionalString("revision"), if (value.has("page")) value.getInt("page") else null,
        if (value.has("chapterNumber")) value.getInt("chapterNumber") else null)
    private fun anchorJson(value: ContentAnchor): JSONObject = JSONObject().put("block", value.blockId).put("offset", value.offset)
    private fun parseAnchor(value: JSONObject) = ContentAnchor(value.getString("block"), value.getInt("offset"))
    private fun JSONObject.optionalString(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
