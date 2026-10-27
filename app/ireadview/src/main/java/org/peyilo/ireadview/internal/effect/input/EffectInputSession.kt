package org.peyilo.ireadview.internal.effect.input

import android.view.MotionEvent
import org.peyilo.ireadview.api.effect.TurnPoint
import org.peyilo.ireadview.spi.presentation.ReadViewport

/** Optional built-in input capability. The Widget retains content arbitration and navigation. */
internal interface EffectInputProvider {
    fun inputSession(viewport: ReadViewport, vertical: Boolean, available: (Boolean) -> Boolean): EffectInputSession
    /** Null means the center/menu region; coordinates are content-local pixels. */
    fun tapForward(viewport: ReadViewport,vertical: Boolean,point: TurnPoint): Boolean?
}
internal interface EffectInputSession {
    fun event(event: MotionEvent,paddingX: Int,paddingY: Int): EffectInputUpdate
}
internal data class EffectInputUpdate(
    val beginForward: Boolean? = null, val point: TurnPoint? = null,
    val moved: Boolean = false, val release: Boolean? = null,
    val velocity: TurnPoint? = null, val tap: Boolean = false, val terminal: Boolean = false
)
