package org.peyilo.ireadview.source

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.source.TextContentIdProvider
import org.peyilo.ireadview.api.source.TextNormalization
import org.peyilo.ireadview.internal.source.TextSourceSnapshot
import org.peyilo.ireadview.internal.source.DiskTextSnapshot
import org.peyilo.ireadview.internal.source.TextSnapshotParser

data class TextBookOptions(
    val title: String = "Text book",
    val charset: Charset = Charsets.UTF_8,
    /** Full trimmed line match. Null disables chapter detection. */
    val chapterHeading: Regex? = Regex("(?:第[零〇一二三四五六七八九十百千万两0-9]+[章回节卷部篇].*|Chapter\\s+[0-9IVXLCDM]+(?:\\s.*)?)", RegexOption.IGNORE_CASE),
    val preambleTitle: String = "Introduction",
    val maxInputBytes: Int = 32 * 1024 * 1024,
    /** Null uses memory; otherwise private records live in a managed child of this host directory.
     * Opening a disk snapshot also tries to reclaim unlocked records older than 24 hours.
     */
    val indexDirectory: File? = null,
    val maxIndexBytes: Long = 128L * 1024 * 1024,
    /** Null retains chapter-N / line-N identities. Resolved IDs are frozen in each snapshot. */
    val contentIdProvider: TextContentIdProvider? = null,
    val normalization: TextNormalization = TextNormalization.PRESERVE,
    /** Opt-in cross-process reuse. Input bytes are still read and verified on every loadBook. */
    val reuseDiskIndex: Boolean = false,
    /** Total retained index files in the managed cache; private active snapshots are separate. */
    val persistentIndexCacheBytes: Long = 256L * 1024 * 1024
) {
    init {
        require(maxInputBytes > 0); require(maxIndexBytes > 0); require(persistentIndexCacheBytes > 0)
        require(!reuseDiskIndex || indexDirectory != null) { "Disk index reuse requires indexDirectory" }
    }
}

/** Immutable memory or disk snapshots. loadBook rereads; the creator closes this source. */
class TextBookSource @JvmOverloads constructor(
    override val bookId: String,
    private val input: ReaderInput,
    private val options: TextBookOptions = TextBookOptions()
) : BookSource, AutoCloseable {
    private data class ParsedChapter(val id: String, val title: String, val blocks: List<ContentBlock>)
    private data class Snapshot(override val book: BookDescriptor, val chapters: Map<String, ChapterContent>) : TextSourceSnapshot {
        override suspend fun chapter(chapterId: String) = chapters[chapterId] ?: throw NoSuchElementException("Unknown chapter: $chapterId")
        override fun close() {}
    }
    private val mutex = Mutex()
    private val lifecycle = Any()
    private var closed = false
    private var snapshot: TextSourceSnapshot? = null

    init { require(bookId.isNotBlank()) }

    override suspend fun loadBook(): BookDescriptor = withContext(Dispatchers.IO) {
        mutex.withLock {
            val fresh = parseSnapshot()
            install(fresh)
            fresh.book
        }
    }

    override suspend fun loadChapter(chapterId: String): ChapterContent = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = synchronized(lifecycle) { checkOpen(); snapshot } ?: parseSnapshot().also { install(it) }
            current.chapter(chapterId)
        }
    }

    /** Permanent close. In-flight disk readers retain a lease until their stream closes. */
    override fun close() {
        val old = synchronized(lifecycle) { if (closed) return; closed = true; snapshot.also { snapshot = null } }
        old?.close()
    }
    private fun checkOpen() { check(!closed) { "TextBookSource is closed" } }
    private suspend fun install(fresh: TextSourceSnapshot) {
        try {
            currentCoroutineContext().ensureActive()
            val old = synchronized(lifecycle) { checkOpen(); snapshot.also { snapshot = fresh } }
            old?.close()
        } catch (failure: Throwable) { fresh.close(); throw failure }
    }
    private suspend fun parseSnapshot(): TextSourceSnapshot {
        val coroutine = currentCoroutineContext()
        val checkActive = { coroutine.ensureActive(); synchronized(lifecycle) { checkOpen() } }
        checkActive()
        return if (options.indexDirectory != null) DiskTextSnapshot.build(bookId, input, options, checkActive)
        else parseMemory(checkActive)
    }

    private suspend fun parseMemory(checkActive: () -> Unit): Snapshot {
        val output = ByteArrayOutputStream()
        val digest = MessageDigest.getInstance("SHA-256")
        input.openStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                checkActive()
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count > options.maxInputBytes - output.size()) throw IOException("Text input exceeds ${options.maxInputBytes} bytes")
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
            }
        }
        checkActive()
        val decoded = options.charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
        val parsedChapters = java.util.ArrayDeque<ParsedChapter>()
        val chapters = LinkedHashMap<String, ChapterContent>()
        val catalogue = ArrayList<TocEntry>()
        var blocks = ArrayList<ContentBlock>()
        val parsedDigest = TextSnapshotParser.parse(StringReader(decoded), bookId, options, checkActive, onBlock = { id, text, heading ->
            blocks.add(if (heading) HeadingBlock(id, text) else TextBlock(id, text))
        }, onChapter = { id, title, _ ->
            parsedChapters.addLast(ParsedChapter(id, title, blocks))
            blocks = ArrayList()
        })
        val revision = TextSnapshotParser.revision(digest.digest(), parsedDigest)
        while (parsedChapters.isNotEmpty()) {
            checkActive()
            val chapter = parsedChapters.removeFirst()
            chapters[chapter.id] = ChapterContent(bookId, chapter.id, revision, chapter.blocks)
            catalogue.add(TocEntry.Chapter(ChapterDescriptor(chapter.id, chapter.title, revision)))
        }
        checkActive()
        return Snapshot(BookDescriptor(bookId, options.title, revision, catalogue), chapters)
    }
}
