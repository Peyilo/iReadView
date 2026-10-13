package org.peyilo.ireadview.api.effect

import org.peyilo.ireadview.spi.presentation.ReadViewport

/** Coordinates use a canonical page with its turning edge on the right, including RTL.
 * The default point moves relative to the initial touch, so centre starts do not jump.
 */
data class CurlControlContext(
    val defaultControlPoint: TurnPoint,
    val touchOrigin: TurnPoint?,
    val touch: TurnPoint?,
    val sheetProgress: Float,
    val direction: TurnDirection,
    val viewport: ReadViewport
)

/** Pure main-thread geometry policy; do not navigate or retain page surfaces here.
 * For continuous animation return a continuous point, with x=viewport.widthPx at sheetProgress=0
 * and x=-viewport.widthPx at sheetProgress=1. These are sheet coordinates, also for backward turns.
 * Apple uses the point's y relative to touchOrigin; Google uses only x.
 */
fun interface CurlControlPointPolicy {
    fun resolve(context: CurlControlContext): TurnPoint
}

private object DefaultCurlControl : CurlControlPointPolicy {
    override fun resolve(context: CurlControlContext) = context.defaultControlPoint
}

/** Shared appearance/quality controls for the two built-in curl families. */
data class CurlOptions @JvmOverloads constructor(
    /** Apple: peak reference radius as a fraction of page width; the reference radius
     * is bounded by pull distance/binding; the cone's local radius varies. Google: normalized wave
     * amplitude scale (2.25 times this value), also used by the reference's width compression.
     * Google fits the visible reference travel to the full control-point interval so larger
     * amplitudes do not remove the sheet before the gesture endpoint.
     * The two families do not share a physical radius. */
    val radiusFraction: Float = 0.08f,
    /** iBook软阴影的最大高斯柔和尺度(dp)，实际随卷起高度缩小；端部平滑衰减。
     * Google仍用于波形边缘的渐变宽度。0关闭阴影，默认22，允许0–128。 */
    val shadowWidthDp: Float = 22f,
    /** 阴影峰值强度上限，默认0.25，建议0.15–0.30；iBook再按卷起高度和可见边长衰减。
     * 0关闭阴影，允许0–1。 */
    val shadowAlpha: Float = 0.25f,
    /** Apple reverse face RGB tint; its alpha channel is ignored, snapshot alpha is preserved.
     * Google renders a travelling front face and does not use reverse-face controls. */
    val backTintColor: Int = 0xffeee9df.toInt(),
    /** Apple reverse-face content/tint mix, not whole-sheet opacity. */
    val backContentAlpha: Float = 0.3f,
    /** Angular segments per Apple visible curved face on API29+, horizontal cells on
     * its older bitmap-mesh path and on Google. Flat Apple regions need no subdivisions. */
    val meshColumns: Int = 64,
    /** Vertical cells for Google and Apple's older bitmap-mesh path. Apple's API29+
     * indexed cone/cylinder interpolates along straight generators and does not use rows.
     * The older horizontal-cylinder limit uses one row regardless of this value. */
    val meshRows: Int = 48,
    val controlPointPolicy: CurlControlPointPolicy = DefaultCurlControl
) {
    init {
        require(radiusFraction.isFinite() && radiusFraction in 0.005f..0.3f)
        require(shadowWidthDp.isFinite() && shadowWidthDp in 0f..128f)
        require(shadowAlpha.isFinite() && shadowAlpha in 0f..1f)
        require(backContentAlpha.isFinite() && backContentAlpha in 0f..1f)
        require(meshColumns in 8..128 && meshRows in 4..128)
    }
}
