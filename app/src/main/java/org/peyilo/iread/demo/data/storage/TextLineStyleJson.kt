package org.peyilo.iread.demo.data.storage

import org.json.JSONObject
import org.peyilo.ireadview.api.style.*

/** Persistence codec only; Demo no longer has line-style preference controls. */
internal object TextLineStyleJson {
    fun encode(style: TextLineStyle) = JSONObject().put("pattern", style.pattern.name).put("color", style.color)
        .put("thickness", style.thicknessDp.toDouble()).put("amplitude", style.waveAmplitudeDp.toDouble())
        .put("wavelength", style.waveLengthDp.toDouble()).put("dash", style.dashLengthDp.toDouble()).put("gap", style.gapDp.toDouble())
    fun decode(data: JSONObject) = TextLineStyle(TextLinePattern.valueOf(data.getString("pattern")), data.getInt("color"),
        data.getDouble("thickness").toFloat(), data.getDouble("amplitude").toFloat(), data.getDouble("wavelength").toFloat(),
        data.getDouble("dash").toFloat(), data.getDouble("gap").toFloat())
}
