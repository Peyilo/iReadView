package org.peyilo.ireadview.epub

import android.content.Context
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.*
import org.peyilo.ireadview.source.EpubPublication
import org.peyilo.ireadview.spi.presentation.*

/** Original XHTML/CSS source for ReaderView. No FragmentActivity or external EPUB engine required. */
class EpubReaderBookSource(
    override val bookId: String,
    private val input: ReaderInput,
    private val cacheDirectory: File
) : PresentationBookSource, BookNavigationSource, AutoCloseable {
    private val mutex = Mutex()
    private val lock = Any()
    private var closed = false
    private var snapshot: EpubSnapshot? = null
    init { require(bookId.isNotBlank()) }

    override suspend fun loadBook(): BookDescriptor = withContext(Dispatchers.IO) {
        mutex.withLock {
            synchronized(lock) { check(!closed) }
            val publication = EpubPublication.open(input, cacheDirectory)
            var installed = false
            try {
                val fresh = EpubSnapshot(bookId, publication)
                currentCoroutineContext().ensureActive()
                val previous = synchronized(lock) {
                    check(!closed)
                    snapshot.also { snapshot = fresh; installed = true }
                }
                previous?.release()
                fresh.book
            } finally { if (!installed) publication.close() }
        }
    }
    internal fun acquire(): EpubSnapshot = synchronized(lock) {
        check(!closed)
        (snapshot ?: error("EPUB is not open")).also { it.retain() }
    }
    override suspend fun loadChapter(chapterId: String): ChapterContent {
        val current = acquire()
        try {
            require(current.book.chapters.any { it.chapterId == chapterId })
            return ChapterContent(bookId, chapterId, current.revision, emptyList())
        } finally { current.release() }
    }
    override suspend fun loadNavigation(): List<BookNavigationEntry> {
        val current = acquire()
        try { return current.navigation() } finally { current.release() }
    }
    override fun createPresentation(context: Context, mode: ReadingMode, host: BookPresentationHost): BookPresentation =
        EpubBookPresentation(context, this, mode, host)
    override fun close() {
        val previous = synchronized(lock) {
            if (closed) return
            closed = true
            snapshot.also { snapshot = null }
        }
        previous?.release()
    }
}

/** Source and prepared/visible bodies lease the same archive through reload and cancellation. */
internal class EpubSnapshot(val bookId: String, val publication: EpubPublication) {
    val revision = publication.revision + ":iread-dom-v1"
    private var references = 1
    private val labels = HashMap<String, String>().apply {
        fun visit(entries: List<EpubPublication.Navigation>) {
            entries.forEach { entry -> entry.path?.let { putIfAbsent(it, entry.title) }; visit(entry.children) }
        }
        visit(publication.navigation)
    }
    val book = BookDescriptor(bookId, publication.title, revision, publication.readingOrder.mapIndexed { index, item ->
        TocEntry.Chapter(ChapterDescriptor("spine-$index", labels[item.path] ?: "${index + 1}", revision))
    })
    @Synchronized fun retain() { check(references > 0); references++ }
    @Synchronized fun release() { check(references > 0); if (--references == 0) publication.close() }
    fun position(index: Int, locator: JSONObject, page: Int? = null): ReadingPosition =
        ReadingPosition(bookId, book.chapters[index].chapterId, contentRevision = revision,
            pageNumberHint = page, chapterNumberHint = index + 1, documentLocator = locator.toString())
    fun locator(index: Int, progression: Double = 0.0, fragment: String? = null): JSONObject = JSONObject()
        .put("format", "iread-epub").put("version", 1).put("href", publication.readingOrder[index].path)
        .put("progression", progression).also { if (fragment != null) it.put("fragment", fragment) }
    fun index(position: ReadingPosition): Int = book.chapters.indexOfFirst { it.chapterId == position.chapterId }
    fun locator(position: ReadingPosition): JSONObject? {
        if (position.bookId != bookId || position.anchor != null) return null
        val index = index(position)
        if (index < 0) return null
        if (position.contentRevision != null && position.contentRevision != revision) return null
        if (position.documentLocator != null) {
            return runCatching { JSONObject(position.documentLocator) }.getOrNull()?.takeIf {
                it.optString("format") == "iread-epub" && it.optInt("version") == 1 &&
                    it.optString("href") == publication.readingOrder[index].path
            }
        }
        return if ((position.pageNumberHint ?: 1) == 1) locator(index) else null
    }
    fun navigation(): List<BookNavigationEntry> {
        fun convert(entries: List<EpubPublication.Navigation>): List<BookNavigationEntry> = entries.map { entry ->
            val index = publication.readingOrder.indexOfFirst { it.path == entry.path }
            BookNavigationEntry(entry.title, if (index >= 0) position(index, locator(index, fragment = entry.fragment)) else null,
                convert(entry.children))
        }
        return convert(publication.navigation).ifEmpty {
            book.chapters.mapIndexed { index, chapter -> BookNavigationEntry(chapter.title, position(index, locator(index))) }
        }
    }
}
