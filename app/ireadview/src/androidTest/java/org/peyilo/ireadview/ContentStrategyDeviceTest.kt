package org.peyilo.ireadview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookResourceSource
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.internal.layout.ImageBlockLayoutProvider
import org.peyilo.ireadview.spi.content.*
import org.peyilo.ireadview.spi.presentation.ReadViewport
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ContentStrategyDeviceTest {
    private val viewport = ReadViewport(200, 400, 1f, 1f)
    private val context get() = BlockLayoutContext(viewport, ReaderStyle(), checkCancellation = {})
    private fun content(vararg ids: String) = ChapterContent("book", "chapter", "v1",
        ids.mapIndexed { i, id -> ImageBlock("image-$i", id) })

    @Test fun imageBudgetDeduplicatesResourcesAndStopsBeforeNextAllocation() = runBlocking {
        val requests = mutableListOf<ReaderImageRequest>()
        val options = ReaderImageLoadingOptions(ReaderImageLoader {
            requests.add(it)
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        }, maxDecodedImageBytes = 64, maxDecodedChapterBytes = 64)
        val chapter = content("same", "same", "next")
        val provider = ImageBlockLayoutProvider.prepare(chapter, null, "book-v2", viewport, emptyList(), options)
        assertEquals(1, requests.size)
        assertEquals("book-v2", requests.single().bookRevision)
        assertEquals(64L, requests.single().maxDecodedBytes)
        val output = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        provider.measure(chapter.blocks.first(), context).fragment(0, 1).draw(Canvas(output))
        assertEquals(Color.RED, output.getPixel(100, 1))
        // The next unique resource has no budget and uses the documented alt placeholder.
        assertEquals(96f, provider.measure(chapter.blocks.last(), context).units.single().heightPx)
    }

    @Test fun invalidImageFallsBackButCancellationPropagatesAndCustomBlocksSkipLoading() = runBlocking {
        val chapter = content("large")
        val options = ReaderImageLoadingOptions(ReaderImageLoader {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        }, maxDecodedImageBytes = 4)
        val rejected = ImageBlockLayoutProvider.prepare(chapter, null, null, viewport, emptyList(), options)
        assertEquals(96f, rejected.measure(chapter.blocks.first(), context).units.single().heightPx)
        var calls = 0
        val cancelled = options.copy(loader = ReaderImageLoader { calls++; throw CancellationException("cancel") })
        try {
            ImageBlockLayoutProvider.prepare(chapter, null, null, viewport, emptyList(), cancelled)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {}
        assertEquals(1, calls)
        val custom = object : BlockLayoutProvider by BlockLayoutProviders.text() {
            override fun supports(block: ContentBlock) = block is ImageBlock
        }
        ImageBlockLayoutProvider.prepare(chapter, null, null, viewport, listOf(custom), cancelled)
        assertEquals(1, calls)
    }

    @Test fun defaultLoaderUsesSourceRevisionAndRejectsOversizedEncodedResources() = runBlocking {
        val bytes = ByteArrayOutputStream().also { stream ->
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, stream)
        }.toByteArray()
        var requested = 0
        val source = object : BookResourceSource {
            override val bookId = "book"
            override suspend fun loadBook(): BookDescriptor = error("not used")
            override suspend fun loadChapter(chapterId: String): ChapterContent = error("not used")
            override suspend fun loadResource(resourceId: String, bookRevision: String?, maxBytes: Int): ByteArray {
                assertEquals("image", resourceId); assertEquals("revision", bookRevision)
                requested = maxBytes
                return bytes
            }
        }
        val loader = ReaderImageLoaders.defaultLoader()
        val request = ReaderImageRequest("image", source, "revision", viewport, bytes.size, 1024)
        val bitmap = requireNotNull(loader.load(request))
        assertTrue(bitmap.allocationByteCount <= 1024)
        assertEquals(bytes.size, requested)
        try { loader.load(request.copy(maxEncodedBytes = 1)); fail("Encoded budget must be checked") }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun publicTextProviderCanBeWrappedWithoutLosingSourceGeometry() {
        val standard = BlockLayoutProviders.text()
        val wrapper = object : BlockLayoutProvider by standard {
            override val id = "host.text"
            override val version = "1"
        }
        val block = TextBlock("p", "Custom wrappers retain source selection and accessibility.")
        assertTrue(wrapper.supports(block))
        val layout = wrapper.measure(block, context)
        val fragment = layout.fragment(0, layout.units.size)
        assertEquals("p", fragment.blockId)
        assertEquals(block.text, fragment.accessibilityText.toString())
        assertFalse(requireNotNull(fragment.selectionPath(0, 6)).isEmpty)
        assertNotNull(fragment.caretForOffset(0, false))
    }
}
