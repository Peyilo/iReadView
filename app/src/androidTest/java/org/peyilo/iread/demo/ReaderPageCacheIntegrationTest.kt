package org.peyilo.iread.demo

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.spi.body.ReaderBodyView
import org.peyilo.ireadview.spi.page.*
import org.peyilo.ireadview.widget.ReaderView
import java.io.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class ReaderPageCacheIntegrationTest {
    private val bindings=java.util.WeakHashMap<View,ReadingPosition?>()
    private val text="Identical page cache ownership and navigation behavior. ".repeat(18)
    @Test fun textAndEpubUseTheSameSwitchableThreePagePolicy() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase==ReaderPhase.Ready }
            scenario.onActivity {
                val reader=it.findViewById<ReaderView>(R.id.reader)
                reader.closeBook()
                val defaults=ReaderPageFactories.default()
                reader.setPageFactory(ReaderPageFactory { context ->
                    val template=defaults.create(context)
                    object:ReaderPageTemplate by template {
                        override fun bind(context:ReaderPageContext) {
                            template.bind(context)
                            bindings[root]=context.position
                        }
                        override fun unbind() {bindings.remove(root);template.unbind()}
                    }
                })
            }
            for(epub in listOf(false,true)) {
                var owned:AutoCloseable?=null
                val id="page-cache-$epub-${System.nanoTime()}"
                scenario.onActivity { assertTrue(it.findViewById<ReaderView>(R.id.reader).setPageCacheMode(ReaderPageCacheMode.THREE_PAGE) is ReaderResult.Success) }
                request<OpenResult>(scenario) { r,c ->
                    val source=if(epub) EpubReaderBookSource(id,ReaderInput { ByteArrayInputStream(publication()) },File(r.context.cacheDir,"cache-contract")).also { owned=it }
                    else MemoryBookSource(BookDescriptor(id,"Cache","v1",listOf(TocEntry.Chapter(ChapterDescriptor("chapter","Chapter","v1")))),
                        listOf(ChapterContent(id,"chapter","v1",(0 until 100).map { TextBlock("p$it",text) })))
                    r.openBook(source,callback=c)
                }
                try {
                    request<ReaderResult<ModeChange>>(scenario) { r,c -> r.setReadingMode(ReadingMode.PAGED,c) }
                    repeat(3) { request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.nextPage(NavigationOptions(animated=false,respectInterval=false),c) } }
                    await(scenario) { bodies(it).size==3 }
                    SystemClock.sleep(600) // Both asynchronous body backends finish idle read-ahead.
                    var oldBodies=emptySet<ReaderBodyView>()
                    var previous:ReaderBodyView?=null
                    var before:ReadingPosition?=null
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader)
                        oldBodies=bodies(r).toSet();previous=displayed(r);before=r.currentPosition
                        assertEquals(ReaderPageCacheMode.THREE_PAGE,r.pageCacheMode)
                        r.setPageTurnEffect(org.peyilo.ireadview.api.effect.PageTurnEffects.fromId("slide")!!)
                        r.setPageTurnOptions(org.peyilo.ireadview.api.effect.PageTurnOptions(durationMs=240))
                    }
                    val turning=LinkedBlockingQueue<ReaderCompletion<NavigationResult<ReadingPosition>>>()
                    scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).nextPage(NavigationOptions(respectInterval=false),ReaderCallback { result -> turning.add(result) }) }
                    await(scenario) { it.navigationState != ReadNavigationState.Idle }
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader)
                        assertEquals(ReaderResult.Rejected(ReaderRejection.Busy),r.setPageCacheMode(ReaderPageCacheMode.SINGLE_PAGE))
                        assertEquals(ReaderPageCacheMode.THREE_PAGE,r.pageCacheMode)
                    }
                    assertTrue(turning.poll(40,TimeUnit.SECONDS) is ReaderCompletion.Completed)
                    scenario.onActivity { assertTrue("$epub next page was not preloaded",displayed(it.findViewById(R.id.reader)) in oldBodies) }
                    request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.previousPage(NavigationOptions(animated=false,respectInterval=false),c) }
                    SystemClock.sleep(600)
                    await(scenario) { bodies(it).size==3 }
                    var cancelBodies=emptySet<ReaderBodyView>()
                    val cancelling=LinkedBlockingQueue<ReaderCompletion<NavigationResult<ReadingPosition>>>()
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader)
                        cancelBodies=bodies(r).toSet()
                        r.setPageTurnOptions(org.peyilo.ireadview.api.effect.PageTurnOptions(durationMs=1200))
                        r.nextPage(NavigationOptions(respectInterval=false),ReaderCallback { result -> cancelling.add(result) })
                    }
                    await(scenario) { it.navigationState==ReadNavigationState.Settling }
                    scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).cancelTransition() }
                    assertEquals(ReaderCompletion.Cancelled,cancelling.poll(40,TimeUnit.SECONDS))
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader)
                        assertEquals("$epub cancelled navigation changed position",before,r.currentPosition)
                        assertEquals("$epub cancelled prepared page was recreated",cancelBodies,bodies(r).toSet())
                    }
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader)
                        assertSame("$epub reverse page was recreated",previous,displayed(r))
                        assertEquals(before,r.currentPosition)
                        assertTrue(r.setPageCacheMode(ReaderPageCacheMode.SINGLE_PAGE) is ReaderResult.Success)
                        assertEquals(before,r.currentPosition)
                    }
                    await(scenario) { bodies(it).size==1 }
                    request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.nextPage(NavigationOptions(animated=false,respectInterval=false),c) }
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader)
                        assertEquals(1,bodies(r).size)
                        assertEquals(ReaderPageCacheMode.SINGLE_PAGE,r.pageCacheMode)
                        before=r.currentPosition
                        assertTrue(r.setPageCacheMode(ReaderPageCacheMode.THREE_PAGE) is ReaderResult.Success)
                        assertEquals(before,r.currentPosition)
                    }
                    await(scenario) { bodies(it).size==3 }
                    scenario.onActivity { oldBodies=bodies(it.findViewById(R.id.reader)).toSet() }
                    request<ReaderResult<StyleApplication>>(scenario) { r,c -> r.applyStyle(r.style.copy(body=r.style.body.copy(textColor=r.style.body.textColor xor 0x00000101)),c) }
                    await(scenario) { bodies(it).size==3 }
                    scenario.onActivity { assertTrue("Old style pages retained",bodies(it.findViewById(R.id.reader)).none { body -> body in oldBodies }) }
                    request<ReaderResult<ModeChange>>(scenario) { r,c -> r.setReadingMode(ReadingMode.CONTINUOUS_SCROLL,c) }
                    scenario.onActivity {
                        val r=it.findViewById<ReaderView>(R.id.reader)
                        before=r.currentPosition
                        assertTrue(r.setPageCacheMode(ReaderPageCacheMode.SINGLE_PAGE) is ReaderResult.Success)
                        assertEquals(before,r.currentPosition)
                    }
                    request<ReaderResult<ModeChange>>(scenario) { r,c -> r.setReadingMode(ReadingMode.PAGED,c) }
                    await(scenario) { bodies(it).size==1 }
                    scenario.onActivity { assertEquals(ReaderPageCacheMode.SINGLE_PAGE,it.findViewById<ReaderView>(R.id.reader).pageCacheMode) }
                } finally {
                    scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                    owned?.close()
                }
                scenario.onActivity { assertTrue(bodies(it.findViewById(R.id.reader)).isEmpty()) }
            }
        }
    }
    private fun bodies(view:View):List<ReaderBodyView> = if(view is ReaderBodyView)listOf(view)
        else if(view is ViewGroup)(0 until view.childCount).flatMap { bodies(view.getChildAt(it)) } else emptyList()
    private fun displayed(reader:ReaderView):ReaderBodyView {
        val current=requireNotNull(reader.currentPosition)
        return bodies(reader).single { body ->
            var node:View?=body
            var bound:ReadingPosition?=null
            while(node!=null && bound==null) {bound=bindings[node];node=node.parent as? View}
            bound?.let { it.bookId==current.bookId && it.chapterId==current.chapterId && it.pageNumberHint==current.pageNumberHint }==true
        }
    }
    private fun await(scenario:ActivityScenario<ReaderActivity>,predicate:(ReaderView)->Boolean) {
        val deadline=SystemClock.uptimeMillis()+20000
        do { var ready=false;scenario.onActivity { ready=predicate(it.findViewById(R.id.reader)) };if(ready)return;SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<deadline)
        error("Page cache timed out")
    }
    private fun <T> request(scenario:ActivityScenario<ReaderActivity>,action:(ReaderView,ReaderCallback<T>)->Unit):T {
        val queue=LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity { action(it.findViewById(R.id.reader),ReaderCallback { queue.add(it) }) }
        val result=queue.poll(40,TimeUnit.SECONDS)
        assertTrue("$result",result is ReaderCompletion.Completed)
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
            entry("book.opf","""<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Cache</dc:title></metadata><manifest><item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""")
            entry("chapter.xhtml","""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Cache</title></head><body>${(0 until 100).joinToString("") { "<p>$text</p>" }}</body></html>""")
        }
        return output.toByteArray()
    }
}
