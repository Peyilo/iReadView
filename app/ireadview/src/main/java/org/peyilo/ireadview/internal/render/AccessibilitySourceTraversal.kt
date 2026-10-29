package org.peyilo.ireadview.internal.render

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.peyilo.ireadview.api.model.*

internal sealed interface AccessibilityTraversalBoundary {
    data class Source(val anchor: ContentAnchor) : AccessibilityTraversalBoundary
    data class CompletedBlock(val blockId: String) : AccessibilityTraversalBoundary
}

/** Pure source lookup shared by presentations; execute on a worker, never borrow View geometry. */
internal object AccessibilitySourceTraversal {
    sealed interface Result {
        data class Found(val position: ReadingPosition, val caret: ContentAnchor) : Result
        data object Exhausted : Result
        data class Unsupported(val position: ReadingPosition? = null) : Result
        data object Invalid : Result
    }

    fun find(
        content: ChapterContent,
        boundary: AccessibilityTraversalBoundary?,
        forward: Boolean,
        granularity: Int,
        checkCancellation: () -> Unit
    ): Result {
        if (granularity != AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER &&
            granularity != AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD &&
            granularity != AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH
        ) return Result.Invalid
        val blocks = content.blocks
        val source = (boundary as? AccessibilityTraversalBoundary.Source)?.anchor
        val blockId =
            source?.blockId ?: (boundary as? AccessibilityTraversalBoundary.CompletedBlock)?.blockId
        var index = blockId?.let(content::blockIndexForId)
            ?: if (forward) 0 else blocks.lastIndex
        if (boundary != null && index !in blocks.indices) return Result.Invalid
        if (boundary is AccessibilityTraversalBoundary.CompletedBlock) index += if (forward) 1 else -1
        while (index in blocks.indices) {
            checkCancellation()
            val block = blocks[index]
            val text = when (block) {
                is TextContentBlock -> block.text
                else -> return Result.Unsupported(
                    ReadingPosition(
                        content.bookId, content.chapterId,
                        ContentAnchor(block.blockId, 0), content.revision
                    )
                )
            }
            val offset = if (block.blockId == source?.blockId) source.offset else null
            if (offset != null && !AccessibilityTextTraversal.isCharacterBoundary(
                    text,
                    offset
                )
            ) return Result.Invalid
            val step = AccessibilityTextTraversal.step(
                text,
                offset,
                granularity,
                forward,
                checkCancellation
            )
            checkCancellation()
            if (step != null) {
                return Result.Found(
                    ReadingPosition(
                        content.bookId, content.chapterId,
                        ContentAnchor(block.blockId, step.start), content.revision
                    ),
                    ContentAnchor(block.blockId, if (forward) step.start else step.end)
                )
            }
            index += if (forward) 1 else -1
        }
        return Result.Exhausted
    }
}
