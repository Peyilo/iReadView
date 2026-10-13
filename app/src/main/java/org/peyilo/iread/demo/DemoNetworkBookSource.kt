package org.peyilo.iread.demo

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Demo protocol: HTTPS catalogue JSON and same-origin UTF-8 chapter text. No Widget networking. */
internal class DemoNetworkBookSource(catalogueUrl: String) : BookSource, AutoCloseable {
    private val catalogue = checkedUri(URI(catalogueUrl.also { require(it.length <= 2048) { "目录地址过长" } }).normalize())
    override val bookId = "network-${digest(catalogue.toASCIIString().toByteArray(Charsets.UTF_8))}"
    private data class Chapter(val descriptor: ChapterDescriptor, val uri: URI)
    private data class Snapshot(val book: BookDescriptor, val chapters: Map<String, Chapter>)
    private val metadata = Mutex()
    private val connectionLock = Any()
    private val connections = LinkedHashSet<HttpURLConnection>()
    @Volatile private var closed = false
    @Volatile private var snapshot: Snapshot? = null

    override suspend fun loadBook(): BookDescriptor = withContext(Dispatchers.IO) {
        metadata.withLock {
            check(!closed) { "网络来源已关闭" }
            val bytes = read(catalogue, 256 * 1024)
            val json = decode(bytes).removePrefix("\uFEFF")
            checkCatalogueStructure(json)
            val data = JSONObject(json)
            require(data.get("version") == 1) { "不支持的网络目录版本" }
            val title = data.string("title").also { require(it.isNotBlank() && it.length <= 256) { "书名无效" } }
            val entries = data.getJSONArray("chapters").also { require(it.length() <= 1000) { "目录超过1000章" } }
            val chapters = LinkedHashMap<String, Chapter>()
            for (index in 0 until entries.length()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.getJSONObject(index)
                val id = entry.string("id").also { require(it.isNotBlank() && it.length <= 128 && !chapters.containsKey(it)) { "章节身份无效或重复" } }
                val name = entry.string("title").also { require(it.length <= 256) { "章节标题过长" } }
                val path = entry.string("url").also { require(it.length <= 2048) { "章节地址过长" } }
                val uri = checkedUri(catalogue.resolve(path).normalize())
                require(uri.host.equals(catalogue.host, ignoreCase = true) && port(uri) == port(catalogue)) { "章节必须与目录同源" }
                chapters[id] = Chapter(ChapterDescriptor(id, name), uri)
            }
            val book = BookDescriptor(bookId, title, digest(bytes), chapters.values.map { TocEntry.Chapter(it.descriptor) })
            currentCoroutineContext().ensureActive()
            synchronized(connectionLock) {
                check(!closed) { "网络来源已关闭" }
                snapshot = Snapshot(book, chapters.toMap())
            }
            book
        }
    }

    override suspend fun loadChapter(chapterId: String): ChapterContent = withContext(Dispatchers.IO) {
        check(!closed) { "网络来源已关闭" }
        val chapter = snapshot?.chapters?.get(chapterId) ?: error("目录未加载或章节不存在")
        val bytes = read(chapter.uri, 4 * 1024 * 1024)
        val text = decode(bytes).removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = ArrayList<ContentBlock>()
        blocks.add(HeadingBlock("title", chapter.descriptor.title))
        text.splitToSequence('\n').forEachIndexed { index, line ->
            currentCoroutineContext().ensureActive()
            require(index < 100000) { "章节超过100000行" }
            blocks.add(TextBlock("line-$index", line))
        }
        val revision = chapterRevision(chapter.descriptor.title, bytes)
        currentCoroutineContext().ensureActive()
        check(!closed) { "网络来源已关闭" }
        ChapterContent(bookId, chapterId, revision, blocks)
    }

