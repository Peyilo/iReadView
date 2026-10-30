package org.peyilo.ireadview.internal.effect.ioscurl.render

import org.peyilo.ireadview.api.effect.PageTurnContext
import org.peyilo.ireadview.internal.effect.PageTurnSnapshots

internal sealed interface IosCurlPageInput : AutoCloseable {
    val width: Int
    val height: Int
    val opaque: Boolean
    val firstFrame: IosCurlFirstFrameTrace?

    class Pixels(
        val pages: PageTurnSnapshots.Retained,
        override val opaque: Boolean,
        override val firstFrame: IosCurlFirstFrameTrace?,
    ) : IosCurlPageInput {
        override val width = pages.current.bitmap.width
        override val height = pages.current.bitmap.height

        override fun close() = pages.close()
    }

    class Canvas(
        val context: PageTurnContext,
        override val width: Int,
        override val height: Int,
        override val firstFrame: IosCurlFirstFrameTrace?,
    ) : IosCurlPageInput {
        override val opaque = true

        override fun close() = Unit
    }
}
