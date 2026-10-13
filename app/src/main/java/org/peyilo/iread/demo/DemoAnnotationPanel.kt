package org.peyilo.iread.demo

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.widget.ReaderView
import java.util.UUID

/** Host annotation browser. Stale windows never edit a replacement book or annotation. */
internal class DemoAnnotationPanel(
    private val activity: Activity,
    private val reader: ReaderView,
    private val scope: CoroutineScope,
    private val result: (NavigationResult<ReadingPosition>) -> Unit,
    private val addExample: () -> Unit
) {
    private var dialog: AlertDialog? = null
    private var navigation: Job? = null
    private var serial = 0L
    fun dismiss() { serial++; navigation?.cancel(); navigation = null; dialog?.dismiss(); dialog = null }
    private fun alive(book: BookDescriptor) = !activity.isFinishing && !activity.isDestroyed && reader.currentBook === book

    fun show() {
        val book = reader.currentBook ?: return
        val snapshot = reader.exportAnnotations() ?: return
        val entries = arrayOf("书签（${snapshot.bookmarks.size}）", "高亮（${snapshot.highlights.size}）", "添加当前位置书签", "添加示例高亮")
        val next = AlertDialog.Builder(activity).setTitle("书签与高亮").setItems(entries) { selected, index ->
            if (!alive(book) || dialog !== selected) return@setItems
            when (index) {
                0 -> bookmarks(book)
                1 -> highlights(book)
                2 -> reader.currentPosition?.takeIf { it.bookId == book.bookId }?.let {
                    report(reader.addBookmark(Bookmark(UUID.randomUUID().toString(), it, "${reader.currentChapter?.title} · 第${it.pageNumberHint}页")))
                }
                3 -> addExample()
            }
        }.setNegativeButton("取消", null).create()
        display(next)
    }

    private fun bookmarks(book: BookDescriptor) {
        val entries = reader.exportAnnotations()?.bookmarks ?: return
        if (entries.isEmpty()) { message("还没有书签"); return }
        display(AlertDialog.Builder(activity).setTitle("书签").setItems(entries.map { bookmark ->
            "${bookmark.label ?: bookmark.position.chapterId} · ${status(bookmark.id)}"
        }.toTypedArray()) { selected, index ->
            if (!alive(book) || dialog !== selected) return@setItems
            val bookmark = entries[index]
            fun unchanged() = alive(book) && reader.exportAnnotations()?.bookmarks?.any { it == bookmark } == true
            val details = AlertDialog.Builder(activity).setTitle(bookmark.label ?: "书签").setMessage(status(bookmark.id))
                .setPositiveButton("跳转") { selectedDetails, _ ->
                    if (dialog === selectedDetails && unchanged()) navigate(book) {
                        if (unchanged()) reader.goToBookmark(bookmark.id) else NavigationResult.Rejected(ReaderRejection.UnknownAnnotation)
                    }
                }.setNeutralButton("删除") { selectedDetails, _ ->
                    if (dialog === selectedDetails && unchanged()) report(reader.removeBookmark(bookmark.id))
                }.setNegativeButton("取消", null).create()
            display(details)
        }.setNegativeButton("取消", null).create())
    }

    private fun highlights(book: BookDescriptor) {
        val entries = reader.exportAnnotations()?.highlights ?: return
        if (entries.isEmpty()) { message("还没有高亮，请长按正文选择文字"); return }
        display(AlertDialog.Builder(activity).setTitle("高亮").setItems(entries.map {
            "${preview(it.quote)} · ${status(it.id)}"
        }.toTypedArray()) { selected, index ->
            if (alive(book) && dialog === selected) showHighlight(entries[index].id)
        }.setNegativeButton("取消", null).create())
    }

    fun showHighlight(id: String) {
        val book = reader.currentBook ?: return
        val highlight = reader.exportAnnotations()?.highlights?.firstOrNull { it.id == id } ?: return
        val annotationStatus = reader.getAnnotationStatus(id)
        fun unchanged() = alive(book) && reader.exportAnnotations()?.highlights?.any { it == highlight } == true
        // Details are bounded in the UI; the original quote in the model is never shortened.
        val quote = highlight.quote.take(4096).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
        val next = AlertDialog.Builder(activity).setTitle("高亮 · ${status(id)}")
            .setMessage(quote + if (quote.length < highlight.quote.length) "\n…（这里只显示摘录前部）" else "")
            .setNeutralButton("删除") { selected, _ -> if (dialog === selected && unchanged()) report(reader.removeHighlight(id)) }
            .setNegativeButton("颜色") { selected, _ -> if (dialog === selected && unchanged()) colors(book, highlight) }
        when (annotationStatus) {
            AnnotationStatus.Valid -> next.setPositiveButton("跳转") { selected, _ ->
                if (dialog === selected && unchanged() && reader.getAnnotationStatus(id) == AnnotationStatus.Valid) navigate(book) {
                    if (!unchanged() || reader.getAnnotationStatus(id) != AnnotationStatus.Valid) NavigationResult.Rejected(ReaderRejection.UnknownAnnotation)
                    else reader.goToPosition(ReadingPosition(book.bookId, highlight.range.chapterId, highlight.range.start, highlight.range.contentRevision))
                }
            }
            AnnotationStatus.PendingValidation -> next.setPositiveButton("打开章节校验") { selected, _ ->
                if (dialog === selected && unchanged()) navigate(book, after = { if (unchanged()) showHighlight(id) }) {
                    if (unchanged()) reader.goToPosition(ReadingPosition(book.bookId, highlight.range.chapterId, pageNumberHint = 1))
                    else NavigationResult.Rejected(ReaderRejection.UnknownAnnotation)
                }
            }
            else -> next.setPositiveButton("关闭", null)
        }
        display(next.create())
    }

    private fun colors(book: BookDescriptor, highlight: Highlight) {
        val ids = reader.style.highlightStyles.styles.keys.toList()
        display(AlertDialog.Builder(activity).setTitle("高亮颜色").setSingleChoiceItems(ids.map {
            when (it) { "default" -> "默认"; "blue" -> "蓝色"; else -> it }
        }.toTypedArray(), ids.indexOf(highlight.styleId)) { selected, index ->
            if (dialog !== selected || !alive(book)) return@setSingleChoiceItems
            if (reader.exportAnnotations()?.highlights?.any { it == highlight } != true) { message("标注已更新，请重新打开"); selected.dismiss(); return@setSingleChoiceItems }
            report(reader.updateHighlight(highlight.copy(styleId = ids[index]))); selected.dismiss()
        }.setNegativeButton("取消", null).create())
    }

    private fun navigate(book: BookDescriptor, after: (() -> Unit)? = null, action: suspend () -> NavigationResult<ReadingPosition>) {
        dialog?.dismiss(); dialog = null; navigation?.cancel()
        val sequence = ++serial
        navigation = scope.launch {
            try {
                if (!alive(book) || sequence != serial) return@launch
                val outcome = action()
                if (alive(book) && sequence == serial) {
                    result(outcome)
                    if (outcome is NavigationResult.Moved || outcome is NavigationResult.Unchanged) after?.invoke()
                }
            } finally { if (sequence == serial) navigation = null }
        }
    }
    private fun status(id: String) = when (val status = reader.getAnnotationStatus(id)) {
        AnnotationStatus.Valid -> "有效"
        AnnotationStatus.PendingValidation -> "尚未校验"
        is AnnotationStatus.Invalid -> when (status.reason) {
            AnnotationInvalidReason.UnknownChapter -> "章节已移除"
            AnnotationInvalidReason.RevisionMismatch -> "内容版本已变化"
            AnnotationInvalidReason.InvalidRange -> "文字范围失效"
            AnnotationInvalidReason.QuoteMismatch -> "摘录与正文不符"
            AnnotationInvalidReason.UnsupportedContent -> "内容不支持校验"
        }
        null -> "标注已移除"
    }
    private fun preview(text: String): String = text.take(80).replace('\r', ' ').replace('\n', ' ').let {
        val prefix = if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it
        prefix + if (prefix.length < text.length) "…" else ""
    }
    private fun report(result: ReaderResult<*>) {
        when (result) {
            is ReaderResult.Failure -> message(result.error.message)
            is ReaderResult.Rejected -> message("当前无法修改标注：${result.reason}")
            else -> Unit
        }
    }
    private fun message(text: String) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
    private fun display(next: AlertDialog) {
        if (activity.isFinishing || activity.isDestroyed) return
        dialog?.dismiss(); dialog = next
        next.setOnDismissListener { if (dialog === next) dialog = null }
        next.show()
    }
}
