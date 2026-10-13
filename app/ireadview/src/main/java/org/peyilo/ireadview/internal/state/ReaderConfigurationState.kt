package org.peyilo.ireadview.internal.state

import android.os.Bundle
import org.peyilo.ireadview.api.effect.PageTurnOptions
import org.peyilo.ireadview.api.input.PagingAxis
import org.peyilo.ireadview.api.input.ReaderInputOptions

/** Small configuration values only. No factories, sources, timers or gesture state. */
internal data class ReaderConfigurationState(
    val input: ReaderInputOptions?, val turn: PageTurnOptions?, val builtInEffectId: String?
) {
    fun toBundle() = Bundle().apply {
        putInt("version", 1)
        input?.let { value -> putBundle("input", Bundle().apply {
            putBoolean("gesture", value.gesturePagingEnabled); putBoolean("tap", value.tapPagingEnabled)
            putBoolean("keyboard", value.keyboardPagingEnabled); putBoolean("volume", value.volumeKeyPagingEnabled)
            putString("axis", value.pagingAxis.name); putFloat("edge", value.tapEdgeFraction)
        }) }
        turn?.let { value -> putBundle("turn", Bundle().apply {
            putLong("duration", value.durationMs); putBoolean("reduce", value.reduceMotion)
            putLong("budget", value.bitmapBudgetBytes); putFloat("distance", value.commitDistanceFraction)
            putFloat("velocity", value.flingVelocityDpPerSecond); putLong("interval", value.minimumTurnIntervalMs)
        }) }
        putString("effect", builtInEffectId)
    }
    companion object {
        fun fromBundle(bundle: Bundle?): ReaderConfigurationState? {
            if (bundle == null || bundle.getInt("version") != 1) return null
            val input = bundle.getBundle("input")?.let { data -> runCatching {
                ReaderInputOptions(data.getBoolean("gesture", true), data.getBoolean("tap", true),
                    data.getBoolean("keyboard", true), data.getBoolean("volume", false),
                    PagingAxis.valueOf(data.getString("axis") ?: PagingAxis.Horizontal.name), data.getFloat("edge", 0.3f))
            }.getOrNull() }
            val turn = bundle.getBundle("turn")?.let { data -> runCatching {
                PageTurnOptions(data.getLong("duration", 240), data.getBoolean("reduce", false),
                    data.getLong("budget", 8L * 1024 * 1024), data.getFloat("distance", 0.33f),
                    data.getFloat("velocity", 600f), data.getLong("interval", 0))
            }.getOrNull() }
            return ReaderConfigurationState(input, turn, bundle.getString("effect"))
        }
    }
}
