package org.peyilo.ireadview.api.source

import org.peyilo.ireadview.api.model.LinkTarget
import org.peyilo.ireadview.api.model.ReadingPosition

/** Optional source capability, called on IO workers. Caller owns the returned bytes.
 * Fail before allocating beyond maxBytes; reject a stale bookRevision. Cooperate with cancellation.
 * Resource IDs are scoped to this source; implementations own authorization and transport.
 */
interface BookResourceSource : BookSource {
    suspend fun loadResource(resourceId: String, bookRevision: String?, maxBytes: Int): ByteArray
}

/** Optional source capability. Null means an unresolved target; never guess another position. */
interface BookLinkSource : BookSource {
    suspend fun resolveLink(target: LinkTarget.Internal, bookRevision: String?): ReadingPosition?
}
