package org.peyilo.iread.host

import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.source.MemoryBookSource

object SampleBooks {
    @JvmStatic fun create(): MemoryBookSource {
        val chapter = ChapterDescriptor("chapter", "独立宿主接入", "1")
        val book = BookDescriptor("maven-host", "iReadView 示例", "1", listOf(TocEntry.Chapter(chapter)))
        val blocks = buildList<ContentBlock> {
            add(HeadingBlock("heading", chapter.title))
            repeat(30) { index ->
                add(HostTextBlock("paragraph-$index",
                    "阅读器由宿主通过公开接口创建。原文排版、选择和阅读位置由组件处理；来源标签属于宿主元数据。".repeat(6), "section-$index"))
            }
        }
        return MemoryBookSource(book, listOf(ChapterContent(book.bookId, chapter.chapterId, "1", blocks)))
    }
}

private data class HostTextBlock(override val blockId: String, override val text: String,
    val sourceTag: String) : TextContentBlock {
    override val estimatedRetainedBytes = 256L + 2L * (blockId.length.toLong() + text.length + sourceTag.length)
}
