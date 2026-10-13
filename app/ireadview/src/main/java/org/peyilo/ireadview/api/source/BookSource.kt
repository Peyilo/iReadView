package org.peyilo.ireadview.api.source

import org.peyilo.ireadview.api.model.BookDescriptor
import org.peyilo.ireadview.api.model.ChapterContent

/** Called on an IO dispatcher. Different chapters may be requested concurrently. */
interface BookSource {
    val bookId: String
    suspend fun loadBook(): BookDescriptor
    suspend fun loadChapter(chapterId: String): ChapterContent
}
