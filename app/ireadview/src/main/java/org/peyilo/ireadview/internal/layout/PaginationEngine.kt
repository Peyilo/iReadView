package org.peyilo.ireadview.internal.layout

import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.spi.content.*
import org.peyilo.ireadview.spi.presentation.ChapterLayout
import org.peyilo.ireadview.spi.presentation.ContinuousFragment
import org.peyilo.ireadview.internal.cache.RetainedSize
import org.peyilo.ireadview.internal.selection.TextCharacterBoundaries

/** Packs whole layout units and coalesces adjacent lines into a single clipped draw. */
internal class PaginationEngine {
    fun paginate(
        content: ChapterContent,
        context: BlockLayoutContext,
        providers: List<BlockLayoutProvider>,
        key: String
    ): ChapterLayout {
        require(context.viewport.isValid) { "Invalid body viewport" }
        val pages = ArrayList<List<BlockLayoutFragment>>()
        val continuous = ArrayList<ContinuousFragment>()
        val anchorPages = HashMap<String, MutableList<AnchorPage>>()
        var fragments = ArrayList<BlockLayoutFragment>()
        var y = 0f
        var retainedBytes = RetainedSize.content(content, context.checkCancellation)
        fun endPage() {
            if (fragments.isNotEmpty()) {
                pages.add(immutableList(fragments)); fragments = ArrayList(); y = 0f
            }
        }
        // Source snapshots keep blank lines/IDs. Only terminal whitespace is
        // excluded from visual pagination; interior blank paragraphs remain real.
        val lastVisibleBlock = content.blocks.indexOfLast { block ->
            context.checkCancellation()
            block !is TextContentBlock || block.text.any { !it.isWhitespace() }
        }
        content.blocks.forEachIndexed { blockIndex, block ->
            context.checkCancellation()
            if (blockIndex > lastVisibleBlock && block is TextContentBlock) {
                val lastPage = maxOf(1, pages.size + if (fragments.isNotEmpty()) 1 else 0)
                anchorPages.getOrPut(block.blockId) { ArrayList() }
                    .add(AnchorPage(lastPage, 0, block.text.length))
                return@forEachIndexed
            }
            val provider = providers.firstOrNull { context.checkCancellation(); it.supports(block) }
                ?: throw IllegalArgumentException("Unsupported block: ${block.javaClass.name}")
            context.checkCancellation()
            val layout = measurePaddedBlock(provider, block, context)
            context.checkCancellation()
            retainedBytes = retainedBytes?.let { total ->
                layout.estimatedRetainedBytes?.takeIf { it >= 0 }
                    ?.let { RetainedSize.add(total, it) }
            }
            require(layout.blockId == block.blockId) { "Provider changed block identity" }
            require(layout.spacingAfterPx.isFinite() && layout.spacingAfterPx >= 0f) { "Provider returned invalid paragraph spacing" }
            val units = layout.units
            val textLength = (block as? TextContentBlock)?.text?.length
            for (unit in units) {
                context.checkCancellation()
                validateOffsets(unit.startOffset, unit.endOffset, textLength)
            }
            var unitLimit = units.size
            if (blockIndex == lastVisibleBlock && block is TextContentBlock) {
                // StaticLayout can produce a final empty line for a terminal
                // newline; explicit whitespace-only lines have the same issue.
                // Unknown provider offsets are not treated as invisible content.
                while (unitLimit > 0) {
                    context.checkCancellation()
                    val unit = units[unitLimit - 1]
                    val from = unit.startOffset ?: break
                    val to = unit.endOffset ?: break
                    if ((from until to).any { !block.text[it].isWhitespace() }) break
                    unitLimit--
                }
            }
            var first = 0
            while (first < unitLimit) {
                context.checkCancellation()
                var last = first
                var height = 0f
                while (last < unitLimit && y + height + units[last].heightPx <= context.viewport.heightPx) {
                    context.checkCancellation()
                    height += units[last].heightPx
                    last++
                }
                if (last == first) {
                    if (fragments.isNotEmpty()) {
                        endPage(); continue
                    }
                    throw IllegalArgumentException("A content line/fragment exceeds the body viewport height")
                }
                val fragment = layout.fragment(first, last)
                context.checkCancellation()
                require(fragment.blockId == block.blockId) { "Provider fragment changed block identity" }
                val start = fragment.startOffset
                val end = fragment.endOffset
                validateOffsets(start, end, textLength)
                require(fragment.leftPx.isFinite() && fragment.topPx.isFinite()) { "Provider returned invalid fragment origin" }
                require(fragment.heightPx.isFinite() && fragment.heightPx > 0 && fragment.heightPx <= context.viewport.heightPx - y + 1) { "Provider returned invalid fragment geometry" }
                fragments.add(PositionedFragment(fragment, y))
                continuous.add(
                    ContinuousFragment(
                        fragment,
                        if (last == unitLimit) layout.spacingAfterPx else 0f
                    )
                )
                anchorPages.getOrPut(block.blockId) { ArrayList() }
                    .add(AnchorPage(pages.size + 1, start ?: 0, end ?: 0))
                y += fragment.heightPx
                first = last
                if (first < unitLimit) endPage()
            }
            y += layout.spacingAfterPx
        }
        endPage()
        context.checkCancellation()
        if (pages.isEmpty()) pages.add(emptyList())
        retainedBytes = retainedBytes?.let {
            RetainedSize.add(
                it,
                512L + pages.size.toLong() * 64 + continuous.size.toLong() * 320 + anchorPages.size.toLong() * 128
            )
        }
        val frozenAnchorPages = anchorPages.mapValues { (_, entries) ->
            context.checkCancellation()
            immutableList(entries)
        }
        context.checkCancellation()
        return PaginatedChapter(
            content,
            key,
            immutableList(pages),
            immutableList(continuous),
            frozenAnchorPages,
            retainedBytes
        )
    }
}

