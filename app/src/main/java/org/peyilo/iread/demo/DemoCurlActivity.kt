package org.peyilo.iread.demo

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.event.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.spi.page.*
import org.peyilo.ireadview.widget.ReaderView
import org.peyilo.ireadview.widget.BookReadView

/** Real Widget gestures, with interactive ordinary Views in both decoration slots. */
class DemoCurlActivity : Activity() {
    private lateinit var reader: ReaderView
    private lateinit var status: TextView
    private var opening: ReaderRequest? = null
    private var started = false
    private var serial = 0L
    private var modeChangeSerial = 0L
    private var decorationClicks = 0
    private var google = false
    private var customized = false
    private var strongGoogleCurl = false
    private var fullResolutionPreview = true
    private val open = Runnable {
        if (started && opening == null && reader.currentPosition == null) {
            val expected = ++serial
            opening = reader.openBook(book()) {
                if (expected != serial) return@openBook
                opening = null
                val result = (it as? ReaderCompletion.Completed)?.value
                if (result is OpenResult.Failed) status.text = result.error.message
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        status = TextView(this).apply { minLines = 2; maxLines = 2; text = "Apple：斜折边 · Google：波浪透视；从不同高度拖动，反向松手可复位" }
        reader = ReaderView(this)
        reader.addReaderListener(object : ReaderListener {
            override fun onEffectFallback(view: BookReadView, requestedId: String, appliedId: String,
                reason: EffectFallbackReason) {
                status.text = "翻页效果已回退：$requestedId → $appliedId（$reason）"
            }
            override fun onError(view: BookReadView, error: ReaderError) {
                status.text = error.message
            }
        })
        reader.setReaderActionHandler { _, action ->
            if (action == ReaderAction.ShowSettings) {
                status.text = "页眉按钮已响应 ${++decorationClicks} 次"
                true
            } else false
        }
        reader.setPageFactory(ReaderPageFactories.withDecorations(
            ReaderPageDecorationFactory { context ->
                val row = LinearLayout(context).apply { setPadding(20, 12, 20, 12) }
                val icon = ImageView(context).apply { setImageResource(android.R.drawable.ic_menu_compass); contentDescription = "自定义页眉图标" }
                val title = TextView(context).apply {
                    textSize = 16f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val action = Button(context).apply { text = "信息"; textSize = 12f }
                row.addView(icon, LinearLayout.LayoutParams(48, 48))
                row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(action, LinearLayout.LayoutParams(-2, -2))
                object : ReaderPageDecoration {
                    override val view: View = row
                    override fun bind(context: ReaderPageContext) {
                        val page = context.position?.pageNumberHint ?: 1
                        row.setBackgroundColor(if (page % 2 == 1) 0xffd8eaf4.toInt() else 0xffffe6b5.toInt())
                        title.text = "任意 View 页眉 · 第$page 页"
                        action.isEnabled = context.position != null
                        action.setOnClickListener { context.requestReaderAction(ReaderAction.ShowSettings) }
                    }
                    override fun unbind() { title.text = ""; action.setOnClickListener(null) }
                }
            },
            ReaderPageDecorationFactory { context ->
                val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 10, 24, 10) }
                val progress = SeekBar(context)
                val label = TextView(context)
                column.addView(progress, LinearLayout.LayoutParams(-1, -2))
                column.addView(label)
                object : ReaderPageDecoration {
                    override val view: View = column
                    override fun bind(context: ReaderPageContext) {
                        val page = context.position?.pageNumberHint ?: 1
                        column.setBackgroundColor(if (page % 2 == 1) 0xffd8eaf4.toInt() else 0xffffe6b5.toInt())
                        label.gravity = android.view.Gravity.END
                        progress.max = context.pageCount ?: 100
                        progress.progress = page
                        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                                if (fromUser) status.text = "页脚滑块已响应：$value（不改变阅读位置）"
                            }
                            override fun onStartTrackingTouch(bar: SeekBar) {}
                            override fun onStopTrackingTouch(bar: SeekBar) {}
                        })
                        label.text = "任意 View 页脚 · $page / ${context.pageCount ?: "…"}"
                    }
                    override fun unbind() { progress.setOnSeekBarChangeListener(null); label.text = ""; progress.progress = 0 }
                }
            }
        ))
        reader.setPageTurnEffect(PageTurnEffects.iBookCurl())
        if (intent.getBooleanExtra("pageCurl", false)) {
            reader.setPageTurnEffect(PageTurnEffects.pageCurl())
            reader.setPageTurnOptions(PageTurnOptions(durationMs = 650))
            status.text = "卷页：固定装订、平滑卷边；左右拖动，松手完成或复位"
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() } }
        fun applyCurl() {
            val baseOptions = if (!customized) CurlOptions() else CurlOptions(
                radiusFraction = 0.12f, shadowWidthDp = 30f, shadowAlpha = 0.35f,
                backTintColor = 0xffe0eff5.toInt(), backContentAlpha = 0.15f,
                controlPointPolicy = CurlControlPointPolicy { frame ->
                    val point = frame.defaultControlPoint
                    val originY = frame.touchOrigin?.yPx ?: frame.viewport.heightPx * 0.65f
                    val t = frame.sheetProgress
                    val eased = t * t * (3f - 2f * t)
                    TurnPoint(frame.viewport.widthPx * (1f - 2f * eased),
                        originY + (point.yPx - originY) * 0.5f)
                }
            )
            val options = if (google && strongGoogleCurl) baseOptions.copy(radiusFraction = 0.3f) else baseOptions
            reader.setPageTurnEffect(if (google) PageTurnEffects.googleCurl(options)
                else PageTurnEffects.iBookCurl(options))
            val name = if (google) "Google · 波浪透视${if (strongGoogleCurl) " · 强卷曲" else ""}"
                else "Apple · 装订锥面"
            status.text = "$name · ${if (customized) "自定义参数" else "默认参数"}${if (fullResolutionPreview) " · 清晰预览" else ""}"
        }
        val effects = LinearLayout(this)
        listOf(
            button("Apple") { google = false; applyCurl() },
            button("Google") { google = true; applyCurl() }.apply {
                setOnLongClickListener {
                    google = true; strongGoogleCurl = !strongGoogleCurl; applyCurl()
                    true
                }
            },
            button("滑动") { reader.setPageTurnEffect(PageTurnEffects.slide()); status.text = "滑动" },
            button("覆盖") { reader.setPageTurnEffect(PageTurnEffects.cover()); status.text = "覆盖" }
        ).forEach { effects.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        val configuration = LinearLayout(this)
        listOf(
            button("视差滑动") { reader.setPageTurnEffect(PageTurnEffects.iBookSlide()); status.text = "视差滑动" },
            button("无动画") { reader.setPageTurnEffect(PageTurnEffects.none()); status.text = "无动画" },
            button("卷页") {
                reader.setPageTurnEffect(PageTurnEffects.pageCurl())
                reader.setPageTurnOptions(PageTurnOptions(durationMs = 650))
                status.text = "卷页：左右拖动，松手完成或复位"
            },
            button("左右方向") {
                reader.layoutDirection = if (reader.layoutDirection == View.LAYOUT_DIRECTION_RTL) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
                status.text = if (reader.layoutDirection == View.LAYOUT_DIRECTION_RTL) "向右拖动前翻" else "向左拖动前翻"
            },
            button("仿真参数") { customized = !customized; applyCurl() }.apply {
                setOnLongClickListener {
                    val requested = !fullResolutionPreview
                    val result = reader.setPageTurnOptions(PageTurnOptions(
                        bitmapBudgetBytes = (if (requested) 16L else 8L) * 1024 * 1024))
                    if (result is ReaderResult.Success) {
                        fullResolutionPreview = requested
                        applyCurl()
                    } else status.text = "画质切换未完成，请稍后重试"
                    true
                }
            }
        ).forEach { configuration.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        val navigation = LinearLayout(this)
        listOf(button("上一页") { reader.previousPage(NavigationOptions()) {} },
            button("下一页") { reader.nextPage(NavigationOptions()) {} },
            button("滚动/分页") {
                val expected = ++modeChangeSerial
                val mode = if (reader.readingMode == ReadingMode.PAGED) ReadingMode.CONTINUOUS_SCROLL else ReadingMode.PAGED
                reader.setReadingMode(mode) { completion ->
                    if (!started || expected != modeChangeSerial) return@setReadingMode
                    status.text = when (completion) {
                        ReaderCompletion.Cancelled -> "阅读模式切换已取消"
                        is ReaderCompletion.Completed -> when (val result = completion.value) {
                            is ReaderResult.Success -> if (result.value.to == ReadingMode.PAGED) "分页模式" else "连续滚动模式"
                            is ReaderResult.Rejected -> if (result.reason == ReaderRejection.Busy)
                                "翻页尚未结束，请松手后切换" else "暂时无法切换阅读模式"
                            is ReaderResult.Failure -> "阅读模式切换失败：${result.error.message}"
                            ReaderResult.Superseded -> "阅读模式切换已被新操作替代"
                        }
                    }
                }
            }, button("退出") { finish() }).forEach { navigation.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        // Fixed space: status text and font fallback must not resize the reading viewport.
        layout.addView(status, LinearLayout.LayoutParams(-1, (48f * resources.displayMetrics.density).toInt()))
        layout.addView(effects)
        layout.addView(configuration)
        layout.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(navigation)
        setContentView(layout)
    }
    private fun book(): MemoryBookSource {
        val chapter = ChapterDescriptor("curl", "翻页与页眉页脚", "1")
        val descriptor = BookDescriptor("demo-curl", "翻页观察", "1", listOf(TocEntry.Chapter(chapter)))
        val blocks = (1..80).map { TextBlock("p$it", "段落 $it。仿真翻页的控制点取决于手势起始落点【$it】。可以从顶部、中央、底部开始【$it】，向前或向后翻页，拖动不足再松手检查复位【$it】。页眉的图标与页脚的进度条是普通 Android View，并随页面一起绘制【$it】。") }
        return MemoryBookSource(descriptor, listOf(ChapterContent(descriptor.bookId, chapter.chapterId, "1", blocks)))
    }
    override fun onStart() { super.onStart(); started = true; reader.post(open) }
    override fun onStop() {
        started = false; serial++; modeChangeSerial++; reader.removeCallbacks(open)
        val request = opening; opening = null
        try { request?.cancel() } finally { reader.closeBook(); super.onStop() }
    }
    override fun onDestroy() { try { reader.release() } finally { super.onDestroy() } }
}


