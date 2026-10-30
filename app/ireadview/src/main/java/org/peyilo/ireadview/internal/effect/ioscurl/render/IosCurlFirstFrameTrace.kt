package org.peyilo.ireadview.internal.effect.ioscurl.render

import androidx.core.os.TraceCompat
import androidx.core.os.trace
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Spans UI recording, producer submission and EGL presentation, not just one thread's work. */
@Suppress("DEPRECATION")
internal class IosCurlFirstFrameTrace {
    private val cookie = sequence.incrementAndGet()
    private val closed = AtomicBoolean()

    init {
        TraceCompat.beginAsyncSection(NAME, cookie)
    }

    fun submitted() = finish("firstSubmit")

    fun cancel() = finish("cancelledStartup")

    private fun finish(outcome: String) {
        if (closed.compareAndSet(false, true)) {
            trace("iRead.curl.$outcome:$cookie") {}
            TraceCompat.endAsyncSection(NAME, cookie)
        }
    }

    private companion object {
        const val NAME = "iRead.curl.startup"
        val sequence = AtomicInteger()
    }
}
