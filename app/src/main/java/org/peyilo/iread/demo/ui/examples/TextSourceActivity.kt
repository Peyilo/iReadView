package org.peyilo.iread.demo.ui.examples

import org.peyilo.iread.demo.R
import org.peyilo.iread.demo.data.state.SnapshotState

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.source.TextBookOptions
import org.peyilo.ireadview.source.TextBookSource
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

/** Public TXT configuration example; source files and persistence remain host responsibilities. */
class TextSourceActivity : Activity() {
    private lateinit var reader: ReaderView
    private lateinit var status: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private var source: TextBookSource? = null
    private var request: ReaderRequest? = null
    private var snapshot: ReaderSnapshot? = null
    private var disk = false
    private var reuseIndex = false
    private var contentIds = false
    private var normalization = TextNormalization.PRESERVE
    private var started = false
    private var sequence = 0L
    private var optionsDialog: AlertDialog? = null
    private val resume = Runnable { if (started && source == null) open() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disk = savedInstanceState?.getBoolean("disk") ?: false
        reuseIndex = savedInstanceState?.getBoolean("reuseIndex") ?: false
        contentIds = savedInstanceState?.getBoolean("contentIds") ?: false
        normalization = TextNormalization.values().firstOrNull { it.name == savedInstanceState?.getString("normalization") }
            ?: TextNormalization.PRESERVE
        snapshot = SnapshotState.read(savedInstanceState?.getBundle("textSnapshot"))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        status = TextView(this).apply { text = "TXT 来源配置示例" }
        reader = ReaderView(this).apply {
            id = R.id.text_source_reader
            setPageChrome(org.peyilo.iread.demo.reader.PageChromeSettings().template().withBackButton(true,
                org.peyilo.ireadview.spi.page.ReaderPageChromeAction { finish() }))
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; setOnClickListener { if (started && !isFinishing) action() }
        }
        previous = button("上一页") { reader.previousPage(NavigationOptions(), navigationCallback()) }
        next = button("下一页") { reader.nextPage(NavigationOptions(), navigationCallback()) }
        layout.addView(status)
        layout.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(LinearLayout(this).apply {
            listOf(previous, button("来源配置") { showOptions() }, next).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
        layout.addView(LinearLayout(this).apply {
            listOf(button("重读") { open() }, button("模式") {
                val mode = if (reader.readingMode == ReadingMode.PAGED) ReadingMode.CONTINUOUS_SCROLL else ReadingMode.PAGED
                val serial = sequence
                reader.setReadingMode(mode) { result ->
                    if (current(serial)) ((result as? ReaderCompletion.Completed)?.value as? ReaderResult.Failure)?.let { status.text = it.error.message }
                }
            }, button("退出") { finish() }).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
        setContentView(layout)
        reader.addReaderListener(object : ReaderListener {
            override fun onStateChanged(view: BookReadView, state: ReaderState) {
                previous.isEnabled = state.navigationCapabilities.previous == NavigationAvailability.Available
                next.isEnabled = state.navigationCapabilities.next == NavigationAvailability.Available
                val position = state.position
                status.text = state.error?.message ?: "${if (disk) "磁盘" else "内存"}${if (disk && reuseIndex) "（允许复用）" else ""} · ${normalizationLabel()} · ${if (contentIds) "内容 ID" else "顺序 ID"}\n" +
                    (position?.let { "${view.currentChapter?.title} · 第${it.pageNumberHint}页\n${it.anchor?.blockId ?: "空章"}" } ?: "正在准备…")
            }
        })
        previous.isEnabled = false; next.isEnabled = false
    }

    private fun showOptions() {
        optionsDialog?.dismiss()
        optionsDialog = AlertDialog.Builder(this).setTitle("TXT 来源配置（更换后重读）")
            .setItems(arrayOf("存储：${if (disk) "磁盘" else "内存"}", "空白：${normalizationLabel()}", "标识：${if (contentIds) "内容摘要" else "顺序编号"}", "索引复用：${if (reuseIndex) "开启（磁盘时）" else "关闭"}")) { _, index ->
                when (index) {
                    0 -> disk = !disk
                    1 -> normalization = TextNormalization.values()[(normalization.ordinal + 1) % TextNormalization.values().size]
                    2 -> contentIds = !contentIds
                    3 -> reuseIndex = !reuseIndex
                }
                open()
            }.setNegativeButton("取消", null).create().also { it.show() }
    }

    private fun open() {
        val bytes = sample().toByteArray(Charsets.UTF_8)
        val fresh = TextBookSource("demo-text-source", ReaderInput.fromStream { ByteArrayInputStream(bytes) }, TextBookOptions(
            title = "TXT 来源示例", indexDirectory = if (disk) cacheDir.resolve("text-source-demo") else null,
            normalization = normalization, contentIdProvider = if (contentIds) ContentIds else null,
            reuseDiskIndex = disk && reuseIndex
        ))
        try { stop() } catch (error: Exception) { fresh.close(); throw error }
        source = fresh
        val serial = ++sequence
        val callback = ReaderCallback<OpenResult> { completion ->
            if (current(serial)) {
                request = null
                when (val result = (completion as? ReaderCompletion.Completed)?.value) {
                    is OpenResult.Failed -> status.text = result.error.message
                    is OpenResult.Opened -> if (result.restoration is Restoration.Fallback) status.append("\n配置已变化，位置按组件规则回退")
                    else -> Unit
                }
            }
        }
        request = snapshot?.let { reader.restoreSnapshot(fresh, it, callback) } ?: reader.openBook(fresh, callback = callback)
    }

    private fun current(serial: Long) = serial == sequence && started && !isFinishing && !isDestroyed
    private fun normalizationLabel() = when (normalization) {
        TextNormalization.PRESERVE -> "保留空白"
        TextNormalization.TRIM_LINE_END -> "去行尾空白"
        TextNormalization.TRIM_LINE_EDGES -> "去两端空白"
    }
    private fun navigationCallback(): ReaderCallback<NavigationResult<ReadingPosition>> {
        val serial = sequence
        return ReaderCallback { completion ->
            if (current(serial)) ((completion as? ReaderCompletion.Completed)?.value as? NavigationResult.Failed)?.let { status.text = it.error.message }
        }
    }
    private fun stop() {
        sequence++
        reader.exportSnapshot()?.let { snapshot = it }
        val oldRequest = request; request = null
        val oldSource = source; source = null
        try { oldRequest?.cancel() } finally { try { reader.closeBook() } finally { oldSource?.close() } }
    }
    override fun onStart() { super.onStart(); started = true; reader.post(resume) }
    override fun onStop() {
        started = false; reader.removeCallbacks(resume)
        optionsDialog?.dismiss(); optionsDialog = null
        stop(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("disk", disk); outState.putBoolean("contentIds", contentIds)
        outState.putBoolean("reuseIndex", reuseIndex)
        outState.putString("normalization", normalization.name)
        (reader.exportSnapshot() ?: snapshot)?.let { outState.putBundle("textSnapshot", SnapshotState.write(it)) }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { if (::reader.isInitialized) { stop(); reader.release() }; super.onDestroy() }

    private fun sample() = buildString {
        append('\uFEFF')
        repeat(2) { chapter ->
            append("  Chapter ${chapter + 1} 文本来源  \r\n")
            repeat(18) { paragraph ->
                append("    第${chapter + 1}章第${paragraph + 1}段：行首缩进与行尾空白由来源策略决定。内存和磁盘使用共同解析器；位置指向输出文字，宿主选择内容标识并管理来源释放。  \r\n")
                if (paragraph % 5 == 4) append("  \r\n")
            }
        }
    }
    private object ContentIds : TextContentIdProvider {
        override val persistentIndexKey = "demo-text-content-ids:v1"
        // Unique headings/lines in this sample; real hosts must resolve duplicate-content identities.
        override fun chapterId(context: TextChapterIdentity) = "chapter:${hash(context.title)}"
        override fun blockId(context: TextBlockIdentity) = if (context.text.isBlank()) "blank:${context.blockNumber}" else "line:${hash(context.text)}"
        private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
