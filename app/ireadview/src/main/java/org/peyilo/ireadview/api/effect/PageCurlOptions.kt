package org.peyilo.ireadview.api.effect

/** 当前卷页效果的外观参数，默认值取自用户于2026-10-13调节的Web页面。
 * 翻页进度由PageTurnFrame/Widget动画时钟控制，范围0–1；Web观察值0.275
 * 仅代表一个中间帧，不作为动画初始值或固定外观参数。
 */
data class PageCurlOptions @JvmOverloads constructor(
    /** 卷曲半径占页宽的比例。建议0.08–0.14，默认0.10；越大卷边越宽。允许0.04–0.20。 */
    val radiusFraction: Float = .10f,
    /** 当前页与下一页共用的投影强度。建议0.15–0.30，默认0.25；0关闭投影。允许0–0.75。 */
    val shadowStrength: Float = .25f,
    /** 面积光源大小，控制半影柔和度。建议0.30–0.50，默认0.43；越大越柔和。允许0.02–0.65。 */
    val lightSize: Float = .43f,
    /** 初始卷边倾斜，默认0.55。建议-0.60–0.60；0为水平卷轴，正负改变倾斜方向。
     * 拖动的纵向偏移叠加到此值；接近装订时自动淡出倾斜。允许-0.80–0.80。 */
    val tilt: Float = .55f
) {
    init {
        require(radiusFraction.isFinite() && radiusFraction in .04f.. .20f)
        require(shadowStrength.isFinite() && shadowStrength in 0f.. .75f)
        require(lightSize.isFinite() && lightSize in .02f.. .65f)
        require(tilt.isFinite() && tilt in -.8f.. .8f)
    }
    /** 返回卷边宽度修改后的配置；建议0.08–0.14，默认0.10。 */
    fun withRadiusFraction(value: Float) = copy(radiusFraction = value)
    /** 返回投影强度修改后的配置；建议0.15–0.30，默认0.25，两接收面统一生效。 */
    fun withShadowStrength(value: Float) = copy(shadowStrength = value)
    /** 返回柔和度修改后的配置；建议0.30–0.50，默认0.43，值越大软边越宽。 */
    fun withLightSize(value: Float) = copy(lightSize = value)
    /** 返回初始倾斜修改后的配置；建议-0.60–0.60，默认0.55，0关闭初始倾斜。 */
    fun withTilt(value: Float) = copy(tilt = value)
}
