package org.peyilo.ireadview.spi.presentation

import android.view.View
import org.peyilo.ireadview.api.state.*

data class ReadViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val fontScale: Float,
    val rightToLeft: Boolean = false
) {
    val isValid: Boolean get() = widthPx > 0 && heightPx > 0
}

sealed interface ReadNavigation<out L> {
    data object Next : ReadNavigation<Nothing>
    data object Previous : ReadNavigation<Nothing>
    data object First : ReadNavigation<Nothing>
    data object Last : ReadNavigation<Nothing>
    data class Position<L>(val location: L) : ReadNavigation<L>
}

interface PreparedRead<L : Any> {
    val surface: org.peyilo.ireadview.api.effect.PageSurface? get() = null
    val target: L
    val generation: Long

    /** Called once for a returned Ready candidate, including cancellation/obsolescence.
     * Release only candidate-owned resources; resources transferred by show belong to the adapter.
     */
    fun dispose()
}

/** Optional compositor handoff before the navigation's atomic show/commit.
 * The animation still covers the live content. Cancellation must leave the committed
 * page intact; candidate disposal owns any provisional visibility/resources.
 */
interface ReadVisualHandoff<L : Any> {
    suspend fun prepareVisualHandoff(candidate: PreparedRead<L>)
}

sealed interface ReadPreparation<out L : Any> {
    data class Ready<L : Any>(val candidate: PreparedRead<L>) : ReadPreparation<L>
    data class Unchanged<L : Any>(val location: L?) : ReadPreparation<L>
    data class Rejected(val reason: ReaderRejection) : ReadPreparation<Nothing>
    data class Failed(val error: ReaderError) : ReadPreparation<Nothing>
}

interface ReadContentHost<L : Any> {
    val generation: Long
    val viewport: ReadViewport
    val committedLocation: L?
    suspend fun requestNavigation(target: ReadNavigation<L>): NavigationResult<L>
    fun reportVisiblePosition(location: L, generation: Long): ReaderResult<Unit>
    fun reportError(error: ReaderError)

    /** Visible geometry/window changed without necessarily changing its text anchor. */
    fun reportNavigationCapabilitiesChanged(generation: Long) {}
}

interface ReadContentAdapter<L : Any> {
    val currentSurface: org.peyilo.ireadview.api.effect.PageSurface? get() = null
    val contentView: View

    /** Read-only main-thread query; must not load content, start layout or change position. */
    fun navigationAvailability(target: ReadNavigation<L>): NavigationAvailability =
        NavigationAvailability.Unknown

    fun attach(host: ReadContentHost<L>)
    fun onViewportChanged(viewport: ReadViewport)
    suspend fun prepare(target: ReadNavigation<L>): ReadPreparation<L>

    /** Cancellation must restore the last committed content, if this generation still owns it. */
    suspend fun show(candidate: PreparedRead<L>): L
    fun detach()
}
