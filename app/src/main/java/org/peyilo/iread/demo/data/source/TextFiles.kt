package org.peyilo.iread.demo.data.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.peyilo.ireadview.api.source.ReaderInput
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Demo-owned file selection and encoding policy; the Widget still owns parsing and layout. */
internal object TextFiles {
    const val ASSET_PATH = "txts/妖精之诗 作者：尼希维尔特.txt"
    val sampleUri: Uri get() = Uri.Builder().scheme("asset").path("/$ASSET_PATH").build()
    const val SAMPLE_TITLE = "妖精之诗 · 尼希维尔特"
    val encodings = arrayOf("UTF-8", "UTF-16", "UTF-16LE", "UTF-16BE", "GB18030")

    data class Inspection(val title: String, val size: Long?, val charset: String)

    fun input(context: Context, uri: Uri): ReaderInput =
        if (uri == sampleUri) ReaderInput.fromAsset(context, ASSET_PATH)
        else ReaderInput.fromUri(context, uri)

    suspend fun inspect(context: Context, uri: Uri): Inspection = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "本地 TXT"
        var size: Long? = null
        // Some document providers omit SIZE or do not support metadata queries.
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn).takeIf { it >= 0 }
                }
            }
        }
        val sample = ByteArray(64 * 1024)
        var count = 0
        input(context, uri).openStream().use { stream ->
            while (count < sample.size) {
                coroutine.ensureActive()
                val read = stream.read(sample, count, sample.size - count)
                if (read < 0) break
                if (read > 0) count += read
            }
        }
        coroutine.ensureActive()
        Inspection(name.removeSuffix(".txt").removeSuffix(".TXT"), size, detectCharset(sample, count))
    }

    /** BOM first; otherwise strict UTF-8, UTF-16 ASCII/NUL pattern, then GB18030.
     * This is a bounded heuristic, not a guarantee. The host exposes manual correction.
     */
    private fun detectCharset(bytes: ByteArray, length: Int): String {
        fun byte(index: Int) = bytes[index].toInt() and 255
        if (length >= 3 && byte(0) == 0xef && byte(1) == 0xbb && byte(2) == 0xbf) return "UTF-8"
        if (length >= 2 && byte(0) == 0xff && byte(1) == 0xfe) return "UTF-16LE"
        if (length >= 2 && byte(0) == 0xfe && byte(1) == 0xff) return "UTF-16BE"
        if (length >= 8) {
            var evenNul = 0; var oddNul = 0
            for (index in 0 until length) if (bytes[index] == 0.toByte()) {
                if (index % 2 == 0) evenNul++ else oddNul++
            }
            if (oddNul > length / 4 && evenNul < length / 20) return "UTF-16LE"
            if (evenNul > length / 4 && oddNul < length / 20) return "UTF-16BE"
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        // endOfInput=false permits a multi-byte character cut by the sample boundary.
        val result = decoder.decode(ByteBuffer.wrap(bytes, 0, length), CharBuffer.allocate(length.coerceAtLeast(1)), false)
        return if (result.isError) "GB18030" else "UTF-8"
    }
}

