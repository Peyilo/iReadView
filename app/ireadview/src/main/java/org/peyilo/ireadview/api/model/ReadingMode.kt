package org.peyilo.ireadview.api.model

/** Stable presentation identity, including modes supplied by a Widget subclass. */
class ReadingMode private constructor(val id: String) {
    override fun equals(other: Any?) = other is ReadingMode && id == other.id
    override fun hashCode() = id.hashCode()
    override fun toString() = id
    companion object {
        @JvmField val PAGED = ReadingMode("paged")
        @JvmField val CONTINUOUS_SCROLL = ReadingMode("continuous_scroll")
        @JvmStatic fun custom(id: String): ReadingMode {
            require(id.isNotBlank())
            return ReadingMode(id)
        }
    }
}
