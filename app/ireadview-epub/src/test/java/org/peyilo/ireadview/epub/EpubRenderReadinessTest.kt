package org.peyilo.ireadview.epub

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpubRenderReadinessTest {
    @Test fun cancelledPrefetchWaiterDoesNotRestartTheRenderFence() = runTest {
        val rendered = CompletableDeferred<Unit>()
        var calls = 0
        val readiness = EpubRenderReadiness(backgroundScope) { calls++; rendered.await() }
        readiness.changed()
        val prefetch = launch { readiness.awaitReady() }
        runCurrent()
        prefetch.cancelAndJoin()
        assertFalse(rendered.isCancelled)
        rendered.complete(Unit)
        readiness.awaitReady()
        readiness.awaitReady()
        assertEquals(1, calls)
    }
    @Test fun aNewDomRevisionCannotBeSatisfiedByAnOlderRenderCallback() = runTest {
        val frames = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        var calls = 0
        val readiness = EpubRenderReadiness(backgroundScope) { frames[calls++].await() }
        readiness.changed()
        readiness.changed()
        frames[0].complete(Unit)
        val navigation = async { readiness.awaitReady() }
        runCurrent()
        assertFalse(navigation.isCompleted)
        frames[1].complete(Unit)
        navigation.await()
        assertEquals(2, calls)
    }
    @Test fun clearingABodyReleasesItsPendingFence() = runTest {
        val rendered = CompletableDeferred<Unit>()
        val readiness = EpubRenderReadiness(backgroundScope) { rendered.await() }
        readiness.changed()
        val waiting = async { readiness.awaitReady() }
        runCurrent()
        readiness.clear()
        runCurrent()
        assertTrue(waiting.isCancelled)
        readiness.awaitReady()
    }
}
