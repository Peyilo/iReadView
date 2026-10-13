package org.peyilo.ireadview.api.state

import org.peyilo.ireadview.api.model.BookPageIndexSummary

sealed interface BookPageIndexState {
    data object Unavailable : BookPageIndexState
    data class Building(val completedChapters: Int, val chapterCount: Int) : BookPageIndexState
    data class Ready(val summary: BookPageIndexSummary) : BookPageIndexState
    data class Failed(val error: ReaderError) : BookPageIndexState
}
