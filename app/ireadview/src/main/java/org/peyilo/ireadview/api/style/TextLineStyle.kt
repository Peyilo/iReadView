package org.peyilo.ireadview.api.style

enum class TextLinePattern { STRAIGHT, WAVY, DASHED, DOTTED, DOUBLE }

/** Immutable underline appearance; all dimensions are dp, color is ARGB.
 * Suggested thickness 1..3, wave amplitude 1..3, wavelength 6..12, dash 4..8, gap 2..4.
 */
data class TextLineStyle @JvmOverloads constructor(
    val pattern: TextLinePattern = TextLinePattern.STRAIGHT,
    val color: Int = 0xfff66b22.toInt(),
    val thicknessDp: Float = 1.5f,
    val waveAmplitudeDp: Float = 1.5f,
    val waveLengthDp: Float = 8f,
    val dashLengthDp: Float = 6f,
    val gapDp: Float = 3f
) {
    init {
        require(listOf(thicknessDp, waveLengthDp, dashLengthDp, gapDp).all { it.isFinite() && it > 0 })
        require(waveAmplitudeDp.isFinite() && waveAmplitudeDp >= 0)
    }
}
