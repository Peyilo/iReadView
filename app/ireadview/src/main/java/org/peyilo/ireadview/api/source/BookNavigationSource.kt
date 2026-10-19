package org.peyilo.ireadview.api.source

import org.peyilo.ireadview.api.model.ReadingPosition
import org.peyilo.ireadview.api.model.immutableList

/** Format-independent table-of-contents target, including multiple fragments in one chapter. */
class BookNavigationEntry(val title: String, val position: ReadingPosition?, children: List<BookNavigationEntry> = emptyList()) {
    val children = immutableList(children)
}

interface BookNavigationSource : BookSource {
    suspend fun loadNavigation(): List<BookNavigationEntry>
}
