package org.peyilo.ireadview.internal.source

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.atomic.AtomicBoolean

/** Only for a private temporary file created by a built-in source. */
internal class OwnedSourceFile private constructor(
    val file: File,
    private val marker: File,
    private val markerStream: RandomAccessFile,
    private val processLease: FileLock
) : AutoCloseable {
    private val lock = Any()
    private var readers = 0
    private var retired = false

    class Lease internal constructor(val file: File, private val release: () -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean()
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }
    fun acquire(): Lease = synchronized(lock) {
        check(!retired) { "Source file is closed" }
        readers++
        Lease(file) {
            val remove = synchronized(lock) { readers--; retired && readers == 0 }
            if (remove) removeFile()
        }
    }
    override fun close() {
        val remove = synchronized(lock) { if (retired) return; retired = true; readers == 0 }
        if (remove) removeFile()
    }
    private fun removeFile() {
        try {
            if (!file.delete() && file.exists()) Log.w("iReadView", "Could not remove source file: ${file.name}")
        } catch (error: Exception) {
            Log.w("iReadView", "Source file cleanup failed: ${file.name}", error)
        } finally {
            // A separate marker keeps content stream closes from releasing the process lock.
            try { processLease.release() } catch (error: Exception) { Log.w("iReadView", "Source lease release failed", error) }
            try { markerStream.close() } catch (error: Exception) { Log.w("iReadView", "Source lease close failed", error) }
            synchronized(processMarkersLock) {
                processMarkers.remove(marker.absolutePath)
                try { if (!file.exists()) marker.delete() }
                catch (error: Exception) { Log.w("iReadView", "Source marker cleanup failed", error) }
            }
        }
    }

    companion object {
        private const val DIRECTORY = "iread-temporary-v1"
        private const val PREFIX = "iread-source-"
        private const val SUFFIX = ".lease"
        private const val RETENTION_MS = 24L * 60 * 60 * 1000
        private const val CLEANUP_LIMIT = 64
        private val processMarkersLock = Any()
        private val processMarkers = HashSet<String>()

        /** Called on IO before writing; the source owns this lease until its last reader leaves. */
        fun createTemporary(directory: File, checkActive: () -> Unit): OwnedSourceFile {
            val root = File(directory.canonicalFile, DIRECTORY)
            if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create source temporary directory")
            if (root.canonicalFile != root.absoluteFile) throw IOException("Source temporary directory must not be a symbolic link")
            prune(root, checkActive)
            checkActive()
            val marker = synchronized(processMarkersLock) {
                File.createTempFile(PREFIX, SUFFIX, root).also { processMarkers.add(it.absolutePath) }
            }
            val payload = File(root, marker.name + ".payload")
            var stream: RandomAccessFile? = null
            var lease: FileLock? = null
            var transferred = false
            var ownsPayload = false
            try {
                stream = RandomAccessFile(marker, "rw")
                lease = stream.channel.tryLock() ?: throw IOException("Cannot reserve source temporary file")
                if (!payload.createNewFile()) throw IOException("Source temporary file already exists")
                ownsPayload = true
                checkActive()
                return OwnedSourceFile(payload, marker, stream, lease).also { transferred = true }
            } finally {
                if (!transferred) {
                    try { if (ownsPayload) payload.delete() } finally {
                        try { lease?.release() } finally {
                            try { stream?.close() } finally {
                                synchronized(processMarkersLock) { processMarkers.remove(marker.absolutePath); marker.delete() }
                            }
                        }
                    }
                }
            }
        }

        private fun prune(root: File, checkActive: () -> Unit) {
            val cutoff = System.currentTimeMillis() - RETENTION_MS
            val markers = try {
                root.listFiles { file -> file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX) }
            } catch (error: Exception) {
                Log.w("iReadView", "Source temporary directory scan failed", error)
                null
            } ?: return
            var attempted = 0
            for (marker in markers) {
                checkActive()
                val payload = File(root, marker.name + ".payload")
                try {
                    if (!marker.isFile || marker.lastModified() <= 0 || marker.lastModified() > cutoff) continue
                    if (marker.canonicalFile != marker.absoluteFile || payload.canonicalFile != payload.absoluteFile) continue
                    if (synchronized(processMarkersLock) { marker.absolutePath in processMarkers }) continue
                    if (++attempted > CLEANUP_LIMIT) break
                    var removed = false
                    synchronized(processMarkersLock) {
                        // Do not even open an active marker: some platforms drop process locks
                        // when any descriptor for that same file closes.
                        if (marker.absolutePath !in processMarkers && marker.isFile) {
                            RandomAccessFile(marker, "rw").use { stream ->
                                val lease = try { stream.channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                                if (lease != null) try {
                                    // Recheck metadata under the lease; another process may be active.
                                    if (marker.lastModified() in 1..cutoff) removed = !payload.exists() || payload.delete()
                                } finally { lease.release() }
                            }
                            if (removed) marker.delete()
                        }
                    }
                } catch (error: Exception) {
                    Log.w("iReadView", "Stale source temporary file cleanup failed: ${marker.name}", error)
                }
            }
        }
    }
}
