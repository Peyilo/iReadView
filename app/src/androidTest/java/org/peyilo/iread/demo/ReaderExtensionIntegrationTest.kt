package org.peyilo.iread.demo

import android.graphics.*
import android.os.SystemClock
import android.view.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.SelectionStyle
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.spi.body.*
import org.peyilo.ireadview.spi.presentation.*
import org.peyilo.ireadview.spi.selection.*
import org.peyilo.ireadview.widget.ReaderView
import java.io.*
import java.util.concurrent.*
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class ReaderExtensionIntegrationTest {
    private val paragraph="Independent content, selection and page effects remain replaceable. ".repeat(100)
    private class Counts {
        var bodies=0;var bodyClosed=0;var selection=0;var selectionClosed=0;var controls=0;var controlClosed=0
        var draw=0;var fill=0;var longPress=0;var touch=0;var starts=0;var effectDraw=0
        var underlines=0;var underlineClosed=0;var underlineDraw=0
    }
    @Test fun replaceComponentsForTextAndEpubWithoutReopeningTheSource() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase==ReaderPhase.Ready }
            for(epub in listOf(false,true)) {
                val counts=Counts();var owned:AutoCloseable?=null;var loads=0;var factoryCalls=0
                scenario.onActivity {
                    val reader=it.findViewById<ReaderView>(R.id.reader)
                    reader.closeBook()
                    assertTrue(reader.setPresentationComponents(components(counts)) is ReaderResult.Success)
                    assertTrue(reader.setBookPresentationFactory(BookPresentationFactory { context,mode,source,host ->
                        factoryCalls++
                        if(mode==ReadingMode.PAGED) (source as? PresentationBookSource)?.createPresentation(context,mode,host)
                            ?: BookPresentations.createNative(context,mode,host) else null
                    }) is ReaderResult.Success)
                }
                val id="extensions-$epub-${System.nanoTime()}"
                request<OpenResult>(scenario) { reader,callback ->
                    val source=if(epub) {
                        val backend=EpubReaderBookSource(id,ReaderInput { ByteArrayInputStream(publication()) },File(reader.context.cacheDir,"extension-test"))
                        owned=backend
                        object:PresentationBookSource by backend { override suspend fun loadBook():BookDescriptor { loads++;return backend.loadBook() } }
                    } else {
                        val backend=MemoryBookSource(BookDescriptor(id,"Extensions","v1",listOf(TocEntry.Chapter(ChapterDescriptor("chapter","Chapter","v1")))),
                            listOf(ChapterContent(id,"chapter","v1",listOf(TextBlock("p",paragraph)))))
                        object:BookSource by backend { override suspend fun loadBook():BookDescriptor { loads++;return backend.loadBook() } }
                    }
                    reader.openBook(source,callback=callback)
                }
                try {
                    for(mode in listOf(ReadingMode.PAGED,ReadingMode.CONTINUOUS_SCROLL)) {
                        request<ReaderResult<ModeChange>>(scenario) { r,c -> r.setReadingMode(mode,c) }
                        request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.firstPage(c) }
                        scenario.onActivity {
                            val reader=it.findViewById<ReaderView>(R.id.reader)
                            val features=reader.contentCapabilities
                            assertEquals(if(epub) ReaderSelectionModel.Document else ReaderSelectionModel.Text,features.selectionModel)
                            assertEquals(ReaderFeatureSupport.Supported,features.links)
                            assertEquals(ReaderFeatureSupport.Supported,features.rangedAnnotations)
                            assertEquals(if(epub) ReaderFeatureSupport.Unsupported else ReaderFeatureSupport.Supported,features.wholeBookPageIndex)
                            assertEquals(if(mode==ReadingMode.PAGED) ReaderFeatureSupport.Supported else ReaderFeatureSupport.Unsupported,features.pageTurnEffects)
                            assertEquals(features,reader.readerState.contentCapabilities)
                        }
                        await(scenario) { counts.draw>0 }
                        val press=PointF()
                        scenario.onActivity {
                            val body=shownBody(it.findViewById(R.id.reader))!!
                            val location=IntArray(2);body.getLocationOnScreen(location)
                            press.set(location[0]+body.width*.3f,location[1]+body.height*.2f)
                        }
                        val down=SystemClock.uptimeMillis()
                        pointer(down,MotionEvent.ACTION_DOWN,press);SystemClock.sleep(750)
                        pointer(down,MotionEvent.ACTION_UP,press)
                        await(scenario) { it.getSelection()!=null && counts.fill>0 }
                        scenario.onActivity {
                            assertEquals(paragraph,it.findViewById<ReaderView>(R.id.reader).getSelectedText())
                            assertTrue(counts.longPress>0)
                            assertTrue(counts.touch>0)
                            val reader=it.findViewById<ReaderView>(R.id.reader)
                            val selected=reader.getSelection()!!
                            val selectedPosition=requireNotNull(reader.getSelectionPosition())
                            assertEquals(selected.bookId,selectedPosition.bookId)
                            assertEquals(selected.chapterId,selectedPosition.chapterId)
                            if(epub) assertNotNull(selectedPosition.documentLocator) else assertNotNull(selectedPosition.anchor)
                            assertEquals(selected,reader.getSelection())
                            val bookmark=Bookmark("selection-bookmark-$mode",selectedPosition,selected.text.take(40))
                            assertTrue(reader.addBookmark(bookmark) is ReaderResult.Success)
                            assertEquals(selectedPosition,reader.getAnnotationPosition(bookmark.id))
                            val annotation=when(selected) {
                                is TextSelection -> Underline("line-$mode",selected.range,selected.text)
                                is DocumentTextSelection -> DocumentUnderline("line-$mode",selected)
                                else -> error("Unexpected selection type")
                            }
                            counts.underlineDraw=0
                            assertTrue(reader.addAnnotation(annotation) is ReaderResult.Success)
                        }
                        await(scenario) { counts.underlineDraw>0 }
                        val handle=PointF()
                        scenario.onActivity {
                            val r=it.findViewById<ReaderView>(R.id.reader)
                            val location=IntArray(2);r.getLocationOnScreen(location)
                            val area=requireNotNull(r.getSelectionHandleBounds(true))
                            handle.set(location[0]+area.centerX(),location[1]+area.centerY())
                        }
                        val grabbing=SystemClock.uptimeMillis()
                        pointer(grabbing,MotionEvent.ACTION_DOWN,handle)
                        try {
                            scenario.onActivity {
                                val r=it.findViewById<ReaderView>(R.id.reader)
                                assertEquals(ReaderResult.Rejected(ReaderRejection.Busy),r.setPresentationComponents(ReaderPresentationComponents.DEFAULT))
                                assertEquals(ReaderResult.Rejected(ReaderRejection.Busy),r.setBookPresentationFactory(null))
                                assertEquals(ReaderResult.Rejected(ReaderRejection.Busy),r.setImageLoadingOptions(org.peyilo.ireadview.spi.content.ReaderImageLoadingOptions()))
                            }
                        } finally { pointer(grabbing,MotionEvent.ACTION_UP,handle) }
                        if(mode==ReadingMode.PAGED) {
                            scenario.onActivity {
                                val r=it.findViewById<ReaderView>(R.id.reader);r.clearSelection()
                                val factory=PageTurnEffects.fromId("slide")!!
                                r.setPageTurnEffect(object:PageTurnEffectFactory {
                                    override val id="extension-effect"
                                    override fun create():PageTurnEffect {
                                        val delegate=factory.create()
                                        return object:SnapshotPageTurnEffect,PageTurnEffect by delegate {
                                            override val usesPageSnapshots get()=(delegate as? SnapshotPageTurnEffect)?.usesPageSnapshots==true
                                            override fun start(context:PageTurnContext) { counts.starts++;delegate.start(context) }
                                            override fun draw(canvas:Canvas,frame:PageTurnFrame) { counts.effectDraw++;delegate.draw(canvas,frame) }
                                        }
                                    }
                                })
                            }
                            request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.nextPage(NavigationOptions(respectInterval=false),c) }
                            assertTrue(counts.starts>0 && counts.effectDraw>0)
                        }
                    }
                    var before:ReadingPosition?=null
                    var oldActions:SelectionActions?=null
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader);before=r.currentPosition;oldActions=r.getSelectionActions()
                        assertEquals(1,loads)
                        assertTrue(requireNotNull(oldActions).isValid())
                        assertTrue(r.setPresentationComponents(ReaderPresentationComponents.DEFAULT) is ReaderResult.Success)
                    }
                    await(scenario) { it.readerState.phase==ReaderPhase.Ready && it.navigationState==ReadNavigationState.Idle && shownBody(it)!=null }
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader)
                        assertEquals(before?.chapterId,r.currentPosition?.chapterId)
                        assertFalse(requireNotNull(oldActions).isValid())
                        assertEquals(1,loads)
                        assertEquals(counts.bodies,counts.bodyClosed)
                        assertEquals(counts.selection,counts.selectionClosed)
                        assertEquals(counts.controls,counts.controlClosed)
                        assertEquals(counts.underlines,counts.underlineClosed)
                        assertTrue(factoryCalls>=2)
                        assertTrue(r.setBookPresentationFactory(null) is ReaderResult.Success)
                    }
                    await(scenario) { it.readerState.phase==ReaderPhase.Ready && it.navigationState==ReadNavigationState.Idle }
                    assertEquals(1,loads)
                } finally {
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader);r.closeBook();r.setPageTurnEffect(PageTurnEffects.fromId("none")!!)
                        assertEquals(ReaderContentCapabilities(),r.contentCapabilities)
                        assertNull(r.getSelectionPosition())
                    }
                    owned?.close()
                }
            }
        }
    }
    @Test fun undeclaredCustomEngineDoesNotInheritNativeCapabilities() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase==ReaderPhase.Ready }
            request<ReaderResult<ModeChange>>(scenario) { r,c -> r.setReadingMode(ReadingMode.PAGED,c) }
            scenario.onActivity {
                val reader=it.findViewById<ReaderView>(R.id.reader)
                reader.closeBook()
                reader.setBookPresentationFactory(BookPresentationFactory { context,mode,_,host ->
                    val native=BookPresentations.createNative(context,mode,host)
                    object:BookPresentation(),ReadContentAdapter<ReadingPosition> by native {}
                })
            }
            val id="unknown-features-${System.nanoTime()}"
            val source=MemoryBookSource(BookDescriptor(id,"Custom engine","v1",listOf(TocEntry.Chapter(ChapterDescriptor("chapter","Chapter","v1")))),
                listOf(ChapterContent(id,"chapter","v1",listOf(TextBlock("p",paragraph)))))
            assertTrue(request<OpenResult>(scenario) { r,c -> r.openBook(source,callback=c) } is OpenResult.Opened)
            scenario.onActivity {
                val reader=it.findViewById<ReaderView>(R.id.reader)
                assertEquals(ReaderSelectionModel.Unknown,reader.contentCapabilities.selectionModel)
                assertEquals(ReaderFeatureSupport.Unknown,reader.contentCapabilities.links)
                assertEquals(ReaderFeatureSupport.Unknown,reader.contentCapabilities.rangedAnnotations)
                assertEquals(ReaderFeatureSupport.Unsupported,reader.contentCapabilities.wholeBookPageIndex)
                assertEquals(reader.contentCapabilities,reader.readerState.contentCapabilities)
            }
            assertEquals(ReaderResult.Rejected(ReaderRejection.Unsupported),
                request<ReaderResult<BookPageIndex>>(scenario) { r,c -> r.buildBookPageIndex(c) })
            scenario.onActivity {
                val reader=it.findViewById<ReaderView>(R.id.reader)
                reader.closeBook();reader.setBookPresentationFactory(null);reader.release()
                assertEquals(ReaderContentCapabilities(),reader.contentCapabilities)
                assertNull(reader.getSelectionPosition())
            }
        }
    }
    @Test fun replaceNativeImageLoadingWithoutReopeningBook() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase==ReaderPhase.Ready }
            val first=java.util.concurrent.atomic.AtomicInteger()
            val second=java.util.concurrent.atomic.AtomicInteger()
            val loads=java.util.concurrent.atomic.AtomicInteger()
            fun options(calls:java.util.concurrent.atomic.AtomicInteger)=org.peyilo.ireadview.spi.content.ReaderImageLoadingOptions(
                loader=org.peyilo.ireadview.spi.content.ReaderImageLoader {
                    assertFalse(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())
                    calls.incrementAndGet()
                    Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
                })
            val firstOptions=options(first)
            val secondOptions=options(second)
            scenario.onActivity {
                val reader=it.findViewById<ReaderView>(R.id.reader)
                reader.closeBook()
                assertTrue(reader.setImageLoadingOptions(firstOptions) is ReaderResult.Success)
            }
            val id="image-strategy-${System.nanoTime()}"
            val backend=MemoryBookSource(BookDescriptor(id,"Image strategies","v1",listOf(TocEntry.Chapter(ChapterDescriptor("chapter","Chapter","v1")))),
                listOf(ChapterContent(id,"chapter","v1",listOf(ImageBlock("image","cover"),TextBlock("p",paragraph)))))
            val source=object:BookSource by backend {
                override suspend fun loadBook():BookDescriptor { loads.incrementAndGet();return backend.loadBook() }
            }
            assertTrue(request<OpenResult>(scenario) { r,c -> r.openBook(source,callback=c) } is OpenResult.Opened)
            assertTrue(first.get()>0)
            var before:ReadingPosition?=null
            scenario.onActivity {
                val reader=it.findViewById<ReaderView>(R.id.reader)
                before=reader.currentPosition
                assertTrue(reader.setImageLoadingOptions(secondOptions) is ReaderResult.Success)
            }
            await(scenario) { it.readerState.phase==ReaderPhase.Ready && it.navigationState==ReadNavigationState.Idle && second.get()>0 }
            scenario.onActivity {
                val reader=it.findViewById<ReaderView>(R.id.reader)
                assertEquals(before?.anchor,reader.currentPosition?.anchor)
                assertEquals(before?.chapterId,reader.currentPosition?.chapterId)
                assertEquals(secondOptions,reader.imageLoadingOptions)
                assertEquals(1,loads.get())
                reader.closeBook()
            }
        }
    }
    private fun components(counts:Counts)=ReaderPresentationComponents(
        bodyRendererFactory=ReaderBodyRendererFactory {
            counts.bodies++
            object:ReaderBodyRenderer {
                override fun draw(canvas:Canvas,body:ReaderBodyView,drawDefault:(Canvas)->Unit) { counts.draw++;drawDefault(canvas) }
                override fun close() { counts.bodyClosed++ }
            }
        },
        selectionRendererFactory=ReaderSelectionRendererFactory {
            counts.selection++
            object:ReaderSelectionPainter() {
                override fun drawFill(canvas:Canvas,path:Path,style:SelectionStyle) { counts.fill++;super.drawFill(canvas,path,style) }
                override fun close() { counts.selectionClosed++ }
            }
        },
        selectionControllerFactory=ReaderSelectionControllerFactory { context,delegate ->
            counts.controls++
            val control=ReaderSelectionInteraction(context,delegate)
            object:ReaderSelectionController by control {
                override fun onLongPress(x:Float,y:Float):Boolean? { counts.longPress++;return null }
                override fun onTouch(action:Int,x:Float,y:Float):Boolean { counts.touch++;return control.onTouch(action,x,y) }
                override fun release() { counts.controlClosed++;control.release() }
            }
        },
        underlineRendererFactory=ReaderUnderlineRendererFactory {
            counts.underlines++
            val standard=ReaderUnderlinePainter()
            object:ReaderUnderlineRenderer {
                override fun draw(canvas:Canvas,segments:List<org.peyilo.ireadview.spi.content.TextLineSegment>,
                    style:org.peyilo.ireadview.api.style.TextLineStyle,density:Float) {
                    counts.underlineDraw++;standard.draw(canvas,segments,style,density)
                }
                override fun close() { counts.underlineClosed++;standard.close() }
            }
        })
    private fun shownBody(view:View):ReaderBodyView? {
        if(view.visibility!=View.VISIBLE || view.alpha==0f)return null
        if(view is ReaderBodyView)return view
        if(view is ViewGroup)for(index in 0 until view.childCount)shownBody(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun pointer(down:Long,action:Int,point:PointF) {
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,point.x,point.y,0)
        try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event) } finally { event.recycle() }
    }
    private fun await(scenario:ActivityScenario<ReaderActivity>,predicate:(ReaderView)->Boolean) {
        val deadline=SystemClock.uptimeMillis()+20000
        do { var ready=false;scenario.onActivity { ready=predicate(it.findViewById(R.id.reader)) };if(ready)return;SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<deadline)
        error("Extension test timed out")
    }
    private fun <T> request(scenario:ActivityScenario<ReaderActivity>,action:(ReaderView,ReaderCallback<T>)->Unit):T {
        val queue=LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity { action(it.findViewById(R.id.reader),ReaderCallback { queue.add(it) }) }
        val result=queue.poll(40,TimeUnit.SECONDS);assertTrue("$result",result is ReaderCompletion.Completed)
        return (result as ReaderCompletion.Completed).value
    }
    private fun publication():ByteArray {
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(path:String,value:String) {
                val bytes=value.toByteArray();val item=ZipEntry(path)
                if(path=="mimetype") { item.method=ZipEntry.STORED;item.size=bytes.size.toLong();item.compressedSize=item.size;item.crc=CRC32().apply { update(bytes) }.value }
                zip.putNextEntry(item);zip.write(bytes);zip.closeEntry()
            }
            entry("mimetype","application/epub+zip")
            entry("META-INF/container.xml","""<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("book.opf","""<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Extensions</dc:title></metadata><manifest><item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""")
            entry("chapter.xhtml","""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Extensions</title></head><body><p>$paragraph</p></body></html>""")
        }
        return output.toByteArray()
    }
}
