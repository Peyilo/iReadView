package org.peyilo.ireadview.api.model

import java.util.Collections

/** Host-owned identity and catalogue. Collections are copied at construction. */
class BookDescriptor(
    val bookId: String,
    val title: String,
    val revision: String? = null,
    tableOfContents: List<TocEntry>
) {
    val tableOfContents: List<TocEntry> = immutableList(tableOfContents)
    val chapters: List<ChapterDescriptor>
    private val chaptersById: Map<String, ChapterDescriptor>

    init {
        require(bookId.isNotBlank()) { "bookId must be stable and non-blank" }
        val flattened = ArrayList<ChapterDescriptor>()
        fun visit(entries: List<TocEntry>, depth: Int) {
            require(depth <= 32) { "Catalogue nesting exceeds 32 levels" }
            entries.forEach { entry ->
                when (entry) {
                    is TocEntry.Chapter -> flattened.add(entry.descriptor)
                    is TocEntry.Group -> visit(entry.children, depth + 1)
                }
            }
        }
        visit(this.tableOfContents, 0)
        chaptersById = flattened.associateBy { it.chapterId }
        require(chaptersById.size == flattened.size) { "Duplicate chapterId" }
        chapters = immutableList(flattened)
    }
    internal fun chapterForId(chapterId: String): ChapterDescriptor? = chaptersById[chapterId]
}

sealed interface TocEntry {
    data class Chapter(val descriptor: ChapterDescriptor) : TocEntry
    class Group(val id: String, val title: String, children: List<TocEntry>) : TocEntry {
        val children: List<TocEntry> = immutableList(children)
        init { require(id.isNotBlank()) }
    }
}

data class ChapterDescriptor(val chapterId: String, val title: String, val revision: String? = null) {
    init { require(chapterId.isNotBlank()) }
}

/** Blocks must be immutable. Non-text custom blocks need a registered layout provider. */
interface ContentBlock {
    val blockId: String
    /** Stable estimated bytes retained by this immutable block, including its payload and ID.
     * null means unknown; chapters containing an unknown block are read without cache admission.
     * Queried on workers during content/layout preparation; never access Views in this getter.
     */
    val estimatedRetainedBytes: Long? get() = null
}

/** Immutable selectable source text, including text blocks carrying host metadata.
 * Fragment offsets and accessible source ranges refer to this unchanged UTF-16 string.
 * Custom providers take precedence; otherwise the built-in provider uses body styling.
 * Custom rendering metadata is not fingerprinted: change the chapter revision when it changes.
 * A description of a graphic alone should use ContentBlock instead.
 */
interface TextContentBlock : ContentBlock {
    val text: String
}

data class TextBlock(override val blockId: String, override val text: String) : TextContentBlock {
    override val estimatedRetainedBytes: Long get() = 224L + blockId.length.toLong() * 2 + text.length.toLong() * 2
    init { require(blockId.isNotBlank()) }
}

data class HeadingBlock(override val blockId: String, override val text: String, val level: Int = 1) : TextContentBlock {
    override val estimatedRetainedBytes: Long get() = 224L + blockId.length.toLong() * 2 + text.length.toLong() * 2
    init { require(blockId.isNotBlank()); require(level in 1..6) }
}

class ChapterContent(
    val bookId: String,
    val chapterId: String,
    val revision: String? = null,
    blocks: List<ContentBlock>
) {
    val blocks: List<ContentBlock> = immutableList(blocks)
    private val blockIndices = HashMap<String, Int>()
    internal fun blockIndexForId(blockId: String): Int = blockIndices[blockId] ?: -1
    internal fun blockForId(blockId: String): ContentBlock? = blockIndices[blockId]?.let { blocks[it] }
    private val fingerprintLock = Any()
    private var fingerprintReady = false
    private var fingerprintValue: String? = null
    /** Prepared by the repository on a worker before publishing the immutable snapshot. */
    internal val contentFingerprint: String? get() = prepareContentFingerprint()
    internal fun prepareContentFingerprint(checkActive: () -> Unit = {}): String? = synchronized(fingerprintLock) {
        checkActive()
        if (fingerprintReady) return@synchronized fingerprintValue
        val value = calculateContentFingerprint(checkActive)
        checkActive()
        fingerprintValue = value
        fingerprintReady = true
        value
    }
    private fun calculateContentFingerprint(checkActive: () -> Unit): String? {
        if (this.blocks.any { checkActive(); it !is TextBlock && it !is HeadingBlock }) return null
        else {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(4096)
            fun number(value: Int) { for (shift in 24 downTo 0 step 8) digest.update((value ushr shift).toByte()) }
            fun text(value: String) {
                checkActive()
                number(value.length)
                var length = 0
                for (character in value) {
                    buffer[length++] = (character.code ushr 8).toByte()
                    buffer[length++] = character.code.toByte()
                    if (length == buffer.size) { checkActive(); digest.update(buffer); length = 0 }
                }
                if (length > 0) digest.update(buffer, 0, length)
            }
            number(this.blocks.size)
            this.blocks.forEach { block ->
                checkActive()
                text(block.blockId)
                when (block) {
                    is TextBlock -> { number(0); text(block.text) }
                    is HeadingBlock -> { number(block.level); text(block.text) }
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }
    }
    init {
        require(bookId.isNotBlank() && chapterId.isNotBlank())
        this.blocks.forEachIndexed { index, block ->
            require(block.blockId.isNotBlank())
            require(blockIndices.put(block.blockId, index) == null) { "Duplicate blockId" }
        }
    }
}

internal fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
