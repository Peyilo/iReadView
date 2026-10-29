package org.peyilo.ireadview.internal.layout

import android.graphics.*
import kotlinx.coroutines.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.spi.content.*
import org.peyilo.ireadview.spi.presentation.ReadViewport

/** Per-preparation working set; decoded resources are then retained only by their measured layouts. */
internal class ImageBlockLayoutProvider private constructor(private val images: Map<String, Bitmap>) :
    BlockLayoutProvider {
    override val id = "iread.image"
    override val version = "1"
    override fun supports(block: ContentBlock) = block is ImageBlock
    override fun measure(block: ContentBlock, context: BlockLayoutContext): BlockLayout {
        context.checkCancellation()
        block as ImageBlock
        val bitmap = images[block.resourceId]
        val padding = context.style.body.blockPadding
        val availableHeight =
            (context.viewport.heightPx - (padding.topDp + padding.bottomDp) * context.viewport.density).coerceAtLeast(
                1f
            )
        val width = context.viewport.widthPx.toFloat().coerceAtLeast(1f)
        val scale = if (bitmap != null) minOf(
            1f,
            width / bitmap.width,
            availableHeight / bitmap.height
        ) else 1f
        val imageWidth = bitmap?.let { it.width * scale } ?: width
        val height = bitmap?.let { it.height * scale } ?: minOf(
            availableHeight,
            96f * context.viewport.density
        )
        return ImageLayout(
            block, bitmap, width, imageWidth, height,
            context.style.body.textColor, context.bodyTextSizePx,
            context.style.body.paragraphSpacingDp * context.viewport.density
        )
    }

    companion object {
        /** Bounded sequential IO and decode, independent of source chapter text caches.
         * A failed image is represented by a stable alt placeholder; cancellation always propagates.
         */
        suspend fun prepare(
            content: ChapterContent,
            source: BookSource?,
            bookRevision: String?,
            viewport: ReadViewport,
            custom: List<BlockLayoutProvider>,
            options: ReaderImageLoadingOptions
        ): ImageBlockLayoutProvider = withContext(Dispatchers.IO) {
            val images = LinkedHashMap<String, Bitmap>()
            val attempted = HashSet<String>()
            var remaining = options.maxDecodedChapterBytes
            for (block in content.blocks) {
                ensureActive()
                if (block !is ImageBlock || custom.any { it.supports(block) } || !attempted.add(
                        block.resourceId
                    )) continue
                if (remaining < 4) break
                try {
                    val budget = minOf(remaining, options.maxDecodedImageBytes)
                    ensureActive()
                    val bitmap = options.loader.load(
                        ReaderImageRequest(
                            block.resourceId, source, bookRevision,
                            viewport, options.maxEncodedImageBytes, budget
                        )
                    ) ?: continue
                    ensureActive()
                    if (!bitmap.isRecycled && bitmap.allocationByteCount.toLong() <= budget &&
                        (android.os.Build.VERSION.SDK_INT < 26 || bitmap.config != Bitmap.Config.HARDWARE)
                    ) {
                        images[block.resourceId] = bitmap
                        remaining -= bitmap.allocationByteCount
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { /* Missing, stale or unsupported resources use alt placeholders. */
                }
            }
            ImageBlockLayoutProvider(images)
        }
    }
}

private class ImageLayout(
    private val block: ImageBlock,
    private val bitmap: Bitmap?,
    private val width: Float,
    private val imageWidth: Float,
    private val height: Float,
    private val color: Int,
    private val textSize: Float,
    override val spacingAfterPx: Float
) : BlockLayout {
    override val blockId = block.blockId
    override val estimatedRetainedBytes = 512L + (bitmap?.allocationByteCount?.toLong() ?: 0)
    override val units = listOf(BlockLayoutUnit(height, null, null))
    override fun fragment(firstUnitIndex: Int, endUnitExclusive: Int): BlockLayoutFragment {
        require(firstUnitIndex == 0 && endUnitExclusive == 1)
        return object : BlockLayoutFragment {
            override val blockId = block.blockId
            override val startOffset: Int? = null
            override val endOffset: Int? = null
            override val heightPx = height
            override val accessibilityText = block.alternativeText.ifBlank { "Image" }
            override val accessibilityDescribesWholeBlock = true
            override fun anchorForVerticalOffset(yPx: Float) = ContentAnchor(blockId, 0)
            override fun linkAt(xPx: Float, yPx: Float): LinkTarget? =
                block.link?.takeIf { xPx >= (width - imageWidth) / 2 && xPx < (width + imageWidth) / 2 && yPx >= 0 && yPx < height }

            override fun draw(canvas: Canvas) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val destination =
                    RectF((width - imageWidth) / 2, 0f, (width + imageWidth) / 2, height)
                if (bitmap != null) canvas.drawBitmap(bitmap, null, destination, paint)
                else {
                    val save = canvas.save()
                    try {
                        canvas.clipRect(destination)
                        paint.color = color; paint.alpha = 32
                        canvas.drawRect(destination, paint)
                        paint.alpha = 255; paint.textSize = textSize
                        canvas.drawText(
                            accessibilityText.replace('\n', ' '),
                            8f,
                            minOf(height - 2f, textSize + 8f),
                            paint
                        )
                    } finally {
                        canvas.restoreToCount(save)
                    }
                }
            }
        }
    }
}
