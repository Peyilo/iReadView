package org.peyilo.ireadview.api.source

import android.content.Context
import org.peyilo.ireadview.api.model.ReadingMode
import org.peyilo.ireadview.spi.presentation.BookPresentation
import org.peyilo.ireadview.spi.presentation.BookPresentationHost

/** Optional content backend, selected by ReaderView.openBook just like any other BookSource.
 * Owns format-specific rendering inside the Widget's page template; never opens application UI.
 * Construct a fresh presentation on the main thread; defer IO until loadBook/prepare.
 */
interface PresentationBookSource : BookSource {
    /** False when native text-block pagination/indexing cannot describe this publication. */
    val supportsBlockLayout: Boolean get() = false
    fun createPresentation(context: Context, mode: ReadingMode, host: BookPresentationHost): BookPresentation
}
