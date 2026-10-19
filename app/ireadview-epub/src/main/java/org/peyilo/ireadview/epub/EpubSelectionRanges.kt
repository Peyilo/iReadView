package org.peyilo.ireadview.epub

import org.json.JSONObject
import org.peyilo.ireadview.api.model.DocumentTextSelection

/** Canonical publication text paths, unaffected by transient decoration spans. */
internal object EpubSelectionRanges {
    private fun decode(value: DocumentTextSelection): JSONObject? = runCatching {
        JSONObject(value.documentLocator).takeIf { it.optString("format") == "iread-epub-selection" && it.optInt("version") == 1 }
    }.getOrNull()
    fun href(value: DocumentTextSelection): String? = decode(value)?.optString("href")
    fun locator(value: DocumentTextSelection): JSONObject? = runCatching {
        val data = decode(value) ?: return null
        JSONObject().put("format", "iread-epub").put("version", 1).put("href", data.getString("href"))
            .put("text", data.getJSONObject("range").getJSONObject("start"))
    }.getOrNull()
    private fun compare(a: JSONObject, b: JSONObject): Int {
        val left = a.getJSONArray("path"); val right = b.getJSONArray("path")
        require(left.length() in 1..256 && right.length() in 1..256 && a.getInt("offset") >= 0 && b.getInt("offset") >= 0)
        for (index in 0 until minOf(left.length(), right.length())) {
            val x = left.getInt(index); val y = right.getInt(index)
            require(x >= 0 && y >= 0)
            if (x != y) return x.compareTo(y)
        }
        return if (left.length() != right.length()) left.length().compareTo(right.length()) else a.getInt("offset").compareTo(b.getInt("offset"))
    }
    fun covers(outer: DocumentTextSelection, inner: DocumentTextSelection): Boolean = runCatching {
        if (outer.bookId != inner.bookId || outer.chapterId != inner.chapterId || outer.contentRevision != inner.contentRevision) return false
        val a = decode(outer) ?: return false; val b = decode(inner) ?: return false
        if (a.getString("href") != b.getString("href")) return false
        val x = a.getJSONObject("range"); val y = b.getJSONObject("range")
        compare(x.getJSONObject("start"), y.getJSONObject("start")) <= 0 && compare(x.getJSONObject("end"), y.getJSONObject("end")) >= 0
    }.getOrDefault(false)
}
