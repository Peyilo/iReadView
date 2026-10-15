package org.peyilo.ireadview.internal.effect

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.opengl.GLUtils
import org.peyilo.ireadview.api.effect.PageCurlOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GLES2 page renderer: inverse UV, filtered page textures and shadows in one pass.
 * No SurfaceView, independent clock, or navigation ownership. All calls stay on the UI thread.
 */
internal class BoundPageCurlRenderer : AutoCloseable {
    private val display = sharedDisplay
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private var program = 0
    private var buffer = 0
    private var output: Bitmap? = null
    private var pixels: ByteBuffer? = null
    private val textures = IntArray(2)
    private val locations = HashMap<String, Int>()

    init {
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0) { "No EGL page config" }
            config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(
                EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) { "Page surface unavailable" }
            withCurrent {
                val vertex = compile(GLES20.GL_VERTEX_SHADER,
                    "attribute vec2 position; varying vec2 screenUv; void main(){screenUv=position*.5+.5;gl_Position=vec4(position,0.,1.);}")
                val fragment = compile(GLES20.GL_FRAGMENT_SHADER, boundPageCurlFragment)
                try {
                    program = GLES20.glCreateProgram()
                    GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment)
                    GLES20.glLinkProgram(program)
                    val status = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
                    check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
                } finally { GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment) }
                val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); buffer = ids[0]
                val vertices = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder()).asFloatBuffer()
                vertices.put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,-1f,1f,1f,-1f,1f,1f)).position(0)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 48, vertices, GLES20.GL_STATIC_DRAW)
                for (name in arrayOf("progress","aspect","radiusFraction","tilt","shadowStrength","lightSize","lighting","currentPage","nextPage","rightToLeft","backTint","backContentAlpha"))
                    locations[name] = GLES20.glGetUniformLocation(program, name)
            }
        } catch (error: Throwable) { close(); throw error }
    }

    fun upload(current: Bitmap, target: Bitmap) = withCurrent {
        GLES20.glGenTextures(2,textures,0)
        val fullNpot = GLES20.glGetString(GLES20.GL_EXTENSIONS)?.contains("GL_OES_texture_npot") == true ||
            GLES20.glGetString(GLES20.GL_VERSION)?.contains("OpenGL ES 3") == true
        for ((index,bitmap) in arrayOf(current,target).withIndex()) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0+index)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[index])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
            val powerOfTwo = bitmap.width and (bitmap.width-1) == 0 && bitmap.height and (bitmap.height-1) == 0
            val mipmapped = fullNpot || powerOfTwo
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,
                if(mipmapped)GLES20.GL_LINEAR_MIPMAP_LINEAR else GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            if(mipmapped)GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        }
        check(GLES20.glGetError()==GLES20.GL_NO_ERROR){"Page texture upload failed"}
    }
    fun bindOutput(bitmap: Bitmap) {
        val replacement=EGL14.eglCreatePbufferSurface(display,requireNotNull(config),intArrayOf(
            EGL14.EGL_WIDTH,bitmap.width,EGL14.EGL_HEIGHT,bitmap.height,EGL14.EGL_NONE),0)
        check(replacement!=EGL14.EGL_NO_SURFACE){"Cannot create page output surface"}
        EGL14.eglDestroySurface(display,surface)
        surface=replacement
        output=bitmap
        val bytes=bitmap.width*bitmap.height*4
        pixels=borrowReadback(bytes).apply { clear(); limit(bytes) }
    }
    fun render(progress: Float, aspect: Float, tilt: Float, rightToLeft: Boolean, forward: Boolean, options: PageCurlOptions, backTint: Int) = withCurrent {
        val bitmap = requireNotNull(output)
        val readBuffer = requireNotNull(pixels)
        GLES20.glUseProgram(program); GLES20.glViewport(0,0,bitmap.width,bitmap.height)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        val attribute = GLES20.glGetAttribLocation(program,"position")
        GLES20.glEnableVertexAttribArray(attribute)
        GLES20.glVertexAttribPointer(attribute,2,GLES20.GL_FLOAT,false,0,0)
        fun uniform(name: String, value: Float) { GLES20.glUniform1f(requireNotNull(locations[name]),value) }
        uniform("progress",progress); uniform("aspect",aspect); uniform("tilt",tilt)
        uniform("radiusFraction",options.radiusFraction); uniform("shadowStrength",options.shadowStrength)
        uniform("lightSize",options.lightSize); uniform("lighting",1f)
        uniform("rightToLeft",if(rightToLeft)1f else 0f)
        uniform("backContentAlpha",options.backContentAlpha)
        GLES20.glUniform3f(requireNotNull(locations["backTint"]), android.graphics.Color.red(backTint)/255f,
            android.graphics.Color.green(backTint)/255f, android.graphics.Color.blue(backTint)/255f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[if(forward)0 else 1])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[if(forward)1 else 0])
        GLES20.glUniform1i(requireNotNull(locations["currentPage"]),0)
        GLES20.glUniform1i(requireNotNull(locations["nextPage"]),1)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6)
        readBuffer.position(0)
        GLES20.glReadPixels(0,0,bitmap.width,bitmap.height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,readBuffer)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "Page rendering failed" }
        readBuffer.position(0); bitmap.copyPixelsFromBuffer(readBuffer)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader)
        val status = IntArray(1); GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
        if (status[0] == 0) { val message=GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader); error(message) }
        return shader
    }
    private fun <T> withCurrent(action: () -> T): T {
        val previousDisplay = EGL14.eglGetCurrentDisplay()
        val previousContext = EGL14.eglGetCurrentContext()
        val draw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val read = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        check(EGL14.eglMakeCurrent(display,surface,surface,context)) { "Cannot bind page context" }
        try { return action() }
        finally {
            if (previousDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglMakeCurrent(previousDisplay,draw,read,previousContext)
            else EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        }
    }
    override fun close() {
        if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) {
            runCatching { withCurrent { if(textures[0]!=0) GLES20.glDeleteTextures(2,textures,0); if(program!=0) GLES20.glDeleteProgram(program); if(buffer!=0) GLES20.glDeleteBuffers(1,intArrayOf(buffer),0) } }
        }
        if(surface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,surface)
        if(context!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,context)
        surface=EGL14.EGL_NO_SURFACE; context=EGL14.EGL_NO_CONTEXT; program=0;buffer=0
        output=null; pixels?.let(::returnReadback); pixels=null; config=null; textures.fill(0)
        // The EGL default display can be shared with HWUI; never terminate it here.
    }
    private companion object {
        private var readbackCache: ByteBuffer? = null
        @Synchronized fun borrowReadback(bytes: Int): ByteBuffer {
            val cached=readbackCache
            if(cached!=null && cached.capacity()>=bytes){readbackCache=null;return cached}
            return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        }
        @Synchronized fun returnReadback(buffer: ByteBuffer) {
            // Retain at most one common phone-size staging buffer, avoiding per-turn churn.
            if(buffer.capacity()<=8*1024*1024 && buffer.capacity()>(readbackCache?.capacity()?:0))
                readbackCache=buffer
        }
        // Initialize once per process, rather than acquiring display ownership on every turn.
        val sharedDisplay by lazy {
            EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).also {
                check(EGL14.eglInitialize(it,IntArray(2),0,IntArray(2),0)) { "EGL initialization failed" }
            }
        }
    }
}

