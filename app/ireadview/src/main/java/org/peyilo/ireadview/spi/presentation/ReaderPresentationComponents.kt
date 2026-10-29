package org.peyilo.ireadview.spi.presentation

import org.peyilo.ireadview.spi.body.*
import org.peyilo.ireadview.spi.selection.*

/** Independent factories; stateful instances must be fresh for each owning body/controller. */
data class ReaderPresentationComponents @JvmOverloads constructor(
    val bodyRendererFactory: ReaderBodyRendererFactory = ReaderBodyRendererFactory { DefaultReaderBodyRenderer },
    val selectionRendererFactory: ReaderSelectionRendererFactory = ReaderSelectionRendererFactory { ReaderSelectionPainter() },
    val selectionControllerFactory: ReaderSelectionControllerFactory = ReaderSelectionControllerFactory { context, delegate ->
        ReaderSelectionInteraction(
            context,
            delegate
        )
    },
    val underlineRendererFactory: ReaderUnderlineRendererFactory = ReaderUnderlineRendererFactory { ReaderUnderlinePainter() }
) {
    companion object {
        @JvmField
        val DEFAULT = ReaderPresentationComponents()
    }
}
