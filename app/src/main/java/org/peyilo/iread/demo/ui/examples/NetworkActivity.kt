package org.peyilo.iread.demo.ui.examples

import org.peyilo.iread.demo.R
import org.peyilo.iread.demo.data.source.BiqugeBookSource
import org.peyilo.iread.demo.reader.ReaderEffects
import org.peyilo.iread.demo.data.state.SnapshotState

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.*
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.BookSource
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

/** Opens a real HTML book source; each chapter is requested independently by the Widget. */
class NetworkActivity : Activity() {
    private lateinit var reader: ReaderView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private var source: BookSource? = null
    private var request: ReaderRequest? = null
    private var url: String? = null
    private var snapshot: ReaderSnapshot? = null
    private var sequence = 0L
    private var started = false
    private var needsResume = true
    private val resume = Runnable { if (started && needsResume) { needsResume = false; url?.let { open(it) } } }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) org.peyilo.iread.demo.reader.ReaderSystemBars.apply(this, window, fullScreenContent = false)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.peyilo.iread.demo.reader.ReaderSystemBars.apply(this, window, fullScreenContent = false)
        url = savedInstanceState?.getString("catalogue")?.takeIf { it.startsWith("https://www.yuyouku.com/book/") } ?: BiqugeBookSource.DEFAULT_URL
        needsResume = savedInstanceState?.getBoolean("resumeNetwork", true) ?: true
        snapshot = SnapshotState.read(savedInstanceState?.getBundle("networkSnapshot"))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        address = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "书籍目录网址"; contentDescription = "网络书籍目录地址"; isSingleLine = true
            setText(url ?: "")
        }
        status = TextView(this).apply { text = "正在打开网络示例书籍 185030…" }
        reader = ReaderView(this, defStyleRes = R.style.ReaderDefaultStyle).apply {
            id = R.id.network_reader
            setPageChrome(org.peyilo.iread.demo.reader.PageChromeSettings().template().withBackButton(true,
                org.peyilo.ireadview.spi.page.ReaderPageChromeAction { finish() }))
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { if (started) action() } }
        layout.addView(address)
        layout.addView(LinearLayout(this).apply {
            listOf(button("打开") { open(address.text.toString().trim()) }, button("示例书籍") {
                address.setText(BiqugeBookSource.DEFAULT_URL); open(BiqugeBookSource.DEFAULT_URL)
            },
                button("取消") { needsResume = false; stop(); status.text = "已取消网络阅读" },
                button("返回") { finish() }).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
        layout.addView(status); layout.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
        previous = button("上一页") { reader.previousPage(NavigationOptions(), navigationCallback()) }
        next = button("下一页") { reader.nextPage(NavigationOptions(), navigationCallback()) }
        layout.addView(LinearLayout(this).apply {
            listOf(previous, button("目录") { showContents() }, button("效果") { showEffects() },
                button("重试") { url?.let { open(it) } }, next).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
        setContentView(layout)
        reader.addReaderListener(object : ReaderListener {
            override fun onStateChanged(view: BookReadView, state: ReaderState) {
                previous.isEnabled = state.navigationCapabilities.previous == NavigationAvailability.Available || state.navigationCapabilities.previous == NavigationAvailability.Unknown
                next.isEnabled = state.navigationCapabilities.next == NavigationAvailability.Available || state.navigationCapabilities.next == NavigationAvailability.Unknown
                status.text = state.error?.message ?: state.position?.let { "${view.currentChapter?.title} · 第${it.pageNumberHint} / ${state.chapterPageCount ?: "…"}页" }
                    ?: when (state.phase) {
                        ReaderPhase.Closed -> "网络阅读已关闭"
                        ReaderPhase.LoadingBook -> "正在从书源读取目录…"
                        ReaderPhase.LoadingChapter -> "正在加载网络章节…"
                        else -> "正在准备阅读页面…"
                    }
            }
        })
        previous.isEnabled = false; next.isEnabled = false
    }
    private fun open(value: String) {
        val created: BookSource = try {
            BiqugeBookSource(value)
        } catch (error: Exception) { address.error = error.message ?: "请输入有效书源地址"; return }
        try { stop() } catch (error: Exception) { (created as AutoCloseable).close(); throw error }
        if (snapshot?.bookId != created.bookId) snapshot = null
        url = value; source = created
        val serial = ++sequence
        val callback = ReaderCallback<OpenResult> { completion ->
            if (serial != sequence || !started || isFinishing || isDestroyed) return@ReaderCallback
            request = null
            when (val result = (completion as? ReaderCompletion.Completed)?.value) {
                is OpenResult.Failed -> status.text = "${result.error.message}；可重试或更换地址"
                is OpenResult.Empty -> status.text = "网络目录没有章节"
                else -> Unit
            }
        }
        val handle = snapshot?.let { reader.restoreSnapshot(created, it, callback) } ?: reader.openBook(created, callback = callback)
        if (serial == sequence && started && !handle.isCompleted) request = handle
        else handle.cancel()
    }
    private fun stop() {
        sequence++
        reader.exportSnapshot()?.let { snapshot = it }
        val oldRequest = request; request = null
        val oldSource = source; source = null
        try {
            oldRequest?.cancel()
        } finally {
            try { reader.closeBook() } finally { (oldSource as? AutoCloseable)?.close() }
        }
    }
    private fun navigationCallback(): ReaderCallback<NavigationResult<ReadingPosition>> {
        val serial = sequence
        return ReaderCallback { completion ->
            if (serial != sequence || !started || isFinishing || isDestroyed) return@ReaderCallback
            when (val result = (completion as? ReaderCompletion.Completed)?.value) {
                is NavigationResult.Failed -> status.text = result.error.message
                is NavigationResult.Rejected -> status.text = "当前无法翻页：${result.reason}"
                else -> Unit
            }
        }
    }
    private fun showContents() {
        val book = reader.currentBook ?: return
        AlertDialog.Builder(this).setTitle("${book.title} · ${book.chapters.size} 章")
            .setItems(book.chapters.map { it.title }.toTypedArray()) { _, index ->
                if (started && reader.currentBook === book) reader.goToChapter(book.chapters[index].chapterId, 1, navigationCallback())
            }.setNegativeButton("取消", null).show()
    }
    private fun showEffects() {
        val effects = ReaderEffects.factories()
        AlertDialog.Builder(this).setTitle("翻页效果")
            .setSingleChoiceItems(ReaderEffects.labels, effects.indexOfFirst { it.id == reader.pageTurnEffectId }) { dialog, index ->
                reader.setPageTurnEffect(effects[index]); dialog.dismiss()
            }.setNegativeButton("取消", null).show()
    }
    override fun onStart() { super.onStart(); started = true; reader.post(resume) }
    override fun onStop() {
        started = false; reader.removeCallbacks(resume); needsResume = source != null; stop(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        url?.let { outState.putString("catalogue", it) }
        outState.putBoolean("resumeNetwork", source != null || needsResume)
        (reader.exportSnapshot() ?: snapshot)?.let { saved ->
            outState.putBundle("networkSnapshot", SnapshotState.write(saved))
        }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { if (::reader.isInitialized) { stop(); reader.release() }; super.onDestroy() }
}
