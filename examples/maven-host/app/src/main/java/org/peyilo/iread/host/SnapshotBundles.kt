package org.peyilo.iread.host

import android.os.Bundle
import android.util.Log
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.api.model.ReaderSnapshot
import org.peyilo.ireadview.api.model.ReadingPosition

/** Host-owned saved-state format: only lightweight identity and position, never source objects. */
object SnapshotBundles {
    @JvmStatic fun read(data: Bundle?): ReaderSnapshot? {
        if (data == null) return null
        return try {
            require(data.getInt("schema", -1) == 1)
            require(data.getInt("format", -1) == 1)
            val book = requireNotNull(data.getString("book"))
            val block = data.getString("block")
            require((block != null) == data.containsKey("offset"))
            val anchor = block?.let { ContentAnchor(it, data.getInt("offset", -1)) }
            val position = ReadingPosition(book, requireNotNull(data.getString("chapter")), anchor,
                data.getString("revision"),
                if (data.containsKey("page")) data.getInt("page", -1) else null,
                if (data.containsKey("chapterNumber")) data.getInt("chapterNumber", -1) else null)
            ReaderSnapshot(book, position, requireNotNull(data.getString("mode")),
                builtInEffectId = data.getString("effect"))
        } catch (error: RuntimeException) {
            Log.w("iReadHost", "Ignoring invalid saved reader snapshot", error)
            null
        }
    }

    @JvmStatic fun write(snapshot: ReaderSnapshot): Bundle = Bundle().apply {
        putInt("schema", 1); putInt("format", snapshot.formatVersion)
        putString("book", snapshot.bookId); putString("chapter", snapshot.position.chapterId)
        putString("revision", snapshot.position.contentRevision)
        putString("mode", snapshot.modeId); putString("effect", snapshot.builtInEffectId)
        snapshot.position.anchor?.let { putString("block", it.blockId); putInt("offset", it.offset) }
        snapshot.position.pageNumberHint?.let { putInt("page", it) }
        snapshot.position.chapterNumberHint?.let { putInt("chapterNumber", it) }
    }
}
