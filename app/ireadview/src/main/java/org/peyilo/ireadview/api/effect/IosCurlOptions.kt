package org.peyilo.ireadview.api.effect

/** Edge is relative to the paging axis; RTL reading order is mapped by the Android input adapter. */
enum class IosCurlBinding { Minimum, Maximum }

/** Recovered iOS curl algorithm, executed entirely in Kotlin. The single-sheet adapter uses the
 * turning page on both faces; independent recto/verso sources are reserved for spread integration.
 * Density conversion is applied before control/gesture evaluation, so thresholds use logical points.
 */
data class IosCurlOptions(val binding: IosCurlBinding = IosCurlBinding.Minimum)

/** Optional future spread capability. A normal PageSurface continues to describe one page.
 * A spread provider owns page lifetime and ordering; an effect must not fetch adjacent book content.
 */
interface PageTurnSpreadSurface : PageSurface {
    val pageCount: Int
    fun pageSurface(index: Int): PageSurface
}
