package org.peyilo.ireadview.source

import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.peyilo.ireadview.api.model.BookDescriptor
import org.peyilo.ireadview.api.model.ChapterContent
import org.peyilo.ireadview.api.model.ChapterDescriptor
import org.peyilo.ireadview.api.model.TocEntry
import org.peyilo.ireadview.api.model.ReadingPosition
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.internal.source.*

/** Budgets bound input and parser work, not total JVM heap usage. */
data class EpubTextBookOptions(
    val fallbackTitle: String = "EPUB book",
    val maxArchiveBytes: Long = 64L * 1024 * 1024,
    val maxEntryBytes: Long = 32L * 1024 * 1024,
    val maxExpandedBytes: Long = 256L * 1024 * 1024,
    val maxEntries: Int = 10000,
    val maxXmlDepth: Int = 64,
    val maxXmlElements: Int = 100000,
    val maxXmlCharacters: Int = 32 * 1024 * 1024,
    val maxFallbackDepth: Int = 64
) {
    init {
        require(maxArchiveBytes > 0 && maxEntryBytes > 0 && maxExpandedBytes > 0 && maxEntries > 0)
        require(maxXmlDepth > 0 && maxXmlElements > 0 && maxXmlCharacters > 0 && maxFallbackDepth > 0)
    }
}

data class EpubDiagnostic(val code: String, val resourcePath: String, val message: String)

/** Navigation metadata; resolveNavigation maps current entries into Widget reading positions. */
class EpubTocEntry internal constructor(
    val title: String,
    val resourcePath: String?,
    val fragment: String?,
    children: List<EpubTocEntry>
) {
    val children: List<EpubTocEntry> = Collections.unmodifiableList(ArrayList(children))
}

/**
 * Text extraction from EPUB 2/3. The host supplies a private cache directory and closes the source.
 * loadBook rereads metadata atomically; chapters are extracted on demand, without an internal text cache.
 */
