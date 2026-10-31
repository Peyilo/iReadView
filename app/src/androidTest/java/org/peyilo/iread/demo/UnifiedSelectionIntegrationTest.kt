package org.peyilo.iread.demo

import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.iread.demo.data.storage.AnnotationCodecs
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.style.TextLineStyle
import org.peyilo.ireadview.api.style.TextLinePattern
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.spi.body.ReaderBodyView
import org.peyilo.ireadview.spi.selection.SelectionActions
import org.peyilo.ireadview.widget.ReaderView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Identical user gestures and public API assertions for the two body backends. */
@RunWith(AndroidJUnit4::class)
class UnifiedSelectionIntegrationTest {
    @Test fun nativeParagraphHandlesCrossPageAndAnnotationsUseTheCommonContract() = verify(false)
    @Test fun epubParagraphHandlesCrossPageAndAnnotationsUseTheCommonContract() = verify(true)

    @Test fun epubSelectionTracksNativeScrollBeforeJavascriptReply() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase==ReaderPhase.Ready }
            var source:EpubReaderBookSource?=null
            request<OpenResult>(scenario) { reader,callback ->
                source=EpubReaderBookSource("scroll-selection-${System.nanoTime()}",ReaderInput { ByteArrayInputStream(publication()) },
                    File(reader.context.cacheDir,"scroll-selection"))
                reader.openBook(source!!,callback=callback)
            }
            try {
                request<ReaderResult<ModeChange>>(scenario) { reader,callback -> reader.setReadingMode(ReadingMode.CONTINUOUS_SCROLL,callback) }
                request<NavigationResult<ReadingPosition>>(scenario) { reader,callback -> reader.firstPage(callback) }
                val press=PointF()
                scenario.onActivity {
                    val body=findBody(it.findViewById(R.id.reader))!!
                    val origin=IntArray(2);body.getLocationOnScreen(origin)
                    press.set(origin[0]+body.width*.3f,origin[1]+body.height*.2f)
                }
                val down=SystemClock.uptimeMillis()
                pointer(down,MotionEvent.ACTION_DOWN,press)
                try {
                    await(scenario) { it.getSelection()!=null }
                    scenario.onActivity {
                        fun selectedBody(view:View):ReaderBodyView? {
                            if(view is ReaderBodyView && view.selectionBounds()!=null)return view
                            if(view is ViewGroup)for(index in 0 until view.childCount)selectedBody(view.getChildAt(index))?.let { return it }
                            return null
                        }
                        val body=selectedBody(it.findViewById(R.id.reader))!!
                        val type=body.javaClass
                        val web=type.getDeclaredField("web").apply { isAccessible=true }.get(body) as android.webkit.WebView
                        val path=type.getDeclaredField("selectionPath").apply { isAccessible=true }.get(body) as android.graphics.Path
                        val selected=type.getDeclaredField("selected").apply { isAccessible=true }
                        val snapshot=JSONObject((selected.get(body) as JSONObject).toString())
                        fun area()=RectF().also { path.computeBounds(it,true) }
                        // All checks occur in this UI turn: no evaluateJavascript reply can
                        // arrive between scrollBy and the geometry assertion.
                        for(delta in List(20){12}+List(10){-12}) {
                            val before=area();val y=web.scrollY
                            web.scrollBy(0,delta)
                            val moved=web.scrollY-y
                            assertTrue("Document did not scroll",moved!=0)
                            val after=area()
                            assertEquals("Selection fill lagged native scroll",before.top-moved,after.top,.1f)
                            assertEquals("Selection fill height changed while scrolling",before.height(),after.height(),.1f)
                        }
                        val beforeReply=area()
                        type.getDeclaredMethod("acceptSelection",JSONObject::class.java).apply { isAccessible=true }.invoke(body,snapshot)
                        assertEquals("Old JS reply moved selection backwards",beforeReply.top,area().top,.1f)
                        assertEquals(beforeReply.bottom,area().bottom,.1f)
                    }
                } finally { pointer(down,MotionEvent.ACTION_UP,press) }
            } finally { scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() };source?.close() }
        }
    }

    private val paragraph = ("A complete paragraph with stable words and 中文内容 for selection. ".repeat(200) + "END")

    private fun verify(epub: Boolean) {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase == ReaderPhase.Ready }
            val id = "selection-contract-${if (epub) "epub" else "text"}-${System.nanoTime()}"
            var owned: AutoCloseable? = null
            var openedSource: BookSource? = null
            val opened = request<OpenResult>(scenario) { reader, callback ->
                val source = if (epub) {
                    val bytes = publication()
                    EpubReaderBookSource(id, ReaderInput { ByteArrayInputStream(bytes) },
                        File(reader.context.cacheDir, "selection-contract")).also { owned = it }
                } else MemoryBookSource(BookDescriptor(id, "Selection", "v1", listOf(TocEntry.Chapter(ChapterDescriptor("chapter", "Chapter", "v1")))),
                    listOf(ChapterContent(id, "chapter", "v1", listOf(TextBlock("paragraph", paragraph)))))
                openedSource = source
                reader.openBook(source, callback = callback)
            }
            assertTrue("open: $opened", opened is OpenResult.Opened)
            try {
                request<ReaderResult<ModeChange>>(scenario) { reader, callback -> reader.setReadingMode(ReadingMode.PAGED, callback) }
                request<ReaderResult<StyleApplication>>(scenario) { reader, callback -> reader.applyStyle(ReaderStyle.default(), callback) }
                var body = RectF()
                var origin = PointF()
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    reader.setSelectionToolbarFactory(null)
                    val location = IntArray(2); reader.getLocationOnScreen(location)
                    origin = PointF(location[0].toFloat(), location[1].toFloat())
                    val content = findBody(reader) ?: error("No body")
                    content.getLocationOnScreen(location)
                    body = RectF(location[0].toFloat(), location[1].toFloat(), (location[0] + content.width).toFloat(), (location[1] + content.height).toFloat())
                }
                val press = PointF(body.left + body.width() * .3f, body.top + body.height() * .2f)
                gesture(press, press, 750)
                await(scenario) { it.getSelectedText() == paragraph }
                var originalStart = ""
                var stale: SelectionActions? = null
                var end = PointF()
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    originalStart = start(reader.getSelection()!!)
                    stale = reader.getSelectionActions()
                    end = reader.getSelectionHandleBounds(false)!!.let { PointF(it.centerX() + origin.x, it.centerY() + origin.y) }
                    assertNotNull(reader.getSelectionHandleBounds(true))
                }
                gesture(end, PointF(body.left + body.width() * .6f, body.top + body.height() * .5f), 350)
                // ACTION_UP resolves its final DOM caret asynchronously. The next handle must be
                // read after that operation ends, rather than from the preceding MOVE reply.
                await(scenario) { val text = it.getSelectedText(); text != null && text.length < paragraph.length && selectionDragFinished(it) }
                var shortLength = 0
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    assertEquals(originalStart, start(reader.getSelection()!!))
                    shortLength = reader.getSelectedText()!!.length
                    assertFalse(stale!!.isValid())
                    assertEquals(ReaderResult.Rejected(ReaderRejection.InvalidSelection), stale!!.copyText())
                    end = reader.getSelectionHandleBounds(false)!!.let { PointF(it.centerX() + origin.x, it.centerY() + origin.y) }
                }
                val bottom = PointF(body.left + body.width() * .65f, body.bottom - 2)
                val down = SystemClock.uptimeMillis()
                pointer(down, MotionEvent.ACTION_DOWN, end)
                pointer(down, MotionEvent.ACTION_MOVE, bottom)
                try { await(scenario) { (it.currentPosition?.pageNumberHint ?: 1) >= 2 && it.navigationState == ReadNavigationState.Idle } }
                finally { pointer(down, MotionEvent.ACTION_UP, bottom) }
                await(scenario) { (it.getSelectedText()?.length ?: 0) > shortLength && selectionDragFinished(it) }
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    assertEquals(originalStart, start(reader.getSelection()!!))
                    assertTrue(reader.getSelectedText()!!.startsWith("A complete paragraph"))
                    assertNotNull(reader.getSelectionHandleBounds(true)) // off-page proxy, original endpoint retained
                    assertTrue(reader.highlightSelection() is ReaderResult.Success)
                    assertNull(reader.getSelection())
                }
                await(scenario) { val item = it.exportAnnotations()?.allHighlights?.singleOrNull(); item != null && it.getAnnotationStatus(item.id) == AnnotationStatus.Valid }
                var saved: AnnotationSnapshot? = null
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    val snapshot = reader.exportAnnotations()!!
                    val roundTrip = snapshot.items.map { AnnotationCodecs.decode(id, AnnotationCodecs.encode(it))!! }
                    assertEquals(snapshot.items, roundTrip)
                    saved = AnnotationSnapshot.fromItems(id, roundTrip)
                    assertTrue(reader.replaceAnnotations(AnnotationSnapshot(id)) is ReaderResult.Success)
                    assertTrue(reader.replaceAnnotations(saved!!) is ReaderResult.Success)
                }
                await(scenario) { it.getAnnotationStatus(saved!!.items.single().id) == AnnotationStatus.Valid }
                val moved = request<NavigationResult<ReadingPosition>>(scenario) { reader, callback -> reader.goToAnnotation(saved!!.items.single().id, callback) }
                assertTrue("annotation jump: $moved", moved is NavigationResult.Moved || moved is NavigationResult.Unchanged)
                gesture(press, press, 750)
                await(scenario) { it.getSelectionActions()?.hasHighlight() == true }
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    assertTrue(reader.toggleSelectionHighlight() is ReaderResult.Success)
                    assertTrue(reader.exportAnnotations()!!.allHighlights.isEmpty())
                }
                gesture(press, press, 750)
                await(scenario) { it.getSelection() != null }
                var underlineId = ""
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    val line = reader.underlineSelection(TextLineStyle(pattern = TextLinePattern.WAVY))
                    assertTrue(line is ReaderResult.Success)
                    underlineId = (line as ReaderResult.Success).value.id
                    assertNull(reader.getSelection())
                    saved = reader.exportAnnotations()!!
                    assertEquals(saved!!.items, saved!!.items.map { AnnotationCodecs.decode(id, AnnotationCodecs.encode(it))!! })
                }
                await(scenario) { it.getAnnotationStatus(underlineId) == AnnotationStatus.Valid }
                assertTrue(request<OpenResult>(scenario) { reader, callback -> reader.openBook(openedSource!!, callback = callback) } is OpenResult.Opened)
                scenario.onActivity { assertTrue(it.findViewById<ReaderView>(R.id.reader).replaceAnnotations(saved!!) is ReaderResult.Success) }
                await(scenario) { it.getAnnotationStatus(underlineId) == AnnotationStatus.Valid }
                gesture(press, press, 750)
                await(scenario) { it.getSelectedText() == paragraph }
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    assertTrue(reader.copySelectedText() is ReaderResult.Success)
                    assertNull(reader.getSelection())
                }
                request<ReaderResult<ModeChange>>(scenario) { reader, callback -> reader.setReadingMode(ReadingMode.CONTINUOUS_SCROLL, callback) }
                request<NavigationResult<ReadingPosition>>(scenario) { reader, callback -> reader.firstPage(callback) }
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    val content = findBody(reader)!!
                    val location = IntArray(2); content.getLocationOnScreen(location)
                    body = RectF(location[0].toFloat(), location[1].toFloat(), (location[0]+content.width).toFloat(), (location[1]+content.height).toFloat())
                }
                val scrollPress = PointF(body.left + body.width()*.3f, body.top + body.height()*.2f)
                gesture(scrollPress, scrollPress, 750)
                await(scenario) { it.getSelectedText() == paragraph }
                scenario.onActivity { activity ->
                    val reader = activity.findViewById<ReaderView>(R.id.reader)
                    end = reader.getSelectionHandleBounds(true)!!.let { PointF(it.centerX()+origin.x,it.centerY()+origin.y) }
                }
                val scrollEdge = PointF(body.left+body.width()*.6f, body.bottom-2)
                val scrollDown = SystemClock.uptimeMillis()
                pointer(scrollDown, MotionEvent.ACTION_DOWN, end)
                pointer(scrollDown, MotionEvent.ACTION_MOVE, scrollEdge)
                try {
                    await(scenario) { (it.getSelectedText()?.length ?: paragraph.length) < paragraph.length }
                    var beforeAutoScroll = 0
                    scenario.onActivity { beforeAutoScroll = it.findViewById<ReaderView>(R.id.reader).getSelectedText()!!.length }
                    await(scenario) { (it.getSelectedText()?.length ?: paragraph.length) < beforeAutoScroll - 20 }
                } finally { pointer(scrollDown, MotionEvent.ACTION_UP, scrollEdge) }
                await(scenario) { it.getSelectionActions()?.isValid() == true }
            } finally {
                scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                owned?.close()
            }
        }
    }
    private fun selectionDragFinished(reader: ReaderView): Boolean =
        !org.peyilo.ireadview.widget.BookReadView::class.java.getDeclaredField("draggingSelection")
            .apply { isAccessible = true }.getBoolean(reader)

    private fun start(selection: ReaderSelection): String = when (selection) {
        is TextSelection -> selection.range.start.toString()
        is DocumentTextSelection -> JSONObject(selection.documentLocator).getJSONObject("range").getJSONObject("start").toString()
        else -> error("Unknown selection")
    }
    private fun findBody(view: View): ReaderBodyView? {
        if (view is ReaderBodyView && view.isShown) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findBody(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun pointer(down: Long, action: Int, point: PointF) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
        try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event) } finally { event.recycle() }
    }
    private fun gesture(from: PointF, to: PointF, duration: Long) {
        val down = SystemClock.uptimeMillis()
        pointer(down, MotionEvent.ACTION_DOWN, from)
        if (from == to) SystemClock.sleep(duration) else for (step in 1..10) {
            SystemClock.sleep(duration / 10)
            pointer(down, MotionEvent.ACTION_MOVE, PointF(from.x + (to.x-from.x)*step/10, from.y + (to.y-from.y)*step/10))
        }
        pointer(down, MotionEvent.ACTION_UP, to)
    }
    private fun await(scenario: ActivityScenario<ReaderActivity>, predicate: (ReaderView) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20000
        var state = ""
        do {
            var ready = false
            scenario.onActivity { val reader = it.findViewById<ReaderView>(R.id.reader); ready = predicate(reader); state = "${reader.currentPosition} selected=${reader.getSelectedText()?.length} ${reader.readerState.phase}" }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Selection condition timed out: $state")
    }
    private fun <T> request(scenario: ActivityScenario<ReaderActivity>, call: (ReaderView, ReaderCallback<T>) -> Unit): T {
        val queue = LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity { call(it.findViewById(R.id.reader), ReaderCallback { result -> queue.add(result) }) }
        val completion = queue.poll(30, TimeUnit.SECONDS)
        assertTrue("operation: $completion", completion is ReaderCompletion.Completed)
        return (completion as ReaderCompletion.Completed).value
    }
    private fun publication(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, text: String, stored: Boolean = false) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                val item = ZipEntry(path)
                if (stored) { item.method = ZipEntry.STORED; item.size = bytes.size.toLong(); item.compressedSize = item.size; item.crc = CRC32().apply { update(bytes) }.value }
                zip.putNextEntry(item); zip.write(bytes); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip", true)
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Selection</dc:title></metadata><manifest><item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""")
            entry("chapter.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Selection</title></head><body><p>$paragraph</p></body></html>""")
        }
        return out.toByteArray()
    }
}
