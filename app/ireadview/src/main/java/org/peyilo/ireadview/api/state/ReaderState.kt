package org.peyilo.ireadview.api.state

import org.peyilo.ireadview.api.model.ReadingPosition

enum class ReaderPhase { Closed, LoadingBook, LoadingChapter, AwaitingViewport, LayingOut, Ready, Empty, Error, Released }
enum class ReadNavigationState { Idle, Preparing, Settling, Dragging }

sealed interface ChapterState {
    data object Unloaded : ChapterState
    data object Loading : ChapterState
    data class Loaded(val revision: String?) : ChapterState
    data class Failed(val error: ReaderError) : ChapterState
}

sealed interface ChapterPaginationState {
    data object Unknown : ChapterPaginationState
    data object Calculating : ChapterPaginationState
    data class Ready(val pageCount: Int, val layoutKey: String) : ChapterPaginationState
    data class Failed(val error: ReaderError) : ChapterPaginationState
}

data class ReaderState(
    val phase: ReaderPhase = ReaderPhase.Closed,
    val bookId: String? = null,
    val position: ReadingPosition? = null,
    val chapterPageCount: Int? = null,
    val chapterCount: Int = 0,
    val error: ReaderError? = null,
    val readingMode: org.peyilo.ireadview.api.model.ReadingMode = org.peyilo.ireadview.api.model.ReadingMode.PAGED,
    val pageIndex: BookPageIndexState = BookPageIndexState.Unavailable,
    val globalPageNumber: Long? = null,
    val totalPageCount: Long? = null,
    val navigationState: ReadNavigationState = ReadNavigationState.Idle,
    val navigationCapabilities: ReadNavigationCapabilities = ReadNavigationCapabilities()
)

/** force replaces pending navigation and bypasses the turn interval; boundaries remain enforced. */
data class NavigationOptions(val animated: Boolean = true, val force: Boolean = false, val respectInterval: Boolean = true)

data class LoadingOptions(
    val preloadBefore: Int = 1,
    val preloadAfter: Int = 1,
    val maxConcurrentLoads: Int = 3,
    val contentCacheBytes: Long = 8L * 1024 * 1024,
    val layoutCacheBytes: Long = 16L * 1024 * 1024
) {
    init {
        require(preloadBefore >= 0 && preloadAfter >= 0 && maxConcurrentLoads >= 1)
        require(contentCacheBytes > 0 && layoutCacheBytes > 0)
    }
}
