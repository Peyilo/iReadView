package org.peyilo.iread.demo.data.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.peyilo.ireadview.api.model.*

/** Unified versioned records. Codecs own type payloads; storage owns the book transaction. */
internal class AnnotationStorage(context: Context) {
    private val preferences = context.getSharedPreferences("reader-annotations", Context.MODE_PRIVATE)
    private fun read(book: String): JSONObject? = runCatching {
        preferences.getString(book, null)?.let { JSONObject(it) }?.takeIf { it.optString("book") == book }
    }.getOrNull()
    fun save(snapshot: AnnotationSnapshot) {
        val previous = read(snapshot.bookId)
        if (previous != null && previous.optInt("version") !in 1..2) return
        val records = JSONArray()
        snapshot.items.forEach { records.put(AnnotationCodecs.encode(it)) }
        // Keep unknown/future record schemas during writes by this older Demo version.
        previous?.takeIf { it.optInt("version") == 2 }?.optJSONArray("items")?.let { old ->
            for (i in 0 until old.length()) {
                val record = old.optJSONObject(i) ?: continue
                if (runCatching { AnnotationCodecs.decode(snapshot.bookId, record) }.getOrNull() == null) records.put(record)
            }
        }
        val data = JSONObject().put("version", 2).put("book", snapshot.bookId).put("items", records)
        preferences.edit().putString(snapshot.bookId, data.toString()).apply()
    }
    fun load(bookId: String): AnnotationSnapshot? = runCatching {
        val data = read(bookId) ?: return null
        val items = ArrayList<ReaderAnnotation>()
        when (data.getInt("version")) {
            1 -> listOf("bookmarks" to "bookmark", "highlights" to "highlight", "underlines" to "underline").forEach { (key, type) ->
                val records = data.optJSONArray(key) ?: JSONArray()
                for (i in 0 until records.length()) items.add(AnnotationCodecs.legacy(bookId, type, records.getJSONObject(i)))
            }
            2 -> {
                val records = data.getJSONArray("items")
                for (i in 0 until records.length()) {
                    val record = records.optJSONObject(i) ?: continue
                    runCatching { AnnotationCodecs.decode(bookId, record) }.getOrNull()?.let(items::add)
                }
            }
            else -> return null
        }
        AnnotationSnapshot.fromItems(bookId, items)
    }.getOrNull()
}
