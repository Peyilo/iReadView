package org.peyilo.iread.demo.content

import org.peyilo.ireadview.api.model.TextContentBlock

/** Host metadata remains outside the source text; no custom provider is needed for body styling. */
internal data class AttributedTextBlock(
    override val blockId: String,
    override val text: String,
    val sourceTag: String
) : TextContentBlock {
    init { require(blockId.isNotBlank() && sourceTag.isNotBlank()) }
    override val estimatedRetainedBytes =
        256L + 2L * (blockId.length.toLong() + text.length + sourceTag.length)
}
