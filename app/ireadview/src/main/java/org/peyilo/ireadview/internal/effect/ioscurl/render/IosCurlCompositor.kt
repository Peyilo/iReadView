package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.view.View
import org.peyilo.ireadview.internal.effect.PageTurnSnapshots

internal interface IosCurlCompositor : AutoCloseable {
    val replacesLiveContent: Boolean
        get() = true

    val view: View
    val ready: Boolean
    val hasFrame: Boolean

    /** True transfers the read pins; false leaves ownership with the caller. */
    fun begin(
        capture: PageTurnSnapshots.Retained,
        width: Int,
        height: Int,
        opaque: Boolean,
    ): Boolean

    fun submit(scene: IosCurlScene)

    fun checkFailure()

    fun end()
}
