package org.peyilo.ireadview.internal.presentation

import org.peyilo.ireadview.spi.page.ReaderPageTemplate

/** Cleanup failures cannot prevent the other resources from releasing or replace a preparation error. */
internal fun cleanupReaderPage(
    template: ReaderPageTemplate, before: List<() -> Unit>,
    removeRoot: () -> Unit, onFailure: (Exception) -> Unit
) {
    var failure: Exception? = null
    for (action in before + listOf(removeRoot, template::unbind)) {
        try {
            action()
        } catch (error: Exception) {
            val first = failure
            if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
        }
    }
    ReaderPageOwnership.release(template)
    failure?.let(onFailure)
}
