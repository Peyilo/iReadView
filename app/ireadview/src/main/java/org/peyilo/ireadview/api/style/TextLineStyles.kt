package org.peyilo.ireadview.api.style

/** Java/Kotlin convenience factories. Colors are ARGB and all lengths are dp. */
object TextLineStyles {
    @JvmStatic @JvmOverloads fun straight(color: Int = 0xfff66b22.toInt(), thicknessDp: Float = 1.5f) =
        TextLineStyle(TextLinePattern.STRAIGHT, color, thicknessDp)
    @JvmStatic @JvmOverloads fun wavy(color: Int = 0xfff66b22.toInt(), thicknessDp: Float = 1.5f,
        amplitudeDp: Float = 1.5f, waveLengthDp: Float = 8f) =
        TextLineStyle(TextLinePattern.WAVY, color, thicknessDp, amplitudeDp, waveLengthDp)
    @JvmStatic @JvmOverloads fun dashed(color: Int = 0xfff66b22.toInt(), thicknessDp: Float = 1.5f,
        dashLengthDp: Float = 6f, gapDp: Float = 3f) =
        TextLineStyle(TextLinePattern.DASHED, color, thicknessDp, dashLengthDp = dashLengthDp, gapDp = gapDp)
    @JvmStatic @JvmOverloads fun dotted(color: Int = 0xfff66b22.toInt(), thicknessDp: Float = 1.5f, gapDp: Float = 3f) =
        TextLineStyle(TextLinePattern.DOTTED, color, thicknessDp, gapDp = gapDp)
    @JvmStatic @JvmOverloads fun doubleLine(color: Int = 0xfff66b22.toInt(), thicknessDp: Float = 1.5f, gapDp: Float = 3f) =
        TextLineStyle(TextLinePattern.DOUBLE, color, thicknessDp, gapDp = gapDp)
}
