package org.peyilo.ireadview.internal.effect.ioscurl.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.peyilo.pagecurl.mesh.CurlDrawBatch
import org.peyilo.pagecurl.model.CurlRect

/** Streams the core's packed vertices directly. Only old drivers need float color expansion. */
internal class IosCurlVertexBuffer : AutoCloseable {
    private val halfType =
        when {
            GLES20.glGetString(GLES20.GL_VERSION).orEmpty().contains("OpenGL ES 3") -> 0x140B
            GLES20.glGetString(GLES20.GL_EXTENSIONS)
                .orEmpty()
                .split(' ')
                .contains("GL_OES_vertex_half_float") -> 0x8D61
            else -> GLES20.GL_FLOAT
        }
    private val packed = halfType != GLES20.GL_FLOAT
    private val stride = if (packed) 64 else 40
    private val buffers = IntArray(2)
    private var vertices = allocate(4096 * stride)
    private var indices = allocate(16384)
    private var vertexStarts = IntArray(16)
    private var indexStarts = IntArray(16)
    private var attributeBase = 0

    init {
        GLES20.glGenBuffers(2, buffers, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0])
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[1])
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        pointAttributes(0)
    }

    /** A single stream upload per frame; draw order, vertices and indices stay unchanged. */
    fun prepare(
        batches: List<CurlDrawBatch>,
        flats: List<Pair<Int, CurlRect>>,
        tw: Float,
        th: Float,
    ) {
        val vertexBytes = (flats.size * 4 + batches.sumOf { it.vertexCount }) * stride
        val indexBytes = batches.sumOf { it.indices?.size ?: 0 } * 2
        if (vertices.capacity() < vertexBytes) vertices = allocate(vertexBytes)
        if (indices.capacity() < indexBytes) indices = allocate(indexBytes)
        if (vertexStarts.size < batches.size) {
            vertexStarts = IntArray(batches.size)
            indexStarts = IntArray(batches.size)
        }
        vertices.clear()
        indices.clear()
        for ((_, rect) in flats) appendFlat(rect, tw, th)
        for ((index, batch) in batches.withIndex()) {
            vertexStarts[index] = vertices.position() / stride
            indexStarts[index] = indices.position()
            append(batch)
            batch.indices?.forEach { indices.putShort(it) }
        }
        vertices.flip()
        indices.flip()
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexBytes, vertices, GLES20.GL_STREAM_DRAW)
        if (indexBytes > 0)
            GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indexBytes,
                indices,
                GLES20.GL_STREAM_DRAW,
            )
    }

    private fun append(batch: CurlDrawBatch) {
        if (packed) vertices.put(batch.vertices, 0, batch.vertexCount * stride)
        else {
            val source = ByteBuffer.wrap(batch.vertices).order(ByteOrder.LITTLE_ENDIAN)
            for (vertex in 0 until batch.vertexCount) {
                val at = vertex * 64
                for (component in 0..5) vertices.putFloat(source.getFloat(at + component * 4))
                for (channel in 0..3) vertices.putFloat(
                    half(source.getShort(at + 40 + channel * 2).toInt() and 65535)
                )
            }
        }
    }

    fun draw(index: Int, batch: CurlDrawBatch) {
        val primitive =
            when (batch.primitive) {
                0 -> GLES20.GL_POINTS
                1 -> GLES20.GL_LINES
                2 -> GLES20.GL_TRIANGLE_STRIP
                3 -> GLES20.GL_TRIANGLES
                4 -> GLES20.GL_TRIANGLE_FAN
                else -> error("Unknown curl primitive ${batch.primitive}")
            }
        if (batch.indices == null) {
            attributes(0)
            GLES20.glDrawArrays(primitive, vertexStarts[index], batch.count)
        } else {
            // Keep indices local to their packet, including streams larger than 65535 vertices.
            attributes(vertexStarts[index] * stride)
            GLES20.glDrawElements(
                primitive,
                batch.count,
                GLES20.GL_UNSIGNED_SHORT,
                indexStarts[index],
            )
        }
    }

    fun flat(index: Int) {
        attributes(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, index * 4, 4)
    }

    private fun attributes(base: Int) {
        if (attributeBase != base) {
            pointAttributes(base)
            attributeBase = base
        }
    }

    private fun pointAttributes(base: Int) {
        GLES20.glVertexAttribPointer(0, 4, GLES20.GL_FLOAT, false, stride, base)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, stride, base + 16)
        GLES20.glVertexAttribPointer(2, 4, halfType, false, stride, base + if (packed) 40 else 24)
    }

    private fun appendFlat(rect: CurlRect, textureWidth: Float, textureHeight: Float) {
        fun point(x: Double, y: Double, u: Float, v: Float) {
            val at = vertices.position()
            vertices
                .putFloat(x.toFloat())
                .putFloat(y.toFloat())
                .putFloat(0f)
                .putFloat(1f)
                .putFloat(u)
                .putFloat(v)
            if (packed) {
                vertices.position(at + 40)
                repeat(4) { vertices.putShort(0x3C00.toShort()) }
            } else repeat(4) { vertices.putFloat(1f) }
            vertices.position(at + stride)
        }
        point(rect.x, rect.y, 0f, 0f)
        point(rect.x, rect.y + rect.height, 0f, textureHeight)
        point(rect.x + rect.width, rect.y, textureWidth, 0f)
        point(rect.x + rect.width, rect.y + rect.height, textureWidth, textureHeight)
    }

    override fun close() {
        GLES20.glDeleteBuffers(2, buffers, 0)
    }

    private fun half(value: Int): Float {
        val sign = (value and 0x8000) shl 16
        val exponent = value ushr 10 and 31
        var mantissa = value and 1023
        val bits =
            if (exponent == 0) {
                if (mantissa == 0) sign
                else {
                    var shift = 0
                    while (mantissa and 1024 == 0) {
                        mantissa = mantissa shl 1
                        shift++
                    }
                    sign or ((113 - shift) shl 23) or ((mantissa and 1023) shl 13)
                }
            } else
                sign or ((if (exponent == 31) 255 else exponent + 112) shl 23) or (mantissa shl 13)
        return Float.fromBits(bits)
    }

    private fun allocate(size: Int) = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
}
