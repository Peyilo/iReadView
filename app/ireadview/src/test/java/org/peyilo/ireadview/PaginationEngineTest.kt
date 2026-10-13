package org.peyilo.ireadview

import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.internal.layout.PaginationEngine
import org.peyilo.ireadview.spi.content.*
import org.peyilo.ireadview.spi.presentation.ReadViewport

class PaginationEngineTest {
    @Test fun pageBoundaryAnchorsBelongToTheFollowingPageAndNoTextIsDropped() {
        val content = ChapterContent("book", "one", blocks = listOf(TextBlock("p", "abcdefghijkl"), TextBlock("q", "abcdefghijkl")))
        val layout = PaginationEngine().paginate(content, context(20), listOf(provider), "layout")
        assertEquals(3, layout.pageCount)
        assertEquals(ReaderResult.Success(2), layout.pageForAnchor(ContentAnchor("p", 8)))
        assertEquals(ReaderResult.Success(3), layout.pageForAnchor(ContentAnchor("q", 4)))
        val ranges = (1..layout.pageCount).flatMap { (layout.fragmentsForPage(it) as ReaderResult.Success).value }.map { Triple(it.blockId, it.startOffset, it.endOffset) }
        assertEquals(listOf(Triple("p", 0, 8), Triple("p", 8, 12), Triple("q", 0, 4), Triple("q", 4, 12)), ranges)
        assertEquals(ReaderResult.Rejected(ReaderRejection.InvalidAnchor), layout.pageForAnchor(ContentAnchor("p", 20)))
    }

    @Test fun emptyChapterHasOneExplicitEmptyPage() {
        val layout = PaginationEngine().paginate(ChapterContent("book", "empty", blocks = emptyList()), context(20), listOf(provider), "empty")
        assertEquals(1, layout.pageCount)
        assertEquals(ReaderResult.Success<ContentAnchor?>(null), layout.anchorForPage(1))
    }

    @Test fun oversizedUnitsFailInsteadOfGeneratingAnInfiniteSequenceOfPages() {
        assertThrows(IllegalArgumentException::class.java) {
            PaginationEngine().paginate(ChapterContent("book", "one", blocks = listOf(TextBlock("p", "abcdefghijkl"))), context(5), listOf(provider), "tiny")
        }
    }

    private fun context(height: Int) = BlockLayoutContext(ReadViewport(100, height, 1f, 1f), ReaderStyle.default()) {}
    private val provider = object : BlockLayoutProvider {
        override val id = "test"
        override val version = "1"
        override fun supports(block: ContentBlock) = true
        override fun measure(block: ContentBlock, context: BlockLayoutContext): BlockLayout = object : BlockLayout {
            override val blockId = block.blockId
            override val spacingAfterPx = 0f
            override val units = listOf(BlockLayoutUnit(10f, 0, 4), BlockLayoutUnit(10f, 4, 8), BlockLayoutUnit(10f, 8, 12))
            override fun fragment(firstUnitIndex: Int, endUnitExclusive: Int): BlockLayoutFragment = object : BlockLayoutFragment {
                override val blockId = block.blockId
                override val startOffset = firstUnitIndex * 4
                override val endOffset = endUnitExclusive * 4
                override val heightPx = (endUnitExclusive - firstUnitIndex) * 10f
                override fun draw(canvas: Canvas) = Unit
            }
        }
    }
}
