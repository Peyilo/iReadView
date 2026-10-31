package org.peyilo.ireadview.epub

import android.annotation.TargetApi
import android.net.Uri
import android.os.Build
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebView
import java.util.UUID

/** Document changes are distinct from Chromium's presentation-only View invalidations. */
internal class EpubVisualChanges(private val web: WebView, private val changed: () -> Unit) {
    companion object {
        val supported
            get() = Build.VERSION.SDK_INT in 26..28
    }

    private var signal = ""
    private var session: Session? = null
    var canRetainRecording = false
        private set

    fun begin(): String {
        clear()
        signal = if (supported) "iread-visual-${UUID.randomUUID()}" else ""
        return signal
    }

    fun connect(origin: Uri) {
        if (!supported) return
        val token = signal
        session =
            Session(web, origin, token) { stable ->
                if (signal == token) {
                    canRetainRecording = stable
                    changed()
                }
            }
    }

    fun clear() {
        signal = ""
        canRetainRecording = false
        if (Build.VERSION.SDK_INT >= 26) session?.close()
        session = null
    }

    @TargetApi(26)
    private class Session(web: WebView, origin: Uri, signal: String, changed: (Boolean) -> Unit) {
        private val port: WebMessagePort

        init {
            val channel = web.createWebMessageChannel()
            port = channel[0]
            port.setWebMessageCallback(
                object : WebMessagePort.WebMessageCallback() {
                    override fun onMessage(port: WebMessagePort, message: WebMessage) {
                        when (message.data) {
                            "0" -> changed(false)
                            "1" -> changed(true)
                        }
                    }
                }
            )
            web.postWebMessage(WebMessage(signal, arrayOf(channel[1])), origin)
        }

        fun close() = port.close()
    }
}
