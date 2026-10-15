package org.peyilo.iread.demo.data.source

import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.GZIPInputStream

/** Shared host transport. Closing a source disconnects its active catalogue/chapter requests. */
internal class HttpsBookClient : AutoCloseable {
    data class Response(val bytes: ByteArray, val contentType: String?)
    private val lock = Any()
    private val connections = LinkedHashSet<HttpURLConnection>()
    private var closed = false

    suspend fun get(uri: URI, limit: Int, html: Boolean = false): Response = try {
        withTimeout(30_000) { response(uri, limit, html) }
    } catch (timeout: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw IOException("网络请求超过30秒", timeout)
    }

    private suspend fun response(uri: URI, limit: Int, html: Boolean): Response = coroutineScope {
        currentCoroutineContext().ensureActive()
        val connection = URL(uri.toASCIIString()).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false; connection.useCaches = false
        connection.setRequestProperty("Accept-Encoding", if (html) "gzip, identity" else "identity")
        connection.setRequestProperty("User-Agent", "iReadView-Demo/0.1 (Android; reading widget example)")
        synchronized(lock) {
            if (closed) { connection.disconnect(); error("网络来源已关闭") }
            connections.add(connection)
        }
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { connection.disconnect() }
        }
        try {
            val code = connection.responseCode
            if (code != 200) throw IOException("网络请求未成功（HTTP $code）")
            val encoding = connection.contentEncoding
            val gzip = html && encoding.equals("gzip", ignoreCase = true)
            require(gzip || encoding == null || encoding.equals("identity", ignoreCase = true)) { "网络响应压缩格式不受支持" }
            val rawLength = connection.getHeaderField("Content-Length")
            val declared = rawLength?.toLongOrNull()
            require(rawLength == null || declared != null && rawLength.all { it in '0'..'9' }) { "响应长度格式无效" }
            require(declared == null || declared in 0..limit.toLong()) { "响应超过读取上限" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { raw ->
                val input = if (gzip) GZIPInputStream(raw) else raw
                input.use {
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = it.read(buffer)
                        if (count < 0) break
                        require(count <= limit - output.size()) { "响应超过读取上限" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(gzip || declared == null || declared == output.size().toLong()) { "响应未完整读取" }
            currentCoroutineContext().ensureActive()
            Response(output.toByteArray(), connection.contentType)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            cancellation.cancel()
            try { connection.disconnect() } finally { synchronized(lock) { connections.remove(connection) } }
        }
    }

    override fun close() {
        val active = synchronized(lock) {
            closed = true
            connections.toList().also { connections.clear() }
        }
        active.forEach { runCatching { it.disconnect() } }
    }
}
