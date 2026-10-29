package org.peyilo.ireadview.spi.content

import android.graphics.Bitmap
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.spi.presentation.ReadViewport

/** A bounded native-block image request, scoped to the current book/source revision. */
data class ReaderImageRequest(
    val resourceId: String,
    val source: BookSource?,
    val bookRevision: String?,
    val viewport: ReadViewport,
    val maxEncodedBytes: Int,
    val maxDecodedBytes: Long
) {
    init {
        require(resourceId.isNotBlank() && maxEncodedBytes > 0 && maxDecodedBytes >= 4)
    }
}

/** IO worker callback. Implementations may be called concurrently and must cooperate with
 * coroutine cancellation. Return null for a placeholder. Ordinary exceptions also fall back
 * to a placeholder; cancellation propagates. Bound allocations before decoding, not afterward.
 * A returned bitmap must be software-renderable and immutable in practice: neither the loader
 * nor its external cache may mutate/recycle it while layouts retain it. The library holds a
 * reference and never recycles returned bitmaps; loader/cache lifetime belongs to the host.
 * This replaces native ImageBlock loading/decoding, not a document engine's resource pipeline.
 */
fun interface ReaderImageLoader {
    suspend fun load(request: ReaderImageRequest): Bitmap?
}

/** Immutable registration. Reinstall a new configuration when loader behavior changes. */
data class ReaderImageLoadingOptions @JvmOverloads constructor(
    val loader: ReaderImageLoader = ReaderImageLoaders.defaultLoader(),
    val maxEncodedImageBytes: Int = 4 * 1024 * 1024,
    val maxDecodedImageBytes: Long = 4L * 1024 * 1024,
    val maxDecodedChapterBytes: Long = 16L * 1024 * 1024
) {
    init {
        require(maxEncodedImageBytes > 0 && maxDecodedImageBytes >= 4 && maxDecodedChapterBytes >= 0)
    }
}

object ReaderImageLoaders {
    /** Stateless default loader; suitable for delegation from a custom loader. */
    @JvmStatic
    fun defaultLoader(): ReaderImageLoader =
        org.peyilo.ireadview.internal.layout.DefaultReaderImageLoader
}
