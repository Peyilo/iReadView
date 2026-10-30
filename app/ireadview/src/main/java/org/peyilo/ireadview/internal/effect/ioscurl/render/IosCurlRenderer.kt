package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.graphics.Bitmap
import android.opengl.*
import android.view.Surface
import androidx.core.os.trace
import java.nio.*
import org.peyilo.pagecurl.mesh.*
import org.peyilo.pagecurl.model.CurlRect

/**
 * EGL raster adapter for the core's 0/3/12 mesh programs. One owner/thread; output is either an
 * Android Surface or the software host's bitmap. Navigation and animation clocks stay in the Widget.
 */
internal class IosCurlRenderer(private val retainContext: Boolean = false) : AutoCloseable {
    private val display = sharedDisplay
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private val programs = IntArray(3)
    private val textures = IosCurlPageTextures()
    private val matrices = IntArray(3)
    private val texSizes = IntArray(3)
    private var vertexBuffer: IosCurlVertexBuffer? = null
    private var boundProgram = -1
    private var boundTexture = -1
    private var blending = false
    private var logicalWidth = 0f
    private var logicalHeight = 0f
    private var textureWidth = 0f
    private var textureHeight = 0f
    private var readback: ByteBuffer? = null
    private var output: Bitmap? = null
    private var nativeWindow: Surface? = null
    private var windowOutput = false
    var presentationTimeNanos = 0L
    private var outputWidth = 0
    private var outputHeight = 0
    private val matrix = FloatArray(16)

    init {
        try {
            createContext()
            surface =
                EGL14.eglCreatePbufferSurface(
                    display,
                    config,
                    intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                    0,
                )
            check(context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE)
            withCurrent {
                for (index in programs.indices) {
                    val mode = intArrayOf(0, 3, 12)[index]
                    val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
                    val fs =
                        compile(
                            GLES20.GL_FRAGMENT_SHADER,
                            "precision mediump float;uniform lowp sampler2D page;varying highp vec2 uv;varying lowp vec4 tint;void main(){" +
                                (if (mode == 0) "gl_FragColor=tint;"
                                else
                                    "lowp vec4 s0=texture2D(page,uv);gl_FragColor=" +
                                        if (mode == 3) "tint*s0;" else "tint*s0.a;") +
                                "}",
                        )
                    try {
                        val program = GLES20.glCreateProgram()
                        programs[index] = program
                        GLES20.glAttachShader(program, vs)
                        GLES20.glAttachShader(program, fs)
                        GLES20.glBindAttribLocation(
                            program,
                            0,
                            "position",
                        )
                        GLES20.glBindAttribLocation(
                            program,
                            1,
                            "texcoord",
                        )
                        GLES20.glBindAttribLocation(program, 2, "color")
                        GLES20.glLinkProgram(program)
                        val status = IntArray(1)
                        GLES20.glGetProgramiv(
                            program,
                            GLES20.GL_LINK_STATUS,
                            status,
                            0,
                        )
                        check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
                        matrices[index] = GLES20.glGetUniformLocation(program, "vertex_matrix")
                        texSizes[index] = GLES20.glGetUniformLocation(program, "texmat0")
                        GLES20.glUseProgram(program)
                        GLES20.glUniform1i(
                            GLES20.glGetUniformLocation(
                                program,
                                "page",
                            ),
                            0,
                        )
                    } finally {
                        GLES20.glDeleteShader(vs)
                        GLES20.glDeleteShader(fs)
                    }
                }
                boundProgram = 2
                vertexBuffer = IosCurlVertexBuffer()
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glDisable(GLES20.GL_BLEND)
                GLES20.glBlendEquation(GLES20.GL_FUNC_ADD)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                GLES20.glDisable(GLES20.GL_DEPTH_TEST)
                GLES20.glDisable(GLES20.GL_CULL_FACE)
                GLES20.glDisable(GLES20.GL_DITHER)
            }
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    private fun createContext() {
        val versions = intArrayOf(3, 2)
        for (version in versions) {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attributes =
                intArrayOf(
                    EGL14.EGL_SURFACE_TYPE,
                    EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RENDERABLE_TYPE,
                    if (version >= 3) 0x0040 else EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_RED_SIZE,
                    8,
                    EGL14.EGL_GREEN_SIZE,
                    8,
                    EGL14.EGL_BLUE_SIZE,
                    8,
                    EGL14.EGL_ALPHA_SIZE,
                    8,
                    EGL14.EGL_NONE,
                )
            if (
                !EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) ||
                    count[0] == 0
            )
                continue
            val next =
                EGL14.eglCreateContext(
                    display,
                    configs[0],
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, version, EGL14.EGL_NONE),
                    0,
                )
            if (next != EGL14.EGL_NO_CONTEXT) {
                config = configs[0]
                context = next
                return
            }
        }
        error("Cannot create curl EGL context: ${EGL14.eglGetError()}")
    }

