package org.peyilo.iread.demo

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.event.ReadViewListener
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.input.ReaderInputOptions
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.widget.BaseReadView
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

/** Uses actual factories: optional input/dynamics capabilities must remain visible. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class IosCurlPerformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private var compositorView = "none"
    private val paragraphs =
        (0 until 200).map {
            "Paragraph $it. " +
                "The same source text compares reader rendering without changing animation timing. "
                    .repeat(8)
        }
    private val metrics =
        linkedMapOf(
                "frame" to FrameMetrics.TOTAL_DURATION,
                "unknown" to FrameMetrics.UNKNOWN_DELAY_DURATION,
                "input" to FrameMetrics.INPUT_HANDLING_DURATION,
                "draw" to FrameMetrics.DRAW_DURATION,
                "layout" to FrameMetrics.LAYOUT_MEASURE_DURATION,
                "animation" to FrameMetrics.ANIMATION_DURATION,
                "sync" to FrameMetrics.SYNC_DURATION,
                "commands" to FrameMetrics.COMMAND_ISSUE_DURATION,
                "swap" to FrameMetrics.SWAP_BUFFERS_DURATION,
            )
            .apply { if (Build.VERSION.SDK_INT >= 31) put("gpu", FrameMetrics.GPU_DURATION) }

    private class Sample(val started: Long = System.nanoTime()) {
        val values = Collections.synchronizedList(mutableListOf<Map<String, Double>>())
        val phases =
            Collections.synchronizedList(mutableListOf(started to ReadNavigationState.Idle))
        var elapsed = 0.0
    }

    @Volatile private var sample: Sample? = null

    @Test
    fun realFactoriesAcrossTextAndEpub() {
        val formats = arguments.getString("formats", "text,epub,arm")!!.split(',')
        val effects = arguments.getString("effects", "ios_curl")!!.split(',')
        val turns = arguments.getString("turns", "8")!!.toInt()
        val gesturePairs = arguments.getString("gesturePairs", "4")!!.toInt()
        val cacheMode =
            when (arguments.getString("cacheMode", "three")) {
                "three" -> ReaderPageCacheMode.THREE_PAGE
                "single" -> ReaderPageCacheMode.SINGLE_PAGE
                else -> error("cacheMode must be three or single")
            }
        require(turns > 0 && gesturePairs > 0)
        val rows = JSONArray()
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario ->
            await(scenario) { it.readerState.phase == ReaderPhase.Ready }
            val metricsThread = HandlerThread("curl-frame-metrics").apply { start() }
            val listener = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
                sample?.let { active ->
                    if (frame.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) >= active.started) {
                        val stages = metrics.mapValues { (_, id) -> frame.getMetric(id) / 1e6 }
                        val accounted =
                            stages.filterKeys { it != "frame" && it != "gpu" }.values.sum()
                        active.values.add(
                            stages +
                                mapOf(
                                    "unattributed" to (stages.getValue("frame") - accounted),
                                    "vsyncNs" to
                                        frame
                                            .getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
                                            .toDouble(),
                                )
                        )
                    }
                }
            }
            val fallbacks = mutableListOf<String>()
            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(
                    listener,
                    Handler(metricsThread.looper),
                )
                activity
                    .findViewById<ReaderView>(R.id.reader)
                    .addReadViewListener(
                        object : ReadViewListener<ReadingPosition> {
                            override fun onNavigationStateChanged(
                                view: BaseReadView<ReadingPosition>,
                                state: ReadNavigationState,
                            ) {
                                sample?.phases?.add(System.nanoTime() to state)
                            }
                        }
                    )
                activity
                    .findViewById<ReaderView>(R.id.reader)
                    .addReaderListener(
                        object : ReaderListener {
                            override fun onEffectFallback(
                                view: BookReadView,
                                requestedId: String,
                                appliedId: String,
                                reason: EffectFallbackReason,
                            ) {
                                fallbacks += "$requestedId:$reason"
                            }
                        }
                    )
            }
            try {
                for (format in formats) {
                    var sourceOwner: AutoCloseable? = null
                    val id = "curl-perf-$format-${System.nanoTime()}"
                    assertTrue(
                        request<OpenResult>(scenario) { reader, callback ->
                            val source =
                                when (format) {
                                    "text" ->
                                        MemoryBookSource(
                                            BookDescriptor(
                                                id,
                                                "Performance",
                                                "v1",
                                                listOf(
                                                    TocEntry.Chapter(
                                                        ChapterDescriptor(
                                                            "chapter",
                                                            "Chapter",
                                                            "v1",
                                                        )
                                                    )
                                                ),
                                            ),
                                            listOf(
                                                ChapterContent(
                                                    id,
                                                    "chapter",
                                                    "v1",
                                                    paragraphs.mapIndexed { index, text ->
                                                        TextBlock("p$index", text)
                                                    },
                                                )
                                            ),
                                        )
                                    "epub",
                                    "arm" ->
                                        EpubReaderBookSource(
                                                id,
                                                if (format == "arm")
                                                    ReaderInput.fromAsset(
                                                        reader.context,
                                                        "epubs/the-art-of-arm-assembly.epub",
                                                    )
                                                else
                                                    ReaderInput {
                                                        ByteArrayInputStream(publication())
                                                    },
                                                File(reader.context.cacheDir, "curl-performance"),
                                            )
                                            .also { sourceOwner = it }
                                    else -> error("Unknown format $format")
                                }
                            reader.openBook(source, callback = callback)
                        }
                            is OpenResult.Opened
                    )
                    try {
                        request<ReaderResult<ModeChange>>(scenario) { reader, callback ->
                            reader.setReadingMode(ReadingMode.PAGED, callback)
                        }
                        request<ReaderResult<StyleApplication>>(scenario) { reader, callback ->
                            reader.applyStyle(ReaderStyle.default(), callback)
                        }
                        if (format == "arm")
                            request<NavigationResult<ReadingPosition>>(scenario) { reader, callback
                                ->
                                reader.goToChapter("spine-9", callback = callback)
                            }
                        await(scenario) { it.navigationState == ReadNavigationState.Idle }
                        repeat(3) {
                            val warmed =
                                request<NavigationResult<ReadingPosition>>(scenario) {
                                    reader,
                                    callback ->
                                    reader.nextPage(
                                        NavigationOptions(
                                            animated = false,
                                            respectInterval = false,
                                        ),
                                        callback,
                                    )
                                }
                            assertTrue(
                                "Warm-up navigation must move: $warmed",
                                warmed is NavigationResult.Moved,
                            )
                            await(scenario) { it.navigationState == ReadNavigationState.Idle }
                        }
                        scenario.onActivity {
                            Log.i(
                                "IosCurlPerf",
                                "initial $format ${it.findViewById<ReaderView>(R.id.reader).currentPosition}",
                            )
                        }
                        for (effect in effects) {
                            scenario.onActivity { activity ->
                                activity.findViewById<ReaderView>(R.id.reader).apply {
                                    assertTrue(setPageCacheMode(cacheMode) is ReaderResult.Success)
                                    setInputOptions(ReaderInputOptions())
                                    setPageTurnEffect(
                                        if (
                                            effect == "ios_curl" &&
                                                arguments.getString("output") == "surface"
                                        )
                                            PageTurnEffects.iosCurl(
                                                IosCurlOptions(output = IosCurlOutput.Surface)
                                            )
                                        else requireNotNull(PageTurnEffects.fromId(effect))
                                    )
                                    setPageTurnOptions(
                                        PageTurnOptions(
                                            durationMs = 240,
                                            bitmapBudgetBytes = 64L * 1024 * 1024,
                                        )
                                    )
                                    compositorView = findCompositor(this) ?: "none"
                                }
                            }
                            SystemClock.sleep(500)
                            val samples = mutableListOf<Sample>()
                            repeat(turns) { index ->
                                val active = Sample()
                                sample = active
                                val result =
                                    request<NavigationResult<ReadingPosition>>(scenario) {
                                        reader,
                                        callback ->
                                        if (index < turns / 2)
                                            reader.nextPage(
                                                NavigationOptions(respectInterval = false),
                                                callback,
                                            )
                                        else
                                            reader.previousPage(
                                                NavigationOptions(respectInterval = false),
                                                callback,
                                            )
                                    }
                                active.elapsed = (System.nanoTime() - active.started) / 1e6
                                assertTrue(
                                    "$format $effect $result",
                                    result is NavigationResult.Moved,
                                )
                                SystemClock.sleep(120)
                                sample = null
                                samples += active
                            }
                            report(rows, format, effect, "automatic", samples)
                            val gestures = mutableListOf<Sample>()
                            profileMethods("$format-$effect") {
                                repeat(gesturePairs) {
                                    for (commit in listOf(true, false)) {
                                        val active = Sample()
                                        sample = active
                                        swipe(scenario, commit)
                                        await(scenario) {
                                            it.navigationState == ReadNavigationState.Idle
                                        }
                                        active.elapsed = (System.nanoTime() - active.started) / 1e6
                                        SystemClock.sleep(120)
                                        sample = null
                                        gestures += active
                                    }
                                }
                            }
                            report(rows, format, effect, "gesture", gestures)
                            assertTrue("Unexpected fallback: $fallbacks", fallbacks.isEmpty())
                        }
                    } finally {
                        scenario.onActivity { it.findViewById<ReaderView>(R.id.reader).closeBook() }
                        sourceOwner?.close()
                    }
                }
                scenario.onActivity {
                    File(it.filesDir, "ioscurl-performance.json")
                        .writeText(
                            JSONObject()
                                .put("api", Build.VERSION.SDK_INT)
                                .put("output", arguments.getString("output", "texture"))
                                .put("compositorView", compositorView)
                                .put("cacheMode", cacheMode.name)
                                .put(
                                    "methodTracing",
                                    arguments.getString("profileMethods") == "true",
                                )
                                .put(
                                    "metricsScope",
                                    if (compositorView == "surface")
                                        "parent-window; excludes direct Surface GPU work"
                                    else
                                        "parent-window including TextureView composition; producer GPU work requires separate measurement",
                                )
                                .put("rows", rows)
                                .toString(2)
                        )
                }
            } finally {
                sample = null
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                metricsThread.quitSafely()
            }
        }
    }

    private fun profileMethods(label: String, block: () -> Unit) {
        if (arguments.getString("profileMethods") != "true") return block()
        val output = File(instrumentation.targetContext.filesDir, "ioscurl-method-$label.trace")
        android.os.Debug.startMethodTracingSampling(output.absolutePath, 64 * 1024 * 1024, 1000)
        try {
            block()
        } finally {
            android.os.Debug.stopMethodTracing()
        }
    }

    private fun findCompositor(view: View): String? {
        if (view is SurfaceView) return "surface"
        if (view is TextureView) return "texture"
        if (view is ViewGroup)
            for (i in 0 until view.childCount) {
                findCompositor(view.getChildAt(i))?.let {
                    return it
                }
            }
        return null
    }

    private fun report(
        rows: JSONArray,
        format: String,
        effect: String,
        kind: String,
        samples: List<Sample>,
    ) {
        val frames = samples.flatMap { active ->
            val phases = synchronized(active.phases) { active.phases.toList() }
            synchronized(active.values) { active.values.toList() }
                .map { values ->
                    val phase =
                        phases.lastOrNull { it.first <= (values["vsyncNs"] ?: 0.0) }?.second
                            ?: ReadNavigationState.Idle
                    values + ("phase" to phase.ordinal.toDouble())
                }
        }
        assertTrue(
            "$format $kind must contain actual rendered frames",
            frames.size >= if (compositorView == "surface") 0 else 10,
        )
        val row =
            JSONObject()
                .put("format", format)
                .put("effect", effect)
                .put("kind", kind)
                .put("compositorView", compositorView)
                .put("turns", samples.size)
                .put("frames", frames.size)
                .put("totalMedianMs", percentile(samples.map { it.elapsed }, .5))
                .put("coldTotalMs", samples.first().elapsed)
        for (metric in metrics.keys + "unattributed") for ((label, quantile) in
            listOf("Median" to .5, "P95" to .95, "P99" to .99)) {
            row.put("${metric}${label}Ms", percentile(frames.map { it.getValue(metric) }, quantile))
        }
        row.put("framesOver16Ms", frames.count { it.getValue("frame") > 16.667 })
            .put("framesOver32Ms", frames.count { it.getValue("frame") > 32.0 })
        assertTrue(
            "$format $kind must contain visual frames",
            arguments.getString("output") == "surface" ||
                frames.any {
                    it["phase"] == ReadNavigationState.Dragging.ordinal.toDouble() ||
                        it["phase"] == ReadNavigationState.Settling.ordinal.toDouble()
                },
        )
        val byPhase = JSONObject()
        for (phase in ReadNavigationState.entries) {
            val selected = frames.filter { it["phase"] == phase.ordinal.toDouble() }
            val values = JSONObject().put("frames", selected.size)
            for (metric in metrics.keys + "unattributed") for ((label, q) in
                listOf("Median" to .5, "P95" to .95)) values.put(
                "${metric}${label}Ms",
                percentile(selected.map { it.getValue(metric) }, q),
            )
            byPhase.put(phase.name, values)
        }
        row.put("byNavigationState", byPhase)
        if (arguments.getString("rawFrames") == "true")
            row.put("frameSamples", JSONArray(frames.map { JSONObject(it) }))
        rows.put(row)
        Log.i("IosCurlPerf", row.toString())
        if (arguments.getString("captureProfiles") == "true") {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(instrumentation.targetContext.filesDir, "curl-profile-$format-$kind.png")
                    .outputStream()
                    .use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                screenshot.recycle()
            }
        }
    }

    private fun percentile(values: List<Double>, quantile: Double) =
        if (values.isEmpty()) 0.0 else values.sorted()[((values.size - 1) * quantile).toInt()]

    private fun await(scenario: ActivityScenario<ReaderActivity>, ready: (ReaderView) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (true) {
            var done = false
            scenario.onActivity { done = ready(it.findViewById(R.id.reader)) }
            if (done) return
            check(SystemClock.uptimeMillis() < deadline) { "Reader did not become ready" }
            SystemClock.sleep(25)
        }
    }

    private fun <T> request(
        scenario: ActivityScenario<ReaderActivity>,
        action: (ReaderView, ReaderCallback<T>) -> Unit,
    ): T {
        val result = LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity {
            action(it.findViewById(R.id.reader), ReaderCallback { value -> result.add(value) })
        }
        val completion = result.poll(40, TimeUnit.SECONDS)
        return (completion as? ReaderCompletion.Completed<T>)?.value
            ?: error("Request: $completion")
    }

    private fun swipe(scenario: ActivityScenario<ReaderActivity>, commit: Boolean) {
        val rect = Rect()
        var before: ReadingPosition? = null
        scenario.onActivity {
            val reader = it.findViewById<ReaderView>(R.id.reader)
            reader.getGlobalVisibleRect(rect)
            before = reader.currentPosition
        }
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float) {
            val value =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    rect.left + rect.width() * x,
                    rect.top + rect.height() * .65f,
                    0,
                )
            value.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(value, true))
            } finally {
                value.recycle()
            }
        }
        event(MotionEvent.ACTION_DOWN, .9f)
        for (index in 1..12) {
            SystemClock.sleep(25)
            event(MotionEvent.ACTION_MOVE, .9f - .7f * index / 12)
        }
        if (!commit)
            for (index in 1..8) {
                SystemClock.sleep(25)
                event(MotionEvent.ACTION_MOVE, .2f + .75f * index / 8)
            }
        await(scenario) { it.navigationState == ReadNavigationState.Dragging }
        event(MotionEvent.ACTION_UP, if (commit) .2f else .95f)
        await(scenario) { it.navigationState == ReadNavigationState.Idle }
        scenario.onActivity {
            val after = it.findViewById<ReaderView>(R.id.reader).currentPosition
            if (commit) assertNotEquals("Injected swipe must commit", before, after)
            else assertEquals("Reverse swipe must cancel", before, after)
        }
    }

    private fun publication(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                val bytes = text.toByteArray()
                val entry = ZipEntry(name)
                if (name == "mimetype") {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = entry.size
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            entry(
                "book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Performance</dc:title></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""",
            )
            entry(
                "chapter.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Performance</title></head><body>${paragraphs.joinToString("") { "<p>$it</p>" }}</body></html>""",
            )
        }
        return out.toByteArray()
    }
}
