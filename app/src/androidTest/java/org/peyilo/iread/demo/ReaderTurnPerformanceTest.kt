package org.peyilo.iread.demo

import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Handler
import android.os.HandlerThread
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.spi.presentation.ReadViewport
import org.peyilo.ireadview.widget.ReaderView
import java.io.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.*

/** Same content/settings, real built-in effects. Reports preparation, capture, draw, and window frame costs. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class ReaderTurnPerformanceTest {
    private val paragraphs = (0 until 300).map { "Paragraph $it. " + "A stable reading performance comparison with ordinary text and EPUB layout. ".repeat(8) }
    private class Sample {
        var requested = 0L; var began = 0L; var ended = 0L; var setup = 0.0; var total = 0.0
        val draws = mutableListOf<Double>(); val surfaces = mutableListOf<Double>()
        val updates = mutableListOf<Double>()
        val frames = java.util.Collections.synchronizedList(mutableListOf<Double>())
        val gpu = java.util.Collections.synchronizedList(mutableListOf<Double>())
    }
    @Volatile private var sample: Sample? = null
    @Test fun compareEveryBuiltInEffect() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 30000
            while (true) {
                var ready = false
                scenario.onActivity { ready = it.findViewById<ReaderView>(R.id.reader).readerState.phase == ReaderPhase.Ready }
                if (ready) break
                check(SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(50)
            }
            val thread = HandlerThread("turn-frame-metrics").apply { start() }
            val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                val at = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
                sample?.let { s -> if (s.began > 0 && at >= s.began && (s.ended == 0L || at <= s.ended)) {
                    s.frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6)
                    if(android.os.Build.VERSION.SDK_INT>=31) s.gpu.add(metrics.getMetric(FrameMetrics.GPU_DURATION)/1e6)
                } }
            }
            scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
            val rows = mutableListOf<JSONObject>()
            try {
                for (format in listOf("text", "epub", "arm")) {
                    var owned: AutoCloseable? = null
                    val id = "perf-$format-${System.nanoTime()}"
                    assertTrue(request<OpenResult>(scenario) { r, c ->
                        val source = if (format == "text") MemoryBookSource(
                            BookDescriptor(id, "Performance", "v1", listOf(TocEntry.Chapter(ChapterDescriptor("chapter", "Chapter", "v1")))),
                            listOf(ChapterContent(id, "chapter", "v1", paragraphs.mapIndexed { i, text -> TextBlock("p$i", text) })))
                        else EpubReaderBookSource(id, if (format == "arm") ReaderInput.fromAsset(r.context, "epubs/the-art-of-arm-assembly.epub")
                            else ReaderInput { ByteArrayInputStream(publication()) }, File(r.context.cacheDir, "turn-performance")).also { owned = it }
                        r.openBook(source, callback = c)
                    } is OpenResult.Opened)
                    try {
                        request<ReaderResult<ModeChange>>(scenario) { r, c -> r.setReadingMode(ReadingMode.PAGED, c) }
                        request<ReaderResult<StyleApplication>>(scenario) { r, c -> r.applyStyle(ReaderStyle.default(), c) }
                        if (format == "arm") request<NavigationResult<ReadingPosition>>(scenario) { r, c -> r.goToChapter("spine-9", callback = c) }
                        // Compare a later page, where chapter-wide DOM scans are most visible.
                        request<NavigationResult<ReadingPosition>>(scenario) { r, c ->
                            if (format == "text") r.goToChapter("chapter", 40, c)
                            else {
                                val position = requireNotNull(r.currentPosition)
                                r.goToPosition(position.copy(documentLocator=JSONObject(position.documentLocator!!).put("selectionPage",if(format=="arm")80 else 40).toString()), c)
                            }
                        }
                        // Warm both documents and native layouts before comparing steady-state effects.
                        repeat(3) { request<NavigationResult<ReadingPosition>>(scenario) { r, c -> r.nextPage(NavigationOptions(animated=false, respectInterval=false), c) } }
                        for (effectId in listOf("none", "slide", "cover", "ibook_slide", "curl", "google_curl", "page_curl")) {
                            scenario.onActivity { activity ->
                                val r = activity.findViewById<ReaderView>(R.id.reader)
                                val factory = PageTurnEffects.fromId(effectId)!!
                                r.setPageTurnEffect(if (effectId == "none") factory else measured(factory))
                                r.setPageTurnOptions(PageTurnOptions(durationMs=240))
                            }
                            val samples = mutableListOf<Sample>()
                            repeat(8) { turn ->
                                val s = Sample(); sample = s
                                val result = request<NavigationResult<ReadingPosition>>(scenario) { r, c ->
                                    s.requested = System.nanoTime()
                                    if (turn < 4) r.nextPage(NavigationOptions(respectInterval=false), c)
                                    else r.previousPage(NavigationOptions(respectInterval=false), c)
                                }
                                s.total = (System.nanoTime() - s.requested) / 1e6
                                assertTrue("$format $effectId: $result", result is NavigationResult.Moved)
                                SystemClock.sleep(150) // Idle reading time; also lets asynchronous frame metrics arrive.
                                samples += s
                                sample = null
                            }
                            val warm = samples.drop(1)
                            val frames = warm.flatMap { synchronized(it.frames) { it.frames.toList() } }
                            val row = JSONObject().put("format",format).put("effect",effectId)
                                .put("prepareMedianMs", percentile(warm.map { if(it.began>0) (it.began-it.requested)/1e6 else it.total }, .5))
                                .put("setupMedianMs",percentile(warm.map { it.setup },.5))
                                .put("totalMedianMs",percentile(warm.map { it.total },.5))
                                .put("drawP95Ms",percentile(warm.flatMap { it.draws },.95))
                                .put("updateP95Ms",percentile(warm.flatMap { it.updates },.95))
                                .put("surfaceP95Ms",percentile(warm.flatMap { it.surfaces },.95))
                                .put("frameP95Ms",percentile(frames,.95)).put("frames",frames.size)
                                .put("gpuP95Ms",percentile(warm.flatMap { synchronized(it.gpu) { it.gpu.toList() } },.95))
                                .put("framesOver32Ms",frames.count { it > 32 })
                            rows += row; Log.i("ReaderTurnPerf",row.toString())
                            // Real input also exercises drag handoff while the live hierarchy
                            // is suspended, and restores the baseline page after each probe.
                            val before = position(scenario)
                            sample = Sample().apply { requested=System.nanoTime() }
                            swipe(scenario, commit=true)
                            awaitIdle(scenario)
                            assertEquals("$format $effectId drag",before.pageNumberHint!!+1,position(scenario).pageNumberHint)
                            sample = null
                            request<NavigationResult<ReadingPosition>>(scenario) { r,c -> r.previousPage(NavigationOptions(animated=false,respectInterval=false),c) }
                            sample = Sample().apply { requested=System.nanoTime() }
                            swipe(scenario, commit=false)
                            awaitIdle(scenario)
                            assertEquals("$format $effectId cancelled drag",before.pageNumberHint,position(scenario).pageNumberHint)
                            sample = null
                            SystemClock.sleep(150)
                        }
                    } finally { scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }; owned?.close() }
                }
                scenario.onActivity { File(it.filesDir,"turn-performance.json").writeText(rows.joinToString(",\n","[\n","\n]")) }
            } finally {
                sample = null
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                thread.quitSafely()
            }
        }
    }
    private fun measured(factory: PageTurnEffectFactory) = object : PageTurnEffectFactory {
        override val id = "measured-${factory.id}"
        override fun create(): PageTurnEffect {
            val delegate = factory.create()
            return object : SnapshotPageTurnEffect {
                override val usesPageSnapshots get() = (delegate as? SnapshotPageTurnEffect)?.usesPageSnapshots == true
                override fun attach(host: PageTurnEffectHost) = delegate.attach(host)
                override fun supports(direction: TurnDirection, viewport: ReadViewport) = delegate.supports(direction,viewport)
                override fun start(context: PageTurnContext) {
                    val s = sample!!; s.began = System.nanoTime()
                    fun wrap(surface: PageSurface): PageSurface = object : PageBackgroundSurface, SnapshotPreferredSurface, RecordedPageSurface {
                        override val widthPx get() = surface.widthPx
                        override val heightPx get() = surface.heightPx
                        override val preferSnapshot get() = (surface as? SnapshotPreferredSurface)?.preferSnapshot == true
                        override val isRecorded get() = (surface as? RecordedPageSurface)?.isRecorded == true
                        override val backgroundColor get() = (surface as? PageBackgroundSurface)?.backgroundColor
                        override fun draw(canvas: Canvas) { val t=System.nanoTime(); surface.draw(canvas); s.surfaces += (System.nanoTime()-t)/1e6 }
                    }
                    delegate.start(context.copy(current=wrap(context.current),target=wrap(context.target)))
                    s.setup = (System.nanoTime()-s.began)/1e6
                }
                override fun update(frame: PageTurnFrame) { val t=System.nanoTime(); delegate.update(frame); sample?.updates?.add((System.nanoTime()-t)/1e6) }
                override fun draw(canvas: Canvas, frame: PageTurnFrame) {
                    val t=System.nanoTime(); delegate.draw(canvas,frame); sample?.draws?.add((System.nanoTime()-t)/1e6)
                }
                override fun finish(outcome: TurnOutcome) { sample?.ended=System.nanoTime(); delegate.finish(outcome) }
                override fun abort(reason: TurnAbortReason) = delegate.abort(reason)
                override fun detach() = delegate.detach()
            }
        }
    }
    private fun percentile(values: List<Double>, fraction: Double): Double = if(values.isEmpty()) 0.0 else values.sorted()[((values.size-1)*fraction).toInt()]
    private fun position(scenario:ActivityScenario<ReaderActivity>):ReadingPosition {
        var value:ReadingPosition?=null
        scenario.onActivity { value=it.findViewById<ReaderView>(R.id.reader).currentPosition }
        return requireNotNull(value)
    }
    private fun awaitIdle(scenario:ActivityScenario<ReaderActivity>) {
        val deadline=SystemClock.uptimeMillis()+10000
        do {
            var idle=false
            scenario.onActivity { idle=it.findViewById<ReaderView>(R.id.reader).navigationState==ReadNavigationState.Idle }
            if(idle)return
            SystemClock.sleep(30)
        } while(SystemClock.uptimeMillis()<deadline)
        error("Gesture did not settle")
    }
    private fun swipe(scenario:ActivityScenario<ReaderActivity>,commit:Boolean) {
        val rect=Rect()
        scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).getGlobalVisibleRect(rect) }
        val down=SystemClock.uptimeMillis()
        fun pointer(action:Int,fraction:Float) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,rect.left+rect.width()*fraction,rect.exactCenterY(),0)
            try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event) } finally { event.recycle() }
        }
        pointer(MotionEvent.ACTION_DOWN,.85f)
        if(commit) {
            for(i in 1..10) { SystemClock.sleep(20);pointer(MotionEvent.ACTION_MOVE,.85f-.7f*i/10) }
            pointer(MotionEvent.ACTION_UP,.15f)
        } else {
            pointer(MotionEvent.ACTION_MOVE,.72f);SystemClock.sleep(60)
            pointer(MotionEvent.ACTION_MOVE,.84f);SystemClock.sleep(20)
            pointer(MotionEvent.ACTION_UP,.85f)
        }
    }
    private fun <T> request(scenario: ActivityScenario<ReaderActivity>, action: (ReaderView, ReaderCallback<T>) -> Unit): T {
        val q=LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity { action(it.findViewById(R.id.reader), ReaderCallback { q.add(it) }) }
        val value=q.poll(40,TimeUnit.SECONDS)
        assertTrue("Request: $value",value is ReaderCompletion.Completed)
        return (value as ReaderCompletion.Completed).value
    }
    private fun publication(): ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(path:String,text:String) {
                val bytes=text.toByteArray(); val item=ZipEntry(path)
                if(path=="mimetype") { item.method=ZipEntry.STORED;item.size=bytes.size.toLong();item.compressedSize=item.size;item.crc=CRC32().apply { update(bytes) }.value }
                zip.putNextEntry(item);zip.write(bytes);zip.closeEntry()
            }
            entry("mimetype","application/epub+zip")
            entry("META-INF/container.xml","""<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("book.opf","""<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Performance</dc:title></metadata><manifest><item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""")
            entry("chapter.xhtml","""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Performance</title></head><body>${paragraphs.joinToString("") { "<p>$it</p>" }}</body></html>""")
        }
        return out.toByteArray()
    }
}
