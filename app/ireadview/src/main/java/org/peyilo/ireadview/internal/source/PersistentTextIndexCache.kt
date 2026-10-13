package org.peyilo.ireadview.internal.source

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.CancellationException

/** Short exclusive operation leases; active sources only retain private temporary copies. */
internal object PersistentTextIndexCache {
    private const val DIRECTORY = "iread-text-index-v2"
    private const val ENTRY_LIMIT = 128
    private val entryName = Regex("[0-9a-f]{64}\\.idx")
    private val processLock = Any()
    private val activeMarkers = HashSet<String>()

    fun restore(directory: File, key: String, limit: Long, budget: Long, checkActive: () -> Unit): OwnedSourceFile? {
        var reserved: OwnedSourceFile? = null
        var transferred = false
        try {
            val restored = operation(directory, checkActive) { root ->
                if (!prune(root, budget, 0, null, checkActive)) return@operation null
                val cached = File(root, "$key.idx")
                if (!regular(cached) || cached.length() > limit || cached.length() > budget) return@operation null
                val owner = OwnedSourceFile.createTemporary(directory, checkActive).also { reserved = it }
                copy(cached, owner.file, limit, checkActive)
                cached.setLastModified(System.currentTimeMillis())
                owner
            }
            return restored.also { transferred = it != null }
        } finally { if (!transferred) reserved?.close() }
    }

    fun publish(directory: File, key: String, source: File, budget: Long, checkActive: () -> Unit) {
        operation(directory, checkActive) { root ->
            val size = source.length()
            val target = File(root, "$key.idx")
            val existing = regular(target)
            val incoming = if (existing || size <= 0 || size > budget) 0L else size
            if (!prune(root, budget, incoming, if (existing) null else target, checkActive)) return@operation Unit
            if (existing || size <= 0 || size > budget) return@operation Unit
            if (target.exists()) throw IOException("Invalid text cache entry")
            val staging = File.createTempFile("iread-", ".pending", root)
            try {
                copy(source, staging, size, checkActive)
                checkActive()
                if (!staging.renameTo(target)) throw IOException("Cannot publish text index cache")
            } finally { if (staging.exists()) staging.delete() }
        }
    }

    fun discard(directory: File, key: String, checkActive: () -> Unit) {
        operation(directory, checkActive) { root ->
            val file = File(root, "$key.idx")
            if (regular(file) && !file.delete()) throw IOException("Cannot remove invalid text index cache")
        }
    }

    private fun regular(file: File): Boolean = file.isFile && file.canonicalFile == file.absoluteFile

    private fun copy(source: File, target: File, limit: Long, checkActive: () -> Unit) {
        val expected = source.length()
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count.toLong() > limit - total) throw IOException("Text index cache exceeds limit")
                    output.write(buffer, 0, count)
                    total += count
                }
                if (total != expected) throw IOException("Text index cache copy is truncated")
                checkActive()
                output.fd.sync()
                checkActive()
            }
        }
    }

    /** Under the directory lease, no other managed operation owns a pending file. */
    private fun prune(root: File, budget: Long, incoming: Long, target: File?, checkActive: () -> Unit): Boolean {
        require(budget > 0 && incoming in 0..budget)
        val files = root.listFiles() ?: return false
        for (file in files) {
            checkActive()
            if (file.name.startsWith("iread-") && file.name.endsWith(".pending") && regular(file) && !file.delete()) return false
        }
        val entries = files.filter {
            checkActive()
            entryName.matches(it.name) && regular(it) && it != target
        }.sortedByDescending { checkActive(); it.lastModified() }
        var remaining = budget - incoming
        var retainedCount = 0
        val allowedCount = ENTRY_LIMIT - if (incoming > 0) 1 else 0
        for (file in entries) {
            checkActive()
            val size = file.length()
            if (size > 0 && size <= remaining && retainedCount < allowedCount) {
                remaining -= size
                retainedCount++
            } else if (!file.delete()) return false
        }
        return true
    }

    /** Cache I/O failure is optional acceleration failure; cancellation and fatal errors propagate. */
    private fun <T> operation(directory: File, checkActive: () -> Unit, action: (File) -> T): T? {
        var markerPath: String? = null
        try {
            checkActive()
            val root = File(directory.canonicalFile, DIRECTORY)
            if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create text index cache")
            if (root.canonicalFile != root.absoluteFile) throw IOException("Invalid text index cache directory")
            val marker = File(root, "directory.lock")
            if (marker.canonicalFile != marker.absoluteFile) throw IOException("Invalid text cache lease")
            synchronized(processLock) {
                if (!activeMarkers.add(marker.absolutePath)) return null
                markerPath = marker.absolutePath
            }
            RandomAccessFile(marker, "rw").use { stream ->
                val lease = try { stream.channel.tryLock() } catch (_: OverlappingFileLockException) { null } ?: return null
                try { checkActive(); return action(root) } finally { lease.release() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: IOException) { Log.w("iReadView", "TXT index cache unavailable", failure); return null }
        catch (failure: SecurityException) { Log.w("iReadView", "TXT index cache unavailable", failure); return null }
        finally { markerPath?.let { path -> synchronized(processLock) { activeMarkers.remove(path) } } }
    }
}
