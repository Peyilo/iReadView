package org.peyilo.iread.demo.data.source

import android.content.Context
import android.net.Uri
import org.peyilo.ireadview.api.model.BookDescriptor
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.source.TextNormalization
import org.peyilo.ireadview.source.TextBookOptions
import org.peyilo.ireadview.source.TextBookSource
import java.io.File
import java.io.FilterInputStream
import java.nio.charset.Charset

/** Observes real input consumption. Parsing and chapter extraction remain in TextBookSource. */
internal class ProgressTextBookSource(
    context: Context,
    override val bookId: String,
    uri: Uri,
    title: String,
    charset: String,
    private val totalBytes: Long?,
    private val onProgress: (Progress) -> Unit
) : BookSource, AutoCloseable {
    enum class Stage { Reading, Parsing, Ready }
    data class Progress(val stage: Stage, val bytes: Long = 0, val total: Long? = null, val chapters: Int = 0)

    private val input = TextFiles.input(context, uri)
    private val delegate = TextBookSource(bookId, ReaderInput.fromStream {
        object : FilterInputStream(input.openStream()) {
            private var consumed = 0L
            private var lastReport = 0L
            private var ended = false
            private fun report(count: Int) {
                if (count > 0) consumed += count
                val now = android.os.SystemClock.elapsedRealtime()
                if (count < 0 && !ended) {
                    ended = true
                    onProgress(Progress(Stage.Parsing, consumed, totalBytes))
                } else if (count > 0 && now - lastReport >= 100) {
                    lastReport = now
                    onProgress(Progress(Stage.Reading, consumed, totalBytes))
                }
            }
            override fun read(): Int = `in`.read().also { report(if (it < 0) -1 else 1) }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                `in`.read(buffer, offset, length).also(::report)
        }
    }, TextBookOptions(
        title = title, charset = Charset.forName(charset), preambleTitle = "前言",
        indexDirectory = File(context.cacheDir, "reader-text"),
        normalization = TextNormalization.TRIM_LINE_EDGES
    ))

    override suspend fun loadBook(): BookDescriptor {
        onProgress(Progress(Stage.Reading, total = totalBytes))
        return delegate.loadBook().also { onProgress(Progress(Stage.Ready, chapters = it.chapters.size)) }
    }
    override suspend fun loadChapter(chapterId: String) = delegate.loadChapter(chapterId)
    override fun close() = delegate.close()
}
