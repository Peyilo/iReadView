package org.peyilo.ireadview.api.model

data class ContentAnchor(val blockId: String, val offset: Int) {
    init { require(blockId.isNotBlank()); require(offset >= 0) }
}

/** Page numbers are hints; the content anchor survives layout changes. */
data class ReadingPosition(
    val bookId: String,
    val chapterId: String,
    val anchor: ContentAnchor? = null,
    val contentRevision: String? = null,
    val pageNumberHint: Int? = null,
    val chapterNumberHint: Int? = null
) {
    init {
        require(bookId.isNotBlank() && chapterId.isNotBlank())
        require(pageNumberHint == null || pageNumberHint > 0)
        require(chapterNumberHint == null || chapterNumberHint > 0)
    }
}

data class ReaderSnapshot(
    val bookId: String,
    val position: ReadingPosition,
    val modeId: String = "paged",
    val formatVersion: Int = 1,
    /** Null preserves the host's installed effect; custom factories are never serialized. */
    val builtInEffectId: String? = null
) {
    init {
        require(bookId == position.bookId); require(formatVersion == 1); require(modeId.isNotBlank())
        require(builtInEffectId == null || builtInEffectId.isNotBlank())
    }
}
