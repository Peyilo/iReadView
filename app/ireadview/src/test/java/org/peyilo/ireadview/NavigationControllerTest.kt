package org.peyilo.ireadview

import android.view.View
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ireadview.api.event.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.container.NavigationController
import org.peyilo.ireadview.spi.presentation.*

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationControllerTest {
    @Test fun prepareAndShowDoNotPublishUntilBothHaveFinished() = runTest {
        val commits = mutableListOf<ReadLocationChange<Int>>()
        val controller = NavigationController<Int>({ 1L }, { commits.add(it) }, {}, { _, _ -> })
        val gate = CompletableDeferred<Unit>()
        val adapter = FakeAdapter(1, 1L, gate)
        val operation = async { controller.navigate(adapter, ReadNavigation.Next, NavigationOptions(), PositionChangeReason.ProgrammaticNavigation) }
        runCurrent()
        assertNull(controller.location)
        assertTrue(commits.isEmpty())
        gate.complete(Unit)
        assertEquals(NavigationResult.Moved(null, 1), operation.await())
        assertEquals(1, controller.location)
        assertEquals(1, commits.size)
        assertEquals(1, adapter.disposed)
    }

    @Test fun cancellingDuringShowDisposesTheCandidateAndNeverCommits() = runTest {
        val commits = mutableListOf<ReadLocationChange<Int>>()
        val controller = NavigationController<Int>({ 1L }, { commits.add(it) }, {}, { _, _ -> })
        val adapter = FakeAdapter(1, 1L, CompletableDeferred())
        val operation = launch { controller.navigate(adapter, ReadNavigation.Next, NavigationOptions(), PositionChangeReason.UserNavigation) }
        runCurrent(); operation.cancelAndJoin()
        assertNull(controller.location)
        assertTrue(commits.isEmpty())
        assertEquals(1, adapter.disposed)
        assertEquals(ReadNavigationState.Idle, controller.state)
    }

    @Test fun ordinaryReentryIsBusyAndForcedNavigationSupersedesTheOldOwner() = runTest {
        val commits = mutableListOf<ReadLocationChange<Int>>()
        val controller = NavigationController<Int>({ 1L }, { commits.add(it) }, {}, { _, _ -> })
        val first = FakeAdapter(1, 1L, CompletableDeferred())
        val pending = async { controller.navigate(first, ReadNavigation.Next, NavigationOptions(), PositionChangeReason.UserNavigation) }
        runCurrent()
        val second = FakeAdapter(2, 1L)
        assertEquals(NavigationResult.Rejected(ReaderRejection.Busy), controller.navigate(second, ReadNavigation.Next, NavigationOptions(), PositionChangeReason.UserNavigation))
        assertEquals(NavigationResult.Moved(null, 2), controller.navigate(second, ReadNavigation.Next, NavigationOptions(force = true), PositionChangeReason.UserNavigation))
        assertEquals(NavigationResult.Superseded, pending.await())
        assertEquals(listOf(2), commits.map { it.to })
        assertEquals(1, first.disposed)
    }

    @Test fun aLateCandidateCannotCommitAfterGenerationChanges() = runTest {
        var generation = 1L
        val commits = mutableListOf<ReadLocationChange<Int>>()
        val controller = NavigationController<Int>({ generation }, { commits.add(it) }, {}, { _, _ -> })
        val gate = CompletableDeferred<Unit>()
        val adapter = FakeAdapter(1, 1L, gate)
        val operation = async { controller.navigate(adapter, ReadNavigation.Next, NavigationOptions(), PositionChangeReason.UserNavigation) }
        runCurrent(); generation = 2; gate.complete(Unit)
        assertEquals(NavigationResult.Superseded, operation.await())
        assertNull(controller.location)
        assertTrue(commits.isEmpty())
        assertEquals(1, adapter.disposed)
    }

    private class FakeAdapter(private val value: Int, private val epoch: Long, private val gate: CompletableDeferred<Unit>? = null) : ReadContentAdapter<Int> {
        var disposed = 0
        override val contentView: View get() = error("Controller must not touch Android Views")
        override fun attach(host: ReadContentHost<Int>) {}
        override fun onViewportChanged(viewport: ReadViewport) {}
        override suspend fun prepare(target: ReadNavigation<Int>): ReadPreparation<Int> = ReadPreparation.Ready(object : PreparedRead<Int> {
            override val target = value
            override val generation = epoch
            override fun dispose() { disposed++ }
        })
        override suspend fun show(candidate: PreparedRead<Int>): Int { gate?.await(); return value }
        override fun detach() {}
    }
}
