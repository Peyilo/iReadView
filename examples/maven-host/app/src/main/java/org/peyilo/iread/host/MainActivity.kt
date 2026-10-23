package org.peyilo.iread.host

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import org.peyilo.ireadview.api.event.ReaderListener
import org.peyilo.ireadview.api.model.ReaderSnapshot
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.widget.BookReadView
import org.peyilo.ireadview.widget.ReaderView

/** XML host; the host saves a lightweight snapshot and recreates its own source. */
class MainActivity : Activity() {
    private lateinit var reader: ReaderView
    private var started = false
    private var serial = 0L
    private var request: ReaderRequest? = null
    private var snapshot: ReaderSnapshot? = null
    private val resume = Runnable {
        if (!started) return@Runnable
        val current = ++serial
        val callback = ReaderCallback<OpenResult> { if (serial == current) request = null }
        val source = SampleBooks.create()
        request = snapshot?.let { reader.restoreSnapshot(source, it, callback) }
            ?: reader.openBook(source, callback = callback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        snapshot = SnapshotBundles.read(savedInstanceState?.getBundle("readerSnapshot"))
        setContentView(R.layout.activity_main)
        reader = findViewById(R.id.reader)
        HostContentStrategies.install(reader)
        val status = findViewById<TextView>(R.id.status)
        val previous = findViewById<Button>(R.id.previous)
        val next = findViewById<Button>(R.id.next)
        previous.isEnabled = false; next.isEnabled = false
        reader.addReaderListener(object : ReaderListener {
            override fun onStateChanged(view: BookReadView, state: ReaderState) {
                if (!started) return
                status.text = state.error?.message ?: state.position?.let { "第${it.pageNumberHint}页" } ?: "正在打开…"
                previous.isEnabled = state.navigationCapabilities.previous == NavigationAvailability.Available
                next.isEnabled = state.navigationCapabilities.next == NavigationAvailability.Available
            }
        })
        previous.setOnClickListener { if (started) reader.previousPage(NavigationOptions()) { } }
        next.setOnClickListener { if (started) reader.nextPage(NavigationOptions()) { } }
        findViewById<Button>(R.id.java_reader).setOnClickListener {
            if (started) startActivity(Intent(this, JavaReaderActivity::class.java))
        }
    }
    override fun onStart() { super.onStart(); started = true; reader.post(resume) }
    override fun onSaveInstanceState(outState: Bundle) {
        (reader.exportSnapshot() ?: snapshot)?.let { outState.putBundle("readerSnapshot", SnapshotBundles.write(it)) }
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        started = false; reader.removeCallbacks(resume)
        snapshot = reader.exportSnapshot() ?: snapshot
        serial++
        val old = request; request = null
        try { old?.cancel() } finally { reader.closeBook(); super.onStop() }
    }
    override fun onDestroy() { try { reader.release() } finally { super.onDestroy() } }
}
