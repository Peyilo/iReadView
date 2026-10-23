package org.peyilo.iread.epubhost

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.peyilo.ireadview.api.effect.PageTurnEffects
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.source.ReaderInput
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.epub.EpubReaderBookSource
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView
import java.io.File

/** Both engines come from Maven artifacts and share this single ReaderView instance. */
class MainActivity : Activity() {
    lateinit var reader: ReaderView
        private set
    private var started = false
    private var epub = true
    private var request: ReaderRequest? = null
    private var ownedSource: EpubReaderBookSource? = null
    private var snapshot: ReaderSnapshot? = null
    private var serial = 0L
    private val open = Runnable {
        if (!started) return@Runnable
        val sequence = ++serial
        val source = if (epub) EpubReaderBookSource("epub-maven", ReaderInput.fromAsset(this, "demo.epub"),
            File(cacheDir, "epub")).also { ownedSource = it } else textBook()
        val callback = ReaderCallback<OpenResult> { if (serial == sequence) request = null }
        request = snapshot?.takeIf { it.bookId == source.bookId }?.let { reader.restoreSnapshot(source, it, callback) }
            ?: reader.openBook(source, callback = callback)
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        epub = state?.getBoolean("epub", true) ?: true
        snapshot = state?.getBundle("snapshot")?.let(::readSnapshot)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        val status = TextView(this)
        val sources = LinearLayout(this)
        fun button(label: String, id: Int, action: () -> Unit) = Button(this).apply {
            text = label; this.id = id; setOnClickListener { action() }
        }
        sources.addView(button("普通文本", R.id.text_source) { select(false) }, LinearLayout.LayoutParams(0, -2, 1f))
        sources.addView(button("EPUB", R.id.epub_source) { select(true) }, LinearLayout.LayoutParams(0, -2, 1f))
        reader = ReaderView(this).apply { id = R.id.reader; setPageTurnEffect(PageTurnEffects.fromId("slide")!!) }
        reader.addReaderListener(object : ReaderListener {
            override fun onStateChanged(view: BookReadView, state: ReaderState) {
                status.text = state.error?.message ?: state.position?.let { "第${it.pageNumberHint}页" } ?: "正在打开…"
            }
        })
        val pages = LinearLayout(this)
        pages.addView(button("上一页", android.view.View.generateViewId()) { if (started) reader.previousPage(NavigationOptions()) { } }, LinearLayout.LayoutParams(0, -2, 1f))
        pages.addView(button("下一页", android.view.View.generateViewId()) { if (started) reader.nextPage(NavigationOptions()) { } }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(sources); root.addView(status)
        root.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(pages)
        setContentView(root)
    }
    private fun select(useEpub: Boolean) {
        if (!started || epub == useEpub) return
        stopReading(); snapshot = null; epub = useEpub; reader.post(open)
    }
    private fun stopReading() {
        serial++; reader.removeCallbacks(open)
        val previous = request; request = null
        try { previous?.cancel() }
        finally {
            try { reader.closeBook() }
            finally { ownedSource?.close(); ownedSource = null }
        }
    }
    override fun onStart() { super.onStart(); started = true; reader.post(open) }
    override fun onStop() {
        started = false; snapshot = reader.exportSnapshot() ?: snapshot
        try { stopReading() } finally { super.onStop() }
    }
    override fun onDestroy() { try { reader.release() } finally { super.onDestroy() } }
    override fun onSaveInstanceState(state: Bundle) {
        state.putBoolean("epub", epub)
        (reader.exportSnapshot() ?: snapshot)?.let { state.putBundle("snapshot", writeSnapshot(it)) }
        super.onSaveInstanceState(state)
    }
    private fun textBook(): MemoryBookSource {
        val book = BookDescriptor("text-maven", "普通文本", "1", listOf(TocEntry.Chapter(ChapterDescriptor("chapter", "示例", "1"))))
        return MemoryBookSource(book, listOf(ChapterContent(book.bookId, "chapter", "1",
            List(30) { TextBlock("p-$it", "来源、正文、选区和翻页通过公开接口组合。".repeat(20)) })))
    }
    private fun writeSnapshot(value: ReaderSnapshot) = Bundle().apply {
        val p = value.position
        putString("book", p.bookId); putString("chapter", p.chapterId); putString("revision", p.contentRevision)
        putString("block", p.anchor?.blockId); putInt("offset", p.anchor?.offset ?: 0)
        putString("locator", p.documentLocator); putInt("page", p.pageNumberHint ?: 1)
        putString("mode", value.modeId)
    }
    private fun readSnapshot(value: Bundle): ReaderSnapshot? = runCatching {
        val position = ReadingPosition(requireNotNull(value.getString("book")), requireNotNull(value.getString("chapter")),
            value.getString("block")?.let { ContentAnchor(it, value.getInt("offset")) }, value.getString("revision"),
            value.getInt("page", 1), documentLocator = value.getString("locator"))
        ReaderSnapshot(position.bookId, position, value.getString("mode") ?: "paged")
    }.getOrNull()
}
