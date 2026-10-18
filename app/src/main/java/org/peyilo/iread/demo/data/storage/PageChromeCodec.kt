package org.peyilo.iread.demo.data.storage

import org.json.JSONArray
import org.json.JSONObject
import org.peyilo.iread.demo.reader.PageChromePosition
import org.peyilo.iread.demo.reader.PageChromeSettings
import org.peyilo.ireadview.api.style.ChromeContent

/** Demo-only template schema. No migration from the old single-content style options. */
internal object PageChromeCodec {
    fun encode(settings: PageChromeSettings): String = JSONObject().put("version", 1)
        .put("positions", JSONArray(settings.contents.map { it.name }))
        .put("backButton", settings.showBackButton).toString()

    fun decode(raw: String?): PageChromeSettings? = runCatching {
        if(raw == null || raw.length > 4096) return null
        val data = JSONObject(raw)
        require(data.getInt("version") == 1)
        val positions = data.getJSONArray("positions")
        require(positions.length() == PageChromePosition.entries.size)
        PageChromeSettings(List(positions.length()) { ChromeContent.valueOf(positions.getString(it)) }, data.getBoolean("backButton"))
    }.getOrNull()
}
