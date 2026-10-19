package org.peyilo.ireadview.source

import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.internal.source.*

/** Container access independent of any Android renderer. Resource names are ZIP paths, never files. */
class EpubPublication private constructor(private val archive: EpubArchive, private val metadata: EpubPackage,
    val navigation: List<Navigation>) : AutoCloseable {
    data class Resource(val path: String, val mediaType: String)
    class Navigation(val title: String, val path: String?, val fragment: String?, children: List<Navigation>) {
        val children: List<Navigation> = Collections.unmodifiableList(ArrayList(children))
    }
    val revision: String get() = archive.revision
    val title: String get() = metadata.title ?: "EPUB"
    val fixedLayout: Boolean get() = metadata.fixedLayout
    val readingOrder: List<Resource> = Collections.unmodifiableList(metadata.readingOrder.map { item ->
        Resource(item.path, metadata.manifest.values.first { it.path == item.path }.mediaType)
    })
    val resources: List<Resource> = Collections.unmodifiableList(metadata.manifest.values.mapNotNull { item -> item.path?.let { Resource(it, item.mediaType) } })
    suspend fun readResource(path: String): ByteArray = withContext(Dispatchers.IO) {
        // Archive owns byte/expansion/path limits and verifies the entry against the open snapshot.
        archive.read(path) { it.readBytes() }
    }
    override fun close() = archive.close()
    companion object {
        suspend fun open(input: ReaderInput, cacheDirectory: File): EpubPublication = withContext(Dispatchers.IO) {
            val archive = EpubArchive.open(input, cacheDirectory)
            try {
                val metadata = EpubPackageParser.load(archive)
                val toc = EpubNavigationParser.load(archive, metadata)
                fun convert(entries: List<EpubNavigationEntry>): List<Navigation> = entries.map {
                    Navigation(it.label, it.resource?.path, it.resource?.fragment, convert(it.children))
                }
                EpubPublication(archive, metadata, Collections.unmodifiableList(convert(toc.entries)))
            } catch (failure: Throwable) { archive.close(); throw failure }
        }
    }
}
