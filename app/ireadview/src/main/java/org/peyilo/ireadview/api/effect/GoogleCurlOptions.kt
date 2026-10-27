package org.peyilo.ireadview.api.effect

import org.peyilo.ireadview.spi.presentation.ReadViewport

/** Control coordinates use a canonical page whose turning edge is on the right, including RTL. */
data class GoogleCurlControlContext(
    val defaultControlPoint: TurnPoint,
    val touchOrigin: TurnPoint?,
    val touch: TurnPoint?,
    val sheetProgress: Float,
    val direction: TurnDirection,
    val viewport: ReadViewport
)

/** Pure main-thread policy. Return a continuous x from width at progress0 to -width at progress1.
 * GoogleCurl uses x for its travelling wave; do not navigate or retain page surfaces here.
 */
fun interface GoogleCurlControlPointPolicy {
    fun resolve(context: GoogleCurlControlContext): TurnPoint
}

private object DefaultGoogleCurlControl : GoogleCurlControlPointPolicy {
    override fun resolve(context: GoogleCurlControlContext) = context.defaultControlPoint
}

/** Quality and appearance of the Google/MoonReader front-face wave. */
data class GoogleCurlOptions @JvmOverloads constructor(
    /** Wave amplitude is 2.25 times this fraction; visible travel is fitted to the gesture range. */
    val radiusFraction: Float = .08f,
    /** Gradient width at the travelling edge, in dp. Zero disables the edge shadow. */
    val shadowWidthDp: Float = 22f,
    val shadowAlpha: Float = .25f,
    val meshColumns: Int = 64,
    val meshRows: Int = 48,
    val controlPointPolicy: GoogleCurlControlPointPolicy = DefaultGoogleCurlControl
) {
    init {
        require(radiusFraction.isFinite() && radiusFraction in 0.005f..0.3f)
        require(shadowWidthDp.isFinite() && shadowWidthDp in 0f..128f)
        require(shadowAlpha.isFinite() && shadowAlpha in 0f..1f)
        require(meshColumns in 8..128 && meshRows in 4..128)
    }
}