    /** Includes the catalogue-supplied heading, which is part of the emitted chapter content. */
    private suspend fun chapterRevision(title: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("iRead:demo-network-chapter:v2\u0000".toByteArray(Charsets.UTF_8))
        // Preserve exact UTF-16 units, including JSON strings with unpaired surrogate escapes.
        val heading = ByteBuffer.allocate(4 + title.length * 2).putInt(title.length)
        title.forEach { heading.putChar(it) }
        digest.update(heading.array())
        var offset = 0
        while (offset < bytes.size) {
            currentCoroutineContext().ensureActive()
            val count = minOf(8192, bytes.size - offset)
            digest.update(bytes, offset, count)
            offset += count
        }
        currentCoroutineContext().ensureActive()
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private suspend fun read(uri: URI, limit: Int): ByteArray {
        return try { withTimeout(30000) { readResponse(uri, limit) } }
        catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw IOException("网络请求超过30秒", timeout)
        }
    }
    private suspend fun readResponse(uri: URI, limit: Int): ByteArray = coroutineScope {
        currentCoroutineContext().ensureActive()
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10000; connection.readTimeout = 10000
        connection.instanceFollowRedirects = false; connection.useCaches = false
        connection.setRequestProperty("Accept-Encoding", "identity")
        synchronized(connectionLock) {
            if (closed) { connection.disconnect(); error("网络来源已关闭") }
            connections.add(connection)
        }
        // Best-effort disconnect on coroutine cancellation, plus bounded socket timeouts.
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { connection.disconnect() }
        }
        try {
            val code = connection.responseCode
            require(code == 200) { "网络请求未成功（HTTP $code）" }
            require(connection.contentEncoding == null || connection.contentEncoding.equals("identity", ignoreCase = true)) { "网络示例不支持压缩响应" }
            val rawLength = connection.getHeaderField("Content-Length")
            val declared = rawLength?.toLongOrNull()
            require(rawLength == null || declared != null && rawLength.all { it in '0'..'9' }) { "响应长度格式无效" }
            require(declared == null || declared in 0..limit.toLong()) { "响应超过读取上限" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= limit) { "响应超过读取上限" }
                    output.write(buffer, 0, count)
                }
            }
            require(declared == null || declared == output.size().toLong()) { "响应未完整读取" }
            currentCoroutineContext().ensureActive()
            output.toByteArray()
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            cancellation.cancel()
            try { connection.disconnect() } finally {
                synchronized(connectionLock) { connections.remove(connection) }
            }
        }
    }
    override fun close() {
        val active = synchronized(connectionLock) {
            closed = true
            snapshot = null
            connections.toList().also { connections.clear() }
        }
        active.forEach { connection ->
            try { connection.disconnect() } catch (error: Exception) {
                android.util.Log.w("DemoNetworkSource", "Could not disconnect a retired connection", error)
            }
        }
    }
    /** Bound parser recursion before JSONObject sees remotely supplied arrays/objects. */
    private suspend fun checkCatalogueStructure(json: String) {
        val stack = CharArray(32)
        var depth = 0
        var quoted = false
        var escaped = false
        var started = false
        var finished = false
        for (index in json.indices) {
            if (index % 4096 == 0) currentCoroutineContext().ensureActive()
            val character = json[index]
            if (quoted) {
                if (escaped) escaped = false else if (character == '\\') escaped = true else if (character == '"') quoted = false
                continue
            }
            if (!started) {
                if (character in " \t\r\n") continue
                require(character == '{') { "网络目录必须是JSON对象" }
                started = true
            } else if (finished) {
                require(character in " \t\r\n") { "目录对象后存在额外内容" }
                continue
            }
            when (character) {
                '"' -> quoted = true
                '{', '[' -> { require(depth < stack.size) { "目录嵌套超过32层" }; stack[depth++] = character }
                '}', ']' -> {
                    require(depth > 0 && stack[depth - 1] == if (character == '}') '{' else '[') { "目录结构不完整" }
                    depth--
                    if (depth == 0) finished = true
                }
                '\'', '/', '#' -> error("网络目录不支持非标准JSON引号或注释")
            }
        }
        require(started && finished && !quoted && depth == 0) { "目录结构不完整" }
    }
    private fun JSONObject.string(key: String): String = get(key) as? String ?: error("目录字段必须为字符串：$key")
    companion object {
        private fun checkedUri(uri: URI): URI {
            require(uri.toASCIIString().length <= 2048) { "网络地址超过2048字符" }
            require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535)) {
                "请使用无账号信息和片段的HTTPS地址"
            }
            return uri
        }
        private fun port(uri: URI) = if (uri.port == -1) 443 else uri.port
        private fun decode(bytes: ByteArray) = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
