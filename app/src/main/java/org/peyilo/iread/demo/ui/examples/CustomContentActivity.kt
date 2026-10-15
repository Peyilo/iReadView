package org.peyilo.iread.demo.ui.examples

import org.peyilo.iread.demo.R
import org.peyilo.iread.demo.content.AttributedTextBlock
import org.peyilo.iread.demo.content.DividerBlock
import org.peyilo.iread.demo.content.DividerLayoutProvider

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

/** Host text metadata uses the built-in layout; divider graphics use the content SPI. */
class CustomContentActivity : Activity() {
    private lateinit var reader: ReaderView
    private lateinit var status: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private var opening: ReaderRequest? = null
    private var started = false
    private var openSerial = 0L
    private val open = Runnable {
        if (started && reader.currentPosition == null && opening == null) {
            val serial = ++openSerial
            opening = reader.openBook(book()) { completion ->
                if (serial != openSerial) return@openBook
                opening = null
                if (started && !isFinishing && !isDestroyed) {
                    val result = (completion as? ReaderCompletion.Completed)?.value
                    if (result is OpenResult.Failed) status.text = result.error.message
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        status = TextView(this).apply { text = "自定义文本与图形；文本元数据沿用正文排版，图形不提供文字选区" }
        reader = ReaderView(this).apply {
            id = R.id.custom_content_reader
            setBlockLayoutProviders(listOf(DividerLayoutProvider()))
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { if (started && !isFinishing) action() }
        }
        previous = button("上一页") { reader.previousPage(NavigationOptions(), navigationCallback()) }
        next = button("下一页") { reader.nextPage(NavigationOptions(), navigationCallback()) }
        layout.addView(status)
        layout.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(LinearLayout(this).apply {
            listOf(previous, button("模式") {
                val target = if (reader.readingMode == ReadingMode.PAGED) ReadingMode.CONTINUOUS_SCROLL else ReadingMode.PAGED
                reader.setReadingMode(target) { completion ->
                    val result = (completion as? ReaderCompletion.Completed)?.value
                    if (started && result is ReaderResult.Failure) status.text = result.error.message
                }
            }, next, button("重试") { reader.post(open) }, button("退出") { finish() }).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
        setContentView(layout)
        reader.addReaderListener(object : ReaderListener {
            override fun onStateChanged(view: BookReadView, state: ReaderState) {
                previous.isEnabled = state.navigationCapabilities.previous == NavigationAvailability.Available
                next.isEnabled = state.navigationCapabilities.next == NavigationAvailability.Available
                status.text = state.error?.message ?: state.position?.let {
                    "自定义内容 · ${if (state.readingMode == ReadingMode.PAGED) "分页" else "滚动"} · 第${it.pageNumberHint}页"
                } ?: "正在准备自定义内容…"
            }
        })
        previous.isEnabled = false; next.isEnabled = false
    }

    private fun navigationCallback() = ReaderCallback<NavigationResult<ReadingPosition>> { completion ->
        val result = (completion as? ReaderCompletion.Completed)?.value
        if (started && result is NavigationResult.Failed) status.text = result.error.message
    }

    private fun book(): MemoryBookSource {
        val chapter = ChapterDescriptor("chapter", "宿主自定义正文块", "2")
        val book = BookDescriptor("demo-custom-content", "自定义内容示例", "2", listOf(TocEntry.Chapter(chapter)))
        val blocks = buildList<ContentBlock> {
            add(HeadingBlock("title", chapter.title))
            repeat(24) { index ->
                add(TextBlock("paragraph-$index", "正文仍由组件排版。中间的分隔图形由宿主内容块和布局提供者创建，使用相同分页、滚动和稳定位置约定。图形有无障碍描述，但不是可选择文字；跨过它的文字选区会按不支持的内容处理。"))
                if (index % 4 == 0) add(AttributedTextBlock(
                    "attributed-$index",
                    "这段文字属于宿主自定义文本块，携带来源标签，原文仍由组件排版。选择、标注、位置与辅助阅读共用同一字符串偏移；来源标签不会被追加到正文或改变位置。".repeat(8),
                    "host-section-$index"
                ))
                if (index % 4 == 3) add(DividerBlock("divider-$index", "段落分隔图形"))
            }
        }
        return MemoryBookSource(book, listOf(ChapterContent(book.bookId, chapter.chapterId, "2", blocks)))
    }

    override fun onStart() { super.onStart(); started = true; reader.post(open) }
    override fun onStop() {
        started = false; reader.removeCallbacks(open)
        openSerial++
        val old = opening; opening = null; old?.cancel()
        super.onStop()
    }
    override fun onDestroy() { reader.release(); super.onDestroy() }
}
