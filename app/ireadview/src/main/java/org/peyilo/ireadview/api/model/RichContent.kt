package org.peyilo.ireadview.api.model

/** A source-local resource identity, never a filesystem path interpreted by the Widget. */
sealed interface LinkTarget {
    data class Internal(val resourceId: String, val fragment: String? = null) : LinkTarget {
        init { require(resourceId.isNotBlank()) }
    }
    data class External(val uri: String) : LinkTarget {
        init {
            require(uri.length <= 4096)
            val parsed = java.net.URI(uri)
            require(parsed.scheme?.lowercase(java.util.Locale.ROOT) in setOf("https", "http", "mailto"))
            require(!parsed.isOpaque || parsed.scheme.equals("mailto", true))
            if (!parsed.scheme.equals("mailto", true)) require(!parsed.host.isNullOrBlank())
        }
    }
}

/** Half-open UTF-16 offsets into LinkedTextBlock.text. */
data class TextLink(val startOffset: Int, val endExclusive: Int, val target: LinkTarget) {
    init { require(startOffset >= 0 && endExclusive > startOffset) }
}

class LinkedTextBlock @JvmOverloads constructor(
    override val blockId: String,
    override val text: String,
    links: List<TextLink>,
    val headingLevel: Int = 0
) : TextContentBlock {
    val links: List<TextLink> = immutableList(links)
    init {
        require(blockId.isNotBlank() && headingLevel in 0..6)
        var end = 0
        this.links.forEach {
            require(it.startOffset >= end && it.endExclusive <= text.length) { "Links must be ordered, disjoint and inside source text" }
            fun boundary(offset: Int) = offset == 0 || offset == text.length ||
                !(Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset]))
            require(boundary(it.startOffset) && boundary(it.endExclusive))
            end = it.endExclusive
        }
    }
    override val estimatedRetainedBytes: Long get() = 256L + blockId.length * 2L + text.length * 2L +
        links.sumOf { 96L + it.target.retainedBytes() }
}

/** A graphic has an anchor at offset zero, but no selectable source text. */
data class ImageBlock @JvmOverloads constructor(
    override val blockId: String,
    val resourceId: String,
    val alternativeText: String = "",
    val link: LinkTarget? = null
) : ContentBlock {
    init { require(blockId.isNotBlank() && resourceId.isNotBlank()) }
    override val estimatedRetainedBytes: Long get() = 256L + (blockId.length + resourceId.length + alternativeText.length) * 2L +
        (link?.retainedBytes() ?: 0L)
}

private fun LinkTarget.retainedBytes(): Long = when (this) {
    is LinkTarget.Internal -> 96L + resourceId.length * 2L + (fragment?.length ?: 0) * 2L
    is LinkTarget.External -> 64L + uri.length * 2L
}
