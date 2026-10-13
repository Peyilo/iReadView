package org.peyilo.ireadview.internal

/** Attempt independent releases after ordinary failures; retain all causes for the caller.
 * Fatal Errors propagate immediately. Owners must revoke their references before calling this.
 */
internal fun releaseReaderResources(vararg actions: () -> Unit) {
    var failure: Exception? = null
    for (action in actions) {
        try { action() } catch (error: Exception) {
            val first = failure
            if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
        }
    }
    failure?.let { throw it }
}