    fun upload(current: Bitmap, target: Bitmap) = withCurrent {
        textures.upload(current, target)
        boundTexture = 1
        trace("iRead.curl.uploadCheck") { checkGl("texture upload") }
    }

    fun bindOutput(bitmap: Bitmap) {
        val next =
            EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(
                    EGL14.EGL_WIDTH,
                    bitmap.width,
                    EGL14.EGL_HEIGHT,
                    bitmap.height,
                    EGL14.EGL_NONE,
                ),
                0,
            )
        check(next != EGL14.EGL_NO_SURFACE)
        EGL14.eglDestroySurface(display, surface)
        surface = next
        output = bitmap
        windowOutput = false
        nativeWindow = null
        outputWidth = bitmap.width
        outputHeight = bitmap.height
        if ((readback?.capacity() ?: 0) < bitmap.width * bitmap.height * 4) {
            readback =
                ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
                    .order(ByteOrder.nativeOrder())
        }
    }

    fun bindSurface(nativeSurface: Surface, width: Int, height: Int) {
        if (nativeWindow === nativeSurface && outputWidth == width && outputHeight == height) return
        if (windowOutput) {
            if (retainContext)
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
            EGL14.eglDestroySurface(display, surface)
            surface = EGL14.EGL_NO_SURFACE
        }
        val next =
            EGL14.eglCreateWindowSurface(
                display,
                config,
                nativeSurface,
                intArrayOf(EGL14.EGL_NONE),
                0,
            )
        check(next != EGL14.EGL_NO_SURFACE) {
            "Cannot create curl window surface: ${EGL14.eglGetError()}"
        }
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        surface = next
        nativeWindow = nativeSurface
        output = null
        windowOutput = true
        outputWidth = width
        outputHeight = height
        // The Widget/window already pace frames. Do not add another producer vsync wait.
        withCurrent { check(EGL14.eglSwapInterval(display, 0)) }
    }

    fun clearSurface() = withCurrent {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (windowOutput) check(EGL14.eglSwapBuffers(display, surface))
    }

    fun releasePages() {
        if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE) return
        withCurrent {
            textures.close()
            boundTexture = -1
        }
        output = null
    }

    fun render(
        frame: CurlMeshFrame,
        logicalWidth: Float,
        logicalHeight: Float,
        turningTexture: Int,
        underTexture: Int,
        underlays: List<Pair<Int, CurlRect>>,
    ) =
        trace("iRead.curl.raster") {
            withCurrent {
                GLES20.glViewport(0, 0, outputWidth, outputHeight)
                GLES20.glClearColor(
                    0f,
                    0f,
                    0f,
                    0f,
                )
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                configure(
                    logicalWidth,
                    logicalHeight,
                    frame.width.toFloat(),
                    frame.height.toFloat(),
                )
                trace("iRead.curl.drawBatches") {
                    val flats = underlays.ifEmpty { listOf(underTexture to CurlRect(0.0, 0.0, logicalWidth.toDouble(), logicalHeight.toDouble())) }
                    val stream = requireNotNull(vertexBuffer)
                    stream.prepare(frame.batches, flats, frame.width.toFloat(), frame.height.toFloat())
                    for ((index, flat) in flats.withIndex()) {
                        bind(1, flat.first, false); stream.flat(index)
                    }
                    for ((index, batch) in frame.batches.withIndex()) draw(index, batch, turningTexture)
                }
                if (windowOutput) {
                    if (presentationTimeNanos != 0L)
                        EGLExt.eglPresentationTimeANDROID(display, surface, presentationTimeNanos)
                    trace("iRead.curl.present") { check(EGL14.eglSwapBuffers(display, surface)) }
                } else {
                    val bitmap = requireNotNull(output)
                    val buffer = requireNotNull(readback)
                    buffer.clear()
                    trace("iRead.curl.readPixels") {
                        GLES20.glReadPixels(
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            GLES20.GL_RGBA,
                            GLES20.GL_UNSIGNED_BYTE,
                            buffer,
                        )
                    }
                    checkGl("page rasterization")
                    buffer.position(0)
                    trace("iRead.curl.copyPixels") { bitmap.copyPixelsFromBuffer(buffer) }
                }
            }
        }

    private fun configure(width: Float, height: Float, tw: Float, th: Float) {
        if (
            logicalWidth == width &&
                logicalHeight == height &&
                textureWidth == tw &&
                textureHeight == th
        )
            return
        logicalWidth = width
        logicalHeight = height
        textureWidth = tw
        textureHeight = th
        matrix.fill(0f)
        matrix[0] = 2f / width
        matrix[5] = -2f / height
        matrix[10] = 1f
        matrix[12] = -1f
        matrix[13] = 1f
        matrix[15] = 1f
        for (program in programs.indices) {
            GLES20.glUseProgram(programs[program])
            GLES20.glUniformMatrix4fv(matrices[program], 1, false, matrix, 0)
            GLES20.glUniform2f(texSizes[program], 1f / tw, 1f / th)
            boundProgram = program
        }
    }

    private fun draw(index: Int, batch: CurlDrawBatch, texture: Int) {
        check(batch.blendFunction == 3)
        bind(
            when (batch.mode) {
                0 -> 0
                3 -> 1
                12 -> 2
                else -> error("Unknown curl shader ${batch.mode}")
            },
            texture,
            batch.blendEnabled,
        )
        requireNotNull(vertexBuffer).draw(index, batch)
    }

    private fun bind(program: Int, texture: Int, blend: Boolean) {
        if (boundProgram != program) {
            GLES20.glUseProgram(programs[program])
            boundProgram = program
        }
        if (boundTexture != texture) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[texture])
            boundTexture = texture
        }
        if (blending != blend) {
            if (blend) GLES20.glEnable(GLES20.GL_BLEND) else GLES20.glDisable(GLES20.GL_BLEND)
            blending = blend
        }
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(
            shader,
            source,
        )
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error(error)
        }
        return shader
    }

    private fun checkGl(operation: String) {
        val code = GLES20.glGetError()
        check(code == GLES20.GL_NO_ERROR) { "Curl $operation GL error $code" }
    }

    private fun <T> withCurrent(block: () -> T): T {
        if (retainContext) {
            if (
                EGL14.eglGetCurrentContext() != context ||
                    EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW) != surface
            ) {
                trace("iRead.curl.eglBind") {
                    check(EGL14.eglMakeCurrent(display, surface, surface, context))
                }
            }
            return block()
        }
        val previousDisplay = EGL14.eglGetCurrentDisplay()
        val previousContext = EGL14.eglGetCurrentContext()
        val draw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val read = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        trace("iRead.curl.eglBind") {
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
        }
        try {
            return block()
        } finally {
            trace("iRead.curl.eglRestore") {
                if (previousDisplay != EGL14.EGL_NO_DISPLAY)
                    EGL14.eglMakeCurrent(
                        previousDisplay,
                        draw,
                        read,
                        previousContext,
                    )
                else
                    EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT,
                    )
            }
        }
    }

    override fun close() {
        if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE)
            runCatching {
                withCurrent {
                    textures.close()
                    vertexBuffer?.close()
                    vertexBuffer = null
                    for (program in programs) if (program != 0) GLES20.glDeleteProgram(program)
                }
            }
        if (retainContext)
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
        output = null
        readback = null
        nativeWindow = null
        windowOutput = false
        programs.fill(0)
    }

    private companion object {
        val sharedDisplay by lazy {
            EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).also {
                check(EGL14.eglInitialize(it, IntArray(2), 0, IntArray(2), 0))
            }
        }
        const val VERTEX =
            "attribute highp vec4 position;attribute highp vec2 texcoord;attribute lowp vec4 color;uniform highp mat4 vertex_matrix;uniform highp vec2 texmat0;varying highp vec2 uv;varying lowp vec4 tint;void main(){gl_Position=vertex_matrix*position;uv=texcoord*texmat0;tint=color;}"
    }
}
