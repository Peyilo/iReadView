package org.peyilo.iread.demo

import android.os.Bundle
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.api.model.ReaderSnapshot
import org.peyilo.ireadview.api.model.ReadingPosition

/** Host-owned Bundle format shared by isolated demos; factories and source objects are not saved. */
internal object DemoSnapshotState {
    fun read(data: Bundle?): ReaderSnapshot? = data?.let { runCatching {
        require(it.getInt("version") == 1)
        val book = requireNotNull(it.getString("book"))
        ReaderSnapshot(book, ReadingPosition(book, requireNotNull(it.getString("chapter")),
            it.getString("block")?.let { block -> ContentAnchor(block, it.getInt("offset")) }, it.getString("revision"),
            if (it.containsKey("page")) it.getInt("page") else null,
            if (it.containsKey("chapterNumber")) it.getInt("chapterNumber") else null),
            requireNotNull(it.getString("mode")), builtInEffectId = it.getString("effect"))
    }.getOrNull() }

    fun write(saved: ReaderSnapshot) = Bundle().apply {
        putInt("version", 1); putString("book", saved.bookId); putString("mode", saved.modeId); putString("effect", saved.builtInEffectId)
        putString("chapter", saved.position.chapterId); putString("revision", saved.position.contentRevision)
        saved.position.anchor?.let { putString("block", it.blockId); putInt("offset", it.offset) }
        saved.position.pageNumberHint?.let { putInt("page", it) }
        saved.position.chapterNumberHint?.let { putInt("chapterNumber", it) }
    }
}
