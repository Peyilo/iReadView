package org.peyilo.iread.demo.ui.reader

import org.peyilo.iread.demo.R
import org.peyilo.iread.demo.data.source.ProgressTextBookSource
import org.peyilo.iread.demo.data.source.TextFiles
import org.peyilo.iread.demo.data.storage.AnnotationStorage
import org.peyilo.iread.demo.data.storage.ReaderPreferences
import org.peyilo.iread.demo.data.storage.ReadingStorage
import org.peyilo.iread.demo.data.storage.UriGrantStore
import org.peyilo.iread.demo.reader.PageChromeSettings
import org.peyilo.iread.demo.data.storage.PageChromeCodec
import org.peyilo.ireadview.spi.page.ReaderPageChromeAction
import org.peyilo.iread.demo.reader.CustomPlaceholderFactory
import org.peyilo.iread.demo.reader.CustomSelectionToolbarFactory
import org.peyilo.iread.demo.reader.ReaderEffects
import org.peyilo.iread.demo.reader.ReaderThemes
import org.peyilo.iread.demo.ui.reader.panel.AnnotationPanel
import org.peyilo.iread.demo.ui.reader.panel.PageIndexPanel
import org.peyilo.iread.demo.ui.reader.menu.ReaderMenuController
import org.peyilo.iread.demo.ui.reader.typography.TypographySheet
import org.peyilo.iread.demo.ui.reader.contents.ContentsSheet
import org.peyilo.iread.demo.ui.reader.settings.ReadingSettingsSheet

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.peyilo.ireadview.api.style.EdgeInsets
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
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.api.source.BookNavigationEntry
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.spi.page.ReaderPlaceholderFactories
import java.nio.charset.Charset
import java.security.MessageDigest
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView
import java.util.UUID
import java.io.File

/** A real host: all interactions use the library's public Widget/model/source APIs. */
class ReaderActivity : Activity() {
    companion object {
        const val EXTRA_ENTRY = "readerEntry"
        const val ENTRY_BUILTIN = "builtin"
        const val ENTRY_LOCAL_TXT = "local-txt"
        const val ENTRY_EPUB = "epub"
    }
    private lateinit var reader: ReaderView
    private lateinit var importPanel: View
    private lateinit var importMessage: TextView
    private lateinit var importProgress: ProgressBar
    private lateinit var typographySheet: TypographySheet
    private lateinit var contentsSheet: ContentsSheet
    private lateinit var settingsSheet: ReadingSettingsSheet
    private lateinit var readerMenu: ReaderMenuController
    private lateinit var pageIndexPanel: PageIndexPanel
    private lateinit var annotationPanel: AnnotationPanel
    private lateinit var source: MemoryBookSource
    private data class FileInput(val uri: Uri, val charset: String = "UTF-8", val sourceType: String = "txt", val title: String? = null, val size: Long? = null)
    private var fileInput: FileInput? = null
    private var fileOpenSequence = 0L
    private var fileSelectionSequence = 0L
    private var pendingFileInput: FileInput? = null
    private var requestedFileInput: FileInput? = null
    private var requestedSource: BookSource? = null
    private var textPreparationJob: Job? = null
    private lateinit var uriGrants: UriGrantStore
    private var lastGrantReferences: Set<String>? = null
    private var pageChrome = PageChromeSettings()
    private var pageSafeInsets = EdgeInsets()
    private var customPlaceholder = false
    private var customSelectionToolbar = false
    private var catalogueRevision = 1
    private lateinit var annotationStorage: AnnotationStorage
    private lateinit var readingStorage: ReadingStorage
    private lateinit var readerPreferences: ReaderPreferences
    private var committedStyle: ReaderStyle? = null
    private var preferencesReady = false
    private var committedBookId: String? = null
    private var placeholderRecovery: Pair<BookSource, FileInput?>? = null
    private val ownedFileSources = LinkedHashSet<AutoCloseable>()
    private val hostScope = MainScope()
    private var contentsNavigationJob: Job? = null
    private var contentsNavigationSequence = 0L

