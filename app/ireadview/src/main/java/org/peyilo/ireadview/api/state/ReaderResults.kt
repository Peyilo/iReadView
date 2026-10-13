package org.peyilo.ireadview.api.state

import org.peyilo.ireadview.api.model.BookDescriptor
import org.peyilo.ireadview.api.model.ReadingPosition

/** EFFECT describes renderer configuration/cleanup; it does not imply a chapter failed to load. */
enum class ReaderStage { BOOK, CHAPTER, LAYOUT, NAVIGATION, VIEWPORT, SELECTION, EFFECT }

data class ReaderError(
    val stage: ReaderStage,
    val code: String,
    val message: String,
    val chapterId: String? = null,
    val retryable: Boolean = true,
    val cause: Throwable? = null
)

enum class ReaderRejection {
    NoBook, NotReady, Busy, AtBoundary, UnknownChapter, InvalidPage, InvalidAnchor,
    BookMismatch, PageIndexUnavailable, Unsupported, Released, DuplicateAnnotation, UnknownAnnotation, InvalidSelection, Throttled
}

sealed interface ReaderResult<out T> {
    data class Success<T>(val value: T) : ReaderResult<T>
    data class Failure(val error: ReaderError) : ReaderResult<Nothing>
    data class Rejected(val reason: ReaderRejection) : ReaderResult<Nothing>
    data object Superseded : ReaderResult<Nothing>
}

sealed interface NavigationResult<out L> {
    data class Moved<L>(val from: L?, val to: L) : NavigationResult<L>
    data class Unchanged<L>(val location: L?) : NavigationResult<L>
    data class Rejected(val reason: ReaderRejection) : NavigationResult<Nothing>
    data class Failed(val error: ReaderError) : NavigationResult<Nothing>
    data object Superseded : NavigationResult<Nothing>
}

sealed interface Restoration {
    data object Exact : Restoration
    data class Fallback(val reason: ReaderRejection, val actual: ReadingPosition) : Restoration
}

sealed interface OpenResult {
    data class Opened(val position: ReadingPosition, val restoration: Restoration) : OpenResult
    data class Empty(val book: BookDescriptor) : OpenResult
    data class Failed(val error: ReaderError) : OpenResult
    data object Superseded : OpenResult
}

data class OpenBookRequest(
    val initialPosition: ReadingPosition? = null,
    val initialChapterNumber: Int = 1,
    val initialPageNumber: Int = 1,
    val initialFallback: InitialFallback = InitialFallback.BOOK_START
)

enum class InitialFallback { BOOK_START, FAIL }
data class StyleApplication(val layoutChanged: Boolean, val position: ReadingPosition?)
data class ModeChange(val from: org.peyilo.ireadview.api.model.ReadingMode, val to: org.peyilo.ireadview.api.model.ReadingMode,
    val position: ReadingPosition?, val restoration: Restoration)

/** Used by Java/non-coroutine hosts. Cancellation is separate from a domain rejection. */
sealed interface ReaderCompletion<out T> {
    data class Completed<T>(val value: T) : ReaderCompletion<T>
    data object Cancelled : ReaderCompletion<Nothing>
}

/** Delivered once on Main after this event. Ordinary host exceptions are logged, without redelivery. */
fun interface ReaderCallback<T> { fun onComplete(outcome: ReaderCompletion<T>) }
interface ReaderRequest {
    /** Thread-safe and idempotent; a terminal result already produced is not rewritten. */
    fun cancel()
    /** Operation completion, which may precede delivery of its queued Main callback. */
    val isCompleted: Boolean
}
