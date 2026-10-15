package org.peyilo.iread.demo.ui.reader.menu

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.peyilo.iread.demo.R
import org.peyilo.ireadview.api.model.BookDescriptor
import org.peyilo.ireadview.api.model.ReadingPosition
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.widget.ReaderView

/** Owns the reference-style menu window and chapter requests; layout lives in XML. */
internal class ReaderMenuController(
    private val activity: Activity,
    private val reader: ReaderView,
    private val scope: CoroutineScope,
    private val contents: () -> Unit,
    private val typography: () -> Unit,
    private val settings: () -> Unit,
    private val result: (NavigationResult<ReadingPosition>) -> Unit
) {
    private class MenuViews(val root: View) {
        val previous: Button = root.findViewById(R.id.reader_menu_previous_chapter)
        val next: Button = root.findViewById(R.id.reader_menu_next_chapter)
        val progress: ChapterProgressView = root.findViewById(R.id.reader_menu_chapter_progress)
        val contents: Button = root.findViewById(R.id.reader_menu_contents)
    }
    private var dialog: BottomSheetDialog? = null
    private var views: MenuViews? = null
    private var dragBook: BookDescriptor? = null
    private var navigation: Job? = null
    private var sequence = 0L
    val isShowing get() = dialog?.isShowing == true

    fun toggle() { if (isShowing) hide() else show() }
    private fun show() {
        if (isShowing || activity.isFinishing || activity.isDestroyed || reader.currentBook == null) return
        val context = ReaderBottomSheets.context(activity)
        val binding = MenuViews(LayoutInflater.from(context).inflate(R.layout.view_reader_menu, null))
        val window = ReaderBottomSheets.show(binding.root)
        dialog = window; views = binding
        binding.previous.setOnClickListener { adjacent(-1) }
        binding.next.setOnClickListener { adjacent(1) }
        binding.contents.setOnClickListener { hide(); contents() }
        binding.root.findViewById<View>(R.id.reader_menu_typography).setOnClickListener { hide(); typography() }
        binding.root.findViewById<View>(R.id.reader_menu_settings).setOnClickListener { hide(); settings() }
        binding.progress.onInteractionStarted = { dragBook = readyBook() }
        binding.progress.onChapterSelected = { index -> (dragBook ?: readyBook())?.let { navigate(it, index) } }
        binding.progress.onInteractionFinished = { dragBook = null; refresh(reader.readerState) }
        window.setOnDismissListener {
            if (dialog === window) {
                sequence++; navigation?.cancel(); navigation = null
                dialog = null; views = null; dragBook = null
                binding.progress.cancelInteraction()
            }
        }
        binding.progress.chapterCount = reader.currentBook?.chapters?.size ?: 0
        binding.progress.setChapterIndex(currentIndex(), resetReference = true)
        refresh(reader.readerState)
    }
    fun hide() { dialog?.dismiss() }
    fun refresh(state: ReaderState) {
        val binding = views ?: return
        val book = reader.currentBook
        val index = currentIndex()
        val ready = state.phase == ReaderPhase.Ready && state.navigationState == ReadNavigationState.Idle && navigation?.isActive != true
        binding.previous.isEnabled = ready && index > 0
        binding.next.isEnabled = ready && index >= 0 && index + 1 < (book?.chapters?.size ?: 0)
        binding.contents.isEnabled = ready && book?.chapters?.isNotEmpty() == true
        binding.progress.isEnabled = ready && (book?.chapters?.size ?: 0) > 1
        if (dragBook != null && dragBook !== book) binding.progress.cancelInteraction()
        if (!binding.progress.isInteracting && navigation?.isActive != true) {
            binding.progress.chapterCount = book?.chapters?.size ?: 0
            binding.progress.setChapterIndex(index)
        }
    }
    private fun currentIndex() = reader.currentBook?.chapters?.indexOfFirst { it.chapterId == reader.currentPosition?.chapterId } ?: -1
    private fun readyBook(): BookDescriptor? = reader.currentBook?.takeIf {
        isShowing && reader.isEnabled && reader.readerState.phase == ReaderPhase.Ready &&
            reader.readerState.navigationState == ReadNavigationState.Idle && navigation?.isActive != true
    }
    private fun adjacent(delta: Int) { readyBook()?.let { navigate(it, currentIndex() + delta) } }
    private fun navigate(book: BookDescriptor, index: Int) {
        val chapter = book.chapters.getOrNull(index) ?: return
        if (readyBook() !== book) { refresh(reader.readerState); return }
        val expected = ++sequence
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!isShowing || reader.currentBook !== book || activity.isFinishing || activity.isDestroyed) return@launch
                val outcome = reader.goToChapter(chapter.chapterId, 1)
                if (expected == sequence && isShowing && reader.currentBook === book) result(outcome)
            } finally {
                if (expected == sequence) { navigation = null; refresh(reader.readerState) }
            }
        }
        navigation = job; job.start(); refresh(reader.readerState)
    }
}
