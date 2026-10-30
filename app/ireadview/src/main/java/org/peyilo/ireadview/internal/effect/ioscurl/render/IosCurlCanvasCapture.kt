package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.core.os.trace
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLongArray
import org.peyilo.ireadview.api.effect.TurnDirection
import org.peyilo.ireadview.internal.effect.EffectResourceUnavailable

/**
 * UI producers and EGL consumers meet only at BufferQueue fences. Destination pixels belong to the
 * turn; live RenderNodes and producer buffers are never sampled during its animation.
 */
@RequiresApi(23)
internal class IosCurlCanvasCapture(
    private val handler: Handler,
    width: Int,
    height: Int,
    private val destination: (Int, Int, Int) -> Int,
    private val restore: () -> Unit,
    private val active: () -> Boolean,
    private val onReady: () -> Unit,
    private val onFailure: (RuntimeException) -> Unit,
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val producerThread =
        HandlerThread("iRead-capture", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val producer = Handler(producerThread.looper)
    private val images = IntArray(2)
    private val framebuffer = IntArray(1)
    private val vertices = IntArray(1)
    private val planes = ArrayList<Plane>(2)
    private var program = 0
    private var matrixUniform = -1
    private val matrix = FloatArray(16)
    private var job: Job? = null
    @Volatile private var serial = 0L
    @Volatile private var closed = false
    @Volatile
    var ready = false
        private set

    private inner class Plane(index: Int, width: Int, height: Int) {
        val image =
            SurfaceTexture(images[index], true).apply {
                setDefaultBufferSize(width, height)
                setOnFrameAvailableListener({ acquire(index) }, handler)
            }
        val surface = Surface(image)
        var timestamp = -1L
        var warm = false
    }

    private class Job(
        val serial: Long,
        val width: Int,
        val height: Int,
        val current: () -> Boolean,
        val complete: () -> Unit,
    ) {
        val floor = AtomicLongArray(longArrayOf(Long.MAX_VALUE, Long.MAX_VALUE))
        var copied = 0
    }

    init {
        try {
            GLES20.glGenTextures(2, images, 0)
            repeat(2) { index ->
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, images[index])
                planes.add(Plane(index, width, height))
            }
            val v =
                compile(
                    GLES20.GL_VERTEX_SHADER,
                    "attribute vec2 position;attribute vec2 coord;uniform mat4 transform;varying highp vec2 uv;void main(){gl_Position=vec4(position,0.,1.);uv=(transform*vec4(coord.x,1.-coord.y,0.,1.)).xy;}",
                )
            val f =
                compile(
                    GLES20.GL_FRAGMENT_SHADER,
                    "#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform lowp samplerExternalOES page;varying highp vec2 uv;void main(){gl_FragColor=texture2D(page,uv);}",
                )
            try {
                program = GLES20.glCreateProgram()
                GLES20.glAttachShader(program, v)
                GLES20.glAttachShader(program, f)
                GLES20.glBindAttribLocation(program, 0, "position")
                GLES20.glBindAttribLocation(program, 1, "coord")
                GLES20.glLinkProgram(program)
                val status = IntArray(1)
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
                check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
                matrixUniform = GLES20.glGetUniformLocation(program, "transform")
                GLES20.glUseProgram(program)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "page"), 0)
            } finally {
                GLES20.glDeleteShader(v)
                GLES20.glDeleteShader(f)
            }
            val data =
                ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(
                        floatArrayOf(
                            -1f,
                            -1f,
                            0f,
                            0f,
                            1f,
                            -1f,
                            1f,
                            0f,
                            -1f,
                            1f,
                            0f,
                            1f,
                            1f,
                            1f,
                            1f,
                            1f,
                        )
                    )
                    position(0)
                }
            GLES20.glGenBuffers(1, vertices, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertices[0])
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 64, data, GLES20.GL_STATIC_DRAW)
            GLES20.glGenFramebuffers(1, framebuffer, 0)
        } catch (error: Throwable) {
            planes.forEach { it.surface.release() }
            close()
            throw error
        }
    }

    fun warm() = clearLater(serial)

    fun begin(input: IosCurlPageInput.Canvas, current: () -> Boolean, complete: () -> Unit) {
        val next = Job(++serial, input.width, input.height, current, complete)
        job = next
        planes.forEach { it.image.setDefaultBufferSize(input.width, input.height) }
        producer.post {
            main.post {
                if (!closed && active() && current() && next.serial == serial)
                    try {
                        trace("iRead.curl.canvasCapture") {
                            val context = input.context
                            val order =
                                if (context.direction == TurnDirection.Forward) intArrayOf(0, 1)
                                else intArrayOf(1, 0)
                            for (index in order) {
                                if (!current()) break
                                val surface = planes[index].surface
                                val source = if (index == 0) context.current else context.target
                                val canvas =
                                    trace("iRead.curl.canvasLock") { surface.lockHardwareCanvas() }
                                try {
                                    trace("iRead.curl.canvasRecord") {
                                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                        canvas.scale(
                                            input.width.toFloat() / context.viewport.widthPx,
                                            input.height.toFloat() / context.viewport.heightPx,
                                        )
                                        source.draw(canvas)
                                    }
                                } finally {
                                    next.floor.set(index, System.nanoTime())
                                    submit(surface, canvas)
                                }
                            }
                        }
                    } catch (error: RuntimeException) {
                        handler.post { if (current()) onFailure(error) }
                    } catch (_: OutOfMemoryError) {
                        handler.post { if (current()) onFailure(EffectResourceUnavailable()) }
                    }
            }
        }
    }

    private fun submit(surface: Surface, canvas: Canvas) {
        producer.post {
            try {
                trace("iRead.curl.canvasSubmit") { surface.unlockCanvasAndPost(canvas) }
            } catch (error: RuntimeException) {
                handler.post { if (active()) onFailure(error) }
            }
        }
    }

    private fun acquire(index: Int) {
        if (closed) return
        val plane = planes[index]
        var acquired = false
        var copied = false
        var completed: Job? = null
        try {
            plane.image.updateTexImage()
            acquired = true
            val timestamp = plane.image.timestamp
            if (timestamp <= plane.timestamp) return
            plane.timestamp = timestamp
            plane.warm = true
            val next = job
            if (
                next != null &&
                    next.current() &&
                    next.serial == serial &&
                    next.copied and (1 shl index) == 0 &&
                    timestamp >= next.floor.get(index)
            ) {
                trace("iRead.curl.canvasCopy") {
                    val texture = destination(index, next.width, next.height)
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
                    GLES20.glFramebufferTexture2D(
                        GLES20.GL_FRAMEBUFFER,
                        GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D,
                        texture,
                        0,
                    )
                    check(
                        GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) ==
                            GLES20.GL_FRAMEBUFFER_COMPLETE
                    )
                    GLES20.glViewport(0, 0, next.width, next.height)
                    GLES20.glDisable(GLES20.GL_BLEND)
                    GLES20.glUseProgram(program)
                    plane.image.getTransformMatrix(matrix)
                    GLES20.glUniformMatrix4fv(matrixUniform, 1, false, matrix, 0)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, images[index])
                    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertices[0])
                    GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 16, 0)
                    GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 16, 8)
                    GLES20.glEnableVertexAttribArray(0)
                    GLES20.glEnableVertexAttribArray(1)
                    GLES20.glDisableVertexAttribArray(2)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    check(GLES20.glGetError() == GLES20.GL_NO_ERROR)
                }
                copied = true
                next.copied = next.copied or (1 shl index)
                if (next.copied == 3) {
                    completed = next
                    job = null
                    clearLater(next.serial)
                }
            }
        } catch (error: RuntimeException) {
            onFailure(error)
        } catch (_: OutOfMemoryError) {
            onFailure(EffectResourceUnavailable())
        } finally {
            if (acquired)
                try {
                    plane.image.releaseTexImage()
                } catch (error: RuntimeException) {
                    onFailure(error)
                }
            if (copied) restore()
        }
        if (!ready && planes.all { it.warm }) {
            ready = true
            onReady()
        }
        completed?.takeIf { it.current() }?.complete?.invoke()
    }

    fun cancel() {
        job = null
        val token = ++serial
        clearLater(token)
    }

    private fun clearLater(token: Long, indices: IntArray = intArrayOf(0, 1)) {
        main.post {
            producer.post {
                if (!closed && active() && token == serial)
                    try {
                        indices.forEach { clear(planes[it].surface) }
                    } catch (error: RuntimeException) {
                        handler.post { if (!closed) onFailure(error) }
                    } catch (_: OutOfMemoryError) {
                        handler.post { if (!closed) onFailure(EffectResourceUnavailable()) }
                    }
            }
        }
    }

    private fun clear(surface: Surface) {
        val canvas = surface.lockHardwareCanvas()
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun compile(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            error(error)
        }
        return id
    }

    /** The main barrier ends recording; the producer queue then ends every pending submission. */
    fun closeProducers(complete: () -> Unit) {
        main.post {
            producer.post {
                try {
                    planes.forEach {
                        runCatching { clear(it.surface) }
                        runCatching { it.surface.release() }
                    }
                } finally {
                    handler.post(complete)
                }
            }
        }
    }

    /** The producer render contexts must be destroyed before their queues are abandoned. */
    override fun close() {
        closed = true
        ready = false
        job = null
        planes.forEach { it.image.release() }
        GLES20.glDeleteTextures(2, images, 0)
        GLES20.glDeleteBuffers(1, vertices, 0)
        GLES20.glDeleteFramebuffers(1, framebuffer, 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        producerThread.quitSafely()
    }
}
