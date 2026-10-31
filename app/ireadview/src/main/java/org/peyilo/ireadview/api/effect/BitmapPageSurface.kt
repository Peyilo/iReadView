package org.peyilo.ireadview.api.effect

/**
 * Optional prepared pixels equivalent to drawing this surface at the requested dimensions. Called
 * on the Widget thread; acquire quickly without navigating or reconfiguring the Widget. Return null
 * to use draw(). A successful lease must contain display-oriented, premultiplied ARGB_8888 sRGB
 * pixels of exactly the requested size. Pixels must remain unchanged until the lease closes,
 * including after source invalidation or detachment. Consumers only read the bitmap; they must not
 * modify its pixels or metadata. The snapshot host charges the borrowed allocation to its existing
 * budget and keeps the provider lease alive until every asynchronous reader has finished.
 * Noncompact allocations may fall back to draw() so borrowing cannot lower capture resolution.
 */
interface BitmapPageSurface : PageSurface {
    fun acquireSnapshot(widthPx: Int, heightPx: Int): BitmapLease?
}
