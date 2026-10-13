package org.peyilo.iread.demo

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

/** An explicit host networking example; nothing is requested until an address is submitted. */
class DemoNetworkActivity : Activity() {
    private lateinit var reader: ReaderView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private var source: DemoNetworkBookSource? = null
    private var request: ReaderRequest? = null
    private var url: String? = null
    private var snapshot: ReaderSnapshot? = null
    private var sequence = 0L
    private var started = false
    private var needsResume = true
    private val resume = Runnable { if (started && needsResume) { needsResume = false; url?.let { open(it) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = savedInstanceState?.getString("catalogue")
        needsResume = savedInstanceState?.getBoolean("resumeNetwork", true) ?: true
        snapshot = DemoSnapshotState.read(savedInstanceState?.getBundle("networkSnapshot"))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        address = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "HTTPS目录JSON地址"; contentDescription = "网络书籍目录地址"; isSingleLine = true
            setText(url ?: "")
        }
        status = TextView(this).apply { text = "输入目录地址后打开；目录与正文格式见项目文档" }
        reader = ReaderView(this).apply { id = R.id.network_reader }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { if (started) action() } }
        layout.addView(address)
        layout.addView(LinearLayout(this).apply {
            listOf(button("打开") { open(address.text.toString().trim()) }, button("取消") { needsResume = false; stop(); status.text = "已取消网络阅读" },
                button("退出") { finish() }).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
        layout.addView(status); layout.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
        previous = button("上一页") { reader.previousPage(NavigationOptions(), navigationCallback()) }
        next = button("下一页") { reader.nextPage(NavigationOptions(), navigationCallback()) }
        layout.addView(LinearLayout(this).apply {
            listOf(previous, button("重试") { url?.let { open(it) } }, next).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
        setContentView(layout)
        reader.addReaderListener(object : ReaderListener {
            override fun onStateChanged(view: BookReadView, state: ReaderState) {
                previous.isEnabled = state.navigationCapabilities.previous == NavigationAvailability.Available || state.navigationCapabilities.previous == NavigationAvailability.Unknown
                next.isEnabled = state.navigationCapabilities.next == NavigationAvailability.Available || state.navigationCapabilities.next == NavigationAvailability.Unknown
                status.text = state.error?.message ?: state.position?.let { "${view.currentChapter?.title} · 第${it.pageNumberHint} / ${state.chapterPageCount ?: "…"}页" }
                    ?: if (state.phase == ReaderPhase.Closed) "网络阅读已关闭" else "正在准备网络书籍…"
            }
        })
        previous.isEnabled = false; next.isEnabled = false
    }
    private fun open(value: String) {
        val created = try { DemoNetworkBookSource(value) } catch (_: Exception) { address.error = "请输入有效HTTPS目录地址"; return }
        try { stop() } catch (error: Exception) { created.close(); throw error }
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
        request = snapshot?.let { reader.restoreSnapshot(created, it, callback) } ?: reader.openBook(created, callback = callback)
    }
    private fun stop() {
        sequence++
        reader.exportSnapshot()?.let { snapshot = it }
        val oldRequest = request; request = null
        val oldSource = source; source = null
        try {
            oldRequest?.cancel()
        } finally {
            try { reader.closeBook() } finally { oldSource?.close() }
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
    override fun onStart() { super.onStart(); started = true; reader.post(resume) }
    override fun onStop() {
        started = false; reader.removeCallbacks(resume); needsResume = source != null; stop(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        url?.let { outState.putString("catalogue", it) }
        outState.putBoolean("resumeNetwork", source != null || needsResume)
        (reader.exportSnapshot() ?: snapshot)?.let { saved ->
            outState.putBundle("networkSnapshot", DemoSnapshotState.write(saved))
        }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { if (::reader.isInitialized) { stop(); reader.release() }; super.onDestroy() }
}
