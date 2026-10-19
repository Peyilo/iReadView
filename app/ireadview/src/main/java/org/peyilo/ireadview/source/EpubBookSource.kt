package org.peyilo.ireadview.source

import java.io.File
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.*

/** Reflowable EPUB text, images and links. CSS, scripting and fixed layout are not interpreted.
 * The host owns the source and its private cache directory; close only after readers stop using it.
 */
class EpubBookSource @JvmOverloads constructor(
    bookId: String,
    input: ReaderInput,
    cacheDirectory: File,
    options: EpubTextBookOptions = EpubTextBookOptions()
) : BookResourceSource, BookLinkSource, AutoCloseable {
    private val delegate = EpubTextBookSource(bookId, input, cacheDirectory, options.copy(contentMode = EpubContentMode.Flow))
    override val bookId get() = delegate.bookId
    val diagnostics get() = delegate.diagnostics
    val navigation get() = delegate.navigation
    override suspend fun loadBook() = delegate.loadBook()
    override suspend fun loadChapter(chapterId: String) = delegate.loadChapter(chapterId)
    override suspend fun loadResource(resourceId: String, bookRevision: String?, maxBytes: Int) =
        delegate.loadResource(resourceId, bookRevision, maxBytes)
    override suspend fun resolveLink(target: LinkTarget.Internal, bookRevision: String?) = delegate.resolveLink(target, bookRevision)
    suspend fun resolveNavigation(entry: EpubTocEntry) = delegate.resolveNavigation(entry)
    override fun close() = delegate.close()
}
