package org.peyilo.ireadview.spi.presentation

import android.content.Context
import org.peyilo.ireadview.api.model.ReadingMode
import org.peyilo.ireadview.api.source.BookSource

/** Host override independent of book transport/format. Null delegates to the source's
 * presentation, then the Widget default. Fresh instance per call; defer IO to prepare.
 */
fun interface BookPresentationFactory {
    fun create(context: Context, mode: ReadingMode, source: BookSource?, host: BookPresentationHost): BookPresentation?
}

object BookPresentations {
    @JvmStatic fun createNative(context: Context, mode: ReadingMode, host: BookPresentationHost): BookPresentation = when(mode) {
        ReadingMode.PAGED -> org.peyilo.ireadview.internal.presentation.paged.PagedBookPresentation(context, host)
        ReadingMode.CONTINUOUS_SCROLL -> org.peyilo.ireadview.internal.presentation.scroll.ScrollingBookPresentation(context, host)
        else -> error("Unsupported reading mode: ${mode.id}")
    }
}
