package org.peyilo.iread.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.peyilo.ireadview.spi.content.*
import org.peyilo.ireadview.widget.ReaderView

/** Consumes only the published AAR: no internal layout classes or project dependency. */
object HostContentStrategies {
    @JvmStatic fun install(reader: ReaderView) {
        val standard = BlockLayoutProviders.text()
        reader.setBlockLayoutProviders(listOf(object : BlockLayoutProvider by standard {
            override val id = "host.extra-spacing"
            override val version = "1"
            override fun measure(block: org.peyilo.ireadview.api.model.ContentBlock, context: BlockLayoutContext): BlockLayout {
                val measured = standard.measure(block, context)
                return object : BlockLayout by measured {
                    override val spacingAfterPx = measured.spacingAfterPx + 2f * context.viewport.density
                }
            }
        }))
        val fallback = ReaderImageLoaders.defaultLoader()
        reader.setImageLoadingOptions(ReaderImageLoadingOptions(loader = ReaderImageLoader { request ->
            if (request.resourceId != "host-cover") fallback.load(request)
            else if (request.maxDecodedBytes < 64L * 64 * 4) null
            else Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.rgb(40, 80, 120))
                canvas.drawText("iRead", 5f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f })
            }
        }))
    }
}
