package org.peyilo.ireadview.api.model

/** Paged presentation retention policy. Single-page mode may mount a transient turn candidate.
 * Scrolling presentations retain this setting for the next switch to paged mode.
 */
enum class ReaderPageCacheMode(val retainedPageCount: Int) {
    SINGLE_PAGE(1),
    THREE_PAGE(3)
}
