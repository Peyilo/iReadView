package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.view.View

internal interface IosCurlCompositor : AutoCloseable {
    val replacesLiveContent: Boolean
        get() = true

    val view: View
    val ready: Boolean
    val hasFrame: Boolean

    /** Ensures capture resources for these pixel dimensions; false keeps this turn on snapshots. */
    fun prepareCapture(width: Int, height: Int): Boolean

    /** True transfers the read pins; false leaves ownership with the caller. */
    fun begin(input: IosCurlPageInput): Boolean

    fun submit(scene: IosCurlScene)

    fun checkFailure()

    fun end()
}
