package org.peyilo.iread.demo.content

import org.peyilo.ireadview.api.model.ContentBlock

/** Immutable host content: this graphic has a stable block anchor, but no source text offsets. */
internal data class DividerBlock(override val blockId: String, val description: String) : ContentBlock {
    init { require(blockId.isNotBlank() && description.isNotBlank()) }
    override val estimatedRetainedBytes = 224L + 2L * (blockId.length.toLong() + description.length)
}

