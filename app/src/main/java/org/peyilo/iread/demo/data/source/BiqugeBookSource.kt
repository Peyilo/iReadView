package org.peyilo.iread.demo.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

/** Adapts the reference BiqugeBookLoader to the Widget's BookSource model.
 * Keeps its website, selectors, browser User-Agent, 20-second timeout and paragraph splitting.
 */
internal class BiqugeBookSource(address: String = DEFAULT_URL) : BookSource, AutoCloseable {
    private val catalogue = URI(address).normalize().also {
        require(it.scheme == "https" && it.host == "www.yuyouku.com" && it.port == -1 &&
            it.rawUserInfo == null && it.rawFragment == null && it.rawQuery == null &&
            Regex("/book/[0-9]+/?").matches(it.path)) { "请输入 https://www.yuyouku.com/book/书籍编号/" }
    }
    override val bookId = "biquge-${catalogue.path.trimEnd('/').substringAfterLast('/')}"
    private data class Chapter(val descriptor: ChapterDescriptor, val uri: URI)
    private data class Snapshot(val book: BookDescriptor, val chapters: Map<String, Chapter>)
    private val metadata = Mutex()
    private val lifecycle = Any()
    @Volatile private var snapshot: Snapshot? = null
    @Volatile private var closed = false

    override suspend fun loadBook(): BookDescriptor = withContext(Dispatchers.IO) {
        metadata.withLock {
            check(!closed) { "网络来源已关闭" }
            val document = fetch(catalogue)
            val title = document.select("article h1").first()?.ownText()?.trim()
                ?.takeIf { it.isNotBlank() } ?: throw IOException("未找到书名，网页结构可能已经变化")
            val chapters = LinkedHashMap<String, Chapter>()
            for (link in document.select("#chapters-list li a")) {
                currentCoroutineContext().ensureActive()
                val uri = URI(BASE_URL).resolve(link.attr("href")).normalize()
                require(uri.scheme == "https" && uri.host == catalogue.host && uri.rawUserInfo == null) { "章节地址不属于当前书源" }
                val id = "chapter-${hash(uri.toASCIIString())}"
                chapters[id] = Chapter(ChapterDescriptor(id, link.text()), uri)
            }
            if (chapters.isEmpty()) throw IOException("未找到章节目录，网页结构可能已经变化")
            val revision = hash(buildString {
                append(title)
                chapters.values.forEach { append('\u0000'); append(it.descriptor.chapterId); append('\u0000'); append(it.descriptor.title) }
            })
            val book = BookDescriptor(bookId, title, revision, chapters.values.map { TocEntry.Chapter(it.descriptor) })
            currentCoroutineContext().ensureActive()
            synchronized(lifecycle) { check(!closed); snapshot = Snapshot(book, chapters.toMap()) }
            book
        }
    }

    override suspend fun loadChapter(chapterId: String): ChapterContent = withContext(Dispatchers.IO) {
        check(!closed) { "网络来源已关闭" }
        val chapter = snapshot?.chapters?.get(chapterId) ?: throw IOException("目录未加载或章节不存在")
        val document = fetch(chapter.uri)
        val content = document.select("#txtContent")
        if (content.isEmpty()) throw IOException("未找到章节正文")
        content.select("div.gad2").remove()
        content.select("#txtContent p").unwrap()
        val paragraphs = content.html().split(Regex("(?i)<br\\s*/?>"))
            .map { Jsoup.parseBodyFragment(it.trim()).text() }.filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) throw IOException("章节正文为空")
        val blocks = ArrayList<ContentBlock>()
        blocks.add(HeadingBlock("title", chapter.descriptor.title))
        paragraphs.forEachIndexed { index, paragraph ->
            currentCoroutineContext().ensureActive()
            blocks.add(TextBlock("paragraph-${index + 1}", paragraph))
        }
        val revision = hash(chapter.descriptor.title + "\u0000" + paragraphs.joinToString("\u0000"))
        currentCoroutineContext().ensureActive()
        check(!closed) { "网络来源已关闭" }
        ChapterContent(bookId, chapterId, revision, blocks)
    }

    private suspend fun fetch(uri: URI): Document {
        currentCoroutineContext().ensureActive()
        check(!closed) { "网络来源已关闭" }
        val document = Jsoup.connect(uri.toASCIIString()).userAgent(UA).timeout(20_000).get()
        currentCoroutineContext().ensureActive()
        check(!closed) { "网络来源已关闭" }
        return document
    }

    override fun close() { synchronized(lifecycle) { closed = true; snapshot = null } }

    companion object {
        private const val BASE_URL = "https://www.yuyouku.com/"
        const val DEFAULT_URL = "https://www.yuyouku.com/book/185030/"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari"
        private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
