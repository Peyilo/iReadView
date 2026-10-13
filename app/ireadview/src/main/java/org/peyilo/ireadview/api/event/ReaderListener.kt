package org.peyilo.ireadview.api.event

import org.peyilo.ireadview.api.model.BookDescriptor
import org.peyilo.ireadview.api.model.ReadingPosition
import org.peyilo.ireadview.api.model.AnnotationSnapshot
import org.peyilo.ireadview.api.model.AnnotationStatus
import org.peyilo.ireadview.api.model.Highlight
import org.peyilo.ireadview.api.model.TextSelection
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.effect.EffectFallbackReason
import org.peyilo.ireadview.widget.BaseReadView
import org.peyilo.ireadview.widget.BookReadView

enum class PositionChangeReason { Open, UserNavigation, ProgrammaticNavigation, Scroll, Restore, Relayout, ModeChange, ContentUpdate, Close }
data class ReadLocationChange<L>(val from: L?, val to: L?, val reason: PositionChangeReason)
typealias PositionChange = ReadLocationChange<ReadingPosition>

interface ReadViewListener<L : Any> {
    fun onEffectFallback(view: BaseReadView<L>, requestedId: String, appliedId: String, reason: EffectFallbackReason) {}
    fun onLocationChanged(view: BaseReadView<L>, change: ReadLocationChange<L>) {}
    fun onNavigationStateChanged(view: BaseReadView<L>, state: ReadNavigationState) {}
    fun onNavigationCapabilitiesChanged(view: BaseReadView<L>, capabilities: ReadNavigationCapabilities) {}
    fun onError(view: BaseReadView<L>, error: ReaderError) {}
}

interface ReaderListener {
    fun onEffectFallback(view: BookReadView, requestedId: String, appliedId: String, reason: EffectFallbackReason) {}
    fun onBookPageIndexChanged(view: BookReadView, state: BookPageIndexState) {}
    fun onReadingModeChanged(view: BookReadView, change: ModeChange) {}
    fun onStateChanged(view: BookReadView, state: ReaderState) {}
    fun onBookChanged(view: BookReadView, book: BookDescriptor?) {}
    fun onChapterStateChanged(view: BookReadView, chapterId: String, state: ChapterState) {}
    fun onPaginationChanged(view: BookReadView, chapterId: String, state: ChapterPaginationState) {}
    fun onPositionChanged(view: BookReadView, event: PositionChange) {}
    fun onAnnotationsChanged(view: BookReadView, snapshot: AnnotationSnapshot) {}
    fun onAnnotationStatusChanged(view: BookReadView, id: String, status: AnnotationStatus) {}
    fun onHighlightStyleFallback(view: BookReadView, id: String, styleId: String) {}
    fun onHighlightTapped(view: BookReadView, highlight: Highlight) {}
    fun onSelectionChanged(view: BookReadView, selection: TextSelection?) {}
    /** Explicit content-update/catalogue-reload recovery; ordinary navigation never falls back. */
    fun onPositionRestored(view: BookReadView, restoration: Restoration, reason: PositionChangeReason) {}
    fun onError(view: BookReadView, error: ReaderError) {}
}

/** Java hosts may override only the book events they need, without implementing every callback. */
abstract class ReaderListenerAdapter : ReaderListener

/** Java counterpart for hosts using the generic navigation container. */
abstract class ReadViewListenerAdapter<L : Any> : ReadViewListener<L>

/** Normalized position in the Widget content viewport, excluding its outer padding. */
data class ReaderTap(val x: Float, val y: Float)
/** Return true to consume before default edge paging and View.performClick. */
fun interface ReaderTapListener { fun onTap(tap: ReaderTap): Boolean }
