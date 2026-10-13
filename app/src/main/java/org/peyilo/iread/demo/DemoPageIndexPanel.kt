package org.peyilo.iread.demo

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

/** Owns the host's index windows and requests; the Widget owns index validity. */
internal class DemoPageIndexPanel(
    private val activity: Activity,
    private val reader: ReaderView,
    private val scope: CoroutineScope,
    private val result: (NavigationResult<ReadingPosition>) -> Unit,
    private val message: (String) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var listener: ReaderListener? = null
    private var job: Job? = null
    private var serial = 0L

    fun dismiss() {
        serial++
        job?.cancel()
        job = null
        closeWindow()
    }

    private fun closeWindow() {
        listener?.let(reader::removeReaderListener)
        listener = null
        val previous = dialog
        dialog = null
        previous?.setOnDismissListener(null)
        previous?.dismiss()
    }

    private fun alive(book: BookDescriptor) =
        !activity.isFinishing && !activity.isDestroyed && reader.currentBook === book

    private fun display(next: AlertDialog, observer: ReaderListener) {
        closeWindow()
        dialog = next
        listener = observer
        reader.addReaderListener(observer)
        next.setOnDismissListener { if (dialog === next) dismiss() }
        next.show()
    }

    fun show() {
        dismiss()
        val book = reader.currentBook ?: return
        if (!alive(book)) return
        reader.bookPageIndex?.let { showPages(book, it); return }
        val token = serial
        val progress = AlertDialog.Builder(activity).setTitle("计算全书页数")
            .setMessage("正在逐章计算…").setNegativeButton("取消", null).create()
        display(progress, object : ReaderListener {
            override fun onBookChanged(view: BookReadView, book: BookDescriptor?) { dismiss() }
            override fun onBookPageIndexChanged(view: BookReadView, state: BookPageIndexState) {
                if (dialog === progress && alive(book) && state is BookPageIndexState.Building) {
                    progress.setMessage("已计算 ${state.completedChapters}/${state.chapterCount} 章")
                }
            }
        })
        start(token) {
            val built = reader.buildBookPageIndex()
            if (serial != token || !alive(book) || dialog !== progress) return@start
            closeWindow()
            when (built) {
                is ReaderResult.Success -> if (reader.bookPageIndex === built.value) showPages(book, built.value)
                    else message("版式或内容已更新，请重新计算")
                is ReaderResult.Failure -> message(built.error.message)
                is ReaderResult.Rejected -> message(built.reason.name)
                ReaderResult.Superseded -> message("版式或内容已更新，请重新计算")
            }
        }
    }

    private fun showPages(book: BookDescriptor, index: BookPageIndex) {
        if (!alive(book) || reader.bookPageIndex !== index) return
        if (index.totalPageCount == 0L) { message("目录为空"); return }
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1～${index.totalPageCount}"
            setText((reader.readerState.globalPageNumber ?: 1L).toString())
            selectAll()
        }
        val next = AlertDialog.Builder(activity).setTitle("全书页 · 共 ${index.totalPageCount} 页")
            .setView(input).setPositiveButton("跳转", null).setNegativeButton("取消", null).create()
        display(next, object : ReaderListener {
            override fun onBookChanged(view: BookReadView, book: BookDescriptor?) { dismiss() }
            override fun onBookPageIndexChanged(view: BookReadView, state: BookPageIndexState) {
                if (dialog === next && (!alive(book) || reader.bookPageIndex !== index)) dismiss()
            }
        })
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (dialog !== next || !alive(book) || reader.bookPageIndex !== index) { dismiss(); return@setOnClickListener }
            val page = input.text.toString().toLongOrNull()
            if (page == null || page !in 1..index.totalPageCount) {
                input.error = "请输入有效页号"
                return@setOnClickListener
            }
            dismiss()
            val token = serial
            start(token) {
                if (!alive(book) || reader.bookPageIndex !== index) return@start
                val navigated = reader.goToGlobalPage(page)
                if (serial == token && alive(book)) result(navigated)
            }
        }
    }

    private fun start(token: Long, action: suspend () -> Unit) {
        // Assign before execution so even immediate completion clears the owned job.
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try { if (serial == token) action() }
            finally { if (serial == token) job = null }
        }
        job = next
        next.start()
    }
}
