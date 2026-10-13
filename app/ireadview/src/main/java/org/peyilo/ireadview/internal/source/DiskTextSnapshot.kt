package org.peyilo.ireadview.internal.source

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.source.TextBookOptions

/** Private normalized UTF-8 records; only chapter offsets/counts remain in memory. */
internal class DiskTextSnapshot private constructor(
    override val book: BookDescriptor,
    private val ownedFile: OwnedSourceFile,
    private val chapters: Map<String, Entry>
) : TextSourceSnapshot {
    private data class Entry(val id: String, val title: String, val start: Long, val end: Long, val count: Int, val digest: ByteArray)

    override suspend fun chapter(chapterId: String): ChapterContent {
        val entry = chapters[chapterId] ?: throw NoSuchElementException("Unknown chapter: $chapterId")
        val lease = ownedFile.acquire()
        try {
            val coroutine = currentCoroutineContext()
            val checkActive = { coroutine.ensureActive() }
            val digest = recordDigest()
            val blocks = ArrayList<ContentBlock>()
            RandomAccessFile(lease.file, "r").use { data ->
                if (data.length() < HEADER_BYTES || data.readInt() != MAGIC || data.readInt() != FORMAT_VERSION)
                    throw IOException("Unsupported text index format")
                if (data.length() < entry.end) throw IOException("Text index is missing or truncated")
                data.seek(entry.start)
                val seenIds = HashSet<String>()
                repeat(entry.count) {
                    checkActive()
                    if (entry.end - data.filePointer < 9) throw IOException("Invalid text index record")
                    val idSize = data.readInt()
                    if (idSize <= 0 || idSize.toLong() > entry.end - data.filePointer - 5) throw IOException("Invalid text index ID")
                    val idBytes = readBytes(data, idSize, checkActive)
                    val id = decode(idBytes)
                    if (id.isBlank() || !seenIds.add(id)) throw IOException("Invalid or duplicate text index ID")
                    val heading = data.readUnsignedByte()
                    val size = data.readInt()
                    if (heading !in 0..1 || size < 0 || size.toLong() > entry.end - data.filePointer) throw IOException("Invalid text index record")
                    val bytes = readBytes(data, size, checkActive)
                    updateRecordDigest(digest, idBytes, heading, bytes, checkActive)
                    val text = decode(bytes)
                    blocks.add(if (heading == 1) HeadingBlock(id, text) else TextBlock(id, text))
                }
                if (data.filePointer != entry.end) throw IOException("Invalid text index chapter boundary")
                checkActive()
                if (!java.util.Arrays.equals(digest.digest(), entry.digest)) throw IOException("Text index chapter checksum mismatch")
            }
            return ChapterContent(book.bookId, chapterId, book.revision, blocks)
        } finally {
            lease.close()
        }
    }

    override fun close() = ownedFile.close()

    companion object {
        private const val MAGIC = 0x49525458 // IRTX
        private const val FORMAT_VERSION = 2
        private const val HEADER_BYTES = 8L
        private const val FOOTER_MAGIC = 0x49525446 // IRTF
        private const val FOOTER_BYTES = 44L // metadata offset, SHA-256, magic
        fun build(bookId: String, input: ReaderInput, options: TextBookOptions, checkActive: () -> Unit): DiskTextSnapshot {
            if (!options.reuseDiskIndex) return buildFresh(bookId, input, options, checkActive)
            val policyKey = options.contentIdProvider?.persistentIndexKey
            checkActive()
            val systemCharset = try { java.nio.charset.Charset.forName(options.charset.name()) } catch (_: IllegalArgumentException) { null }
            if (options.contentIdProvider != null && policyKey == null ||
                options.charset.javaClass != systemCharset?.javaClass)
                return buildFresh(bookId, input, options, checkActive)
            require(policyKey == null || policyKey.isNotBlank()) { "Persistent identity policy key must be nonblank" }
            val directory = requireNotNull(options.indexDirectory)
            val raw = OwnedSourceFile.createTemporary(directory, checkActive)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                input.openStream().use { stream ->
                    raw.file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            checkActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            if (count > options.maxInputBytes.toLong() - total) throw IOException("Text input exceeds ${options.maxInputBytes} bytes")
                            digest.update(buffer, 0, count); output.write(buffer, 0, count); total += count
                        }
                    }
                }
                val key = cacheKey(bookId, options, policyKey, digest.digest(), checkActive)
                val cached = PersistentTextIndexCache.restore(directory, key, options.maxIndexBytes, options.persistentIndexCacheBytes, checkActive)
                if (cached != null) {
                    var transferred = false
                    try {
                        val restored = readSnapshot(cached, bookId, options.maxIndexBytes, checkActive)
                        RandomAccessFile(cached.file, "r").use { data ->
                            val buffer = ByteArray(8192)
                            for (entry in restored.chapters.values) {
                                val actual = digestRegion(data, entry.start, entry.end, checkActive, buffer, recordDigest())
                                if (!java.util.Arrays.equals(actual, entry.digest)) throw IOException("Cached text chapter checksum mismatch")
                            }
                        }
                        checkActive()
                        return restored.also { transferred = true }
                    } catch (_: IOException) {
                        PersistentTextIndexCache.discard(directory, key, checkActive)
                    } finally { if (!transferred) cached.close() }
                }
                val fresh = buildFresh(bookId, ReaderInput.fromFile(raw.file), options, checkActive)
                try {
                    PersistentTextIndexCache.publish(directory, key, fresh.ownedFile.file, options.persistentIndexCacheBytes, checkActive)
                    checkActive()
                    return fresh
                } catch (failure: Throwable) { fresh.close(); throw failure }
            } finally { raw.close() }
        }
        private fun cacheKey(bookId: String, options: TextBookOptions, policyKey: String?, original: ByteArray, checkActive: () -> Unit): String {
            val digest = MessageDigest.getInstance("SHA-256")
            fun text(value: String) {
                for (shift in 24 downTo 0 step 8) digest.update((value.length ushr shift).toByte())
                for (index in value.indices) {
                    if (index % 4096 == 0) checkActive()
                    digest.update((value[index].code ushr 8).toByte()); digest.update(value[index].code.toByte())
                }
            }
            text("iRead:txt-persistent-index:format$FORMAT_VERSION:parser-v2")
            digest.update(original)
            text(bookId); text(options.title); text(options.preambleTitle); text(options.charset.name())
            text(options.normalization.name)
            digest.update(if (options.chapterHeading == null) 0.toByte() else 1.toByte())
            options.chapterHeading?.let { heading ->
                text(heading.pattern)
                text(heading.options.map { it.name }.sorted().joinToString(","))
            }
            digest.update(if (options.contentIdProvider == null) 0.toByte() else 1.toByte())
            text(policyKey ?: "chapter-N/line-N:v1")
            // Charset/regex implementations depend on platform/runtime versions.
            text(android.os.Build.VERSION.SDK_INT.toString())
            text(android.os.Build.FINGERPRINT)
            text(System.getProperty("java.runtime.version") ?: "unknown")
            checkActive()
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        private fun buildFresh(bookId: String, input: ReaderInput, options: TextBookOptions, checkActive: () -> Unit): DiskTextSnapshot {
            val directory = requireNotNull(options.indexDirectory)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create text index directory")
            val owner = OwnedSourceFile.createTemporary(directory, checkActive)
            val file = owner.file
            var transferred = false
            try {
                val entries = LinkedHashMap<String, Entry>()
                val digest = MessageDigest.getInstance("SHA-256")
                var chapterDigest = recordDigest()
                val parsedDigest = RandomAccessFile(file, "rw").use { output ->
                    if (options.maxIndexBytes < HEADER_BYTES) throw IOException("Text index exceeds ${options.maxIndexBytes} bytes")
                    output.writeInt(MAGIC)
                    output.writeInt(FORMAT_VERSION)
                    var start = HEADER_BYTES
                    input.openStream().use { raw ->
                        val stream = DigestInputStream(BoundedInput(raw, options.maxInputBytes, checkActive), digest)
                        val decoder = options.charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                        // The outer use owns the raw stream, including decoder construction failures.
                        val reader = InputStreamReader(stream, decoder).buffered()
                        TextSnapshotParser.parse(reader, bookId, options, checkActive, onBlock = { id, line, heading ->
                            val bytes = line.toByteArray(Charsets.UTF_8)
                            val idBytes = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(id))
                            if (bytes.size.toLong() + idBytes.remaining() + 9 > options.maxIndexBytes - output.filePointer) throw IOException("Text index exceeds ${options.maxIndexBytes} bytes")
                            output.writeInt(idBytes.remaining())
                            val encodedId = ByteArray(idBytes.remaining())
                            idBytes.get(encodedId)
                            output.write(encodedId)
                            output.writeByte(if (heading) 1 else 0)
                            output.writeInt(bytes.size)
                            output.write(bytes)
                            updateRecordDigest(chapterDigest, encodedId, if (heading) 1 else 0, bytes, checkActive)
                        }, onChapter = { id, title, count ->
                            entries[id] = Entry(id, title, start, output.filePointer, count, chapterDigest.digest())
                            chapterDigest = recordDigest()
                            start = output.filePointer
                        })
                    }
                }
                checkActive()
                val revision = TextSnapshotParser.revision(digest.digest(), parsedDigest)
                writeMetadata(file, bookId, options.title, revision, entries, options.maxIndexBytes, checkActive)
                entries.clear()
                return readSnapshot(owner, bookId, options.maxIndexBytes, checkActive).also { transferred = true }
            } finally {
                if (!transferred) owner.close()
            }
        }
        private fun writeMetadata(file: File, bookId: String, title: String, revision: String,
            entries: Map<String, Entry>, limit: Long, checkActive: () -> Unit) {
            RandomAccessFile(file, "rw").use { data ->
                val start = data.length()
                var size = FOOTER_BYTES + 4
                fun account(bytes: Long) {
                    if (size > limit - start || bytes > limit - start - size) throw IOException("Text index exceeds $limit bytes")
                    size += bytes
                }
                for (value in listOf(bookId, title, revision)) account(4L + value.length.toLong() * 2)
                for (entry in entries.values) {
                    checkActive()
                    account(60L + entry.id.length.toLong() * 2 + entry.title.length.toLong() * 2)
                }
                data.seek(start)
                val buffer = ByteArray(8192)
                writeString(data, bookId, checkActive, buffer)
                writeString(data, title, checkActive, buffer)
                writeString(data, revision, checkActive, buffer)
                data.writeInt(entries.size)
                for (entry in entries.values) {
                    checkActive()
                    writeString(data, entry.id, checkActive, buffer)
                    writeString(data, entry.title, checkActive, buffer)
                    data.writeLong(entry.start); data.writeLong(entry.end); data.writeInt(entry.count); data.write(entry.digest)
                }
                val end = data.filePointer
                val digest = digestRegion(data, start, end, checkActive, buffer)
                data.seek(end)
                data.writeLong(start); data.write(digest); data.writeInt(FOOTER_MAGIC)
                checkActive()
                data.fd.sync()
                checkActive()
            }
        }
        private fun readSnapshot(owner: OwnedSourceFile, expectedBookId: String, limit: Long, checkActive: () -> Unit): DiskTextSnapshot {
            RandomAccessFile(owner.file, "r").use { data ->
                checkActive()
                val length = data.length()
                if (length < HEADER_BYTES + FOOTER_BYTES || length > limit || data.readInt() != MAGIC || data.readInt() != FORMAT_VERSION)
                    throw IOException("Unsupported text index format or size")
                val end = length - FOOTER_BYTES
                data.seek(end)
                val start = data.readLong()
                val checksum = readBytes(data, 32, checkActive)
                if (data.readInt() != FOOTER_MAGIC || start < HEADER_BYTES || start > end)
                    throw IOException("Invalid text index footer")
                val buffer = ByteArray(8192)
                if (!java.util.Arrays.equals(checksum, digestRegion(data, start, end, checkActive, buffer)))
                    throw IOException("Text index metadata checksum mismatch")
                data.seek(start)
                val bookId = readString(data, end, checkActive, buffer)
                val title = readString(data, end, checkActive, buffer)
                val revision = readString(data, end, checkActive, buffer)
                if (bookId != expectedBookId || revision.isBlank() || end - data.filePointer < 4)
                    throw IOException("Invalid text index identity")
                val count = data.readInt()
                if (count <= 0 || count.toLong() > (end - data.filePointer) / 60) throw IOException("Invalid text index catalogue size")
                val entries = LinkedHashMap<String, Entry>()
                val catalogue = ArrayList<TocEntry>()
                var previousEnd = HEADER_BYTES
                repeat(count) {
                    checkActive()
                    val id = readString(data, end, checkActive, buffer)
                    val chapterTitle = readString(data, end, checkActive, buffer)
                    if (id.isBlank() || id in entries || end - data.filePointer < 52) throw IOException("Invalid text index chapter metadata")
                    val from = data.readLong(); val to = data.readLong(); val blocks = data.readInt()
                    val digest = readBytes(data, 32, checkActive)
                    if (from != previousEnd || to < from || to > start || blocks < 0 ||
                        blocks.toLong() > (to - from) / 9 || blocks == 0 && from != to)
                        throw IOException("Invalid text index chapter boundaries")
                    entries[id] = Entry(id, chapterTitle, from, to, blocks, digest)
                    catalogue.add(TocEntry.Chapter(ChapterDescriptor(id, chapterTitle, revision)))
                    previousEnd = to
                }
                if (data.filePointer != end || previousEnd != start) throw IOException("Invalid text index metadata boundary")
                checkActive()
                val book = BookDescriptor(bookId, title, revision, catalogue)
                checkActive()
                return DiskTextSnapshot(book, owner, entries)
            }
        }
        /** Exact UTF-16 code units preserve host titles, without writeUTF's 64KiB limit. */
        private fun writeString(data: RandomAccessFile, value: String, checkActive: () -> Unit, buffer: ByteArray) {
            data.writeInt(value.length)
            var offset = 0
            while (offset < value.length) {
                checkActive()
                val count = minOf(buffer.size / 2, value.length - offset)
                for (index in 0 until count) {
                    val character = value[offset + index].code
                    buffer[index * 2] = (character ushr 8).toByte(); buffer[index * 2 + 1] = character.toByte()
                }
                data.write(buffer, 0, count * 2)
                offset += count
            }
            checkActive()
        }
        private fun readString(data: RandomAccessFile, end: Long, checkActive: () -> Unit, buffer: ByteArray): String {
            checkActive()
            if (end - data.filePointer < 4) throw IOException("Invalid text index string")
            val count = data.readInt()
            if (count < 0 || count.toLong() * 2 > end - data.filePointer) throw IOException("Invalid text index string size")
            val characters = CharArray(count)
            var offset = 0
            while (offset < count) {
                checkActive()
                val size = minOf(buffer.size / 2, count - offset)
                data.readFully(buffer, 0, size * 2)
                for (index in 0 until size) characters[offset + index] =
                    (((buffer[index * 2].toInt() and 255) shl 8) or (buffer[index * 2 + 1].toInt() and 255)).toChar()
                offset += size
            }
            checkActive()
            return String(characters)
        }
        private fun digestRegion(data: RandomAccessFile, start: Long, end: Long, checkActive: () -> Unit,
            buffer: ByteArray,
            digest: MessageDigest = MessageDigest.getInstance("SHA-256")): ByteArray {
            data.seek(start)
            var remaining = end - start
            while (remaining > 0) {
                checkActive()
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                data.readFully(buffer, 0, count)
                digest.update(buffer, 0, count)
                remaining -= count
            }
            checkActive()
            return digest.digest()
        }
        private fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        private fun recordDigest(): MessageDigest = MessageDigest.getInstance("SHA-256").apply {
            update("iRead:txt-index-chapter:v1\u0000".toByteArray(Charsets.UTF_8))
        }
        private fun readBytes(data: RandomAccessFile, size: Int, checkActive: () -> Unit): ByteArray {
            checkActive()
            val bytes = ByteArray(size)
            var offset = 0
            while (offset < size) {
                checkActive()
                val count = minOf(8192, size - offset)
                data.readFully(bytes, offset, count)
                offset += count
            }
            checkActive()
            return bytes
        }
        private fun updateRecordDigest(digest: MessageDigest, id: ByteArray, heading: Int, text: ByteArray, checkActive: () -> Unit) {
            fun number(value: Int) { for (shift in 24 downTo 0 step 8) digest.update((value ushr shift).toByte()) }
            fun bytes(value: ByteArray) {
                var offset = 0
                while (offset < value.size) {
                    checkActive()
                    val count = minOf(8192, value.size - offset)
                    digest.update(value, offset, count)
                    offset += count
                }
            }
            number(id.size); bytes(id); digest.update(heading.toByte()); number(text.size); bytes(text)
            checkActive()
        }
    }

    private class BoundedInput(input: InputStream, private val limit: Int, private val checkActive: () -> Unit) : FilterInputStream(input) {
        private var count = 0L
        private fun account(amount: Int): Int {
            if (amount > 0) { count += amount; if (count > limit) throw IOException("Text input exceeds $limit bytes") }
            return amount
        }
        override fun read(): Int { checkActive(); return `in`.read().also { if (it >= 0) account(1) } }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int { checkActive(); return account(`in`.read(buffer, offset, length)) }
    }
}