class EpubTextBookSource @JvmOverloads constructor(
    override val bookId: String,
    private val input: ReaderInput,
    private val cacheDirectory: File,
    private val options: EpubTextBookOptions = EpubTextBookOptions()
) : BookSource, AutoCloseable {
    private class Snapshot(
        val archive: EpubArchive,
        val book: BookDescriptor,
        val paths: Map<String, String>,
        val navigation: List<EpubTocEntry>,
        initialDiagnostics: List<EpubDiagnostic>
    ) {
        val diagnostics = LinkedHashMap<Pair<String, String>, EpubDiagnostic>().apply {
            initialDiagnostics.forEach { put(it.code to it.resourcePath, it) }
        }
    }
    private val mutex = Mutex()
    private val lifecycle = Any()
    private val xmlLimits = EpubXmlLimits(options.maxXmlDepth, options.maxXmlElements, options.maxXmlCharacters, options.maxFallbackDepth)
    private var closed = false
    private var snapshot: Snapshot? = null

    init { require(bookId.isNotBlank()) }

    /** Snapshot copy of metadata and successfully loaded chapter diagnostics; empty before opening/after close. */
    val diagnostics: List<EpubDiagnostic> get() = synchronized(lifecycle) {
        Collections.unmodifiableList(ArrayList(snapshot?.diagnostics?.values ?: emptyList()))
    }
    /** Original nav/NCX tree, separate from the BookDescriptor catalogue in spine order. */
    val navigation: List<EpubTocEntry> get() = synchronized(lifecycle) { snapshot?.navigation ?: emptyList() }

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
            val path = current.paths[chapterId] ?: throw NoSuchElementException("Unknown chapter: $chapterId")
            val document = readDocument(current, path)
            ChapterContent(bookId, chapterId, current.book.revision, document.blocks)
        }
    }

    /**
     * Null means a stale entry, non-spine resource, or missing/omitted fragment; no chapter-start fallback.
     * XML and archive errors propagate. Does not navigate the Widget or retain chapter text.
     */
    suspend fun resolveNavigation(entry: EpubTocEntry): ReadingPosition? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = synchronized(lifecycle) { checkOpen(); snapshot } ?: return@withLock null
            fun belongs(entries: List<EpubTocEntry>): Boolean = entries.any { it === entry || belongs(it.children) }
            if (!belongs(current.navigation)) return@withLock null
            val path = entry.resourcePath ?: return@withLock null
            val chapterId = current.paths.entries.firstOrNull { it.value == path }?.key ?: return@withLock null
            val fragment = entry.fragment?.takeIf { it.isNotEmpty() }
            val anchor = if (fragment != null) {
                readDocument(current, path).anchors[fragment] ?: return@withLock null
            } else null
            currentCoroutineContext().ensureActive()
            synchronized(lifecycle) { checkOpen(); check(snapshot === current) }
            ReadingPosition(bookId, chapterId, anchor, current.book.revision, chapterNumberHint = current.book.chapters.indexOfFirst { it.chapterId == chapterId } + 1)
        }
    }

    private suspend fun readDocument(current: Snapshot, path: String): EpubTextDocument {
        val document = EpubXhtmlParser.load(current.archive, path, xmlLimits)
        currentCoroutineContext().ensureActive()
        synchronized(lifecycle) {
            checkOpen()
            check(snapshot === current) { "EPUB snapshot was replaced" }
            document.diagnostics.forEach { diagnostic ->
                val public = diagnostic.toPublic()
                current.diagnostics[public.code to public.resourcePath] = public
            }
        }
        return document
    }

    override fun close() {
        val old = synchronized(lifecycle) {
            if (closed) return
            closed = true
            snapshot.also { snapshot = null }
        }
        old?.archive?.close()
    }

    private fun checkOpen() { check(!closed) { "EpubTextBookSource is closed" } }

    private suspend fun install(fresh: Snapshot) {
        try {
            currentCoroutineContext().ensureActive()
            val old = synchronized(lifecycle) { checkOpen(); snapshot.also { snapshot = fresh } }
            old?.archive?.close()
        } catch (failure: Throwable) { fresh.archive.close(); throw failure }
    }

    private suspend fun parseSnapshot(): Snapshot {
        synchronized(lifecycle) { checkOpen() }
        val archive = EpubArchive.open(input, cacheDirectory, EpubArchiveLimits(
            options.maxArchiveBytes, options.maxEntryBytes, options.maxExpandedBytes, options.maxEntries))
        try {
            synchronized(lifecycle) { checkOpen() }
            val metadata = EpubPackageParser.load(archive, xmlLimits)
            val navigation = EpubNavigationParser.load(archive, metadata, xmlLimits)
            val labels = navigation.chapterLabels()
            val paths = LinkedHashMap<String, String>()
            val catalogue = metadata.readingOrder.map { item ->
                paths[item.id] = item.path
                TocEntry.Chapter(ChapterDescriptor(item.id, labels[item.path] ?: item.id, archive.revision))
            }
            fun convert(entries: List<EpubNavigationEntry>): List<EpubTocEntry> = entries.map {
                EpubTocEntry(it.label, it.resource?.path, it.resource?.fragment, convert(it.children))
            }
            val diagnostics = (metadata.diagnostics + navigation.diagnostics).map { it.toPublic() } +
                EpubDiagnostic("CatalogueUsesSpineOrder", metadata.path, "The Widget catalogue follows spine order; original navigation groups and fragments are available separately")
            currentCoroutineContext().ensureActive()
            synchronized(lifecycle) { checkOpen() }
            return Snapshot(archive, BookDescriptor(bookId, metadata.title ?: options.fallbackTitle, archive.revision, catalogue),
                Collections.unmodifiableMap(paths), Collections.unmodifiableList(convert(navigation.entries)), diagnostics)
        } catch (failure: Throwable) { archive.close(); throw failure }
    }

    private fun EpubPackageDiagnostic.toPublic() = EpubDiagnostic(code, path, message)
}