/** Optional endpoints remain supported, but known offsets cannot escape the source string. */
private fun validateOffsets(start: Int?, end: Int?, textLength: Int?) {
    require(
        (start == null || start >= 0) && (end == null || end >= 0) &&
                (start == null || end == null || end >= start)
    ) { "Provider returned invalid source offsets" }
    if (textLength != null) require(
        (start == null || start <= textLength) &&
                (end == null || end <= textLength)
    ) { "Provider offsets exceed source text" }
}

private class PositionedFragment(
    private val wrapped: BlockLayoutFragment,
    override val topPx: Float
) : BlockLayoutFragment by wrapped

private data class AnchorPage(val pageNumber: Int, val start: Int, val end: Int)

private class PaginatedChapter(
    private val content: ChapterContent,
    override val layoutKey: String,
    private val pages: List<List<BlockLayoutFragment>>,
    private val continuous: List<ContinuousFragment>,
    private val anchorPages: Map<String, List<AnchorPage>>,
    override val estimatedRetainedBytes: Long?
) : ChapterLayout {
    override val chapterId = content.chapterId
    override val contentRevision = content.revision
    override val contentSnapshot get() = content
    override val pageCount get() = pages.size
    override fun continuousFragments(): ReaderResult<List<ContinuousFragment>> =
        ReaderResult.Success(continuous)

    override fun rangesForText(range: TextRange): ReaderResult<List<BlockTextRange>> {
        if (range.bookId != content.bookId) return ReaderResult.Rejected(ReaderRejection.BookMismatch)
        if (range.chapterId != chapterId || range.contentRevision != null && range.contentRevision != contentRevision) return ReaderResult.Rejected(
            ReaderRejection.InvalidAnchor
        )
        val first = content.blockIndexForId(range.start.blockId)
        val last = content.blockIndexForId(range.endExclusive.blockId)
        if (first !in 0..last) return ReaderResult.Rejected(ReaderRejection.InvalidAnchor)
        val ranges = ArrayList<BlockTextRange>()
        for (index in first..last) {
            val block = content.blocks[index]
            val text = when (block) {
                is TextContentBlock -> block.text; else -> return ReaderResult.Rejected(
                    ReaderRejection.Unsupported
                )
            }
            val from = if (index == first) range.start.offset else 0
            val to = if (index == last) range.endExclusive.offset else text.length
            if (!TextCharacterBoundaries.isRange(text, from, to)) return ReaderResult.Rejected(
                ReaderRejection.InvalidAnchor
            )
            ranges.add(BlockTextRange(block.blockId, from, to))
        }
        return ReaderResult.Success(immutableList(ranges))
    }

    override fun pageForAnchor(anchor: ContentAnchor): ReaderResult<Int> {
        val block = content.blockForId(anchor.blockId) ?: return ReaderResult.Rejected(
            ReaderRejection.InvalidAnchor
        )
        val validOffset = when (block) {
            is TextContentBlock -> TextCharacterBoundaries.isBoundary(block.text, anchor.offset)
            else -> anchor.offset == 0
        }
        if (!validOffset) return ReaderResult.Rejected(ReaderRejection.InvalidAnchor)
        var lastMatch: Int? = null
        anchorPages[anchor.blockId].orEmpty().forEach { entry ->
            lastMatch = entry.pageNumber
            if (anchor.offset >= entry.start && (anchor.offset < entry.end || entry.start == entry.end && anchor.offset == entry.start))
                return ReaderResult.Success(entry.pageNumber)
        }
        return lastMatch?.let { ReaderResult.Success(it) }
            ?: ReaderResult.Rejected(ReaderRejection.InvalidAnchor)
    }

    override fun anchorForPage(pageNumber: Int): ReaderResult<ContentAnchor?> =
        if (pageNumber in 1..pageCount) ReaderResult.Success(pages[pageNumber - 1].firstOrNull()?.anchor)
        else ReaderResult.Rejected(ReaderRejection.InvalidPage)

    override fun fragmentsForPage(pageNumber: Int): ReaderResult<List<BlockLayoutFragment>> =
        if (pageNumber in 1..pageCount) ReaderResult.Success(pages[pageNumber - 1])
        else ReaderResult.Rejected(ReaderRejection.InvalidPage)
}
