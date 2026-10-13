package org.peyilo.iread.demo

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.widget.ReaderView

/** Non-modal host overlay: toggling controls never changes the measured reader viewport. */
internal class DemoReaderControlPanel(
    private val activity: Activity,
    private val reader: ReaderView,
    private val scope: CoroutineScope,
    private val contents: () -> Unit,
    private val typography: () -> Unit,
    private val settings: () -> Unit,
    private val result: (NavigationResult<ReadingPosition>) -> Unit
) {
    private data class Target(val book: BookDescriptor, val chapterId: String, val revision: String?, val layoutKey: String, val count: Int)
    private val parent = activity.findViewById<FrameLayout>(android.R.id.content)
    private val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(0xfff7f0e5.toInt())
        val padding = (12 * resources.displayMetrics.density).toInt(); setPadding(padding, padding, padding, padding)
    }
    private val root = ScrollView(activity).apply { addView(panel); elevation = 12 * resources.displayMetrics.density }
    private val label = TextView(activity).apply { setTextColor(0xff302c28.toInt()) }
    private val seek = SeekBar(activity).apply { contentDescription = "章节页进度" }
    private lateinit var contentsButton: Button
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var pageInput: Button
    private var dragTarget: Target? = null
    private var inputDialog: AlertDialog? = null
    private var inputTarget: Target? = null
    private var navigation: Job? = null
    private var navigationSequence = 0L
    private var backCallback: Any? = null
    val isShowing get() = root.parent === parent

    init {
        contentsButton = button("目录") { hide(); contents() }
        row(contentsButton, button("排版") { hide(); typography() }, button("设置") { hide(); settings() }, button("收起") { hide() })
        previous = button("上一章") { adjacent(-1) }
        next = button("下一章") { adjacent(1) }
        pageInput = button("输入页号") { editPage() }
        row(previous, next, pageInput)
        panel.addView(label); panel.addView(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { dragTarget = target() }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val captured = dragTarget
                if (captured != null) label.text = "目标第 ${progress.toLong() + 1} / ${captured.count} 页"
                else target()?.let { goToPage(it, progress + 1) } // Keyboard/accessibility updates have no touch tracking callbacks.
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val captured = dragTarget; dragTarget = null
                if (captured != null) goToPage(captured, seekBar.progress + 1) else refresh(reader.readerState)
            }
        })
    }

    fun toggle() { if (isShowing) hide() else show() }
    private fun show() {
        if (activity.isFinishing || activity.isDestroyed || isShowing) return
        val height = minOf((240 * root.resources.displayMetrics.density).toInt(), (parent.height * 0.55f).toInt()).coerceAtLeast(1)
        parent.addView(root, FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM))
        if (android.os.Build.VERSION.SDK_INT >= 33) backCallback = ModernBack.register(activity, ::hide)
        refresh(reader.readerState)
    }
    fun hide() {
        if (android.os.Build.VERSION.SDK_INT >= 33) backCallback?.let { ModernBack.unregister(activity, it) }
        backCallback = null
        navigationSequence++
        parent.removeView(root)
        dragTarget = null; inputTarget = null; inputDialog?.dismiss(); inputDialog = null
        navigation?.cancel(); navigation = null
    }

    fun refresh(state: ReaderState) {
        if (!isShowing) return
        val book = reader.currentBook
        val position = state.position
        val index = book?.chapters?.indexOfFirst { it.chapterId == position?.chapterId } ?: -1
        val ready = state.phase == ReaderPhase.Ready && state.navigationState == ReadNavigationState.Idle && position != null && navigation?.isActive != true
        contentsButton.isEnabled = ready && book?.chapters?.isNotEmpty() == true
        previous.isEnabled = ready && index > 0
        next.isEnabled = ready && index >= 0 && index + 1 < (book?.chapters?.size ?: 0)
        val current = target()
        if (dragTarget != null && dragTarget != current) dragTarget = null
        if (inputTarget != null && inputTarget != current) { inputDialog?.dismiss(); inputDialog = null; inputTarget = null }
        seek.isEnabled = ready && current != null && current.count > 1
        pageInput.isEnabled = ready && current != null
        if (dragTarget == null) {
            seek.max = (current?.count ?: 1) - 1
            seek.progress = ((position?.pageNumberHint ?: 1) - 1).coerceIn(0, seek.max)
            label.text = "${reader.currentChapter?.title ?: "尚未准备章节"} · 第 ${position?.pageNumberHint ?: "…"} / ${current?.count ?: "…"} 页"
        }
    }

    private fun target(): Target? {
        val state = reader.readerState
        if (state.phase != ReaderPhase.Ready || state.navigationState != ReadNavigationState.Idle) return null
        val position = state.position ?: return null
        val book = reader.currentBook ?: return null
        val pagination = reader.getChapterPagination(position.chapterId) as? ChapterPaginationState.Ready ?: return null
        if (pagination.pageCount < 1) return null
        return Target(book, position.chapterId, position.contentRevision, pagination.layoutKey, pagination.pageCount)
    }
    private fun valid(captured: Target): Boolean {
        val current = target() ?: return false
        return isShowing && reader.isEnabled && current.book === captured.book && current == captured
    }
    private fun adjacent(delta: Int) {
        val captured = target() ?: return
        val index = captured.book.chapters.indexOfFirst { it.chapterId == captured.chapterId }
        val chapter = captured.book.chapters.getOrNull(index + delta) ?: return
        navigate(captured, ReadingPosition(captured.book.bookId, chapter.chapterId, pageNumberHint = 1))
    }
    private fun goToPage(captured: Target, page: Int) {
        if (page !in 1..captured.count) return
        navigate(captured, ReadingPosition(captured.book.bookId, captured.chapterId, contentRevision = captured.revision, pageNumberHint = page))
    }
    private fun navigate(captured: Target, position: ReadingPosition) {
        if (!valid(captured) || navigation?.isActive == true) { refresh(reader.readerState); return }
        val sequence = ++navigationSequence
        navigation = scope.launch {
            try {
                // Recheck inside the dispatched coroutine, before entering the suspend Widget API.
                if (!valid(captured)) return@launch
                val outcome = reader.goToPosition(position)
                if (sequence == navigationSequence && isShowing && reader.currentBook === captured.book && !activity.isFinishing && !activity.isDestroyed) result(outcome)
            } finally { if (sequence == navigationSequence) { navigation = null; refresh(reader.readerState) } }
        }
        refresh(reader.readerState)
    }

    private fun editPage() {
        val captured = target() ?: return
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; contentDescription = "章节页号，1至${captured.count}"
            setText((reader.currentPosition?.pageNumberHint ?: 1).toString()); selectAll()
        }
        val editor = AlertDialog.Builder(activity).setTitle("章节页 · 共${captured.count}页").setView(input)
            .setPositiveButton("跳转", null).setNegativeButton("取消", null).create()
        inputDialog?.dismiss(); inputDialog = editor; inputTarget = captured
        editor.setOnDismissListener { if (inputDialog === editor) { inputDialog = null; inputTarget = null } }
        editor.show()
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (inputDialog !== editor || !valid(captured)) { editor.dismiss(); return@setOnClickListener }
            val page = input.text.toString().toIntOrNull()
            if (page == null || page !in 1..captured.count) input.error = "请输入1至${captured.count}的页号"
            else { editor.dismiss(); goToPage(captured, page) }
        }
    }
    private fun button(text: String, action: () -> Unit) = Button(activity).apply {
        this.text = text
        setOnClickListener { if (isShowing && !activity.isFinishing && !activity.isDestroyed) action() }
    }
    private fun row(vararg buttons: Button) {
        panel.addView(LinearLayout(activity).apply { buttons.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) } })
    }

    @androidx.annotation.RequiresApi(33)
    private object ModernBack {
        fun register(activity: Activity, hide: () -> Unit): Any {
            val callback = android.window.OnBackInvokedCallback { hide() }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            return callback
        }
        fun unregister(activity: Activity, callback: Any) {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback as android.window.OnBackInvokedCallback)
        }
    }
}
