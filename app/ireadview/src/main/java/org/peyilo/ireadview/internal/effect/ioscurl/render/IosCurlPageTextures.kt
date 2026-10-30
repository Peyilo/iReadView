package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.core.os.trace

/** Reuses two allocations, never their previous pixels. Confined to the EGL owner. */
internal class IosCurlPageTextures : AutoCloseable {
    private data class Storage(val width: Int, val height: Int, val format: Int, val type: Int)

    private val names = IntArray(2)
    private val storage = arrayOfNulls<Storage>(2)

    operator fun get(index: Int) = names[index]

    fun upload(current: Bitmap, target: Bitmap) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        upload(0, current)
        upload(1, target)
    }

    private fun upload(index: Int, bitmap: Bitmap) {
        bind(index)
        val next =
            Storage(
                bitmap.width,
                bitmap.height,
                GLUtils.getInternalFormat(bitmap),
                GLUtils.getType(bitmap),
            )
        trace("iRead.curl.uploadPage") {
            if (storage[index] == next) {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap, next.format, next.type)
            } else {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            }
        }
        storage[index] = next
    }

    fun allocate(index: Int, width: Int, height: Int): Int {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        bind(index)
        val next = Storage(width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE)
        if (storage[index] != next) {
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                next.format,
                width,
                height,
                0,
                next.format,
                next.type,
                null,
            )
            storage[index] = next
        }
        return names[index]
    }

    private fun bind(index: Int) {
        if (names[index] == 0) {
            GLES20.glGenTextures(1, names, index)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, names[index])
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE,
            )
        } else GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, names[index])
    }

    override fun close() {
        if (names.any { it != 0 }) GLES20.glDeleteTextures(2, names, 0)
        names.fill(0)
        storage.fill(null)
    }
}
