package org.peyilo.ireadview.internal.source

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.peyilo.ireadview.api.source.ReaderInput

internal data class EpubArchiveLimits(
    val maxArchiveBytes: Long = 64L * 1024 * 1024,
    val maxEntryBytes: Long = 32L * 1024 * 1024,
    val maxExpandedBytes: Long = 256L * 1024 * 1024,
    val maxEntries: Int = 10000
) {
    init { require(maxArchiveBytes > 0 && maxEntryBytes > 0 && maxExpandedBytes > 0 && maxEntries > 0) }
}

/** ZIP resources stay inside a private archive; entry names are never filesystem destinations. */
internal class EpubArchive private constructor(
    private val ownedFile: OwnedSourceFile,
    val revision: String,
    private val entries: Map<String, Entry>,
    private val limits: EpubArchiveLimits
) : AutoCloseable {
    private data class Entry(val size: Long, val compressedSize: Long, val crc: Long, val method: Int)
    data class Resource(val path: String, val fragment: String?)
    fun contains(path: String): Boolean = entries.containsKey(path)

    suspend fun <T> read(path: String, parse: (InputStream) -> T): T {
        validatePath(path, directory = false)
        val expected = entries[path] ?: throw IOException("Missing EPUB resource: $path")
        val coroutine = currentCoroutineContext()
        val lease = ownedFile.acquire()
        try {
            ZipFile(lease.file).use { zip ->
                coroutine.ensureActive()
                val entry = zip.getEntry(path) ?: throw IOException("Missing EPUB resource: $path")
                if (entry.size != expected.size || entry.compressedSize != expected.compressedSize || entry.crc != expected.crc || entry.method != expected.method)
                    throw IOException("EPUB archive changed while open")
                CheckedEntry(zip.getInputStream(entry), expected, limits.maxEntryBytes) { coroutine.ensureActive() }.use { stream ->
                    val value = parse(stream)
                    val buffer = ByteArray(8192)
                    while (stream.read(buffer) >= 0) { coroutine.ensureActive() }
                    return value
                }
            }
        } finally { lease.close() }
    }

    override fun close() = ownedFile.close()

    companion object {
        suspend fun open(input: ReaderInput, directory: File, limits: EpubArchiveLimits = EpubArchiveLimits()): EpubArchive {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create EPUB cache directory")
            val coroutine = currentCoroutineContext()
            val owner = OwnedSourceFile.createTemporary(directory) { coroutine.ensureActive() }
            val file = owner.file
            var transferred = false
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                input.openStream().use { source ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            if (count > limits.maxArchiveBytes - copied) throw IOException("EPUB archive exceeds byte budget")
                            output.write(buffer, 0, count); digest.update(buffer, 0, count); copied += count
                        }
                    }
                }
                val entries = LinkedHashMap<String, Entry>()
                val names = HashSet<String>()
                ZipFile(file).use { zip ->
                    var expanded = 0L
                    var count = 0
                    val all = zip.entries()
                    while (all.hasMoreElements()) {
                        currentCoroutineContext().ensureActive()
                        val entry = all.nextElement()
                        if (++count > limits.maxEntries) throw IOException("EPUB archive exceeds entry budget")
                        validatePath(entry.name, entry.isDirectory)
                        if (!names.add(entry.name.removeSuffix("/"))) throw IOException("Duplicate EPUB entry: ${entry.name}")
                        if (entry.size < 0 || entry.compressedSize < 0 || entry.crc < 0 || entry.size > limits.maxEntryBytes ||
                            entry.size > limits.maxExpandedBytes - expanded) throw IOException("EPUB entry exceeds expanded budget: ${entry.name}")
                        if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) throw IOException("Unsupported EPUB compression")
                        expanded += entry.size
                        if (!entry.isDirectory) entries[entry.name] = Entry(entry.size, entry.compressedSize, entry.crc, entry.method)
                    }
                }
                val mime = entries["mimetype"] ?: throw IOException("Missing EPUB mimetype")
                if (mime.method != ZipEntry.STORED || mime.size != 20L) throw IOException("Invalid EPUB mimetype entry")
                if (!entries.containsKey("META-INF/container.xml")) throw IOException("Missing EPUB container.xml")
                val archive = EpubArchive(owner, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }, entries, limits)
                val mimeText = archive.read("mimetype") { String(it.readBytes(), Charsets.US_ASCII) }
                if (mimeText != "application/epub+zip") throw IOException("Not an EPUB archive")
                currentCoroutineContext().ensureActive()
                transferred = true
                return archive
            } finally { if (!transferred) owner.close() }
        }

        fun resolve(basePath: String, reference: String): Resource {
            validatePath(basePath, directory = false)
            if (reference.length > 4096) throw IOException("EPUB resource reference is too long")
            val uri = try { URI(reference) } catch (failure: Exception) { throw IOException("Invalid EPUB resource reference", failure) }
            if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null || uri.isOpaque) throw IOException("External or queried EPUB resource is unsupported")
            val relative = uri.path ?: ""
            if (relative.startsWith('/') || relative.contains('\\') || relative.contains('\u0000')) throw IOException("Invalid EPUB resource path")
            val segments = if (relative.isEmpty()) basePath.split('/').toMutableList() else
                basePath.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
            if (relative.isNotEmpty()) relative.split('/').forEach { segment ->
                when (segment) {
                    "." -> Unit
                    ".." -> if (segments.isEmpty()) throw IOException("EPUB resource escapes container") else segments.removeAt(segments.lastIndex)
                    "" -> throw IOException("Invalid EPUB resource path")
                    else -> segments.add(segment)
                }
            }
            val path = segments.joinToString("/")
            validatePath(path, directory = false)
            return Resource(path, uri.fragment)
        }

        private fun validatePath(path: String, directory: Boolean) {
            val value = if (directory) path.removeSuffix("/") else path
            if (value.isEmpty() || value.length > 4096 || value.startsWith('/') || value.contains('\\') || value.any { it.code < 32 || it == ':' || it.code == 127 } ||
                value.split('/').any { it.isEmpty() || it == "." || it == ".." }) throw IOException("Invalid EPUB entry path")
        }
    }

    private class CheckedEntry(input: InputStream, private val entry: Entry, private val limit: Long, private val checkActive: () -> Unit) : FilterInputStream(input) {
        private var size = 0L
        private val crc = CRC32()
        private fun verifyEnd() {
            if (size != entry.size || crc.value != entry.crc) throw IOException("EPUB entry size or checksum mismatch")
        }
        override fun read(): Int {
            checkActive()
            val value = `in`.read()
            if (value < 0) verifyEnd() else { size++; if (size > limit || size > entry.size) throw IOException("EPUB entry exceeds byte budget"); crc.update(value) }
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkActive()
            val count = `in`.read(buffer, offset, length)
            if (count < 0) verifyEnd() else if (count > 0) {
                size += count
                if (size > limit || size > entry.size) throw IOException("EPUB entry exceeds byte budget")
                crc.update(buffer, offset, count)
            }
            return count
        }
        override fun skip(amount: Long): Long {
            val buffer = ByteArray(8192)
            var remaining = amount.coerceAtLeast(0)
            while (remaining > 0) { val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()); if (count < 0) break; remaining -= count }
            return amount.coerceAtLeast(0) - remaining
        }
        override fun markSupported(): Boolean = false
        override fun mark(readlimit: Int) {}
        override fun reset(): Unit = throw IOException("EPUB resource stream cannot rewind")
    }
}
