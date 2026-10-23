package org.peyilo.ireadview.internal.layout

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.peyilo.ireadview.api.source.BookResourceSource
import org.peyilo.ireadview.spi.content.ReaderImageLoader
import org.peyilo.ireadview.spi.content.ReaderImageRequest

internal object DefaultReaderImageLoader : ReaderImageLoader {
    override suspend fun load(request: ReaderImageRequest): Bitmap? {
        val resources = request.source as? BookResourceSource ?: return null
        val bytes = resources.loadResource(request.resourceId, request.bookRevision, request.maxEncodedBytes)
        require(bytes.size <= request.maxEncodedBytes) { "Source exceeded resource byte budget" }
        currentCoroutineContext().ensureActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        fun sampled(size: Int) = (size.toLong() + sample - 1) / sample
        while (sampled(bounds.outWidth) * sampled(bounds.outHeight) > request.maxDecodedBytes / 4 ||
            sampled(bounds.outWidth) > request.viewport.widthPx.coerceAtLeast(1) * 2L ||
            sampled(bounds.outHeight) > request.viewport.heightPx.coerceAtLeast(1) * 2L) {
            require(sample <= (1 shl 28)) { "Image dimensions exceed decode limits" }
            sample *= 2
        }
        currentCoroutineContext().ensureActive()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }
}
