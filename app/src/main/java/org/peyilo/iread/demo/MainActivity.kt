package org.peyilo.iread.demo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import org.peyilo.ireadview.api.event.PositionChangeReason
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.event.ReaderTapListener
import org.peyilo.ireadview.api.event.ReaderAction
import org.peyilo.ireadview.api.event.ReaderActionHandler
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.input.PagingAxis
import org.peyilo.ireadview.api.style.HighlightStyle
import org.peyilo.ireadview.api.style.HighlightStyles
import org.peyilo.ireadview.api.style.ChromeContent
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.source.TextBookSource
import org.peyilo.ireadview.source.TextBookOptions
import org.peyilo.ireadview.source.EpubTextBookSource
import org.peyilo.ireadview.source.EpubTocEntry
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.spi.page.ReaderPageFactories
import org.peyilo.ireadview.spi.page.ReaderPlaceholderFactories
import java.nio.charset.Charset
import java.security.MessageDigest
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView
import java.util.UUID
import java.io.File

/** A real host: all interactions use the library's public Widget/model/source APIs. */
class MainActivity : Activity() {
    private lateinit var reader: ReaderView
    private lateinit var status: TextView
    private lateinit var stylePanel: DemoReaderStylePanel
    private lateinit var controlPanel: DemoReaderControlPanel
    private lateinit var pageIndexPanel: DemoPageIndexPanel
    private lateinit var annotationPanel: DemoAnnotationPanel
    private lateinit var source: MemoryBookSource
    private data class FileInput(val uri: Uri, val charset: String = "UTF-8", val sourceType: String = "txt")
    private var fileInput: FileInput? = null
    private var fileOpenSequence = 0L
    private var fileSelectionSequence = 0L
    private var pendingFileInput: FileInput? = null
    private var requestedFileInput: FileInput? = null
    private lateinit var uriGrants: DemoUriGrantStore
    private var lastGrantReferences: Set<String>? = null
    private var customPageTemplate = false
    private var customPlaceholder = false
    private var customSelectionToolbar = false
    private var catalogueRevision = 1
    private lateinit var annotationStorage: DemoAnnotationStorage
    private lateinit var readingStorage: DemoReadingStorage
    private lateinit var readerPreferences: DemoReaderPreferences
    private var committedStyle: ReaderStyle? = null
    private var preferencesReady = false
    private var committedBookId: String? = null
    private var placeholderRecovery: Pair<BookSource, FileInput?>? = null
    private val ownedFileSources = LinkedHashSet<AutoCloseable>()
    private val hostScope = MainScope()
    private var epubNavigationJob: Job? = null
    private var epubNavigationSequence = 0L
    private var epubNavigationDialog: AlertDialog? = null
    private val saveProgress = Runnable { persistProgress() }
    private var annotationsRestored = false
    private val navigationCallback = ReaderCallback<NavigationResult<ReadingPosition>> { outcome ->
        val result = (outcome as? ReaderCompletion.Completed)?.value
        when (result) {
            is NavigationResult.Rejected -> status.text = when (result.reason) {
                ReaderRejection.AtBoundary -> "已到书籍边界"
                ReaderRejection.Busy -> "正在准备下一页…"
                else -> result.reason.name
            }
            is NavigationResult.Failed -> status.text = result.error.message
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        reader = findViewById(R.id.reader)
        status = findViewById(R.id.status)
        stylePanel = DemoReaderStylePanel(this, { reader.style }, ::applyDemoStyle)
        controlPanel = DemoReaderControlPanel(this, reader, hostScope, ::showContents, { stylePanel.show() }, ::showEffects) {
            navigationCallback.onComplete(ReaderCompletion.Completed(it))
        }
        findViewById<Button>(R.id.control_panel).setOnClickListener { controlPanel.toggle() }
        annotationPanel = DemoAnnotationPanel(this, reader, hostScope, { navigationCallback.onComplete(ReaderCompletion.Completed(it)) }, ::addDemoHighlight)
        pageIndexPanel = DemoPageIndexPanel(this, reader, hostScope, { navigationCallback.onComplete(ReaderCompletion.Completed(it)) }, { status.text = it })
        readerPreferences = DemoReaderPreferences(this)
        savedInstanceState?.getString("demoCustomEffect")?.takeIf { DemoReaderEffects.isCustom(it) }
            ?.let { DemoReaderEffects.fromId(it) }?.let { reader.setPageTurnEffect(it) }
        val coldSettings = if (savedInstanceState == null) readerPreferences.load(reader.style) else null
        customPageTemplate = savedInstanceState?.getBoolean("customPageTemplate") ?: coldSettings?.customPage ?: false
        customPlaceholder = savedInstanceState?.getBoolean("customPlaceholder") ?: coldSettings?.customPlaceholder ?: false
        if (customPageTemplate) reader.setPageFactory(DemoReaderPageFactory())
        if (customPlaceholder) reader.setPlaceholderFactory(DemoReaderPlaceholderFactory())
        customSelectionToolbar = savedInstanceState?.getBoolean("customSelectionToolbar") ?: coldSettings?.customSelectionToolbar ?: false
        if (customSelectionToolbar) reader.setSelectionToolbarFactory(DemoSelectionToolbarFactory { reader.style })
        source = sampleBook()
        readingStorage = DemoReadingStorage(this)
        uriGrants = DemoUriGrantStore.get(this)
        fileInput = savedInstanceState?.getString("textUri")?.let { value ->
            val charset = savedInstanceState.getString("textCharset") ?: "UTF-8"
            val type = savedInstanceState.getString("sourceType") ?: "txt"
            runCatching {
                require(type == "txt" || type == "epub")
                if (type == "txt") Charset.forName(charset)
                FileInput(Uri.parse(value), charset, type)
            }.getOrNull()
        }
        if (savedInstanceState == null) {
            readingStorage.recent()?.let { recent ->
                if (recent.uri != null) {
                    fileInput = runCatching {
                        val charset = recent.charset ?: "UTF-8"
                        if (recent.sourceType == "txt") Charset.forName(charset)
                        FileInput(Uri.parse(recent.uri), charset, recent.sourceType).takeIf { fileBookId(it) == recent.bookId }
                    }.getOrNull()
                }
            }
        }
        annotationStorage = DemoAnnotationStorage(this)
        findViewById<Button>(R.id.open_file).setOnClickListener {
            AlertDialog.Builder(this).setTitle("打开书籍与演示").setItems(arrayOf("TXT", "EPUB", "最近阅读", "独立容器效果演示", "网络章节来源演示", "自定义正文块演示", "TXT 来源配置演示", "Java 宿主接入演示", "Apple / Google 翻页与任意 View 页眉页脚", "卷页翻页")) { _, index ->
                when (index) {
                    2 -> showRecentBooks()
                    3 -> startActivity(Intent(this, DemoContainerActivity::class.java))
                    4 -> startActivity(Intent(this, DemoNetworkActivity::class.java))
                    5 -> startActivity(Intent(this, DemoCustomContentActivity::class.java))
                    6 -> startActivity(Intent(this, DemoTextSourceActivity::class.java))
                    7 -> startActivity(Intent(this, DemoJavaActivity::class.java))
                    8 -> startActivity(Intent(this, DemoCurlActivity::class.java))
                    9 -> startActivity(Intent(this, DemoCurlActivity::class.java).putExtra("pageCurl", true))
                    else -> chooseFile(if (index == 0) "txt" else "epub")
                }
            }.setNegativeButton("取消", null).show()
        }
        findViewById<Button>(R.id.sample_book).setOnClickListener { openSource(source, null) }
        reader.addReaderListener(object : ReaderListener {
            override fun onEffectFallback(view: BookReadView, requestedId: String, appliedId: String, reason: EffectFallbackReason) {
                Toast.makeText(this@MainActivity, "翻页效果已回退为无动画（$reason）", Toast.LENGTH_SHORT).show()
            }
            override fun onHighlightTapped(view: BookReadView, highlight: Highlight) {
                annotationPanel.showHighlight(highlight.id)
            }
            override fun onBookChanged(view: BookReadView, book: BookDescriptor?) {
                controlPanel.hide()
                annotationPanel.dismiss()
                pageIndexPanel.dismiss()
                cancelEpubNavigation()
                if (book == null) { annotationsRestored = false; return }
                if (!annotationsRestored) {
                    annotationsRestored = true
                    annotationStorage.load(book.bookId)?.let { view.replaceAnnotations(it) }
                }
            }
            override fun onAnnotationsChanged(view: BookReadView, snapshot: AnnotationSnapshot) {
                if (annotationsRestored) annotationStorage.save(snapshot)
            }
            override fun onPositionRestored(view: BookReadView, restoration: Restoration, reason: PositionChangeReason) {
                if (restoration is Restoration.Fallback) Toast.makeText(this@MainActivity, "内容已更新，阅读位置已回退（${restoration.reason}）", Toast.LENGTH_SHORT).show()
            }
            override fun onStateChanged(view: BookReadView, state: ReaderState) {
                controlPanel.refresh(state)
                val recovery = placeholderRecovery
                if (recovery != null && state.phase == ReaderPhase.Ready && state.position?.bookId == recovery.first.bookId) {
                    placeholderRecovery = null
                    fileInput = recovery.second
                    committedBookId = recovery.first.bookId
                    retireOtherFileSources(recovery.first)
                    persistProgress()
                }
                if (state.position?.bookId == committedBookId && committedBookId != null) {
                    reader.removeCallbacks(saveProgress)
                    reader.postDelayed(saveProgress, 600)
                }
                fun usable(value: NavigationAvailability) = value == NavigationAvailability.Available || value == NavigationAvailability.Unknown
                findViewById<Button>(R.id.previous).isEnabled = usable(state.navigationCapabilities.previous)
                findViewById<Button>(R.id.next).isEnabled = usable(state.navigationCapabilities.next)
                val position = state.position
                status.text = if (position != null) {
                    "${if (state.readingMode == ReadingMode.PAGED) "分页" else "滚动"} · ${view.currentChapter?.title} · ${position.pageNumberHint}/${state.chapterPageCount ?: "…"} · 全书 ${state.globalPageNumber ?: "…"}/${state.totalPageCount ?: "…"}"
                } else "iReadView · ${state.error?.message ?: state.phase}"
            }
        })
        findViewById<Button>(R.id.previous).setOnClickListener { reader.previousPage(NavigationOptions(), navigationCallback) }
        findViewById<Button>(R.id.next).setOnClickListener { reader.nextPage(NavigationOptions(), navigationCallback) }
        reader.setReaderActionHandler(ReaderActionHandler { _, action ->
            when (action) {
                ReaderAction.ShowTableOfContents -> if (reader.currentBook == null) false else { showContents(); true }
                ReaderAction.ShowSettings -> { showEffects(); true }
                ReaderAction.ExitReader -> { finish(); true }
            }
        })
        findViewById<Button>(R.id.contents).setOnClickListener { reader.requestReaderAction(ReaderAction.ShowTableOfContents) }
        findViewById<Button>(R.id.font).setOnClickListener {
            val size = if (reader.style.body.textSizeSp >= 26f) 16f else reader.style.body.textSizeSp + 2f
            applyDemoStyle(reader.style.copy(body = reader.style.body.copy(textSizeSp = size)))
        }
        findViewById<Button>(R.id.mode).setOnClickListener {
            val mode = if (reader.readingMode == ReadingMode.PAGED) ReadingMode.CONTINUOUS_SCROLL else ReadingMode.PAGED
            reader.setReadingMode(mode) { outcome ->
                when (val result = (outcome as? ReaderCompletion.Completed)?.value) {
                    is ReaderResult.Success -> persistReaderPreferences()
                    is ReaderResult.Failure -> status.text = result.error.message
                    is ReaderResult.Rejected -> status.text = result.reason.name
                    else -> Unit
                }
            }
        }
        findViewById<Button>(R.id.effect).setOnClickListener { reader.requestReaderAction(ReaderAction.ShowSettings) }
        reader.setReaderTapListener(ReaderTapListener { tap ->
            val coordinate = if (reader.inputOptions.pagingAxis == PagingAxis.Vertical) tap.y else tap.x
            val edge = reader.inputOptions.tapEdgeFraction
            if (coordinate >= edge && coordinate <= 1f - edge) { controlPanel.toggle(); true } else false
        })
        reader.post {
            hostScope.launch {
                if (isFinishing || isDestroyed) return@launch
                if (committedStyle == null) committedStyle = reader.style
                if (fileOpenSequence != 0L || fileSelectionSequence != 0L) { preferencesReady = true; return@launch }
                coldSettings?.let { settings ->
                    settings.input?.let { reader.setInputOptions(it) }
                    settings.turn?.let { reader.setPageTurnOptions(it) }
                    settings.effectId?.let { DemoReaderEffects.fromId(it) }?.let { reader.setPageTurnEffect(it) }
                    settings.mode?.let { reader.setReadingMode(it) }
                    settings.style?.let { if (reader.applyStyle(it) is ReaderResult.Success) committedStyle = it }
                }
                val palette = LinkedHashMap(reader.style.highlightStyles.styles)
                if (!palette.containsKey("blue")) palette["blue"] = HighlightStyle(0x6657b9ff)
                val merged = reader.style.copy(highlightStyles = HighlightStyles(palette))
                if (reader.applyStyle(merged) is ReaderResult.Success) committedStyle = merged
                preferencesReady = true
                if (fileOpenSequence != 0L || fileSelectionSequence != 0L || isFinishing || isDestroyed) return@launch
                val selected = fileInput
                openSource(selected?.let { fileSource(it) } ?: source, selected, restoreStored = savedInstanceState == null)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun chooseFile(type: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, if (type == "epub") arrayOf("application/epub+zip", "application/octet-stream", "application/zip") else arrayOf("text/*", "application/octet-stream"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try { startActivityForResult(intent, if (type == "epub") 41 else 40) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "系统没有可用的文件选择器", Toast.LENGTH_SHORT).show() }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in 40..41 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == 41) {
            val selection = FileInput(uri, sourceType = "epub")
            openSelectedFile(selection, data.flags)
            return
        }
        // A cancelled picker/encoding dialog leaves the current source and reading position intact.
        val encodings = arrayOf("UTF-8", "UTF-16", "UTF-16LE", "UTF-16BE", "GB18030")
        AlertDialog.Builder(this).setTitle("TXT 编码").setItems(encodings) { _, index ->
            val selection = FileInput(uri, encodings[index])
            openSelectedFile(selection, data.flags)
        }.setNegativeButton("取消", null).show()
    }

    private fun openSelectedFile(selection: FileInput, flags: Int) {
        val sequence = ++fileSelectionSequence
        pendingFileInput = selection
        // Enqueue acquisition immediately, before a later history mutation can enqueue cleanup.
        val retained = uriGrants.retain(selection.uri, flags)
        hostScope.launch {
            val persisted = retained.await()
            if (sequence != fileSelectionSequence || isFinishing || isDestroyed) return@launch
            if (!persisted) Toast.makeText(this@MainActivity, "无法保留文件授权，当前会话仍可尝试读取", Toast.LENGTH_LONG).show()
            openSource(fileSource(selection), selection)
        }
    }

    private fun cleanupUriGrants(force: Boolean = false) {
        val retained = readingStorage.history().mapNotNullTo(LinkedHashSet()) { it.uri }
        listOfNotNull(fileInput, requestedFileInput, pendingFileInput, placeholderRecovery?.second)
            .forEach { retained.add(it.uri.toString()) }
        if (!force && retained == lastGrantReferences) return
        lastGrantReferences = retained.toSet()
        uriGrants.cleanup(retained, readingStorage::flush)
    }

    private fun fileBookId(selection: FileInput): String {
        val uriIdentity = MessageDigest.getInstance("SHA-256").digest(selection.uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        // Encoding changes alter decoded anchors, so this host uses a separate identity per encoding.
        return if (selection.sourceType == "epub") "epub-$uriIdentity" else "txt-$uriIdentity-${selection.charset}"
    }
    private fun fileSource(selection: FileInput): BookSource {
        val created = if (selection.sourceType == "epub")
            EpubTextBookSource(fileBookId(selection), ReaderInput.fromUri(this, selection.uri), File(cacheDir, "reader-epub"))
        else TextBookSource(fileBookId(selection), ReaderInput.fromUri(this, selection.uri),
            TextBookOptions(title = "本地 TXT", charset = Charset.forName(selection.charset), preambleTitle = "前言",
                indexDirectory = File(cacheDir, "reader-text")))
        ownedFileSources.add(created as AutoCloseable)
        return created
    }

    private fun retireOtherFileSources(current: BookSource) {
        val retired = ownedFileSources.filter { it !== current }
        retired.forEach { it.close(); ownedFileSources.remove(it) }
    }

    private fun openSource(nextSource: BookSource, selected: FileInput?, restoreStored: Boolean = true) {
        controlPanel.hide()
        annotationPanel.dismiss()
        pageIndexPanel.dismiss()
        fileSelectionSequence++
        pendingFileInput = null
        requestedFileInput = selected
        placeholderRecovery = null
        cancelEpubNavigation()
        persistProgress()
        reader.removeCallbacks(saveProgress)
        val previousBookId = committedBookId
        committedBookId = null
        val sequence = ++fileOpenSequence
        val snapshot = if (restoreStored) readingStorage.load(nextSource.bookId) else null
        val callback = ReaderCallback<OpenResult> { outcome ->
            if (sequence != fileOpenSequence || isFinishing || isDestroyed) return@ReaderCallback
            when (val result = (outcome as? ReaderCompletion.Completed)?.value) {
                is OpenResult.Opened -> {
                    fileInput = selected
                    committedBookId = result.position.bookId
                    retireOtherFileSources(nextSource)
                    persistProgress()
                    if (result.restoration is Restoration.Fallback) Toast.makeText(this, "已按当前内容恢复到可用位置", Toast.LENGTH_LONG).show()
                }
                is OpenResult.Failed -> {
                    if (reader.currentPosition == null) placeholderRecovery = nextSource to selected
                    // Snapshot prevalidation may retain the previous book; keep saving that session.
                    if (reader.currentBook?.bookId == previousBookId) committedBookId = previousBookId
                    status.text = result.error.message
                    AlertDialog.Builder(this).setTitle("打开失败").setMessage(result.error.message)
                        .setPositiveButton("重试") { _, _ -> if (sequence == fileOpenSequence) openSource(nextSource, selected, restoreStored) }
                        .setNeutralButton("从头打开") { _, _ -> if (sequence == fileOpenSequence) openSource(nextSource, selected, restoreStored = false) }
                        .setNegativeButton("取消", null).show()
                }
                is OpenResult.Empty -> { fileInput = selected; retireOtherFileSources(nextSource); cleanupUriGrants(); status.text = "书籍没有章节" }
                else -> Unit
            }
        }
        if (snapshot != null) reader.restoreSnapshot(nextSource, snapshot, callback)
        else reader.openBook(nextSource, callback = callback)
    }

    private fun persistProgress() {
        if (!::reader.isInitialized || !::readingStorage.isInitialized) return
        val snapshot = reader.exportSnapshot() ?: return
        if (snapshot.bookId != committedBookId) return
        readingStorage.save(snapshot, fileInput?.uri?.toString(), fileInput?.takeIf { it.sourceType == "txt" }?.charset, fileInput?.sourceType ?: "txt", reader.currentBook?.title)
        cleanupUriGrants()
    }

    private fun showRecentBooks() {
        persistProgress()
        val entries = readingStorage.history()
        if (entries.isEmpty()) { Toast.makeText(this, "还没有阅读记录", Toast.LENGTH_SHORT).show(); return }
        fun label(entry: DemoReadingStorage.Recent): String {
            val location = readingStorage.load(entry.bookId)?.position?.let { " · ${it.chapterNumberHint ?: ""}章 ${it.pageNumberHint ?: ""}页" } ?: ""
            val type = if (entry.uri == null) "示例" else if (entry.sourceType == "epub") "EPUB" else "TXT ${entry.charset}"
            val name = entry.title?.takeIf { it.isNotBlank() } ?: "未命名书籍"
            val file = entry.uri?.let { Uri.parse(it).lastPathSegment }?.takeIf { it != name }
            return "$name · $type$location" + (file?.let { "\n$it" } ?: "")
        }
        AlertDialog.Builder(this).setTitle("最近阅读").setItems(entries.map(::label).toTypedArray()) { _, index ->
            val entry = entries[index]
            if (isFinishing || isDestroyed) return@setItems
            if (entry.uri == null) {
                if (entry.bookId == source.bookId) openSource(source, null)
                return@setItems
            }
            val selection = runCatching {
                val charset = entry.charset ?: "UTF-8"
                if (entry.sourceType == "txt") Charset.forName(charset)
                FileInput(Uri.parse(entry.uri), charset, entry.sourceType).also { require(fileBookId(it) == entry.bookId) }
            }.getOrNull()
            if (selection == null) Toast.makeText(this, "记录已失效，请重新选择文件", Toast.LENGTH_LONG).show()
            else openSource(fileSource(selection), selection)
        }.setNeutralButton("移除记录") { _, _ ->
            AlertDialog.Builder(this).setTitle("移除阅读记录").setItems(entries.map(::label).toTypedArray()) { _, index ->
                val selected = entries[index]
                if (selected.bookId == reader.currentBook?.bookId) Toast.makeText(this, "请先切换到其他书籍，再移除当前记录", Toast.LENGTH_LONG).show()
                else {
                    readingStorage.forget(selected.bookId)
                    cleanupUriGrants()
                    Toast.makeText(this, "已移除记录与阅读进度，文件和标注仍保留", Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton("取消", null).show()
        }.setNegativeButton("取消", null).show()
    }

    private fun showContents() {
        val current = currentEpubSource()
        if (current != null && current.navigation.isNotEmpty()) showEpubContents(current)
        else showSpineContents()
    }

    private fun currentEpubSource(): EpubTextBookSource? = ownedFileSources.filterIsInstance<EpubTextBookSource>()
        .firstOrNull { it.bookId == reader.currentBook?.bookId }

    private fun showEpubContents(current: EpubTextBookSource) {
        val book = reader.currentBook ?: return
        val entries = ArrayList<EpubTocEntry>()
        val labels = ArrayList<String>()
        fun visit(items: List<EpubTocEntry>, depth: Int) {
            items.forEach { item ->
                entries.add(item)
                labels.add("  ".repeat(depth) + item.title.ifBlank { "未命名目录项" } + if (item.resourcePath == null) "（无正文目标）" else "")
                visit(item.children, depth + 1)
            }
        }
        visit(current.navigation, 0)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun areAllItemsEnabled() = false
            override fun isEnabled(position: Int) = entries[position].resourcePath != null
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val enabled = isEnabled(position)
                return super.getView(position, convertView, parent).apply {
                    isEnabled = enabled
                }
            }
        }
        AlertDialog.Builder(this).setTitle("书籍原目录").setAdapter(adapter) { _, index ->
            if (reader.currentBook !== book || isFinishing || isDestroyed) return@setAdapter
            val entry = entries[index]
            if (entry.resourcePath != null) navigateEpubEntry(current, entry)
        }.setNeutralButton("正文顺序") { _, _ -> if (reader.currentBook === book) showSpineContents() }
            .setPositiveButton("提取说明") { _, _ -> if (reader.currentBook === book) showEpubDetails() }
            .setNegativeButton("取消", null).show()
    }

    private fun cancelEpubNavigation() {
        epubNavigationSequence++
        epubNavigationJob?.cancel()
        epubNavigationJob = null
        epubNavigationDialog?.dismiss()
        epubNavigationDialog = null
    }

    private fun navigateEpubEntry(current: EpubTextBookSource, entry: EpubTocEntry) {
        cancelEpubNavigation()
        val book = reader.currentBook ?: return
        val sequence = epubNavigationSequence
        val openSequence = fileOpenSequence
        fun active() = sequence == epubNavigationSequence && openSequence == fileOpenSequence &&
            reader.currentBook === book && !isFinishing && !isDestroyed
        val dialog = AlertDialog.Builder(this).setTitle("定位目录内容").setMessage("正在读取目标章节…")
            .setNegativeButton("取消") { _, _ -> cancelEpubNavigation() }.create()
        dialog.setOnCancelListener { cancelEpubNavigation() }
        epubNavigationDialog = dialog
        dialog.show()
        epubNavigationJob = hostScope.launch {
            try {
                val position = current.resolveNavigation(entry)
                if (!active()) return@launch
                if (position == null) Toast.makeText(this@MainActivity, "该目录目标不存在或未保留在文字内容中", Toast.LENGTH_LONG).show()
                else {
                    val result = reader.goToPosition(position)
                    if (active()) navigationCallback.onComplete(ReaderCompletion.Completed(result))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (active()) Toast.makeText(this@MainActivity, "无法读取目录目标：${failure.message ?: "读取失败"}", Toast.LENGTH_LONG).show()
            } finally {
                dialog.dismiss()
                if (sequence == epubNavigationSequence) {
                    epubNavigationDialog = null
                    epubNavigationJob = null
                }
            }
        }
    }

    private fun showSpineContents() {
        val book = reader.currentBook ?: return
        val chapters = book.chapters
        AlertDialog.Builder(this).setTitle("目录").setItems(chapters.map { it.title }.toTypedArray()) { _, index ->
            if (reader.currentBook === book) reader.goToChapter(chapters[index].chapterId, 1, navigationCallback)
        }.setNeutralButton(if (fileInput?.sourceType == "epub") "提取说明" else "更新演示") { _, _ ->
            if (reader.currentBook === book) {
                if (fileInput?.sourceType == "epub") showEpubDetails() else showUpdateOptions()
            }
        }
            .setPositiveButton("书签") { _, _ -> if (reader.currentBook === book) showBookmarks() }
            .setNegativeButton("全书页") { _, _ -> if (reader.currentBook === book) showPageIndex() }.show()
    }
    private fun showEpubDetails() {
        val book = reader.currentBook ?: return
        val current = currentEpubSource() ?: return
        val notes = current.diagnostics.map { diagnostic ->
            val description = when {
                diagnostic.code == "TextPresentationOnly" -> "按文字阅读呈现，未保留原书样式和链接行为"
                diagnostic.code == "ImagesAsAlternativeText" -> "图片仅显示已有的替代文字"
                diagnostic.code == "TableStructureFlattened" -> "表格转为连续文字"
                diagnostic.code == "PreformattedTextOnly" -> "保留预格式空白，字体使用阅读设置"
                diagnostic.code.startsWith("ContentOmitted:") -> "省略了当前文字阅读不支持的内容"
                diagnostic.code == "CatalogueUsesSpineOrder" -> "阅读目录按书籍正文顺序排列"
                diagnostic.code == "NavigationFallbackToNcx" -> "主目录不可用，已使用备用目录"
                diagnostic.code == "NavigationUnavailable" -> "书籍未提供可用目录名称"
                diagnostic.code == "NonLinearItemsIncluded" -> "补充内容保留在原有阅读顺序中"
                else -> "部分书籍信息或资源未能完整呈现"
            }
            "${diagnostic.resourcePath}\n$description"
        }.distinct()
        AlertDialog.Builder(this).setTitle("EPUB 提取说明")
            .setMessage("这里显示目录和已读取章节的说明，继续阅读后可能增加。\n\n${notes.joinToString("\n\n")}")
            .setPositiveButton("确定", null).setNeutralButton("重新读取目录") { _, _ -> if (reader.currentBook === book) showUpdateOptions() }.show()
    }
    private fun showEffects() {
        val factories = DemoReaderEffects.factories()
        AlertDialog.Builder(this).setTitle("翻页效果")
            .setSingleChoiceItems(DemoReaderEffects.labels, factories.indexOfFirst { it.id == reader.pageTurnEffectId }) { dialog, index ->
                val result = reader.setPageTurnEffect(factories[index])
                if (result is ReaderResult.Success) persistReaderPreferences()
                if (result is ReaderResult.Success && result.value is EffectSelection.DeferredUntilPaged) Toast.makeText(this, "已保存，回到分页模式后生效", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }.setNeutralButton(if (reader.pageTurnOptions.reduceMotion) "启用动画" else "减少动画") { _, _ ->
                reader.setPageTurnOptions(reader.pageTurnOptions.copy(reduceMotion = !reader.pageTurnOptions.reduceMotion))
                persistReaderPreferences()
            }.setPositiveButton("输入设置") { _, _ ->
                showInputOptions()
            }.setNegativeButton("取消", null).show()
    }

    private fun showInputOptions() {
        val input = reader.inputOptions
        fun toggleLabel(label: String, enabled: Boolean) = "$label：${if (enabled) "开启" else "关闭"}"
        val labels = arrayOf(toggleLabel("拖动翻页", input.gesturePagingEnabled), toggleLabel("边缘点击翻页", input.tapPagingEnabled),
            toggleLabel("键盘翻页", input.keyboardPagingEnabled), toggleLabel("音量键翻页", input.volumeKeyPagingEnabled),
            "分页方向：${if (input.pagingAxis == PagingAxis.Horizontal) "横向" else "纵向（无动画）"}",
            "翻页间隔：${reader.pageTurnOptions.minimumTurnIntervalMs} 毫秒",
            "页眉内容：${if (customPageTemplate) "工具栏章节名" else if (reader.style.header.content == ChromeContent.TIME) "时间" else "章节名"}",
            "页面模板：${if (customPageTemplate) "工具栏示例" else "默认"}",
            "加载提示：${if (customPlaceholder) "自定义示例" else "默认"}", "排版设置", "阅读主题",
            "选区工具栏：${if (customSelectionToolbar) "自定义示例" else "系统默认"}")
        AlertDialog.Builder(this).setTitle("阅读输入").setItems(labels) { _, index ->
            when (index) {
                0 -> reader.setInputOptions(input.copy(gesturePagingEnabled = !input.gesturePagingEnabled))
                1 -> reader.setInputOptions(input.copy(tapPagingEnabled = !input.tapPagingEnabled))
                2 -> reader.setInputOptions(input.copy(keyboardPagingEnabled = !input.keyboardPagingEnabled))
                3 -> reader.setInputOptions(input.copy(volumeKeyPagingEnabled = !input.volumeKeyPagingEnabled))
                4 -> reader.setInputOptions(input.copy(pagingAxis = if (input.pagingAxis == PagingAxis.Horizontal) PagingAxis.Vertical else PagingAxis.Horizontal))
                5 -> reader.setPageTurnOptions(reader.pageTurnOptions.copy(minimumTurnIntervalMs = if (reader.pageTurnOptions.minimumTurnIntervalMs == 0L) 250 else 0))
                6 -> if (customPageTemplate) Toast.makeText(this, "切回默认页面后可选择页眉内容", Toast.LENGTH_SHORT).show()
                    else applyDemoStyle(reader.style.copy(header = reader.style.header.copy(content = if (reader.style.header.content == ChromeContent.TIME) ChromeContent.CHAPTER_TITLE else ChromeContent.TIME)))
                7 -> {
                    customPageTemplate = !customPageTemplate
                    reader.setPageFactory(if (customPageTemplate) DemoReaderPageFactory() else ReaderPageFactories.default())
                }
                8 -> {
                    customPlaceholder = !customPlaceholder
                    reader.setPlaceholderFactory(if (customPlaceholder) DemoReaderPlaceholderFactory() else ReaderPlaceholderFactories.default())
                }
                9 -> stylePanel.show()
                10 -> AlertDialog.Builder(this).setTitle("阅读主题").setItems(DemoReaderThemes.labels) { _, theme ->
                    applyDemoStyle(DemoReaderThemes.apply(theme, reader.style))
                }.setNegativeButton("取消", null).show()
                11 -> {
                    customSelectionToolbar = !customSelectionToolbar
                    reader.setSelectionToolbarFactory(if (customSelectionToolbar) DemoSelectionToolbarFactory { reader.style } else null)
                }
            }
            persistReaderPreferences()
        }.setNegativeButton("取消", null).show()
    }

    private fun applyDemoStyle(style: ReaderStyle) {
        reader.applyStyle(style) { outcome ->
            if (isFinishing || isDestroyed) return@applyStyle
            when (val result = (outcome as? ReaderCompletion.Completed)?.value) {
                is ReaderResult.Success -> { committedStyle = style; persistReaderPreferences() }
                is ReaderResult.Failure -> Toast.makeText(this, result.error.message, Toast.LENGTH_LONG).show()
                is ReaderResult.Rejected -> Toast.makeText(this, "当前无法应用版式：${result.reason}", Toast.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }

    private fun persistReaderPreferences() {
        val style = committedStyle ?: return
        if (!preferencesReady || !::readerPreferences.isInitialized || isDestroyed) return
        readerPreferences.save(style, reader.inputOptions, reader.pageTurnOptions, reader.readingMode,
            reader.pageTurnEffectId, customPageTemplate, customPlaceholder, customSelectionToolbar)
    }

    private fun showPageIndex() = pageIndexPanel.show()

    private fun showBookmarks() = annotationPanel.show()

    private fun addDemoHighlight() {
        if (reader.currentBook?.bookId != source.bookId) {
            Toast.makeText(this, "请长按正文选词并创建高亮", Toast.LENGTH_SHORT).show()
            return
        }
        val position = reader.currentPosition ?: return
        val anchor = position.anchor ?: return
        val paragraphs = sampleParagraphs()
        val paragraphNumber = anchor.blockId.removePrefix("paragraph-").toIntOrNull()
        val text = if (anchor.blockId == "title") reader.currentChapter?.title ?: return
            else paragraphNumber?.takeIf { it in 0 until 28 }?.let { paragraphs[it % paragraphs.size] } ?: return
        val start = anchor.offset.coerceAtMost(text.length)
        val next = if (anchor.blockId == "title") 0 else paragraphNumber!! + 1
        val end = if (next < 28) ContentAnchor("paragraph-$next", minOf(20, paragraphs[next % paragraphs.size].length)) else ContentAnchor(anchor.blockId, text.length)
        val quote = text.substring(start) + if (next < 28) "\n" + paragraphs[next % paragraphs.size].take(end.offset) else ""
        val highlight = Highlight(UUID.randomUUID().toString(), TextRange(position.bookId, position.chapterId, position.contentRevision, ContentAnchor(anchor.blockId, start), end), quote)
        val result = reader.addHighlight(highlight)
        if (result is ReaderResult.Rejected) status.text = result.reason.name
    }

    private fun showUpdateOptions() {
        cancelEpubNavigation()
        val book = reader.currentBook ?: return
        if (book.bookId != source.bookId) {
            reader.reloadBook { outcome ->
                val result = (outcome as? ReaderCompletion.Completed)?.value
                if (result is ReaderResult.Failure) status.text = result.error.message
            }
            return
        }
        AlertDialog.Builder(this).setTitle("更新目录")
            .setItems(arrayOf("反转章节顺序", "移除当前章节", "重新读取目录")) { _, action ->
                if (reader.currentBook !== book || isFinishing || isDestroyed) return@setItems
                val chapters = when (action) {
                    0 -> book.chapters.reversed()
                    1 -> book.chapters.filter { it.chapterId != reader.currentPosition?.chapterId }
                    else -> book.chapters
                }
                if (action != 2) source.replaceBook(BookDescriptor(book.bookId, book.title, (++catalogueRevision).toString(), chapters.map { TocEntry.Chapter(it) }))
                reader.reloadBook { outcome ->
                    when (val result = (outcome as? ReaderCompletion.Completed)?.value) {
                        is ReaderResult.Failure -> status.text = result.error.message
                        is ReaderResult.Rejected -> status.text = result.reason.name
                        else -> Unit
                    }
                }
            }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        reader.pageTurnEffectId.takeIf { DemoReaderEffects.isCustom(it) }?.let { outState.putString("demoCustomEffect", it) }
        outState.putBoolean("customPageTemplate", customPageTemplate)
        outState.putBoolean("customSelectionToolbar", customSelectionToolbar)
        outState.putBoolean("customPlaceholder", customPlaceholder)
        persistProgress()
        persistReaderPreferences()
        fileInput?.let { selected ->
            outState.putString("textUri", selected.uri.toString())
            outState.putString("textCharset", selected.charset)
            outState.putString("sourceType", selected.sourceType)
        }
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (::controlPanel.isInitialized && controlPanel.isShowing) controlPanel.hide() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::controlPanel.isInitialized) controlPanel.hide()
        if (::annotationPanel.isInitialized) annotationPanel.dismiss()
        if (::pageIndexPanel.isInitialized) pageIndexPanel.dismiss()
        if (::stylePanel.isInitialized) stylePanel.dismiss()
        cancelEpubNavigation()
        hostScope.cancel()
        reader.removeCallbacks(saveProgress)
        fileOpenSequence++
        if (::reader.isInitialized) reader.release()
        ownedFileSources.forEach { it.close() }
        ownedFileSources.clear()
        super.onDestroy()
    }

    override fun onStop() {
        if (::controlPanel.isInitialized) controlPanel.hide()
        if (::annotationPanel.isInitialized) annotationPanel.dismiss()
        if (::pageIndexPanel.isInitialized) pageIndexPanel.dismiss()
        if (::stylePanel.isInitialized) stylePanel.dismiss()
        cancelEpubNavigation()
        reader.removeCallbacks(saveProgress)
        persistProgress()
        persistReaderPreferences()
        if (lastGrantReferences != null) cleanupUriGrants(force = true)
        super.onStop()
    }

    private fun sampleBook(): MemoryBookSource {
        val chapters = listOf("第一章 · 阅读的开始", "第二章 · 内容与位置", "第三章 · 风与书页").mapIndexed { index, title -> ChapterDescriptor("chapter-${index + 1}", title, "1") }
        val book = BookDescriptor("iread-demo-book", "iReadView 阅读示例", "1", chapters.map { TocEntry.Chapter(it) })
        val paragraphs = sampleParagraphs()
        val contents = chapters.map { chapter ->
            ChapterContent(book.bookId, chapter.chapterId, "1", listOf(HeadingBlock("title", chapter.title)) + (0 until 28).map { number -> TextBlock("paragraph-$number", paragraphs[number % paragraphs.size]) })
        }
        return MemoryBookSource(book, contents)
    }

    private fun sampleParagraphs(): List<String> = listOf(
            "清晨的阳光落在窗边，书页上的文字渐渐清晰。阅读从一个安静的片刻开始，我们沿着句子向前，把每一段文字连接成自己的理解。",
            "这是一段由宿主提供的正文。阅读器负责根据可用空间排版，章节、段落和阅读位置都保留各自的身份。字号改变以后，页数可能改变，正在阅读的内容仍然可以被找到。",
            "窗外的风吹过树梢，远处传来脚步声。文字在不同的屏幕上换行，在不同的字体里呈现，内容的顺序却保持稳定。翻到下一页，我们继续这一段尚未结束的旅程。",
            "A reader Widget belongs inside its host application. The host supplies content; the library presents it and reports the committed reading position. Mixed Chinese and English text follows Android's text layout."
    )
}

