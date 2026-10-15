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

class ChapterTrailingWhitespaceTest {
    private fun paginate(vararg blocks:TextBlock) = PaginationEngine().paginate(
        ChapterContent("book","chapter",blocks=blocks.toList()),
        BlockLayoutContext(ReadViewport(100,20,1f,1f),ReaderStyle.default()){},listOf(provider),"blank-tail")
    @Test fun trailingBlankParagraphsDoNotCreateAnotherPageOrLoseTheirAnchors() {
        val layout=paginate(TextBlock("body","a\nb"),TextBlock("tail1",""),TextBlock("tail2"," \t　"))
        assertEquals(1,layout.pageCount)
        assertEquals(3,layout.contentSnapshot!!.blocks.size)
        assertEquals(ReaderResult.Success(1),layout.pageForAnchor(ContentAnchor("tail1",0)))
        assertEquals(ReaderResult.Success(1),layout.pageForAnchor(ContentAnchor("tail2",3)))
    }
    @Test fun terminalWhitespaceLinesAndSyntheticEndLineDoNotCreateBlankPage() {
        val layout=paginate(TextBlock("body","a\nb\n\n"))
        assertEquals(1,layout.pageCount)
        assertEquals(ReaderResult.Success(1),layout.pageForAnchor(ContentAnchor("body",5)))
        assertEquals("a\nb\n\n",(layout.contentSnapshot!!.blocks.single() as TextBlock).text)
    }
    @Test fun blankParagraphInsideChapterIsPreserved() {
        val layout=paginate(TextBlock("a","a"),TextBlock("middle",""),TextBlock("b","b"))
        assertEquals(2,layout.pageCount)
        val first=(layout.fragmentsForPage(1) as ReaderResult.Success).value
        assertEquals(listOf("a","middle"),first.map{it.blockId})
    }
    @Test fun entirelyBlankChapterStillHasOneExplicitEmptyPage() {
        val layout=paginate(TextBlock("a",""),TextBlock("b","\n "))
        assertEquals(1,layout.pageCount)
        assertTrue((layout.fragmentsForPage(1) as ReaderResult.Success).value.isEmpty())
        assertEquals(ReaderResult.Success(1),layout.pageForAnchor(ContentAnchor("b",2)))
    }
    private val provider=object:BlockLayoutProvider {
        override val id="line-fixture";override val version="1"
        override fun supports(block:ContentBlock)=block is TextBlock
        override fun measure(block:ContentBlock,context:BlockLayoutContext):BlockLayout {
            val text=(block as TextBlock).text
            val units=ArrayList<BlockLayoutUnit>();var start=0
            for(i in text.indices)if(text[i]=='\n'){units.add(BlockLayoutUnit(10f,start,i+1));start=i+1}
            units.add(BlockLayoutUnit(10f,start,text.length))
            return object:BlockLayout {
                override val blockId=block.blockId;override val spacingAfterPx=0f
                override val units=units.toList()
                override fun fragment(firstUnitIndex:Int,endUnitExclusive:Int):BlockLayoutFragment=object:BlockLayoutFragment {
                    override val blockId=block.blockId
                    override val startOffset=units[firstUnitIndex].startOffset
                    override val endOffset=units[endUnitExclusive-1].endOffset
                    override val heightPx=(endUnitExclusive-firstUnitIndex)*10f
                    override fun draw(canvas:Canvas){}
                }
            }
        }
    }
}
