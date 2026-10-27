package org.peyilo.ireadview.internal.effect

import org.peyilo.ireadview.api.effect.*

/** Optional visual kinetics. Manager owns the only frame clock and all commit/resource decisions. */
internal interface PageTurnDynamics {
    val warmingUp: Boolean
    fun beginSettling(commit: Boolean, velocity: TurnPoint?): Long
    fun updateSettling(frame: PageTurnFrame, elapsedFraction: Double)
}
