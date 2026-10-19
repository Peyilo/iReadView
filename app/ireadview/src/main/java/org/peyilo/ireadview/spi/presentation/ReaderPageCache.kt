package org.peyilo.ireadview.spi.presentation

import org.peyilo.ireadview.api.model.ReaderPageCacheMode

/** Main-thread owned spare pages, shared by body backends. Active/candidate pages belong to
 * the navigation transaction, never this cache. Taking transfers ownership to the caller;
 * retaining transfers it back. Eviction and clear release each owned page exactly once.
 */
class ReaderPageCache<T : Any>(mode: ReaderPageCacheMode, private val release: (T) -> Unit) {
    private val pages = mutableListOf<T>()
    var mode: ReaderPageCacheMode = mode
        private set
    val entries: List<T> get() = pages.toList()
    val enabled get() = mode == ReaderPageCacheMode.THREE_PAGE
    fun configure(mode: ReaderPageCacheMode) { this.mode=mode; trim(mode.retainedPageCount-1) }
    fun contains(page: T) = pages.any { it === page }
    fun take(predicate: (T) -> Boolean): T? {
        val index=pages.indexOfFirst(predicate)
        return if(index<0)null else pages.removeAt(index)
    }
    fun retain(page: T) {
        if(!contains(page))pages.add(page)
        trim(mode.retainedPageCount-1)
    }
    /** Reserve room before creating an additional page while the current page stays mounted. */
    fun reserveCandidate(hasCurrent: Boolean) = trim((mode.retainedPageCount-if(hasCurrent)2 else 1).coerceAtLeast(0))
    fun discard(predicate: (T) -> Boolean) {
        val removed=pages.filter(predicate)
        pages.removeAll { page -> removed.any { it === page } }
        removed.forEach(release)
    }
    fun clear() { val old=pages.toList();pages.clear();old.forEach(release) }
    private fun trim(capacity: Int) { while(pages.size>capacity)release(pages.removeAt(0)) }
}
