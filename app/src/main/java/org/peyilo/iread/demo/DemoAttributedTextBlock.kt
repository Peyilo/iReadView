package org.peyilo.iread.demo

import org.peyilo.ireadview.api.model.TextContentBlock

/** Host metadata remains outside the source text; no custom provider is needed for body styling. */
internal data class DemoAttributedTextBlock(
    override val blockId: String,
    override val text: String,
    val sourceTag: String
) : TextContentBlock {
    init { require(blockId.isNotBlank() && sourceTag.isNotBlank()) }
    override val estimatedRetainedBytes =
        256L + 2L * (blockId.length.toLong() + text.length + sourceTag.length)
}
