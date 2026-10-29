package org.peyilo.ireadview.internal.presentation

import org.peyilo.ireadview.api.model.ChapterContent
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.internal.render.AccessibilityTraversalAnchor
import org.peyilo.ireadview.spi.presentation.BookPresentationHost

/** Keep source identity, not a retired virtual node. Built-in fingerprints were computed on IO. */
internal fun preserveAccessibilityAnchor(
    anchor: ContentAnchor, content: ChapterContent, generation: Long,
    host: BookPresentationHost, currentContent: () -> ChapterContent?
): AccessibilityTraversalAnchor? {
    val book = host.book ?: return null
    if (generation != host.generation || book.bookId != content.bookId) return null
    val chapterId = content.chapterId
    val revision = content.revision
    val fingerprint = content.contentFingerprint
    val identity = if (revision == null && fingerprint == null) content else null
    return AccessibilityTraversalAnchor(anchor) {
        val shown = currentContent()
        generation == host.generation && host.book === book && shown != null &&
                shown.bookId == book.bookId && shown.chapterId == chapterId && shown.revision == revision &&
                when {
                    fingerprint != null -> shown.contentFingerprint == fingerprint
                    identity != null -> shown === identity
                    else -> true // A non-null source revision is the host's immutable-content contract.
                }
    }
}
