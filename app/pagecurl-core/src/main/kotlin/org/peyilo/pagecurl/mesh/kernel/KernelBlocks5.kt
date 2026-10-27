// Generated from the verified C11 curl geometry; regenerate with tools/Port-CurlKernel.py.
// Direct Kotlin arithmetic and managed scratch storage. No machine instructions are executed.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")
package org.peyilo.pagecurl.mesh.kernel
import kotlin.math.*
import org.peyilo.pagecurl.math.AppleMath
import org.peyilo.pagecurl.math.BinaryMath.fma

// C source block: fallthrough192
internal fun CurlMeshKernel.block192(): Int {
    x22 = (0L and 0xffffffffL)
    return 211
}

// C source block: L185345df8
internal fun CurlMeshKernel.block193(): Int {
    x10 = (sp + 2432L)
    st_d((x10 + (((x9 and 0xffffffffL) and 0xffffffffL) shl 3L.toInt())), v2.d(0L.toInt()))
    return if (((x9 and 0xffffffffL) == 0L)) 197 else 194
}

// C source block: fallthrough194
internal fun CurlMeshKernel.block194(): Int {
    v1.d(0L.toInt(), ld_d((sp + 2440L)))
    v0.d(0L.toInt(), ld_d((sp + 2432L)))
    fcmp(v1.d(0L.toInt()), v0.d(0L.toInt()))
    return if ((Z || (N != V))) 196 else 195
}

// C source block: fallthrough195
internal fun CurlMeshKernel.block195(): Int {
    st_d((sp + 2440L), v0.d(0L.toInt()))
    st_d((sp + 2432L), v2.d(0L.toInt()))
    return 196
}

// C source block: L185345e1c
internal fun CurlMeshKernel.block196(): Int {
    x9 = ((0L or 2L) and 0xffffffffL)
    return 198
}

// C source block: L185345e24
internal fun CurlMeshKernel.block197(): Int {
    x9 = ((0L or 1L) and 0xffffffffL)
    return 198
}

// C source block: L185345e28
internal fun CurlMeshKernel.block198(): Int {
    subflags((x8 and 0xffffffffL), 0L, 32L)
    x15 = ((if ((!(Z) && (N == V))) 1L else 0L) and 0xffffffffL)
    return 199
}

// C source block: L185345e30
internal fun CurlMeshKernel.block199(): Int {
    x22 = 0L
    x10 = (0L and 0xffffffffL)
    x11 = (0L and 0xffffffffL)
    x12 = (sp + 1504L)
    x13 = (sp + 680L)
    x14 = (sp + 2432L)
    return 200
}

// C source block: L185345e48
internal fun CurlMeshKernel.block200(): Int {
    subflags((x10 and 0xffffffffL), (x9 and 0xffffffffL), 32L)
    return if (!(Z)) 203 else 201
}

