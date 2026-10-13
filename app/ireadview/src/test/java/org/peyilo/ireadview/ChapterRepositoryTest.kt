package org.peyilo.ireadview

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.book.ChapterRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterRepositoryTest {
    @Test fun duplicateConsumersShareLoadingAndOneCancellationDoesNotCancelTheOther() = runTest {
        val source = GatedSource()
        val states = mutableListOf<ChapterState>()
        val repository = ChapterRepository(source, backgroundScope, LoadingOptions(), StandardTestDispatcher(testScheduler)) { _, state -> states.add(state) }
        val first = async { repository.get("one") }
        val second = async { repository.get("one") }
        runCurrent()
        assertEquals(1, source.calls)
        first.cancelAndJoin()
        source.gate.complete(Unit)
        assertTrue(second.await() is ReaderResult.Success)
        assertEquals(1, states.count { it == ChapterState.Loading })
        assertTrue(repository.get("one") is ReaderResult.Success)
        assertEquals(1, source.calls)
        repository.close()
    }

    @Test fun invalidatingDuringLoadCannotPublishTheOldRevision() = runTest {
        val source = GatedSource()
        val states = mutableListOf<ChapterState>()
        val repository = ChapterRepository(source, backgroundScope, LoadingOptions(), StandardTestDispatcher(testScheduler)) { _, state -> states.add(state) }
        val old = async { runCatching { repository.get("one") } }
        runCurrent(); repository.invalidate("one"); source.gate.complete(Unit); runCurrent()
        assertFalse(states.any { it is ChapterState.Loaded })
        old.await()
        assertTrue(repository.get("one") is ReaderResult.Success)
        assertEquals(2, source.calls)
        repository.close()
    }

    private class GatedSource : BookSource {
        override val bookId = "book"
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        override suspend fun loadBook() = BookDescriptor(bookId, "Book", tableOfContents = emptyList())
        override suspend fun loadChapter(chapterId: String): ChapterContent {
            calls++; gate.await()
            return ChapterContent(bookId, chapterId, "1", listOf(TextBlock("p", "Content")))
        }
    }
}
