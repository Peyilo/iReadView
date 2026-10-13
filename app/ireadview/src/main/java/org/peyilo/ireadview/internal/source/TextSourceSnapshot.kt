package org.peyilo.ireadview.internal.source

import org.peyilo.ireadview.api.model.BookDescriptor
import org.peyilo.ireadview.api.model.ChapterContent

internal interface TextSourceSnapshot : AutoCloseable {
    val book: BookDescriptor
    suspend fun chapter(chapterId: String): ChapterContent
}