// C source block: fallthrough201
internal fun CurlMeshKernel.block201(): Int {
    x15 = ld_u64((x12 + (((x11 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())))
    return 202
}

// C source block: L185345e54
internal fun CurlMeshKernel.block202(): Int {
    x11 = (((x11 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 209
}

// C source block: L185345e5c
internal fun CurlMeshKernel.block203(): Int {
    return if ((((x15 and 0xffffffffL) and (1L shl 0L.toInt())) == 0L)) 206 else 204
}

// C source block: fallthrough204
internal fun CurlMeshKernel.block204(): Int {
    v0.d(0L.toInt(), ld_d((x12 + (((x11 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))))
    v1.d(0L.toInt(), ld_d((x14 + (((x10 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))))
    fcmp(v0.d(0L.toInt()), v1.d(0L.toInt()))
    return if ((Z || (N != V))) 207 else 205
}

// C source block: fallthrough205
internal fun CurlMeshKernel.block205(): Int {
    x15 = v0.u64(0L.toInt())
    return 202
}

// C source block: L185345e78
internal fun CurlMeshKernel.block206(): Int {
    x15 = ld_u64((x14 + (((x10 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())))
    return 208
}

// C source block: L185345e80
internal fun CurlMeshKernel.block207(): Int {
    x15 = v1.u64(0L.toInt())
    return 208
}

// C source block: L185345e84
internal fun CurlMeshKernel.block208(): Int {
    x10 = (((x10 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 209
}

// C source block: L185345e88
internal fun CurlMeshKernel.block209(): Int {
    st_u64((x13 + (x22 shl 3L.toInt())), x15)
    x22 = (x22 + 1L)
    subflags((x11 and 0xffffffffL), (x8 and 0xffffffffL), 32L)
    x15 = ((if ((N != V)) 1L else 0L) and 0xffffffffL)
    return if ((N != V)) 200 else 210
}

// C source block: fallthrough210
internal fun CurlMeshKernel.block210(): Int {
    subflags((x10 and 0xffffffffL), (x9 and 0xffffffffL), 32L)
    return if ((N != V)) 200 else 211
}

// C source block: L185345ea4
internal fun CurlMeshKernel.block211(): Int {
    v0.set(v12)
    v1.set(v12)
    v2.set(v13)
    v3.set(v9)
    v4.set(v15)
    x0 = 0L
    v0.f(0L.toInt(), displace(v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), x0, v3.d(0L.toInt()), v4.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v3.u64(0L.toInt(), 0L)
    v1.d(0L.toInt(), (v3.d(0L.toInt()) / v13.d(0L.toInt())))
    v1.d(0L.toInt(), (v11.d(0L.toInt()) - v1.d(0L.toInt())))
    v1.f(0L.toInt(), (v1.d(0L.toInt())).toFloat())
    v1.f(0L.toInt(), (v1.f(0L.toInt()) * v1.f(0L.toInt())))
    v2.f(0L.toInt(), -(0.0392276905477047f))
    v4.f(0L.toInt(), 0.0009696679771877825f)
    st_f((sp + 400L), v2.f(0L.toInt()))
    st_f((sp + 392L), v4.f(0L.toInt()))
    v2.f(0L.toInt(), fma(v1.f(0L.toInt()), v4.f(0L.toInt()), v2.f(0L.toInt())))
    v4.f(0L.toInt(), 0.4953497052192688f)
    st_f((sp + 368L), v4.f(0L.toInt()))
    v2.f(0L.toInt(), fma(v1.f(0L.toInt()), v2.f(0L.toInt()), v4.f(0L.toInt())))
    v4.f(0L.toInt(), -(0.9986067414283752f))
    st_f((sp + 352L), v4.f(0L.toInt()))
    v1.f(0L.toInt(), fma(v1.f(0L.toInt()), v2.f(0L.toInt()), v4.f(0L.toInt())))
    v1.d(0L.toInt(), (v1.f(0L.toInt())).toDouble())
    v1.d(0L.toInt(), (v1.d(0L.toInt()) + v14.d(0L.toInt())))
    v1.d(0L.toInt(), (v13.d(0L.toInt()) * v1.d(0L.toInt())))
    v0.d(0L.toInt(), (v12.d(0L.toInt()) + v0.d(0L.toInt())))
    v2.d(0L.toInt(), 0.4000000059604645)
    st_d((sp + 336L), v8.d(0L.toInt()))
    st_d(((sp + 336L) + 8L), v2.d(0L.toInt()))
    v0.d(0L.toInt(), fma(v1.d(0L.toInt()), v2.d(0L.toInt()), -(v0.d(0L.toInt()))))
    v0.d(0L.toInt(), (v8.d(0L.toInt()) + v0.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) / v13.d(0L.toInt())))
    v1.d(0L.toInt(), -(0.949999988079071))
    st_d((sp + 328L), v1.d(0L.toInt()))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) + v1.d(0L.toInt())))
    v0.d(0L.toInt(), max(v0.d(0L.toInt()), v3.d(0L.toInt())))
    v1.d(0L.toInt(), 3.0)
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v0.d(0L.toInt())))
    v0.f(0L.toInt(), (v0.d(0L.toInt())).toFloat())
    v0.f(0L.toInt(), -(v0.f(0L.toInt())))
    v0.f(0L.toInt(), AppleMath.exp(v0.f(0L.toInt())))
    subflags((x22 and 0xffffffffL), 1L, 32L)
    v2.d(0L.toInt(), ld_d((sp + 536L)))
    v3.d(0L.toInt(), ld_d((sp + 528L)))
    v6.d(0L.toInt(), ld_d((sp + 416L)))
    v5.d(0L.toInt(), ld_d(((sp + 416L) + 8L)))
    return if ((N != V)) 214 else 212
}

// C source block: fallthrough212
internal fun CurlMeshKernel.block212(): Int {
    v1.set(v12)
    v12.set(v9)
    x20 = 0L
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v4.d(0L.toInt(), (v0.d(0L.toInt()) * v0.d(0L.toInt())))
    v0.d(0L.toInt(), fma(v4.d(0L.toInt()), v4.d(0L.toInt()), v0.d(0L.toInt())))
    st_d((sp + 320L), v0.d(0L.toInt()))
    x21 = ((x22 and 0xffffffffL) and 0xffffffffL)
    x23 = (sp + 680L)
    v0.d(0L.toInt(), 76.5)
    st_d((sp + 312L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), 0.00392156862745098)
    st_d((sp + 304L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), (v14.d(0L.toInt()) / v13.d(0L.toInt())))
    st_d((sp + 296L), v0.d(0L.toInt()))
    return 213
}

// C source block: L185345fc0
internal fun CurlMeshKernel.block213(): Int {
    v10.d(0L.toInt(), ld_d((x23 + (x20 shl 3L.toInt()))))
    v8.d(0L.toInt(), fma(v2.d(0L.toInt()), v10.d(0L.toInt()), v6.d(0L.toInt())))
    v9.d(0L.toInt(), fma(v3.d(0L.toInt()), v10.d(0L.toInt()), v5.d(0L.toInt())))
    v0.set(v10)
    v2.set(v13)
    v3.set(v12)
    v4.set(v15)
    x0 = 0L
    v0.f(0L.toInt(), displace(v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), x0, v3.d(0L.toInt()), v4.d(0L.toInt())))
    v12.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    x0 = (sp + 2368L)
    x1 = (sp + 592L)
    v2.d(0L.toInt(), ld_d((sp + 456L)))
    v0.d(0L.toInt(), ld_d(((sp + 456L) + 8L)))
    v3.d(0L.toInt(), ld_d((sp + 432L)))
    v1.d(0L.toInt(), ld_d(((sp + 432L) + 8L)))
    v4.set(v8)
    v5.set(v9)
    v7.d(0L.toInt(), ld_d((sp + 224L)))
    v6.d(0L.toInt(), ld_d(((sp + 224L) + 8L)))
    line_clip(x0, x1, v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt()), v4.d(0L.toInt()), v5.d(0L.toInt()), v6.d(0L.toInt()), v7.d(0L.toInt()))
    v0.d(0L.toInt(), ld_d((sp + 520L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) - v10.d(0L.toInt())))
    v3.d(0L.toInt(), ld_d((sp + 296L)))
    v0.d(0L.toInt(), fma(v0.d(0L.toInt()), v3.d(0L.toInt()), v11.d(0L.toInt())))
    v0.f(0L.toInt(), (v0.d(0L.toInt())).toFloat())
    v0.f(0L.toInt(), (v0.f(0L.toInt()) * v0.f(0L.toInt())))
    v1.f(0L.toInt(), ld_f((sp + 400L)))
    v2.f(0L.toInt(), ld_f((sp + 392L)))
    v1.f(0L.toInt(), fma(v0.f(0L.toInt()), v2.f(0L.toInt()), v1.f(0L.toInt())))
    v2.f(0L.toInt(), ld_f((sp + 368L)))
    v1.f(0L.toInt(), fma(v0.f(0L.toInt()), v1.f(0L.toInt()), v2.f(0L.toInt())))
    v2.f(0L.toInt(), ld_f((sp + 352L)))
    v0.f(0L.toInt(), fma(v0.f(0L.toInt()), v1.f(0L.toInt()), v2.f(0L.toInt())))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v0.d(0L.toInt(), (v0.d(0L.toInt()) + v14.d(0L.toInt())))
    v0.d(0L.toInt(), (v13.d(0L.toInt()) * v0.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 344L)))
    v14.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v0.d(0L.toInt(), (v12.d(0L.toInt()) - v14.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 536L)))
    v1.d(0L.toInt(), fma(v1.d(0L.toInt()), v0.d(0L.toInt()), v8.d(0L.toInt())))
    st_d((sp + 472L), v1.d(0L.toInt()))
    v1.d(0L.toInt(), ld_d((sp + 528L)))
    v9.d(0L.toInt(), fma(v1.d(0L.toInt()), v0.d(0L.toInt()), v9.d(0L.toInt())))
    v13.d(0L.toInt(), ld_d((sp + 2368L)))
    v11.d(0L.toInt(), (v13.d(0L.toInt()) - v14.d(0L.toInt())))
    v15.d(0L.toInt(), ld_d((sp + 592L)))
    v8.d(0L.toInt(), (v15.d(0L.toInt()) + v14.d(0L.toInt())))
    v0.d(0L.toInt(), (v10.d(0L.toInt()) + v12.d(0L.toInt())))
    v0.d(0L.toInt(), (v14.d(0L.toInt()) - v0.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 336L)))
    v0.d(0L.toInt(), (v1.d(0L.toInt()) + v0.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 328L)))
    v0.d(0L.toInt(), fma(v0.d(0L.toInt()), v3.d(0L.toInt()), v1.d(0L.toInt())))
    v1.u64(0L.toInt(), 0L)
    v0.d(0L.toInt(), max(v0.d(0L.toInt()), v1.d(0L.toInt())))
    v1.d(0L.toInt(), 3.0)
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v0.d(0L.toInt())))
    v0.f(0L.toInt(), (v0.d(0L.toInt())).toFloat())
    v0.f(0L.toInt(), -(v0.f(0L.toInt())))
    v0.f(0L.toInt(), AppleMath.exp(v0.f(0L.toInt())))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v1.d(0L.toInt(), (v0.d(0L.toInt()) * v0.d(0L.toInt())))
    v0.d(0L.toInt(), fma(v1.d(0L.toInt()), v1.d(0L.toInt()), v0.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 320L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) - v1.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d(x24))
    v2.d(0L.toInt(), 15.0)
    v1.d(0L.toInt(), (v1.d(0L.toInt()) * v2.d(0L.toInt())))
    v2.d(0L.toInt(), 1.0)
    v1.d(0L.toInt(), min(v1.d(0L.toInt()), v2.d(0L.toInt())))
    v2.d(0L.toInt(), ld_d((sp + 312L)))
    v1.d(0L.toInt(), (v2.d(0L.toInt()) * v1.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v1.d(0L.toInt(), (v15.d(0L.toInt()) - v13.d(0L.toInt())))
    v2.d(0L.toInt(), 0.5)
    v3.set(v2)
    v2.d(0L.toInt(), (v1.d(0L.toInt()) * v2.d(0L.toInt())))
    fcmp(v2.d(0L.toInt()), v14.d(0L.toInt()))
    v4.d(0L.toInt(), 0.25)
    v1.d(0L.toInt(), (v1.d(0L.toInt()) * v4.d(0L.toInt())))
    v1.d(0L.toInt(), (v1.d(0L.toInt()) / v14.d(0L.toInt())))
    v1.d(0L.toInt(), (v1.d(0L.toInt()) + v3.d(0L.toInt())))
    v1.d(0L.toInt(), (v1.d(0L.toInt()) * v0.d(0L.toInt())))
    v0.d(0L.toInt(), (if (!(N)) v0.d(0L.toInt()) else v1.d(0L.toInt())))
    fcmp(v14.d(0L.toInt()), v2.d(0L.toInt()))
    v1.d(0L.toInt(), (if (N) v14.d(0L.toInt()) else v2.d(0L.toInt())))
    v14.d(0L.toInt(), 1.0)
    v2.d(0L.toInt(), (v13.d(0L.toInt()) + v1.d(0L.toInt())))
    v13.d(0L.toInt(), ld_d((sp + 512L)))
    v1.d(0L.toInt(), (v15.d(0L.toInt()) - v1.d(0L.toInt())))
    v12.d(0L.toInt(), ld_d((sp + 496L)))
    v15.d(0L.toInt(), ld_d(((sp + 496L) + 8L)))
    v3.d(0L.toInt(), ld_d((sp + 304L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v3.d(0L.toInt())))
    v0.f(0L.toInt(), (v0.d(0L.toInt())).toFloat())
    v3.d(0L.toInt(), ld_d((sp + 208L)))
    run {
        var t = v3.copy()
        for (k in 0L until 4L) {
            v3.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    run {
        var ta = v3.copy()
        var tb = v0.copy()
        var tc = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    st_d((sp + 584L), v0.d(0L.toInt()))
    v0.f(0L.toInt(), (v11.d(0L.toInt())).toFloat())
    v11.d(0L.toInt(), ld_d((sp + 448L)))
    v4.f(0L.toInt(), (v8.d(0L.toInt())).toFloat())
    v2.f(0L.toInt(), (v2.d(0L.toInt())).toFloat())
    v1.f(0L.toInt(), (v1.d(0L.toInt())).toFloat())
    st_f((sp + 8L), v2.f(0L.toInt()))
    st_f(((sp + 8L) + 4L), v1.f(0L.toInt()))
    v2.u64(0L.toInt(), 0L)
    v3.u64(0L.toInt(), 0L)
    v6.u64(0L.toInt(), 0L)
    x0 = (sp + (7L shl 12L.toInt()))
    x0 = (x0 + 896L)
    v7.u64(0L.toInt(), 0L)
    x1 = (sp + (4L shl 12L.toInt()))
    x2 = (sp + 3200L)
    st_f(sp, v0.f(0L.toInt()))
    st_f((sp + 4L), v4.f(0L.toInt()))
    x4 = (sp + 584L)
    v0.d(0L.toInt(), ld_d((sp + 472L)))
    v5.d(0L.toInt(), ld_d(((sp + 472L) + 8L)))
    v1.set(v9)
    v4.d(0L.toInt(), ld_d((sp + 488L)))
    x3 = x20
    strip_emit(x0, x1, x2, x3, x4, v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt()), v4.d(0L.toInt()), v5.d(0L.toInt()), v6.d(0L.toInt()), v7.d(0L.toInt()), sp)
    v6.d(0L.toInt(), ld_d((sp + 416L)))
    v5.d(0L.toInt(), ld_d(((sp + 416L) + 8L)))
    v1.d(0L.toInt(), ld_d((sp + 520L)))
    v3.d(0L.toInt(), ld_d((sp + 528L)))
    v2.d(0L.toInt(), ld_d((sp + 536L)))
    x20 = (x20 + 1L)
    subflags(x21, x20, 64L)
    return if (!(Z)) 213 else 214
}

// C source block: L1853461bc
internal fun CurlMeshKernel.block214(): Int {
    x8 = (((x22 and 0xffffffffL) shl 1L.toInt()) and 0xffffffffL)
    st_u32((sp + 528L), (x8 and 0xffffffffL))
    x23 = ld_u64((sp + 184L))
    return 215
}

// C source block: L1853461c8
internal fun CurlMeshKernel.block215(): Int {
    x8 = ld_u64((x19 + 8L))
    x9 = (ld_u8((x8 + 417L)) and 0xffffffffL)
    x9 = (((x9 and 0xffffffffL) or 1L) and 0xffffffffL)
    st_u8((x8 + 417L), (x9 and 0xffffffffL))
    x8 = ld_u64((sp + 80L))
    x8 = ld_u64((x8 + 24L))
    st_u64((x19 + 552L), x8)
    x8 = (ld_u32((x19 + 232L)) and 0xffffffffL)
    x8 = (((x8 and 0xffffffffL) + 1L) and 0xffffffffL)
    st_u32((x19 + 232L), (x8 and 0xffffffffL))
    x8 = ld_u64((x19 + 8L))
    x9 = ((0L or 3L) and 0xffffffffL)
    st_u8((x8 + 16L), (x9 and 0xffffffffL))
    v0.u32(0L.toInt(), 0L)
    x4 = (9729L and 0xffffffffL)
    x5 = (9729L and 0xffffffffL)
    x2 = (0L and 0xffffffffL)
    x3 = (0L and 0xffffffffL)
    x0 = x19
    x1 = ld_u64((sp + 128L))
    x0 = ld_u64((sp + 88L))
    return if ((x0 == 0L)) 220 else 216
}

// C source block: fallthrough216
internal fun CurlMeshKernel.block216(): Int {
    x20 = ld_u64((x19 + 8L))
    x8 = (ld_u8((x0 + 144L)) and 0xffffffffL)
    return if ((((x8 and 0xffffffffL) and (1L shl 4L.toInt())) != 0L)) 218 else 217
}

// C source block: fallthrough217
internal fun CurlMeshKernel.block217(): Int {
    v0.d(0L.toInt(), ld_d((x0 + 128L)))
    return 219
}

// C source block: L185346238
internal fun CurlMeshKernel.block218(): Int {
    x1 = 0L
    x2 = 0L
    v0.d(0L.toInt(), get_scale(x0, x1, x2))
    return 219
}

// C source block: L185346244
internal fun CurlMeshKernel.block219(): Int {
    v0.d(0L.toInt(), (v14.d(0L.toInt()) / v0.d(0L.toInt())))
    v0.f(0L.toInt(), (v0.d(0L.toInt())).toFloat())
    v1.load(memory, (x20 + 124L))
    run {
        var ta = v1.copy()
        var tb = v0.copy()
        var tc = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    v0.store(memory, (x20 + 124L))
    return 220
}

// C source block: L185346258
internal fun CurlMeshKernel.block220(): Int {
    st_u64((sp + 680L), x19)
    x8 = ld_u64((x19 + 224L))
    x8 = ld_u64((x8 + 8L))
    st_u64((sp + 688L), x8)
    st_u64((sp + 720L), 0L)
    st_u64((sp + 704L), 0L)
    st_u64((sp + 696L), 0L)
    st_u16((sp + 712L), 0L)
    x0 = (sp + 680L)
    clipped = 0L
    x0 = (sp + 680L)
    x1 = (sp + 1504L)
    x0 = next_clip(x1)
    return if (((x0 and 0xffffffffL) == 0L)) 239 else 221
}

// C source block: fallthrough221
internal fun CurlMeshKernel.block221(): Int {
    x8 = ld_u64((sp + 32L))
    x8 = (x8 and 4294967294L)
    x9 = (sp + (39L shl 12L.toInt()))
    x9 = (x9 + 2320L)
    x8 = (x9 + (x8 shl 6L.toInt()))
    st_u64((sp + 520L), x8)
    x8 = (ld_u32((sp + 44L)) and 0xffffffffL)
    x8 = (((x8 and 0xffffffffL) - 2L) and 0xffffffffL)
    st_u32((sp + 512L), (x8 and 0xffffffffL))
    x8 = (ld_u32((sp + 280L)) and 0xffffffffL)
    x21 = (((x8 and 0xffffffffL) shl 1L.toInt()) and 0xffffffffL)
    x8 = (sp + (36L shl 12L.toInt()))
    x8 = (x8 + 1552L)
    x10 = ld_u64((sp + 104L))
    x8 = (x8 + x10)
    st_u64((sp + 496L), x8)
    x8 = (sp + (35L shl 12L.toInt()))
    x8 = (x8 + 1296L)
    x8 = (x8 + x10)
    st_u64((sp + 464L), x8)
    x8 = (sp + (34L shl 12L.toInt()))
    x8 = (x8 + 1040L)
    x8 = (x8 + x10)
    st_u64((sp + 456L), x8)
    x8 = (x9 + x10)
    st_u64((sp + 488L), x8)
    x8 = (sp + (38L shl 12L.toInt()))
    x8 = (x8 + 2064L)
    x8 = (x8 + x10)
    st_u64((sp + 480L), x8)
    x8 = (sp + (37L shl 12L.toInt()))
    x8 = (x8 + 1808L)
    x8 = (x8 + x10)
    st_u64((sp + 472L), x8)
    v0.load(memory, (sp + 112L))
    v0.h(0L.toInt(), v0.h(3L.toInt()))
    x22 = ((0L or 3L) and 0xffffffffL)
    v8.f(0L.toInt(), halfFloat(v0.h(0L.toInt())))
    v0.h(0L.toInt(), 0L)
    v9.f(0L.toInt(), halfFloat(v0.h(0L.toInt())))
    x24 = (11520L and 0xffffffffL)
    x8 = ld_u64((sp + 568L))
    x8 = ((x8 and 0xffffffffL) and 0xffffffffL)
    st_u64((sp + 536L), x8)
    st_u32((sp + 504L), (x21 and 0xffffffffL))
    return 222
}

// C source block: L185346348
internal fun CurlMeshKernel.block222(): Int {
    x8 = (ld_u8(x23) and 0xffffffffL)
    return if (((x8 and 0xffffffffL) == 0L)) 224 else 223
}

// C source block: fallthrough223
internal fun CurlMeshKernel.block223(): Int {
    x8 = ld_u64((x19 + 8L))
    st_u8((x8 + 16L), 0L)
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x4 = (sp + (7L shl 12L.toInt()))
    x4 = (x4 + 896L)
    x20 = (ld_u32((sp + 528L)) and 0xffffffffL)
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x4 = (sp + (4L shl 12L.toInt()))
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x4 = (sp + 3200L)
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64((x19 + 8L))
    st_u8((x8 + 16L), (x22 and 0xffffffffL))
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = (ld_u32((sp + 172L)) and 0xffffffffL)
    x5 = (0L and 0xffffffffL)
    x6 = x2
    x0 = x19
    x3 = 0L
    x4 = ld_u64((sp + 520L))
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x4 = (sp + (38L shl 12L.toInt()))
    x4 = (x4 + 2064L)
    x20 = (ld_u32((sp + 512L)) and 0xffffffffL)
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x4 = (sp + (37L shl 12L.toInt()))
    x4 = (x4 + 1808L)
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    return 224
}

// C source block: L18534647c
internal fun CurlMeshKernel.block224(): Int {
    x8 = (ld_u8((x23 + 1L)) and 0xffffffffL)
    return if (((x8 and 0xffffffffL) == 0L)) 238 else 225
}

// C source block: fallthrough225
internal fun CurlMeshKernel.block225(): Int {
    x8 = ld_u64((sp + 288L))
    return if (((x8 and 0xffffffffL) == 0L)) 235 else 226
}

// C source block: fallthrough226
internal fun CurlMeshKernel.block226(): Int {
    x20 = 0L
    x8 = ld_u64((x19 + 8L))
    st_u8((x8 + 16L), 0L)
    x22 = (sp + (10L shl 12L.toInt()))
    x22 = (x22 + 1808L)
    return 227
}

// C source block: L1853464a0
internal fun CurlMeshKernel.block227(): Int {
    x8 = ld_u64((sp + 568L))
    subflags((x8 and 0xffffffffL), 1L, 32L)
    return if ((N != V)) 230 else 228
}

// C source block: fallthrough228
internal fun CurlMeshKernel.block228(): Int {
    x8 = (x20 + (x20 shl 5L.toInt()))
    x9 = (sp + (21L shl 12L.toInt()))
    x9 = (x9 + 3600L)
    x23 = (x9 + (x8 shl 8L.toInt()))
    x9 = (sp + (28L shl 12L.toInt()))
    x9 = (x9 + 272L)
    x28 = (x9 + (x8 shl 8L.toInt()))
    x27 = (sp + (10L shl 12L.toInt()))
    x27 = (x27 + 1792L)
    x26 = ld_u64((sp + 536L))
    return 229
}

// C source block: L1853464d4
internal fun CurlMeshKernel.block229(): Int {
    x25 = (ld_u32(x27)).toInt().toLong()
    x27 = (x27 + 4L)
    x21 = (((x25 and 0xffffffffL) shl 1L.toInt()) and 0xffffffffL)
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = x21
    x5 = (0L and 0xffffffffL)
    x6 = x21
    x0 = x19
    x3 = 0L
    x4 = x28
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = x21
    x5 = (0L and 0xffffffffL)
    x6 = x21
    x0 = x19
    x3 = 0L
    x4 = x23
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x28 = (x28 + (x25 shl 7L.toInt()))
    x23 = (x23 + (x25 shl 7L.toInt()))
    subflags(x26, 1L, 64L)
    x26 = (x26 - 1L)
    return if (!(Z)) 229 else 230
}

// C source block: L185346544
internal fun CurlMeshKernel.block230(): Int {
    x8 = (ld_u32((sp + 544L)) and 0xffffffffL)
    return if ((((x8 and 0xffffffffL) and (1L shl 0L.toInt())) == 0L)) 232 else 231
}

// C source block: fallthrough231
internal fun CurlMeshKernel.block231(): Int {
    x8 = (sp + (21L shl 12L.toInt()))
    x8 = (x8 + 2832L)
    x4 = (x8 + (x20 shl 8L.toInt()))
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = ((0L or 4L) and 0xffffffffL)
    x6 = ((0L or 4L) and 0xffffffffL)
    x5 = (0L and 0xffffffffL)
    x0 = x19
    x3 = 0L
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    return 232
}

// C source block: L185346580
internal fun CurlMeshKernel.block232(): Int {
    x23 = x22
    x8 = ld_u64((sp + 560L))
    x21 = x8
    subflags((x8 and 0xffffffffL), 1L, 32L)
    return if ((N != V)) 234 else 233
}

// C source block: L185346594
internal fun CurlMeshKernel.block233(): Int {
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = ((0L or 60L) and 0xffffffffL)
    x6 = ((0L or 60L) and 0xffffffffL)
    x5 = (0L and 0xffffffffL)
    x0 = x19
    x3 = 0L
    x4 = x23
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x23 = (x23 + x24)
    subflags(x21, 1L, 64L)
    x21 = (x21 - 1L)
    return if (!(Z)) 233 else 234
}

// C source block: L1853465cc
internal fun CurlMeshKernel.block234(): Int {
    x20 = (x20 + 1L)
    x22 = (x22 + 3840L)
    subflags(x20, 3L, 64L)
    return if (!(Z)) 227 else 235
}

// C source block: L1853465dc
internal fun CurlMeshKernel.block235(): Int {
    x8 = ld_u64((x19 + 8L))
    x22 = ((0L or 3L) and 0xffffffffL)
    st_u8((x8 + 16L), (x22 and 0xffffffffL))
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x21 = (ld_u32((sp + 504L)) and 0xffffffffL)
    x2 = x21
    x5 = (0L and 0xffffffffL)
    x6 = x21
    x0 = x19
    x3 = 0L
    x4 = ld_u64((sp + 496L))
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    fcmp((v8.f(0L.toInt())).toDouble(), (v9.f(0L.toInt())).toDouble())
    x20 = ld_u64((sp + 176L))
    return if (!(Z)) 237 else 236
}

// C source block: fallthrough236
internal fun CurlMeshKernel.block236(): Int {
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x4 = ld_u64((sp + 464L))
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x4 = ld_u64((sp + 456L))
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    return 237
}

// C source block: L18534667c
internal fun CurlMeshKernel.block237(): Int {
    x8 = ld_u64((x19 + 8L))
    x9 = ((0L or 12L) and 0xffffffffL)
    st_u8((x8 + 16L), (x9 and 0xffffffffL))
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = x21
    x5 = (0L and 0xffffffffL)
    x6 = x21
    x0 = x19
    x3 = 0L
    x4 = ld_u64((sp + 488L))
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x4 = ld_u64((sp + 480L))
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x8 = ld_u64(x19)
    x8 = ld_u64((x8 + 208L))
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = x20
    x5 = (0L and 0xffffffffL)
    x6 = x20
    x0 = x19
    x3 = 0L
    x4 = ld_u64((sp + 472L))
    x7 = 0L
    if ((capture_draw(x0, x1, x2, x3, x4, x5, x6, x7) != 0L)) {
        return -2
    }
    x23 = ld_u64((sp + 184L))
    return 238
}

// C source block: L185346710
internal fun CurlMeshKernel.block238(): Int {
    x0 = (sp + 680L)
    x1 = (sp + 1504L)
    x0 = next_clip(x1)
    return if ((((x0 and 0xffffffffL) and (1L shl 0L.toInt())) != 0L)) 222 else 239
}

// C source block: L185346720
internal fun CurlMeshKernel.block239(): Int {
    x2 = (0L and 0xffffffffL)
    x0 = x19
    x1 = 0L
    x8 = ld_u64((sp + 128L))
    x8 = (ld_u8((x8 + 61L)) and 0xffffffffL)
    return if ((((x8 and 0xffffffffL) and (1L shl 5L.toInt())) == 0L)) 241 else 240
}
