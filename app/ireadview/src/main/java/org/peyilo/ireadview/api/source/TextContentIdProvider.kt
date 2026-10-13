package org.peyilo.ireadview.api.source

/** Chapter numbers are one-based and include a preamble chapter when present. */
data class TextChapterIdentity(val bookId: String, val chapterNumber: Int, val title: String)

/** Text is the normalized original line, including its whitespace; block numbers are one-based. */
data class TextBlockIdentity(
    val bookId: String,
    val chapterNumber: Int,
    val blockNumber: Int,
    val text: String,
    val isHeading: Boolean
)

/**
 * Optional TXT identity policy, called on IO during snapshot construction.
 * Return deterministic, nonblank, well-formed Unicode IDs; chapter IDs must be unique in the book and block IDs
 * within their chapter. Do not access Views or depend on invocation order, mutable counters,
 * or reading the same chapter again. A provider shared by sources must be thread-safe.
 * A chapter callback runs after its block callbacks, once its final title is known.
 * Exceptions abort the new snapshot and leave the previous successful snapshot available.
 * Stable IDs do not bypass revision or quote validation of reading positions and annotations.
 */
interface TextContentIdProvider {
    /** Stable, nonblank identity for every setting affecting generated IDs, including algorithm
     * version. Null disables persistent reuse for this policy. Change it whenever outputs may
     * change; on a cache hit the callbacks are not invoked. Never use object identity here.
     */
    val persistentIndexKey: String? get() = null
    fun chapterId(context: TextChapterIdentity): String = "chapter-${context.chapterNumber}"
    fun blockId(context: TextBlockIdentity): String = "line-${context.blockNumber}"
}

/** Java hosts may override only the identity callbacks or cache key they need. */
abstract class TextContentIdProviderAdapter : TextContentIdProvider
