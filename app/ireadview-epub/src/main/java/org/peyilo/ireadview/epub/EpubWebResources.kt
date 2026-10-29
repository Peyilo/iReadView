package org.peyilo.ireadview.epub

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.XmlDeclaration
import org.jsoup.parser.Parser
import org.peyilo.ireadview.source.EpubPublication

/** Publication-scoped byte cache, shared by the three bodies. No WebViews or
 * archive leases are retained. Entries are immutable and bounded by total bytes.
 */
internal class EpubResourceCache(private val capacity: Int = 8 * 1024 * 1024) {
    private data class Key(val revision: String, val path: String)
    private val entries = LinkedHashMap<Key, ByteArray>(16, .75f, true)
    private var size = 0
    suspend fun read(book: EpubPublication, resource: EpubPublication.Resource): ByteArray = withContext(Dispatchers.IO) {
        val key = Key(book.revision, resource.path)
        synchronized(this@EpubResourceCache) { entries[key] }?.let { return@withContext it }
        val original = book.readResource(resource.path)
        val bytes = if (resource.mediaType in DOCUMENT_TYPES) sanitize(original) else original
        synchronized(this@EpubResourceCache) {
            entries[key]?.let { return@synchronized it }
            if (bytes.size <= capacity) {
                while (size + bytes.size > capacity) {
                    val iterator = entries.entries.iterator()
                    size -= iterator.next().value.size
                    iterator.remove()
                }
                entries[key] = bytes
                size += bytes.size
            }
            bytes
        }
    }
    @Synchronized fun clear() { entries.clear(); size = 0 }

    private fun sanitize(bytes: ByteArray): ByteArray {
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, "", Parser.xmlParser())
        document.childNodes().filterIsInstance<XmlDeclaration>().filter { it.name() == "xml" }.forEach { it.remove() }
        document.select("script,iframe,frame,object,embed,base,form").remove()
        document.getAllElements().forEach { element ->
            element.attributes().asList().filter { it.key.startsWith("on", true) }.forEach { element.removeAttr(it.key) }
        }
        document.select("meta[http-equiv]").remove()
        document.select("meta[name=viewport]").first()?.let { meta ->
            document.getElementsByTag("html").first()?.attr("data-iread-viewport", meta.attr("content"))
        }
        document.select("meta[name=viewport]").remove()
        document.getElementsByTag("head").first()?.prependElement("meta")?.attr("name", "viewport")
            ?.attr("content", "width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no")
        return document.outerHtml().toByteArray(Charsets.UTF_8)
    }
    companion object { val DOCUMENT_TYPES = setOf("application/xhtml+xml", "text/html") }
}

/** A body's isolated virtual origin and request boundary. All requests are
 * intercepted: local names never fall through to sockets, file or content URLs.
 * Document MIME/namespace handling stays with WebView's original URL loader.
 */
internal class EpubWebResources(private val cache: EpubResourceCache) {
    private val host = "${UUID.randomUUID()}.epub.iread.localhost"
    private val origin = "https://$host/"
    @Volatile private var publication: EpubPublication? = null
    fun bind(book: EpubPublication) { publication = book }
    fun clear() { publication = null }
    fun owns(url: String) = url.startsWith(origin)
    fun url(path: String) = origin + path.split('/').joinToString("/") { Uri.encode(it) }
    fun respond(uri: Uri): WebResourceResponse {
        val book = publication ?: return denied()
        if (uri.scheme != "https" || uri.host != host || uri.port != -1 || uri.query != null) return denied()
        val path = uri.path?.removePrefix("/") ?: return denied()
        val item = book.resources.firstOrNull { it.path == path } ?: return denied()
        return try {
            val bytes = runBlocking { cache.read(book, item) }
            WebResourceResponse(item.mediaType, if (item.mediaType in EpubResourceCache.DOCUMENT_TYPES) "utf-8" else null,
                200, "OK", mapOf("Content-Security-Policy" to POLICY, "Cache-Control" to "no-store"), ByteArrayInputStream(bytes))
        } catch (_: Exception) { denied() }
    }
    private fun denied() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    companion object {
        private const val POLICY = "default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; media-src 'self'; script-src 'none'; connect-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'"
    }
}
