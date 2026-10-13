package org.peyilo.iread.demo

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.util.Collections
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.api.model.ReaderSnapshot
import org.peyilo.ireadview.api.model.ReadingPosition

/** Host-only persistence; no storage format or URI is added to the Widget's snapshot. */
internal class DemoReadingStorage(context: Context) {
    data class Recent(val bookId: String, val uri: String?, val charset: String?, val sourceType: String = "txt", val title: String? = null, val updatedAt: Long = 0)
    private val preferences = context.getSharedPreferences("reader-progress", Context.MODE_PRIVATE)

    /** Used on the grant queue before releasing permissions referenced by old disk records. */
    fun flush(): Boolean = preferences.edit().commit()

    fun save(snapshot: ReaderSnapshot, uri: String?, charset: String?, sourceType: String = "txt", title: String? = null) {
        require(sourceType == "txt" || sourceType == "epub")
        require(if (sourceType == "txt") (uri == null) == (charset == null) else uri != null && charset == null)
        val position = snapshot.position
        val data = JSONObject().put("version", 1).put("book", snapshot.bookId)
            .put("mode", snapshot.modeId).put("effect", snapshot.builtInEffectId)
            .put("chapter", position.chapterId).put("revision", position.contentRevision)
            .put("page", position.pageNumberHint).put("chapterNumber", position.chapterNumberHint)
        position.anchor?.let { data.put("block", it.blockId).put("offset", it.offset) }
        val boundedTitle = title?.replace('\n', ' ')?.replace('\r', ' ')?.trim()?.take(256)?.let {
            if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it
        }
        val recent = Recent(snapshot.bookId, uri, charset, sourceType, boundedTitle, System.currentTimeMillis())
        val old = history()
        val entries = (listOf(recent) + old.filter { it.bookId != recent.bookId }).take(32)
        val edit = preferences.edit().putString("snapshot:${snapshot.bookId}", data.toString())
            .putString("recent", encodeRecent(recent).toString()).putString("history", encodeHistory(entries).toString())
        old.filter { previous -> entries.none { it.bookId == previous.bookId } }.forEach { edit.remove("snapshot:${it.bookId}") }
        edit.apply()
    }

    fun load(bookId: String): ReaderSnapshot? = runCatching {
        val data = JSONObject(preferences.getString("snapshot:$bookId", null) ?: return null)
        require(data.getInt("version") == 1 && data.getString("book") == bookId)
        val anchor = data.optionalString("block")?.let { ContentAnchor(it, data.getInt("offset")) }
        ReaderSnapshot(bookId, ReadingPosition(bookId, data.getString("chapter"), anchor,
            data.optionalString("revision"), data.optionalInt("page"), data.optionalInt("chapterNumber")),
            data.getString("mode"), builtInEffectId = data.optionalString("effect"))
    }.getOrNull()

    fun recent(): Recent? = runCatching { decodeRecent(JSONObject(preferences.getString("recent", null) ?: return null)) }.getOrNull()

    fun history(): List<Recent> {
        val stored = runCatching {
            val data = JSONObject(preferences.getString("history", null) ?: return@runCatching null)
            require(data.getInt("version") == 1)
            val entries = data.getJSONArray("entries")
            require(entries.length() <= 32)
            (0 until entries.length()).mapNotNull { index -> runCatching { decodeRecent(entries.getJSONObject(index)) }.getOrNull() }
                .distinctBy { it.bookId }
        }.getOrNull()
        return Collections.unmodifiableList(ArrayList(stored ?: listOfNotNull(recent())))
    }

    /** Removes this host record and its progress; the caller decides whether to retire a URI grant. */
    fun forget(bookId: String): Recent? {
        val old = history()
        val removed = old.firstOrNull { it.bookId == bookId } ?: return null
        val entries = old.filter { it.bookId != bookId }
        val edit = preferences.edit().remove("snapshot:$bookId").putString("history", encodeHistory(entries).toString())
        if (recent()?.bookId == bookId) {
            if (entries.isEmpty()) edit.remove("recent") else edit.putString("recent", encodeRecent(entries.first()).toString())
        }
        edit.apply()
        return removed
    }

    private fun encodeHistory(entries: List<Recent>) = JSONObject().put("version", 1).put("entries", JSONArray().apply { entries.forEach { put(encodeRecent(it)) } })
    private fun encodeRecent(value: Recent) = JSONObject().put("version", 2).put("book", value.bookId)
        .put("uri", value.uri).put("charset", value.charset).put("sourceType", value.sourceType).put("title", value.title).put("updatedAt", value.updatedAt)
    private fun decodeRecent(data: JSONObject): Recent {
        val version = data.getInt("version")
        require(version == 1 || version == 2)
        val book = data.getString("book")
        val uri = data.optionalString("uri")
        val charset = data.optionalString("charset")
        val type = if (version == 1) "txt" else data.getString("sourceType")
        require(type == "txt" || type == "epub")
        require(book.isNotBlank() && if (type == "txt") (uri == null) == (charset == null) else uri != null && charset == null)
        return Recent(book, uri, charset, type, data.optionalString("title"), data.optLong("updatedAt", 0))
    }

    private fun JSONObject.optionalString(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject.optionalInt(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
}
