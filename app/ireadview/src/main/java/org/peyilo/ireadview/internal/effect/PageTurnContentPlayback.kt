package org.peyilo.ireadview.internal.effect

import android.view.View
import org.peyilo.ireadview.spi.presentation.ReadVisualHandoff

/** Owns reversible live-content suppression, independent of the effect backend. */
internal class PageTurnContentPlayback(
    private val view: View,
    private val handoff: ReadVisualHandoff<*>?,
) {
    private val alpha = view.alpha
    private var active = false
    private var delegated = false

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) {
            delegated = handoff?.setSnapshotPlayback(true) == true
            if (!delegated) view.alpha = 0f
        } else {
            try {
                handoff?.setSnapshotPlayback(false)
            } finally {
                if (!delegated) view.alpha = alpha
                delegated = false
            }
        }
    }
}
