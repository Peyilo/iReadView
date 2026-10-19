package org.peyilo.ireadview

import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ireadview.api.model.ReaderPageCacheMode
import org.peyilo.ireadview.spi.presentation.ReaderPageCache

class ReaderPageCacheTest {
    @Test fun takingTransfersOwnershipAndReservingNeverEvictsTheTakenPage() {
        val released=mutableListOf<Any>()
        val cache=ReaderPageCache<Any>(ReaderPageCacheMode.THREE_PAGE) { released.add(it) }
        val previous=Any();val next=Any()
        cache.retain(previous);cache.retain(next)
        assertSame(next,cache.take { it === next })
        cache.reserveCandidate(true)
        cache.clear()
        assertEquals(listOf(previous),released)
        assertFalse(released.any { it === next })
    }
    @Test fun switchingToSingleReleasesSparesAndDisabledRetentionReleasesImmediately() {
        val released=mutableListOf<Any>()
        val cache=ReaderPageCache<Any>(ReaderPageCacheMode.THREE_PAGE) { released.add(it) }
        val a=Any();val b=Any();val c=Any()
        cache.retain(a);cache.retain(b)
        cache.configure(ReaderPageCacheMode.SINGLE_PAGE)
        cache.retain(c);cache.clear()
        assertEquals(listOf(a,b,c),released)
        assertTrue(cache.entries.isEmpty())
        cache.configure(ReaderPageCacheMode.THREE_PAGE)
        assertTrue(cache.enabled)
    }
    @Test fun newCandidateReservationBoundsLiveRootsAndDuplicateRetentionDoesNotDoubleRelease() {
        val released=mutableListOf<Any>()
        val cache=ReaderPageCache<Any>(ReaderPageCacheMode.THREE_PAGE) { released.add(it) }
        val a=Any();val b=Any()
        cache.retain(a);cache.retain(b);cache.retain(b)
        cache.reserveCandidate(true)
        assertEquals(listOf(a),released)
        assertEquals(listOf(b),cache.entries)
        cache.clear();cache.clear()
        assertEquals(listOf(a,b),released)
    }
}
