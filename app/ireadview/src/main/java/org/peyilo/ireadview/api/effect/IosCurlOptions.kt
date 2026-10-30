package org.peyilo.ireadview.api.effect

/**
 * Edge is relative to the paging axis; RTL reading order is mapped by the Android input adapter.
 */
enum class IosCurlBinding {
    Minimum,
    Maximum,
}

/**
 * Texture follows normal View composition. Surface draws directly above the reader's window and
 * requires an opaque page in a fixed rectangle without overlapping sibling UI. Translucent inputs
 * keep the bitmap fallback. Both outputs use the same full mesh.
 */
enum class IosCurlOutput {
    Texture,
    Surface,
}

/**
 * Recovered iOS curl algorithm, executed entirely in Kotlin. The single-sheet adapter uses the
 * turning page on both faces; independent recto/verso sources are reserved for spread integration.
 * Density conversion is applied before control/gesture evaluation, so thresholds use logical
 * points.
 */
data class IosCurlOptions
@JvmOverloads
constructor(
    val binding: IosCurlBinding = IosCurlBinding.Minimum,
    /** Width of the fading shadow fringe; 1 retains the recovered mesh profile. */
    val shadowSoftness: Float = 1.35f,
    /** Neutral black source-over shadow. Paper hue is preserved, including dark themes. */
    val shadowOpacity: Float = .55f,
    val output: IosCurlOutput = IosCurlOutput.Texture,
) {
    init {
        require(shadowSoftness.isFinite() && shadowSoftness in 1f..3f)
        require(shadowOpacity.isFinite() && shadowOpacity in 0f..1f)
    }
}

/**
 * Optional future spread capability. A normal PageSurface continues to describe one page. A spread
 * provider owns page lifetime and ordering; an effect must not fetch adjacent book content.
 */
interface PageTurnSpreadSurface : PageSurface {
    val pageCount: Int

    fun pageSurface(index: Int): PageSurface
}
