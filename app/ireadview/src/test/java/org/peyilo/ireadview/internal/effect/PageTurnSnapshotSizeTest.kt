package org.peyilo.ireadview.internal.effect

import org.junit.Assert.*
import org.junit.Test

class PageTurnSnapshotSizeTest {
    @Test
    fun fullResolutionFitsTheExactPairBudget() {
        assertEquals(
            PageTurnSnapshots.Size(1080, 2424),
            PageTurnSnapshots.sizeWithinBudget(1080, 2424, 1080L * 2424 * 8),
        )
    }

    @Test
    fun aSmallerBudgetUsesTheExistingScaleLadder() {
        assertEquals(
            PageTurnSnapshots.Size(810, 1818),
            PageTurnSnapshots.sizeWithinBudget(1080, 2424, 16L * 1024 * 1024),
        )
    }

    @Test
    fun clampedPixelsStillNeedBothPagesToFit() {
        assertNull(PageTurnSnapshots.sizeWithinBudget(1, 1, 7))
        assertEquals(PageTurnSnapshots.Size(1, 1), PageTurnSnapshots.sizeWithinBudget(1, 1, 8))
    }

    @Test
    fun unavailableSizeOrBudgetDoesNotAllocate() {
        assertNull(PageTurnSnapshots.sizeWithinBudget(0, 2424, Long.MAX_VALUE))
        assertNull(PageTurnSnapshots.sizeWithinBudget(1080, 0, Long.MAX_VALUE))
        assertNull(PageTurnSnapshots.sizeWithinBudget(1080, 2424, 0))
    }
}
