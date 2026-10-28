package org.peyilo.iread.demo

import android.graphics.*
import android.os.*
import android.view.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.input.ReaderInputOptions
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.spi.body.ReaderBodyView
import org.peyilo.ireadview.widget.ReaderView
import java.io.*
import java.util.concurrent.*
import java.util.zip.*

/** Response while the finger is held, plus per-frame PixelCopy through live handoff. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=26)
class ReaderGestureHandoffTest {
    private val text="Reader gesture tracking and stable page handoff. ".repeat(35)
    private class Probe {
        @Volatile var moveNs=0L
        @Volatile var firstDrawNs=0L
        @Volatile var progress=0f
        @Volatile var phase:TurnPhase?=null
    }
    @Test fun cancelledEpubDragKeepsBothPagesVisibleOnRetry() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase==ReaderPhase.Ready }
            resetTurnConfiguration(scenario)
            var source:EpubReaderBookSource?=null
            request<OpenResult>(scenario) { r,c ->
                source=EpubReaderBookSource("cancel-pages-${System.nanoTime()}",
                    ReaderInput { ByteArrayInputStream(publication(panels=true)) },File(r.context.cacheDir,"cancel-pages"))
                r.openBook(source!!,callback=c)
            }
            try {
                request<ReaderResult<ModeChange>>(scenario) { r,c -> r.setReadingMode(ReadingMode.PAGED,c) }
                for(effect in selectedEffects(listOf("ibook_slide","slide","cover","google_curl","ios_curl"))) {
                    request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.firstPage(c) }
                    scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).setPageTurnEffect(PageTurnEffects.fromId(effect)!!) }
                    for(forward in listOf(true,false)) {
                        SystemClock.sleep(750)
                        val rect=Rect()
                        var before:ReadingPosition?=null
                        scenario.onActivity {
                            val reader=it.findViewById<ReaderView>(R.id.reader)
                            before=reader.currentPosition
                            findBody(reader)!!.getGlobalVisibleRect(rect)
                        }
                        var firstPixels=0
                        fun x(value:Float)=if(forward)value else 1f-value
                        val currentRed=requireNotNull(before).pageNumberHint!!%2==1
                        repeat(3) { attempt ->
                            val down=SystemClock.uptimeMillis()
                            pointer(down,MotionEvent.ACTION_DOWN,rect,x(.85f))
                            pointer(down,MotionEvent.ACTION_MOVE,rect,.5f)
                            await(scenario) { it.navigationState==ReadNavigationState.Dragging }
                            SystemClock.sleep(350)
                            val bitmap=Bitmap.createBitmap(240,320,Bitmap.Config.ARGB_8888)
                            try {
                                val result=LinkedBlockingQueue<Int>()
                                scenario.onActivity { PixelCopy.request(it.window,rect,bitmap,{ status -> result.add(status) },Handler(Looper.getMainLooper())) }
                                assertEquals(PixelCopy.SUCCESS,result.poll(5,TimeUnit.SECONDS))
                                var currentPixels=0
                                var targetPixels=0
                                for(y in 80 until 240) for(x in 15 until 225) {
                                    val color=bitmap.getPixel(x,y)
                                    val red=Color.red(color)>130 && Color.blue(color)<110
                                    val blue=Color.blue(color)>130 && Color.red(color)<110
                                    if(if(currentRed)red else blue)currentPixels++
                                    if(if(currentRed)blue else red)targetPixels++
                                }
                                android.util.Log.i("CancelPages","$effect forward=$forward attempt=$attempt current=$currentPixels target=$targetPixels")
                                if(attempt==0)firstPixels=currentPixels
                                if(currentPixels<2000 || currentPixels<firstPixels*.65 || targetPixels<=1000) scenario.onActivity { activity ->
                                    File(activity.filesDir,"cancel-page-$effect-$attempt.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                                }
                                assertTrue("$effect attempt $attempt turning page disappeared ($currentPixels, first=$firstPixels)",currentPixels>2000 && currentPixels>=firstPixels*.65)
                                assertTrue("$effect attempt $attempt target page disappeared ($targetPixels)",targetPixels>1000)
                            } finally { bitmap.recycle() }
                            // Exercise both system cancellation and reversing the finger before UP.
                            if(attempt==0)pointer(down,MotionEvent.ACTION_CANCEL,rect,.5f)
                            else {
                                // Multiple return samples also reverse iOSCurl's native
                                // two-sample velocity filter, rather than forcing its decision.
                                for(point in listOf(.65f,.85f,.96f)) {
                                    pointer(down,MotionEvent.ACTION_MOVE,rect,x(point))
                                    SystemClock.sleep(20)
                                }
                                pointer(down,MotionEvent.ACTION_UP,rect,x(.96f))
                            }
                            await(scenario) { it.navigationState==ReadNavigationState.Idle }
                            scenario.onActivity { assertEquals("$effect cancellation must preserve position",before,it.findViewById<ReaderView>(R.id.reader).currentPosition) }
                            SystemClock.sleep(if(attempt==0) 50 else 750)
                        }
                        val down=SystemClock.uptimeMillis()
                        pointer(down,MotionEvent.ACTION_DOWN,rect,x(.85f))
                        pointer(down,MotionEvent.ACTION_MOVE,rect,x(.2f))
                        await(scenario) { it.navigationState==ReadNavigationState.Dragging }
                        SystemClock.sleep(150)
                        pointer(down,MotionEvent.ACTION_UP,rect,x(.2f))
                        await(scenario) { it.navigationState==ReadNavigationState.Idle && it.currentPosition!=before }
                    }
                }
            } finally { scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() };source?.close() }
        }
    }
    @Test fun bothEpubPagesRemainVisibleDuringRepeatedDrags() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase==ReaderPhase.Ready }
            resetTurnConfiguration(scenario)
            var source:EpubReaderBookSource?=null
            request<OpenResult>(scenario) { r,c ->
                source=EpubReaderBookSource("distinct-pages-${System.nanoTime()}",
                    ReaderInput { ByteArrayInputStream(publication(panels=true)) },File(r.context.cacheDir,"distinct-pages"))
                r.openBook(source!!,callback=c)
            }
            try {
                request<ReaderResult<ModeChange>>(scenario) { r,c -> r.setReadingMode(ReadingMode.PAGED,c) }
                for(effect in selectedEffects(listOf("slide","cover","ibook_slide"))) {
                    request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.firstPage(c) }
                    scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).setPageTurnEffect(PageTurnEffects.fromId(effect)!!) }
                    repeat(6) { turn ->
                        val forward=turn<3
                        SystemClock.sleep(750)
                        val rect=Rect()
                        var before:ReadingPosition?=null
                        scenario.onActivity {
                            val reader=it.findViewById<ReaderView>(R.id.reader)
                            before=reader.currentPosition
                            findBody(reader)!!.getGlobalVisibleRect(rect)
                        }
                        val down=SystemClock.uptimeMillis()
                        pointer(down,MotionEvent.ACTION_DOWN,rect,if(forward) .85f else .15f)
                        pointer(down,MotionEvent.ACTION_MOVE,rect,.5f)
                        await(scenario) { it.navigationState==ReadNavigationState.Dragging }
                        SystemClock.sleep(250)
                        val bitmap=Bitmap.createBitmap(240,320,Bitmap.Config.ARGB_8888)
                        val result=LinkedBlockingQueue<Int>()
                        scenario.onActivity { PixelCopy.request(it.window,rect,bitmap,{ status -> result.add(status) },Handler(Looper.getMainLooper())) }
                        assertEquals(PixelCopy.SUCCESS,result.poll(5,TimeUnit.SECONDS))
                        fun colorCount(red:Boolean,x0:Int,x1:Int):Int {
                            var count=0
                            for(y in 80 until 240)for(x in x0 until x1) {
                                val color=bitmap.getPixel(x,y)
                                if(if(red) Color.red(color)>150 && Color.blue(color)<100 else Color.blue(color)>150 && Color.red(color)<100)count++
                            }
                            return count
                        }
                        val red=requireNotNull(before).pageNumberHint!!%2==1
                        val currentPixels=if(forward)colorCount(red,15,120)else colorCount(red,120,225)
                        val targetPixels=if(forward)colorCount(!red,190,225)else colorCount(!red,15,50)
                        android.util.Log.i("DistinctPages","$effect turn=$turn position=$before current=$currentPixels target=$targetPixels")
                        if(currentPixels<=12000 || targetPixels<=3500) scenario.onActivity { activity ->
                            File(activity.filesDir,"distinct-page-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                        }
                        assertTrue("$effect turn $turn current page disappeared ($currentPixels)",currentPixels>12000)
                        assertTrue("$effect turn $turn target page disappeared",targetPixels>3500)
                        bitmap.recycle()
                        pointer(down,MotionEvent.ACTION_MOVE,rect,if(forward) .15f else .85f)
                        pointer(down,MotionEvent.ACTION_UP,rect,if(forward) .15f else .85f)
                        await(scenario) { it.navigationState==ReadNavigationState.Idle && it.currentPosition!=before }
                    }
                }
            } finally { scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() };source?.close() }
        }
    }
    @Test fun heldFingerTracksEveryEffectAndEpubHandoffNeverFlashesWhite() {
        val reports=mutableListOf<JSONObject>()
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase==ReaderPhase.Ready }
            resetTurnConfiguration(scenario)
            for(format in listOf("epub","text","arm")) {
                val epub=format!="text"
                val id="handoff-$format-${System.nanoTime()}"
                var owned:AutoCloseable?=null
                assertTrue(request<OpenResult>(scenario) { r,c ->
                    val source=if(epub) EpubReaderBookSource(id,
                        if(format=="arm") ReaderInput.fromAsset(r.context,"epubs/the-art-of-arm-assembly.epub") else ReaderInput { ByteArrayInputStream(publication()) },
                        File(r.context.cacheDir,"handoff")).also { owned=it }
                    else MemoryBookSource(BookDescriptor(id,"Handoff","v1",listOf(TocEntry.Chapter(ChapterDescriptor("chapter","Chapter","v1")))),
                        listOf(ChapterContent(id,"chapter","v1",(0 until 80).map { TextBlock("p$it",text) })))
                    r.openBook(source,callback=c)
                } is OpenResult.Opened)
                try {
                    request<ReaderResult<ModeChange>>(scenario) { r,c -> r.setReadingMode(ReadingMode.PAGED,c) }
                    request<ReaderResult<StyleApplication>>(scenario) { r,c ->
                        val style=ReaderStyle.default()
                        r.applyStyle(style.copy(page=style.page.copy(backgroundColor=0xff315442.toInt()),body=style.body.copy(textColor=0xffd9e5db.toInt())),c)
                    }
                    if(format=="arm") {
                        request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.goToChapter("spine-9",callback=c) }
                        request<NavigationResult<ReadingPosition>>(scenario) { r,c ->
                            val at=requireNotNull(r.currentPosition)
                            r.goToPosition(at.copy(documentLocator=JSONObject(at.documentLocator!!).put("selectionPage",80).toString()),c)
                        }
                    }
                    repeat(2) { request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.nextPage(NavigationOptions(animated=false,respectInterval=false),c) } }
                    for(effect in selectedEffects(listOf("slide","cover","ibook_slide","google_curl"))) {
                        val probe=Probe()
                        var frameProbe:Frames?=null
                        val rect=Rect()
                        scenario.onActivity { activity ->
                            val reader=activity.findViewById<ReaderView>(R.id.reader)
                            reader.setPageTurnEffect(watch(PageTurnEffects.fromId(effect)!!,probe))
                            reader.setPageTurnOptions(PageTurnOptions(durationMs=240))
                            val body=findBody(reader)!!
                            body.getGlobalVisibleRect(rect)
                            rect.inset(12,12)
                            frameProbe=Frames(activity.window,rect)
                        }
                        // Read-ahead and command recording run while the user is reading.
                        SystemClock.sleep(700)
                        var before:ReadingPosition?=null
                        scenario.onActivity { before=it.findViewById<ReaderView>(R.id.reader).currentPosition }
                        val down=SystemClock.uptimeMillis()
                        pointer(down,MotionEvent.ACTION_DOWN,rect,.85f)
                        probe.moveNs=System.nanoTime()
                        pointer(down,MotionEvent.ACTION_MOVE,rect,.65f)
                        await(scenario) { probe.firstDrawNs>0 }
                        assertEquals("$epub $effect must animate before release",TurnPhase.Dragging,probe.phase)
                        val initial=probe.progress
                        assertTrue("initial progress=$initial",initial in .1f.. .3f)
                        SystemClock.sleep(80)
                        assertEquals("Held finger must not run a timed animation",initial,probe.progress,.01f)
                        scenario.onActivity { frameProbe!!.start() }
                        pointer(down,MotionEvent.ACTION_MOVE,rect,.4f)
                        await(scenario) { probe.progress>initial+.1f }
                        pointer(down,MotionEvent.ACTION_MOVE,rect,.6f)
                        await(scenario) { probe.progress<.35f }
                        scenario.onActivity { assertEquals(before,it.findViewById<ReaderView>(R.id.reader).currentPosition) }
                        pointer(down,MotionEvent.ACTION_MOVE,rect,.2f)
                        SystemClock.sleep(40)
                        pointer(down,MotionEvent.ACTION_UP,rect,.2f)
                        await(scenario) { it.navigationState==ReadNavigationState.Idle && it.currentPosition!=before }
                        SystemClock.sleep(160)
                        scenario.onActivity { frameProbe!!.stop() }
                        val frames=frameProbe!!
                        val response=(probe.firstDrawNs-probe.moveNs)/1e6
                        assertTrue("$epub $effect first drag frame: $response ms",response<150)
                        assertTrue("No screen frames captured",frames.count>8)
                        assertTrue("$format $effect white flash: ${frames.whiteFraction}",frames.whiteFraction<if(format=="arm") .90 else .10)
                        if(format!="arm") assertTrue("$format $effect blank colored frame: ${frames.minInkFraction}",frames.minInkFraction>.001)
                        val row=JSONObject().put("format",format).put("effect",effect)
                            .put("firstDragFrameMs",response).put("screenFrames",frames.count).put("maxWhiteFraction",frames.whiteFraction)
                            .put("minInkFraction",frames.minInkFraction)
                        reports.add(row);android.util.Log.i("ReaderGestureHandoff",row.toString())
                    }
                } finally { scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }; owned?.close() }
            }
            scenario.onActivity { File(it.filesDir,"gesture-handoff.json").writeText(reports.joinToString(",\n","[\n","\n]")) }
        }
    }
    private fun resetTurnConfiguration(scenario:ActivityScenario<ReaderActivity>) {
        scenario.onActivity {
            val reader=it.findViewById<ReaderView>(R.id.reader)
            // Other integration tests deliberately persist a one-byte bitmap budget
            // or vertical/RTL input. Gesture rendering fixtures own their configuration.
            reader.setPageTurnOptions(PageTurnOptions())
            reader.setInputOptions(ReaderInputOptions())
            reader.layoutDirection=View.LAYOUT_DIRECTION_LTR
        }
    }
    private fun selectedEffects(defaults: List<String>): List<String> {
        val requested = InstrumentationRegistry.getArguments().getString("effect") ?: return defaults
        require(requested in defaults) { "Unsupported effect filter: $requested" }
        return listOf(requested)
    }
    private fun watch(factory:PageTurnEffectFactory,probe:Probe)=object:PageTurnEffectFactory {
        override val id="handoff-${factory.id}"
        override fun create():PageTurnEffect {
            val delegate=factory.create()
            return object:SnapshotPageTurnEffect {
                override val usesPageSnapshots get()=(delegate as? SnapshotPageTurnEffect)?.usesPageSnapshots==true
                override fun attach(host:PageTurnEffectHost)=delegate.attach(host)
                override fun supports(direction:TurnDirection,viewport:org.peyilo.ireadview.spi.presentation.ReadViewport)=delegate.supports(direction,viewport)
                override fun start(context:PageTurnContext)=delegate.start(context)
                override fun update(frame:PageTurnFrame)=delegate.update(frame)
                override fun draw(canvas:Canvas,frame:PageTurnFrame) {
                    delegate.draw(canvas,frame)
                    if(probe.firstDrawNs==0L)probe.firstDrawNs=System.nanoTime()
                    probe.progress=frame.progress;probe.phase=frame.phase
                }
                override fun finish(outcome:TurnOutcome)=delegate.finish(outcome)
                override fun abort(reason:TurnAbortReason)=delegate.abort(reason)
                override fun detach()=delegate.detach()
            }
        }
    }
    private class Frames(val window:Window,val rect:Rect):Choreographer.FrameCallback {
        private val handler=Handler(Looper.getMainLooper())
        private val bitmap=Bitmap.createBitmap(96,160,Bitmap.Config.ARGB_8888)
        private val pixels=IntArray(96*160)
        private var running=false;private var pending=false
        var count=0;var whiteFraction=0.0;var minInkFraction=1.0
        fun start() { running=true;Choreographer.getInstance().postFrameCallback(this) }
        fun stop() { running=false;Choreographer.getInstance().removeFrameCallback(this) }
        override fun doFrame(frameTimeNanos:Long) {
            if(!running)return
            if(!pending) {
                pending=true
                PixelCopy.request(window,rect,bitmap,{ status ->
                    pending=false
                    if(status==PixelCopy.SUCCESS) {
                        bitmap.getPixels(pixels,0,96,0,0,96,160)
                        val white=pixels.count { Color.red(it)>240 && Color.green(it)>240 && Color.blue(it)>240 }
                        whiteFraction=maxOf(whiteFraction,white.toDouble()/pixels.size);count++
                        val ink=pixels.count { Color.red(it)>150 && Color.green(it)>150 && Color.blue(it)>150 && !(Color.red(it)>240 && Color.green(it)>240 && Color.blue(it)>240) }
                        minInkFraction=minOf(minInkFraction,ink.toDouble()/pixels.size)
                    }
                },handler)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private fun findBody(view:View):ReaderBodyView? {
        if(view is ReaderBodyView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)findBody(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun pointer(down:Long,action:Int,rect:Rect,x:Float) {
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,rect.left+rect.width()*x,rect.exactCenterY(),0)
        try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event) } finally { event.recycle() }
    }
    private fun await(scenario:ActivityScenario<ReaderActivity>,predicate:(ReaderView)->Boolean) {
        val deadline=SystemClock.uptimeMillis()+15000
        do { var ready=false;scenario.onActivity { ready=predicate(it.findViewById(R.id.reader)) };if(ready)return;SystemClock.sleep(10) } while(SystemClock.uptimeMillis()<deadline)
        error("Gesture/handoff timed out")
    }
    private fun <T> request(scenario:ActivityScenario<ReaderActivity>,action:(ReaderView,ReaderCallback<T>)->Unit):T {
        val q=LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity { action(it.findViewById(R.id.reader),ReaderCallback { q.add(it) }) }
        val value=q.poll(40,TimeUnit.SECONDS);assertTrue("$value",value is ReaderCompletion.Completed)
        return (value as ReaderCompletion.Completed).value
    }
    private fun publication(panels:Boolean=false):ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(path:String,value:String) {
                val bytes=value.toByteArray();val item=ZipEntry(path)
                if(path=="mimetype") { item.method=ZipEntry.STORED;item.size=bytes.size.toLong();item.compressedSize=item.size;item.crc=CRC32().apply { update(bytes) }.value }
                zip.putNextEntry(item);zip.write(bytes);zip.closeEntry()
            }
            entry("mimetype","application/epub+zip")
            entry("META-INF/container.xml","""<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("book.opf","""<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Handoff</dc:title></metadata><manifest><item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""")
            // Leave headroom for legacy WebView viewport/column rounding;
            // exactly 100vh can spill a blank fragment into the next column.
            val content=if(panels) (0 until 12).joinToString("") { "<div style='height:90vh;break-inside:avoid;-webkit-column-break-after:always;break-after:column;background:${if(it%2==0) "#c72525" else "#255bc7"}'>Page $it</div>" }
                else (0 until 80).joinToString("") { "<p>$text</p>" }
            entry("chapter.xhtml","""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Handoff</title></head><body>$content</body></html>""")
        }
        return out.toByteArray()
    }
}
