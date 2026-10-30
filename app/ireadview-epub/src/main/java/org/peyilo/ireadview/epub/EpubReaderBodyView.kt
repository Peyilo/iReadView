package org.peyilo.ireadview.epub

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PointF
import java.text.BreakIterator
import java.util.Locale
import org.peyilo.ireadview.spi.selection.ReaderSelectionHandle
import android.net.Uri
import android.view.*
import android.webkit.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import org.peyilo.ireadview.api.model.ReadingMode
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.style.*
import org.peyilo.ireadview.source.EpubPublication
import org.peyilo.ireadview.spi.body.ReaderBodyView
import org.peyilo.ireadview.spi.content.TextLineSegment

/** Project-owned EPUB body. WebView is only the HTML/CSS layout surface. Main-thread lifecycle. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor", "ClickableViewAccessibility")
class EpubReaderBodyView internal constructor(context: Context, cache: EpubResourceCache) : ReaderBodyView(context) {
    constructor(context: Context) : this(context, EpubResourceCache())
    private val localResources = EpubWebResources(cache)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var released = false
    private var binding = 0L
    private var loaded: CompletableDeferred<Unit>? = null
    private var initialized = false
    private var decorationKey = "[]"
    private var decorationStatuses: Map<String, AnnotationStatus> = emptyMap()
    private var bounds: RectF? = null
    private var selected: JSONObject? = null
    private var appearance = SelectionStyle()
    private val selectionPath = Path()
    private var selectionScrollX = 0f
    private var selectionScrollY = 0f
    private var unclippedSelectionBounds: RectF? = null
    private var handles: List<ReaderSelectionHandle> = emptyList()
    private var visibleLinks: List<Pair<RectF, String>> = emptyList()
    private var visibleMarks: List<Pair<RectF, String>> = emptyList()
    private var clampHandles = true
    var onGeometryChanged: (() -> Unit)? = null
    private var underlineStyles: Map<String, TextLineStyle> = emptyMap()
    private var underlineLines: List<Pair<List<TextLineSegment>, TextLineStyle>> = emptyList()
    var onScrollLocation: ((JSONObject) -> Unit)? = null
    val hasSelection: Boolean get() = selected != null
    internal val isBound: Boolean get() = initialized && !released

    private val web: WebView = object : WebView(context) {
        override fun startActionMode(callback: ActionMode.Callback): ActionMode? = null
        override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = null
        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            if (initialized && !clampHandles) {
                alignSelectionToScroll()
                removeCallbacks(scrollReport)
                postDelayed(scrollReport, 100)
            }
        }
    }
    private val visualState = EpubVisualState(web)
    private val renderReadiness = EpubRenderReadiness(scope, visualState::awaitReady)
    private val scrollReport = Runnable {
        if (!released && initialized) scope.launch {
            val epoch = binding
            if (onScrollLocation != null) runCatching { evaluate("window.__iread.state()") }.getOrNull()?.let {
                if (epoch == binding && !released) onScrollLocation?.invoke(it)
            }
            if (epoch == binding && !released) refreshGeometry()
            if (underlineStyles.isNotEmpty()) runCatching { evaluate("({geometry:window.__iread.decorationGeometry()})") }
                .getOrNull()?.optJSONArray("geometry")?.let { if (epoch == binding && !released) acceptGeometry(it) }
        }
    }
    init {
        addView(web, LayoutParams(-1, -1))
        web.isLongClickable = false
        web.setOnLongClickListener { true }
        web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        web.isHorizontalScrollBarEnabled = false
        web.isVerticalScrollBarEnabled = false
        web.overScrollMode = OVER_SCROLL_NEVER
        web.settings.apply {
            javaScriptEnabled = true // Host-owned evaluator only; no addJavascriptInterface, publication scripts blocked.
            allowFileAccess = false; allowContentAccess = false
            domStorageEnabled = false; databaseEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false); setSupportZoom(false)
            builtInZoomControls = false; displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            textZoom = 100
            // The reader owns the viewport, including fixed-layout scaling. Together with
            // the locked minimum scale below this prevents wide-content viewport expansion.
            useWideViewPort = false
            loadWithOverviewMode = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = localResources.respond(request.url)
            override fun onPageFinished(view: WebView, url: String) { android.util.Log.i("EpubStage", "finished $url"); if (localResources.owns(url)) loaded?.complete(Unit) }
            @Deprecated("API21 error callback")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                if (localResources.owns(failingUrl)) loaded?.completeExceptionally(IOException(description))
            }
        }
    }

    /** Bind only after mounting in a measured page template. Returned locator includes a DOM text anchor. */
    suspend fun bind(publication: EpubPublication, path: String, style: ReaderStyle, mode: ReadingMode, locator: JSONObject): JSONObject =
        prepareBinding(publication, path, style, mode, locator, null).also { awaitReady() }

    internal suspend fun prepareBinding(publication: EpubPublication, path: String, style: ReaderStyle, mode: ReadingMode, locator: JSONObject, initialPage: Int?): JSONObject {
        check(!released)
        clearContentBinding()
        localResources.bind(publication)
        val epoch = binding
        appearance = style.selectionStyle
        clampHandles = mode == ReadingMode.PAGED
        // Cache the paged viewport; scrolling keeps the direct compositor path.
        web.setLayerType(if (clampHandles) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, null)
        val layers = listOf(style.page.viewportPadding, style.body.contentPadding, style.body.containerPadding)
        val density = resources.displayMetrics.density
        setPaddingRelative((layers.sumOf { it.startDp.toDouble() } * density).toInt(),
            (layers.sumOf { it.topDp.toDouble() } * density).toInt(),
            (layers.sumOf { it.endDp.toDouble() } * density).toInt(),
            (layers.sumOf { it.bottomDp.toDouble() } * density).toInt())
        awaitViewport()
        require(web.width > 0 && web.height > 0) { "Body insets leave no EPUB viewport" }
        val ready = CompletableDeferred<Unit>()
        loaded = ready
        var stamp=System.nanoTime()
        fun stage(name:String) { val now=System.nanoTime(); android.util.Log.i("EpubStage", "body $name ${(now-stamp)/1e6}ms"); stamp=now }
        web.loadUrl(localResources.url(path))
        withTimeout(30000) { ready.await() }
        stage("load")
        // onPageFinished covers images/styles; fonts may finish their first layout later.
        var remaining = 20
        while (remaining-- > 0) {
            if (evaluate("({ready: !document.fonts || document.fonts.status === 'loaded'})")?.optBoolean("ready") == true) break
            delay(50)
        }
        currentCoroutineContext().ensureActive()
        stage("fonts")
        check(binding == epoch && !released)
        val script = context.assets.open("iread/epub-body.js").bufferedReader().use { it.readText() }
        val config = JSONObject().put("paged", mode == ReadingMode.PAGED).put("fixed", publication.fixedLayout).put("href", path)
            .put("width", web.width / density).put("height", web.height / density)
            .put("css", css(style)).put("locator", locator).put("initialPage", initialPage)
        val state = evaluate("$script($config)") ?: throw IOException("EPUB layout returned no location")
        stage("layout")
        initialized = true
        refreshGeometry()
        stage("geometry")
        renderReadiness.changed()
        return state
    }
    internal suspend fun preparePage(page: Int): JSONObject {
        val result = evaluate("(function(){var v={}, times={},t=Date.now();function part(n,f){v[n]=f();times[n]=Date.now()-t;t=Date.now();}part('location',function(){return window.__iread.goToPage($page);});part('selected',window.__iread.selection);part('links',window.__iread.links);part('marks',window.__iread.marks);part('geometry',window.__iread.decorationGeometry);v.times=times;return v;})()")
            ?: run { initialized = false; throw IOException("EPUB page navigation failed") }
        acceptViewportGeometry(result)
        android.util.Log.i("EpubStage", "page JS=${result.optJSONObject("times")}")
        renderReadiness.changed()
        return result.getJSONObject("location")
    }
    internal suspend fun awaitReady() = renderReadiness.awaitReady()
    internal suspend fun awaitPresented() = visualState.awaitPresented()
    internal fun linkAt(x: Float, y: Float): String? = visibleLinks.firstOrNull { it.first.contains(x, y) }?.second
    internal fun highlightAt(x: Float, y: Float): String? = visibleMarks.asReversed().firstOrNull { it.first.contains(x, y) }?.second
    internal fun selectionHandleAt(x: Float, y: Float): Boolean? = selectionPainter.handleAt(handles, x, y, appearance, resources.displayMetrics.density)
    internal fun selectionDragOffset(start: Boolean, x: Float, y: Float): PointF? = handles.firstOrNull { it.end != start }
        ?.let { selectionPainter.dragOffset(it, x, y) }
    internal suspend fun selectParagraph(x: Float, y: Float): JSONObject? {
        val density = resources.displayMetrics.density
        val result = evaluate("window.__iread.paragraphAt(${(x - web.left) / density},${(y - web.top) / density})")
        return result
    }
    internal suspend fun showSelection(value: DocumentTextSelection?) {
        if (value == null) { if (hasSelection) clearSelection(); return }
        val range = JSONObject(value.documentLocator).getJSONObject("range")
        acceptSelection(evaluate("window.__iread.select($range)"))
    }
    internal suspend fun extendSelection(value: DocumentTextSelection, start: Boolean, x: Float, y: Float): JSONObject? {
        val density = resources.displayMetrics.density
        val caret = evaluate("window.__iread.caretAt(${(x - web.left) / density},${(y - web.top) / density})") ?: return null
        val text = caret.getString("text")
        val rawOffset = caret.getInt("relativeOffset").coerceIn(0, text.length)
        val boundaries = BreakIterator.getCharacterInstance(Locale.getDefault()).apply { setText(text) }
        val offset = if (boundaries.isBoundary(rawOffset)) rawOffset else
            (if (start) boundaries.preceding(rawOffset) else boundaries.following(rawOffset)).takeIf { it != BreakIterator.DONE } ?: return null
        val point = caret.getJSONObject("point").apply { put("offset", getInt("offset") + offset - rawOffset) }
        val range = JSONObject(value.documentLocator).getJSONObject("range")
        return evaluate("window.__iread.extendSelection($range,$start,$point)")
    }
    internal fun acceptExtendedSelection(value: JSONObject) {
        web.evaluateJavascript("window.__iread.select($value)", null)
        acceptSelection(value)
    }
    internal suspend fun pageForEndpoint(value: DocumentTextSelection, start: Boolean): Int? {
        val point = JSONObject(value.documentLocator).getJSONObject("range").getJSONObject(if (start) "start" else "end")
        return evaluate("({page:window.__iread.pageForPoint($point)})")?.optInt("page")?.takeIf { it > 0 }
    }
    internal fun scrollBodyBy(dy: Int) { web.scrollBy(0, dy) }
    internal suspend fun scrollSelection(value: DocumentTextSelection, start: Boolean, requested: Int, y: Float): Boolean {
        val range = JSONObject(value.documentLocator).getJSONObject("range")
        val density = resources.displayMetrics.density
        return evaluate("window.__iread.scrollSelection($range,$start,${requested / density},${(y - web.top) / density})")?.optBoolean("moved") == true
    }
    internal fun flingBody(velocity: Int) { web.flingScroll(0, velocity) }
    private suspend fun refreshGeometry() {
        if (!initialized || released) return
        val result = evaluate("({selected:window.__iread.selection(),links:window.__iread.links(),marks:window.__iread.marks(),geometry:window.__iread.decorationGeometry()})") ?: return
        acceptViewportGeometry(result)
    }
    private fun acceptViewportGeometry(result: JSONObject) {
        acceptGeometry(result.optJSONArray("geometry") ?: JSONArray())
        acceptSelection(result.optJSONObject("selected"))
        val links = result.optJSONArray("links") ?: JSONArray()
        val density = resources.displayMetrics.density
        visibleLinks = (0 until links.length()).map { index -> val item = links.getJSONObject(index)
            RectF(item.getDouble("left").toFloat() * density + web.left, item.getDouble("top").toFloat() * density + web.top,
                item.getDouble("right").toFloat() * density + web.left, item.getDouble("bottom").toFloat() * density + web.top) to item.getString("href")
        }
        val marks = result.optJSONArray("marks") ?: JSONArray()
        visibleMarks = (0 until marks.length()).map { index -> val item = marks.getJSONObject(index)
            RectF(item.getDouble("left").toFloat() * density + web.left, item.getDouble("top").toFloat() * density + web.top,
                item.getDouble("right").toFloat() * density + web.left, item.getDouble("bottom").toFloat() * density + web.top) to item.getString("id")
        }
    }
    private fun acceptSelection(value: JSONObject?) {
        val previous = bounds
        selected = value
        val density = resources.displayMetrics.density
        val rects = value?.optJSONArray("rects") ?: JSONArray()
        selectionPath.reset()
        for (index in 0 until rects.length()) {
            val rect = rects.getJSONObject(index)
            selectionPath.addRect(rect.getDouble("left").toFloat() * density, rect.getDouble("top").toFloat() * density,
                rect.getDouble("right").toFloat() * density, rect.getDouble("bottom").toFloat() * density, Path.Direction.CW)
        }
        bounds = value?.optJSONObject("bounds")?.let { RectF(it.getDouble("left").toFloat() * density + web.left,
            it.getDouble("top").toFloat() * density + web.top, it.getDouble("right").toFloat() * density + web.left,
            it.getDouble("bottom").toFloat() * density + web.top) }
        fun handle(key: String, end: Boolean): ReaderSelectionHandle? {
            val caret = value?.optJSONObject(key) ?: return null
            if (!clampHandles && caret.optBoolean("proxy")) return null
            return selectionPainter.createHandle(caret.getDouble("x").toFloat() * density + web.left,
                caret.getDouble("top").toFloat() * density + web.top, caret.getDouble("bottom").toFloat() * density + web.top,
                end, width, height, appearance, density, clampHandles)
        }
        handles = listOfNotNull(handle("startCaret", false), handle("endCaret", true))
        if (!clampHandles) {
            unclippedSelectionBounds = if (selectionPath.isEmpty) null else RectF().also {
                selectionPath.computeBounds(it, true)
                it.offset(web.left.toFloat(), web.top.toFloat())
            }
            // JS geometry and its scroll origin are one snapshot. Native scrolling may
            // already have advanced by the time the callback reaches the UI thread.
            selectionScrollX = (value?.optDouble("scrollX") ?: 0.0).toFloat() * density
            selectionScrollY = (value?.optDouble("scrollY") ?: 0.0).toFloat() * density
            alignSelectionToScroll(notify = false)
        }
        invalidate()
        if (previous != bounds) onGeometryChanged?.invoke()
    }
    private fun alignSelectionToScroll(notify: Boolean = true) {
        if (selected == null || clampHandles) return
        val dx = selectionScrollX - web.scrollX
        val dy = selectionScrollY - web.scrollY
        selectionScrollX = web.scrollX.toFloat()
        selectionScrollY = web.scrollY.toFloat()
        selectionPath.offset(dx, dy)
        unclippedSelectionBounds?.offset(dx, dy)
        handles = handles.map { it.copy(x=it.x+dx, y=it.y+dy, caretX=it.caretX+dx,
            caretTop=it.caretTop+dy, caretBottom=it.caretBottom+dy) }
        val previous = bounds
        bounds = unclippedSelectionBounds?.let { RectF(it).takeIf { area ->
            area.intersect(web.left.toFloat(), web.top.toFloat(), web.right.toFloat(), web.bottom.toFloat())
        } }
        invalidate()
        if (notify && previous != bounds) onGeometryChanged?.invoke()
    }
    internal suspend fun applyAnnotations(items: List<DocumentRangedAnnotation>, style: ReaderStyle): Map<String, AnnotationStatus> {
        val values = JSONArray()
        val previousUnderlineStyles = underlineStyles
        underlineStyles = items.filterIsInstance<DocumentUnderline>().associate { it.id to it.style }
        items.forEach { item ->
            val range = runCatching { JSONObject(item.selection.documentLocator).getJSONObject("range") }.getOrNull()
            if (range != null) values.put(JSONObject().put("id", item.id).put("quote", item.quote).put("range", range)
                .put("color", if (item is DocumentHighlight) cssColor(style.highlightStyles[item.styleId].fillColor) else JSONObject.NULL)
                .put("underline", item is DocumentUnderline))
        }
        val key = values.toString()
        // Navigation and Idle notifications often carry exactly the same annotations.
        // Rewrapping DOM text on every turn forces an unnecessary whole-chapter reflow.
        if (key == decorationKey && previousUnderlineStyles == underlineStyles) return decorationStatuses
        val result = evaluate("window.__iread.applyDecorations($values)") ?: throw IOException("EPUB annotation rendering failed")
        acceptGeometry(result.optJSONArray("geometry") ?: JSONArray())
        refreshGeometry()
        renderReadiness.changed()
        val statuses = result.optJSONArray("statuses") ?: JSONArray()
        return items.associate { it.id to (AnnotationStatus.Invalid(AnnotationInvalidReason.InvalidRange) as AnnotationStatus) }.toMutableMap().apply {
            for (index in 0 until statuses.length()) {
                val item = statuses.getJSONObject(index)
                put(item.getString("id"), if (item.optBoolean("valid")) AnnotationStatus.Valid else AnnotationStatus.Invalid(AnnotationInvalidReason.QuoteMismatch))
            }
        }.also { decorationKey = key; decorationStatuses = it }
    }
    private fun acceptGeometry(items: JSONArray) {
        val density = resources.displayMetrics.density
        underlineLines = (0 until items.length()).mapNotNull { index ->
            val item = items.getJSONObject(index)
            val style = underlineStyles[item.optString("id")] ?: return@mapNotNull null
            val lines = item.getJSONArray("lines")
            (0 until lines.length()).map { i -> val line = lines.getJSONObject(i)
                TextLineSegment(line.getDouble("left").toFloat() * density, line.getDouble("right").toFloat() * density,
                    line.getDouble("bottom").toFloat() * density)
            } to style
        }
        invalidate()
    }
    // FrameLayout's software drawing cache can be empty for a parked hardware layer.
    // Draw the document itself when recording an immutable software page instead.
    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child !== web || canvas.isHardwareAccelerated || web.layerType != View.LAYER_TYPE_HARDWARE)
            return super.drawChild(canvas, child, drawingTime)
        web.computeScroll()
        val saved = canvas.save()
        try {
            canvas.clipRect(web.left, web.top, web.right, web.bottom)
            canvas.translate((web.left - web.scrollX).toFloat(), (web.top - web.scrollY).toFloat())
            web.draw(canvas)
        } finally { canvas.restoreToCount(saved) }
        return true
    }
    override fun dispatchDraw(canvas: Canvas) {
        drawBodyContent(canvas) { target -> super.dispatchDraw(target) }
        val save = canvas.save()
        canvas.clipRect(web.left, web.top, web.right, web.bottom)
        canvas.translate(web.left.toFloat(), web.top.toFloat())
        selectionPainter.drawFill(canvas, selectionPath, appearance)
        underlineLines.forEach { (segments, style) -> drawUnderlines(canvas, segments, style, resources.displayMetrics.density) }
        canvas.restoreToCount(save)
        val handleSave = canvas.save()
        if (!clampHandles) canvas.clipRect(web.left, web.top, web.right, web.bottom)
        selectionPainter.drawHandles(canvas, handles, appearance, resources.displayMetrics.density, width)
        canvas.restoreToCount(handleSave)
    }
    fun clearSelection() {
        selected = null; bounds = null; unclippedSelectionBounds = null; handles = emptyList(); selectionPath.reset(); invalidate()
        if (!released && initialized) web.evaluateJavascript("window.__iread.clearSelection()", null)
    }
    override fun selectionBounds(): RectF? = bounds?.let(::RectF)
    override fun selectionHandleBounds(start: Boolean): RectF? = handles.firstOrNull { it.end != start }?.let {
        selectionPainter.handleBounds(it, appearance, resources.displayMetrics.density)
    }
    override fun clearContentBinding() {
        renderReadiness.clear()
        binding++; initialized = false
        decorationKey = "[]"; decorationStatuses = emptyMap()
        onScrollLocation = null; onGeometryChanged = null
        underlineStyles = emptyMap(); underlineLines = emptyList(); invalidate()
        loaded?.cancel(); loaded = null
        selected = null; bounds = null; unclippedSelectionBounds = null; handles = emptyList(); visibleLinks = emptyList(); visibleMarks = emptyList(); selectionPath.reset()
        web.removeCallbacks(scrollReport)
        localResources.clear()
        if (!released) { web.stopLoading(); web.loadUrl("about:blank") }
    }
    override fun onRelease() {
        released = true
        scope.cancel(); web.setOnTouchListener(null); removeView(web); web.destroy()
    }

    private suspend fun awaitViewport() = suspendCancellableCoroutine<Unit> { continuation ->
        if (web.isLaidOut && !isLayoutRequested && !web.isLayoutRequested) { continuation.resume(Unit); return@suspendCancellableCoroutine }
        val listener = object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                web.removeOnLayoutChangeListener(this)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        web.addOnLayoutChangeListener(listener)
        continuation.invokeOnCancellation { web.post { web.removeOnLayoutChangeListener(listener) } }
    }
    private suspend fun evaluate(script: String): JSONObject? = suspendCancellableCoroutine { continuation ->
        val epoch = binding
        web.evaluateJavascript(script) { raw ->
            if (!continuation.isActive) return@evaluateJavascript
            if (released || epoch != binding) { continuation.cancel(); return@evaluateJavascript }
            val value = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
            continuation.resume(if (value is JSONObject) value else if (value is String) runCatching { JSONObject(value) }.getOrNull() else null)
        }
    }
    private fun css(style: ReaderStyle): String {
        val body = style.body; val title = style.title
        val fontScale = resources.configuration.fontScale
        fun color(value: Int) = "#%06x".format(value and 0xffffff)
        fun insets(value: EdgeInsets) = "${value.topDp}px ${value.endDp}px ${value.bottomDp}px ${value.startDp}px"
        fun family(value: ReaderFont?): String = (value as? ReaderFont.System)?.let { "font-family:${JSONObject.quote(it.family)}!important;" }.orEmpty()
        return "html,body{background:transparent!important;color:${color(body.textColor)}!important;}" +
            "body{font-size:${body.textSizeSp * fontScale}px!important;line-height:${body.textSizeSp * fontScale * body.lineSpacingMultiplier + body.lineSpacingExtraDp}px!important;${family(body.readerFont)}}" +
            "p,li,blockquote{font-size:inherit!important;line-height:inherit!important;letter-spacing:${body.letterSpacingDp?.let { "${it}px" } ?: "${body.letterSpacingEm}em"}!important;}" +
            "p{margin-top:0!important;margin-bottom:${body.paragraphSpacingDp}px!important;text-indent:${body.firstLineIndentEm}em!important;padding:${insets(body.blockPadding)}!important;}" +
            "h1,h2,h3,h4,h5,h6{font-size:${title.textSizeSp * fontScale}px!important;color:${color(title.textColor)}!important;" +
            "line-height:${title.textSizeSp * fontScale * title.lineSpacingMultiplier + title.lineSpacingExtraDp}px!important;" +
            "text-align:${title.alignment.name.lowercase()}!important;letter-spacing:${title.letterSpacingDp?.let { "${it}px" } ?: "${title.letterSpacingEm}em"}!important;" +
            "padding:${insets(title.padding)}!important;margin-bottom:${title.spacingAfterDp}px!important;${family(title.readerFont)}}"
    }
    private fun cssColor(value: Int): String = "rgba(${value ushr 16 and 255},${value ushr 8 and 255},${value and 255},${(value ushr 24) / 255.0})"
}