    private val saveProgress = Runnable { persistProgress() }
    private var annotationsRestored = false
    private val navigationCallback = ReaderCallback<NavigationResult<ReadingPosition>> { outcome ->
        val result = (outcome as? ReaderCompletion.Completed)?.value
        when (result) {
            is NavigationResult.Rejected -> showMessage(when (result.reason) {
                ReaderRejection.AtBoundary -> "已到书籍边界"
                ReaderRejection.Busy -> "正在准备下一页…"
                else -> result.reason.name
            })
            is NavigationResult.Failed -> showMessage(result.error.message)
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        reader = findViewById(R.id.reader)
        updateSystemBars()
        importPanel = findViewById(R.id.txt_import_panel)
        importMessage = findViewById(R.id.txt_import_message)
        importProgress = findViewById(R.id.txt_import_progress)
        typographySheet = TypographySheet(this, { reader.style }, ::applyReaderStyle, { pageChrome }, ::applyPageChrome)
        contentsSheet = ContentsSheet(this)
        settingsSheet = ReadingSettingsSheet(this, reader, ::applyReaderStyle, ::persistReaderPreferences, ::showMessage, ::showBookActions, ::showInputOptions, ::showBookmarks, ::showPageIndex, ::showCurrentBookDetails)
        readerMenu = ReaderMenuController(this, reader, hostScope, ::showContents, { typographySheet.show() }, ::showReaderSettings) {
            navigationCallback.onComplete(ReaderCompletion.Completed(it))
        }
        annotationPanel = AnnotationPanel(this, reader, hostScope, { navigationCallback.onComplete(ReaderCompletion.Completed(it)) }, ::addSampleHighlight)
        pageIndexPanel = PageIndexPanel(this, reader, hostScope, { navigationCallback.onComplete(ReaderCompletion.Completed(it)) }, { showMessage(it) })
        readerPreferences = ReaderPreferences(this)
        savedInstanceState?.getString("demoCustomEffect")?.takeIf { ReaderEffects.isCustom(it) }
            ?.let { ReaderEffects.fromId(it) }?.let { reader.setPageTurnEffect(it) }
        val storedSettings = readerPreferences.load(reader.style)
        val coldSettings = if (savedInstanceState == null) storedSettings else null
        pageChrome = PageChromeCodec.decode(savedInstanceState?.getString("pageChrome")) ?: storedSettings?.pageChrome ?: PageChromeSettings()
        installPageChrome()
        customPlaceholder = savedInstanceState?.getBoolean("customPlaceholder") ?: coldSettings?.customPlaceholder ?: false
        installFullScreenPageFactory()
        ViewCompat.setOnApplyWindowInsetsListener(reader) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            val nav = if (org.peyilo.iread.demo.reader.ReaderSystemBars.navigationHidden(this)) androidx.core.graphics.Insets.NONE
                else insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density
            val left = maxOf(top.left, nav.left) / density
            val right = maxOf(top.right, nav.right) / density
            val rtl = reader.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val safe = EdgeInsets(if (rtl) right else left, top.top / density,
                if (rtl) left else right, maxOf(top.bottom, nav.bottom) / density)
            if (safe != pageSafeInsets) { pageSafeInsets = safe; installFullScreenPageFactory() }
            insets
        }
        ViewCompat.requestApplyInsets(reader)
        if (customPlaceholder) reader.setPlaceholderFactory(CustomPlaceholderFactory())
        customSelectionToolbar = savedInstanceState?.getBoolean("customSelectionToolbar") ?: coldSettings?.customSelectionToolbar ?: false
        reader.setSelectionToolbarFactory(if (customSelectionToolbar) CustomSelectionToolbarFactory { reader.style }
            else org.peyilo.iread.demo.reader.ReaderSelectionMenus.factory(this, reader))
        source = sampleBook()
        readingStorage = ReadingStorage(this)
        uriGrants = UriGrantStore.get(this)
        fileInput = savedInstanceState?.getString("textUri")?.let { value ->
            val charset = savedInstanceState.getString("textCharset") ?: "UTF-8"
            val type = savedInstanceState.getString("sourceType") ?: "txt"
            runCatching {
                require(type == "txt" || type == "epub")
                if (type == "txt") Charset.forName(charset)
                FileInput(Uri.parse(value), charset, type, savedInstanceState.getString("textTitle"))
            }.getOrNull()
        }
        if (savedInstanceState == null) {
            val recent = readingStorage.recent()
            if (recent?.uri == null) fileInput = sampleFileInput()
            recent?.let {
                if (recent.uri != null) {
                    fileInput = runCatching {
                        val charset = recent.charset ?: "UTF-8"
                        if (recent.sourceType == "txt") Charset.forName(charset)
                        FileInput(Uri.parse(recent.uri), charset, recent.sourceType, recent.title).takeIf { fileBookId(it) == recent.bookId }
                    }.getOrNull()
                }
            }
        }
        annotationStorage = AnnotationStorage(this)
        if (savedInstanceState == null) {
            when (intent.getStringExtra(EXTRA_ENTRY)) {
                ENTRY_BUILTIN -> fileInput = sampleFileInput()
                ENTRY_LOCAL_TXT -> fileInput = null
                ENTRY_EPUB -> fileInput = sampleEpubInput()
            }
        }
        reader.addReaderListener(object : ReaderListener {
            override fun onEffectFallback(view: BookReadView, requestedId: String, appliedId: String, reason: EffectFallbackReason) {
                Toast.makeText(this@ReaderActivity, "翻页效果已回退为无动画（$reason）", Toast.LENGTH_SHORT).show()
            }
            override fun onHighlightTapped(view: BookReadView, highlight: HighlightAnnotation) {
                annotationPanel.showHighlight(highlight.id)
            }
            override fun onBookChanged(view: BookReadView, book: BookDescriptor?) {
                readerMenu.hide()
                contentsSheet.dismiss(); typographySheet.dismiss(); settingsSheet.dismiss()
                annotationPanel.dismiss()
                pageIndexPanel.dismiss()
                cancelContentsNavigation()
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
                if (restoration is Restoration.Fallback) Toast.makeText(this@ReaderActivity, "内容已更新，阅读位置已回退（${restoration.reason}）", Toast.LENGTH_SHORT).show()
            }
            override fun onStateChanged(view: BookReadView, state: ReaderState) {
                readerMenu.refresh(state)
                if (pendingFileInput == null && (state.phase == ReaderPhase.Ready || state.phase == ReaderPhase.Error || state.phase == ReaderPhase.Empty)) {
                    importPanel.visibility = View.GONE
                } else if (requestedFileInput?.sourceType == "txt" && state.phase == ReaderPhase.LoadingChapter) {
                    showImportProgress("正在准备章节排版…")
                }
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
            }
        })
        reader.setReaderActionHandler(ReaderActionHandler { _, action ->
            when (action) {
                ReaderAction.ShowTableOfContents -> if (reader.currentBook == null) false else { showContents(); true }
                ReaderAction.ShowSettings -> { showReaderSettings(); true }
                ReaderAction.ExitReader -> { finish(); true }
            }
        })
        reader.setReaderLinkHandler { _, _, target ->
            if (target is LinkTarget.External) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.uri))) }
                catch (_: ActivityNotFoundException) { Toast.makeText(this, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show() }
                true
            } else false
        }
        reader.setReaderTapListener(ReaderTapListener { tap ->
            val coordinate = if (reader.inputOptions.pagingAxis == PagingAxis.Vertical) tap.y else tap.x
            val edge = reader.inputOptions.tapEdgeFraction
            if (coordinate >= edge && coordinate <= 1f - edge) { readerMenu.toggle(); true } else false
        })
        reader.post {
            hostScope.launch {
                if (isFinishing || isDestroyed) return@launch
                if (committedStyle == null) committedStyle = reader.style
                if (fileOpenSequence != 0L || fileSelectionSequence != 0L) { preferencesReady = true; return@launch }
                coldSettings?.let { settings ->
                    settings.input?.let { reader.setInputOptions(it) }
                    settings.turn?.let { reader.setPageTurnOptions(it) }
                    settings.effectId?.let { ReaderEffects.fromId(it) }?.let { reader.setPageTurnEffect(it) }
                    settings.mode?.let { reader.setReadingMode(it) }
                    settings.style?.let { if (reader.applyStyle(it) is ReaderResult.Success) committedStyle = it }
                }
                val palette = LinkedHashMap(reader.style.highlightStyles.styles)
                if (!palette.containsKey("blue")) palette["blue"] = HighlightStyle(0x6657b9ff)
                val merged = reader.style.copy(highlightStyles = HighlightStyles(palette))
                if (reader.applyStyle(merged) is ReaderResult.Success) committedStyle = merged
                preferencesReady = true
                updateSystemBars()
                if (fileOpenSequence != 0L || fileSelectionSequence != 0L || isFinishing || isDestroyed) return@launch
                val selected = fileInput
                val incomingFile = intent.data.takeIf { intent.getStringExtra(EXTRA_ENTRY) == ENTRY_LOCAL_TXT && selected == null }
                if (incomingFile != null) openSelectedFile(FileInput(incomingFile), intent.flags)
                else openSource(selected?.let { fileSource(it) } ?: source, selected, restoreStored = savedInstanceState == null)
            }
        }
    }

    private fun showMessage(message: String) {
        if (!isFinishing && !isDestroyed) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun updateSystemBars() {
        val color = reader.style.page.backgroundColor
        findViewById<View>(R.id.reader_root).setBackgroundColor(color)
        org.peyilo.iread.demo.reader.ReaderSystemBars.apply(this, window, color)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) org.peyilo.iread.demo.reader.ReaderSystemBars.apply(this, window)
    }
    private fun installFullScreenPageFactory() {
        reader.setPageFactory(org.peyilo.iread.demo.reader.FullScreenPageFactory(pageSafeInsets))
    }

    @Suppress("DEPRECATION")
    private fun installPageChrome() {
        reader.setPageChrome(pageChrome.template().withBackButton(pageChrome.showBackButton,
            ReaderPageChromeAction { onBackPressed() }))
    }
    private fun applyPageChrome(settings: PageChromeSettings) {
        pageChrome = settings
        installPageChrome()
        typographySheet.refresh()
        persistReaderPreferences()
    }

    private fun showBookActions() {
        AlertDialog.Builder(this).setTitle("书籍")
            .setItems(arrayOf("选择 TXT 文件", "选择 EPUB 文件", "The Art of ARM Assembly", "最近阅读", "内置书籍")) { _, index ->
                when (index) {
                    0 -> chooseFile("txt")
                    1 -> chooseFile("epub")
                    2 -> sampleEpubInput().let { openSource(fileSource(it), it) }
                    3 -> showRecentBooks()
                    else -> showSampleBooks()
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun showReaderSettings() = settingsSheet.show()

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
            openSelectedFile(FileInput(uri, sourceType = "epub"), data.flags)
            return
        }
        openSelectedFile(FileInput(uri), data.flags)
    }

    private fun openSelectedFile(selection: FileInput, flags: Int) {
        textPreparationJob?.cancel()
        val sequence = ++fileSelectionSequence
        pendingFileInput = selection
        if (selection.sourceType == "txt") showImportProgress("正在识别文件编码…")
        // Enqueue acquisition immediately, before a later history mutation can enqueue cleanup.
        val retained = uriGrants.retain(selection.uri, flags)
        textPreparationJob = hostScope.launch {
            try {
                val persisted = retained.await()
                val inspection = if (selection.sourceType == "txt") TextFiles.inspect(this@ReaderActivity, selection.uri) else null
                if (sequence != fileSelectionSequence || isFinishing || isDestroyed) return@launch
                val resolved = inspection?.let { selection.copy(charset = it.charset, title = it.title, size = it.size) } ?: selection
                if (!persisted) Toast.makeText(this@ReaderActivity, "无法保留文件授权，当前会话仍可尝试读取", Toast.LENGTH_LONG).show()
                textPreparationJob = null
                openSource(fileSource(resolved), resolved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (sequence != fileSelectionSequence || isFinishing || isDestroyed) return@launch
                pendingFileInput = null
                importPanel.visibility = View.GONE
                cleanupUriGrants()
                AlertDialog.Builder(this@ReaderActivity).setTitle("无法读取文件").setMessage(error.message ?: "请重新选择可读取的 TXT 文件")
                    .setPositiveButton("确定", null).show()
            }
        }
    }

    private fun sampleFileInput() = FileInput(TextFiles.sampleUri, title = TextFiles.SAMPLE_TITLE)
    private fun sampleEpubInput(name: String = "the-art-of-arm-assembly.epub", title: String = "The Art of ARM Assembly") =
        FileInput(Uri.parse("iread-demo://epub/$name"), sourceType = "epub", title = title)

    private fun showSampleBooks() {
        AlertDialog.Builder(this).setTitle("示例书籍").setItems(arrayOf("妖精之诗 · 真实 TXT 小说", "Widget API 演示短文",
            "EPUB · 图文与链接", "EPUB · 固定版式")) { _, index ->
            val selected = when (index) {
                0 -> sampleFileInput()
                2 -> sampleEpubInput("illustrated-flow.epub", "山间来信")
                3 -> sampleEpubInput("fixed-layout.epub", "山间画册")
                else -> null
            }
            if (selected != null) openSource(fileSource(selected), selected) else openSource(source, null)
        }.setNegativeButton("取消", null).show()
    }

    private fun showImportProgress(message: String, bytes: Long? = null, total: Long? = null) {
        importPanel.visibility = View.VISIBLE
        importMessage.text = message
        importProgress.isIndeterminate = bytes == null || total == null || total <= 0
        if (!importProgress.isIndeterminate) importProgress.progress = ((bytes!! * 100 / total!!).coerceIn(0, 100)).toInt()
    }

    private fun chooseTextEncoding(selection: FileInput) {
        val sequence = fileOpenSequence
        AlertDialog.Builder(this).setTitle("TXT 编码 · 当前 ${selection.charset}").setItems(TextFiles.encodings) { _, index ->
            if (sequence == fileOpenSequence && !isFinishing && !isDestroyed) {
                val revised = selection.copy(charset = TextFiles.encodings[index])
                openSource(fileSource(revised), revised, restoreStored = false)
            }
        }.setNegativeButton("取消", null).show()
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
        return if (selection.sourceType == "epub") "epub-dom-$uriIdentity" else "txt-$uriIdentity-${selection.charset}"
    }
    private fun fileSource(selection: FileInput): BookSource {
        val created = if (selection.sourceType == "epub")
            EpubReaderBookSource(fileBookId(selection),
                if (selection.uri.scheme == "iread-demo") {
                    val name = selection.uri.lastPathSegment
                    require(selection.uri.host == "epub" && name in setOf("the-art-of-arm-assembly.epub", "illustrated-flow.epub", "fixed-layout.epub"))
                    ReaderInput.fromAsset(this, "epubs/$name")
                }
                else ReaderInput.fromUri(this, selection.uri), File(cacheDir, "reader-epub"))
        else {
            val bookId = fileBookId(selection)
            lateinit var trackedSource: ProgressTextBookSource
            trackedSource = ProgressTextBookSource(this, bookId, selection.uri,
                selection.title ?: selection.uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".txt") ?: "本地 TXT",
                selection.charset, selection.size) { progress ->
                reader.post {
                    if (!isFinishing && !isDestroyed && requestedSource === trackedSource && requestedFileInput == selection && pendingFileInput == null &&
                        reader.readerState.phase != ReaderPhase.Ready && reader.readerState.phase != ReaderPhase.Error) {
                        when (progress.stage) {
                            ProgressTextBookSource.Stage.Reading -> showImportProgress("读取并解析 ${selection.title ?: "TXT"} · 已读取 ${progress.bytes / 1024} KB", progress.bytes, progress.total)
                            ProgressTextBookSource.Stage.Parsing -> showImportProgress("正文读取完毕，正在整理章节索引…")
                            ProgressTextBookSource.Stage.Ready -> showImportProgress("已解析 ${progress.chapters} 章，正在准备阅读页面…")
                        }
                    }
                }
            }
            trackedSource
        }
        ownedFileSources.add(created as AutoCloseable)
        return created
    }

    private fun retireOtherFileSources(current: BookSource) {
        val retired = ownedFileSources.filter { it !== current }
        retired.forEach { it.close(); ownedFileSources.remove(it) }
    }

    private fun openSource(nextSource: BookSource, selected: FileInput?, restoreStored: Boolean = true) {
        textPreparationJob?.cancel()
        textPreparationJob = null
        readerMenu.hide()
        contentsSheet.dismiss(); typographySheet.dismiss(); settingsSheet.dismiss()
        annotationPanel.dismiss()
        pageIndexPanel.dismiss()
        fileSelectionSequence++
        pendingFileInput = null
        requestedFileInput = selected
        requestedSource = nextSource
        if (selected?.sourceType == "txt") showImportProgress("正在打开 ${selected.title ?: "TXT 小说"} · ${selected.charset}")
        else importPanel.visibility = View.GONE
        placeholderRecovery = null
        cancelContentsNavigation()
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
                    importPanel.visibility = View.GONE
                    fileInput = selected
                    committedBookId = result.position.bookId
                    retireOtherFileSources(nextSource)
                    persistProgress()
                    if (result.restoration is Restoration.Fallback) Toast.makeText(this, "已按当前内容恢复到可用位置", Toast.LENGTH_LONG).show()
                }
                is OpenResult.Failed -> {
                    importPanel.visibility = View.GONE
                    if (reader.currentPosition == null) placeholderRecovery = nextSource to selected
                    // Snapshot prevalidation may retain the previous book; keep saving that session.
                    if (reader.currentBook?.bookId == previousBookId) committedBookId = previousBookId
                    showMessage(result.error.message)
                    AlertDialog.Builder(this).setTitle("打开失败").setMessage(result.error.message)
                        .setPositiveButton("重试") { _, _ -> if (sequence == fileOpenSequence) openSource(nextSource, selected, restoreStored) }
                        .setNeutralButton(if (selected?.sourceType == "txt") "更换编码" else "从头打开") { _, _ ->
                            if (sequence == fileOpenSequence) {
                                if (selected?.sourceType == "txt") chooseTextEncoding(selected)
                                else openSource(nextSource, selected, restoreStored = false)
                            }
                        }
                        .setNegativeButton("取消", null).show()
                }
                is OpenResult.Empty -> { fileInput = selected; retireOtherFileSources(nextSource); cleanupUriGrants(); showMessage("书籍没有章节") }
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
        fun label(entry: ReadingStorage.Recent): String {
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
                FileInput(Uri.parse(entry.uri), charset, entry.sourceType, entry.title).also { require(fileBookId(it) == entry.bookId) }
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
        cancelContentsNavigation()
        val book = reader.currentBook ?: return
        val sequence = contentsNavigationSequence
        contentsNavigationJob = hostScope.launch {
            when (val result = reader.getNavigation()) {
                is ReaderResult.Success -> {
                    if (sequence != contentsNavigationSequence || reader.currentBook !== book || isFinishing || isDestroyed) return@launch
                    val entries = ArrayList<BookNavigationEntry>()
                    val labels = ArrayList<String>()
                    fun visit(items: List<BookNavigationEntry>, depth: Int) {
                        if (depth > 32) return
                        items.forEach { entry ->
                            entries.add(entry); labels.add("  ".repeat(depth) + entry.title)
                            visit(entry.children, depth + 1)
                        }
                    }
                    visit(result.value, 0)
                    contentsSheet.show(labels, enabled = { entries[it].position != null }) { index ->
                        if (reader.currentBook === book) entries[index].position?.let { reader.goToPosition(it, navigationCallback) }
                    }
                }
                is ReaderResult.Failure -> if (reader.currentBook === book) showMessage(result.error.message)
                else -> Unit
            }
        }
    }
    private fun cancelContentsNavigation() {
        contentsNavigationSequence++
        contentsNavigationJob?.cancel(); contentsNavigationJob = null
    }
    private fun showSpineContents() {
        val book = reader.currentBook ?: return
        val chapters = book.chapters
        val index = chapters.indexOfFirst { it.chapterId == reader.currentPosition?.chapterId }
        contentsSheet.show(chapters.map { it.title }, index) { selected ->
            if (reader.currentBook === book) reader.goToChapter(chapters[selected].chapterId, 1, navigationCallback)
        }
    }
    private fun showCurrentBookDetails() {
        val book = reader.currentBook ?: return

        val selected = fileInput
        AlertDialog.Builder(this).setTitle(book.title)
            .setMessage(if (selected != null) "${if (selected.sourceType == "epub") "EPUB" else "TXT · ${selected.charset}"}\n已解析 ${book.chapters.size} 章" else "${book.chapters.size} 章")
            .setPositiveButton("确定", null)
            .setNeutralButton(if (selected == null || selected.sourceType == "epub") "重新读取" else "更换编码") { _, _ ->
                if (reader.currentBook === book) { if (selected == null || selected.sourceType == "epub") showUpdateOptions() else chooseTextEncoding(selected) }
            }.setNegativeButton("正文目录") { _, _ -> if (reader.currentBook === book) showSpineContents() }.show()
    }
    private fun showInputOptions() {
        val input = reader.inputOptions
        fun toggleLabel(label: String, enabled: Boolean) = "$label：${if (enabled) "开启" else "关闭"}"
        val labels = arrayOf(toggleLabel("拖动翻页", input.gesturePagingEnabled), toggleLabel("边缘点击翻页", input.tapPagingEnabled),
            toggleLabel("键盘翻页", input.keyboardPagingEnabled), toggleLabel("音量键翻页", input.volumeKeyPagingEnabled),
            "分页方向：${if (input.pagingAxis == PagingAxis.Horizontal) "横向" else "纵向（无动画）"}",
            "翻页间隔：${reader.pageTurnOptions.minimumTurnIntervalMs} 毫秒",
            "加载提示：${if (customPlaceholder) "自定义示例" else "默认"}", "排版设置", "阅读主题",
            "选区工具栏：${if (customSelectionToolbar) "自定义视图示例" else "图文分页菜单"}",
            "页面缓存：${if (reader.pageCacheMode == org.peyilo.ireadview.api.model.ReaderPageCacheMode.THREE_PAGE) "三页" else "单页"}")
        AlertDialog.Builder(this).setTitle("阅读输入").setItems(labels) { _, index ->
            when (index) {
                0 -> reader.setInputOptions(input.copy(gesturePagingEnabled = !input.gesturePagingEnabled))
                1 -> reader.setInputOptions(input.copy(tapPagingEnabled = !input.tapPagingEnabled))
                2 -> reader.setInputOptions(input.copy(keyboardPagingEnabled = !input.keyboardPagingEnabled))
                3 -> reader.setInputOptions(input.copy(volumeKeyPagingEnabled = !input.volumeKeyPagingEnabled))
                4 -> reader.setInputOptions(input.copy(pagingAxis = if (input.pagingAxis == PagingAxis.Horizontal) PagingAxis.Vertical else PagingAxis.Horizontal))
                5 -> reader.setPageTurnOptions(reader.pageTurnOptions.copy(minimumTurnIntervalMs = if (reader.pageTurnOptions.minimumTurnIntervalMs == 0L) 250 else 0))
                6 -> {
                    customPlaceholder = !customPlaceholder
                    reader.setPlaceholderFactory(if (customPlaceholder) CustomPlaceholderFactory() else ReaderPlaceholderFactories.default())
                }
                7 -> typographySheet.show()
                8 -> AlertDialog.Builder(this).setTitle("阅读主题").setItems(ReaderThemes.labels) { _, theme ->
                    applyReaderStyle(ReaderThemes.apply(theme, reader.style))
                }.setNegativeButton("取消", null).show()
                9 -> {
                    customSelectionToolbar = !customSelectionToolbar
                    reader.setSelectionToolbarFactory(if (customSelectionToolbar) CustomSelectionToolbarFactory { reader.style }
                        else org.peyilo.iread.demo.reader.ReaderSelectionMenus.factory(this, reader))
                }
                10 -> {
                    val mode = if (reader.pageCacheMode == org.peyilo.ireadview.api.model.ReaderPageCacheMode.THREE_PAGE)
                        org.peyilo.ireadview.api.model.ReaderPageCacheMode.SINGLE_PAGE else org.peyilo.ireadview.api.model.ReaderPageCacheMode.THREE_PAGE
                    val result = reader.setPageCacheMode(mode)
                    if (result is ReaderResult.Rejected) Toast.makeText(this, "当前无法切换页面缓存：${result.reason}", Toast.LENGTH_SHORT).show()
                }
            }
            persistReaderPreferences()
        }.setNegativeButton("取消", null).show()
    }

    private fun applyReaderStyle(style: ReaderStyle) {
        reader.applyStyle(style) { outcome ->
            if (isFinishing || isDestroyed) return@applyStyle
            when (val result = (outcome as? ReaderCompletion.Completed)?.value) {
                is ReaderResult.Success -> { committedStyle = reader.style; persistReaderPreferences() }
                is ReaderResult.Failure -> Toast.makeText(this, result.error.message, Toast.LENGTH_LONG).show()
                is ReaderResult.Rejected -> Toast.makeText(this, "当前无法应用版式：${result.reason}", Toast.LENGTH_LONG).show()
                else -> Unit
            }
            typographySheet.refresh(); settingsSheet.refresh()
        }
    }

    private fun persistReaderPreferences() {
        updateSystemBars()
        val style = committedStyle ?: return
        if (!preferencesReady || !::readerPreferences.isInitialized || isDestroyed) return
        readerPreferences.save(style, reader.inputOptions, reader.pageTurnOptions, reader.readingMode,
            reader.pageTurnEffectId, customPlaceholder, customSelectionToolbar, pageChrome)
    }

    private fun showPageIndex() = pageIndexPanel.show()

    private fun showBookmarks() = annotationPanel.show()

    private fun addSampleHighlight() {
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
        if (result is ReaderResult.Rejected) showMessage(result.reason.name)
    }

    private fun showUpdateOptions() {
        cancelContentsNavigation()
        val book = reader.currentBook ?: return
        if (book.bookId != source.bookId) {
            reader.reloadBook { outcome ->
                val result = (outcome as? ReaderCompletion.Completed)?.value
                if (result is ReaderResult.Failure) showMessage(result.error.message)
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
                        is ReaderResult.Failure -> showMessage(result.error.message)
                        is ReaderResult.Rejected -> showMessage(result.reason.name)
                        else -> Unit
                    }
                }
            }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        reader.pageTurnEffectId.takeIf { ReaderEffects.isCustom(it) }?.let { outState.putString("demoCustomEffect", it) }
        outState.putString("pageChrome", PageChromeCodec.encode(pageChrome))
        outState.putBoolean("customSelectionToolbar", customSelectionToolbar)
        outState.putBoolean("customPlaceholder", customPlaceholder)
        persistProgress()
        persistReaderPreferences()
        fileInput?.let { selected ->
            outState.putString("textUri", selected.uri.toString())
            outState.putString("textCharset", selected.charset)
            outState.putString("sourceType", selected.sourceType)
            outState.putString("textTitle", selected.title)
        }
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (::readerMenu.isInitialized && readerMenu.isShowing) readerMenu.hide() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::readerMenu.isInitialized) readerMenu.hide()
        if (::annotationPanel.isInitialized) annotationPanel.dismiss()
        if (::pageIndexPanel.isInitialized) pageIndexPanel.dismiss()
        if (::typographySheet.isInitialized) typographySheet.dismiss()
        if (::contentsSheet.isInitialized) contentsSheet.dismiss()
        if (::settingsSheet.isInitialized) settingsSheet.dismiss()
        cancelContentsNavigation()
        hostScope.cancel()
        reader.removeCallbacks(saveProgress)
        fileOpenSequence++
        if (::reader.isInitialized) reader.release()
        ownedFileSources.forEach { it.close() }
        ownedFileSources.clear()
        super.onDestroy()
    }

    override fun onStop() {
        if (::readerMenu.isInitialized) readerMenu.hide()
        if (::annotationPanel.isInitialized) annotationPanel.dismiss()
        if (::pageIndexPanel.isInitialized) pageIndexPanel.dismiss()
        if (::typographySheet.isInitialized) typographySheet.dismiss()
        if (::contentsSheet.isInitialized) contentsSheet.dismiss()
        if (::settingsSheet.isInitialized) settingsSheet.dismiss()
        cancelContentsNavigation()
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

