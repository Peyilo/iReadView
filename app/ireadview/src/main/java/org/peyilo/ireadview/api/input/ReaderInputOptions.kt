package org.peyilo.ireadview.api.input

enum class PagingAxis { Horizontal, Vertical }

/** Widget input policy. Touch coordinates and edge widths are normalized to its content viewport. */
data class ReaderInputOptions(
    val gesturePagingEnabled: Boolean = true,
    val tapPagingEnabled: Boolean = true,
    val keyboardPagingEnabled: Boolean = true,
    val volumeKeyPagingEnabled: Boolean = false,
    val pagingAxis: PagingAxis = PagingAxis.Horizontal,
    val tapEdgeFraction: Float = 0.3f
) {
    init { require(tapEdgeFraction.isFinite() && tapEdgeFraction > 0f && tapEdgeFraction <= 0.5f) }
}
