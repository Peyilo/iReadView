// Generated from the verified C11 curl geometry; regenerate with tools/Port-CurlKernel.py.
// Direct Kotlin arithmetic and managed scratch storage. No machine instructions are executed.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")
package org.peyilo.pagecurl.mesh.kernel
import kotlin.math.*
import org.peyilo.pagecurl.math.AppleMath
import org.peyilo.pagecurl.math.BinaryMath.fma

// C source block: L185344fdc
internal fun CurlMeshKernel.block96(): Int {
    x15 = v4.u64(0L.toInt())
    return 97
}

// C source block: L185344fe0
internal fun CurlMeshKernel.block97(): Int {
    x10 = (((x10 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 98
}

// C source block: L185344fe4
internal fun CurlMeshKernel.block98(): Int {
    st_u64((x13 + (x9 shl 3L.toInt())), x15)
    x9 = (x9 + 1L)
    subflags((x11 and 0xffffffffL), (x8 and 0xffffffffL), 32L)
    x15 = ((if ((Z || (N != V))) 1L else 0L) and 0xffffffffL)
    return if ((Z || (N != V))) 89 else 99
}

// C source block: fallthrough99
internal fun CurlMeshKernel.block99(): Int {
    subflags((x10 and 0xffffffffL), (x20 and 0xffffffffL), 32L)
    return if ((N != V)) 89 else 100
}

// C source block: L185345000
internal fun CurlMeshKernel.block100(): Int {
    subflags((x9 and 0xffffffffL), 0L, 32L)
    x15 = ((if ((!(Z) && (N == V))) 1L else 0L) and 0xffffffffL)
    return if ((!(Z) && (N == V))) 103 else 101
}

// C source block: fallthrough101
internal fun CurlMeshKernel.block101(): Int {
    subflags((x20 and 0xffffffffL), 1L, 32L)
    return if ((N == V)) 103 else 102
}

// C source block: fallthrough102
internal fun CurlMeshKernel.block102(): Int {
    x17 = (0L and 0xffffffffL)
    return 115
}

// C source block: L18534501c
internal fun CurlMeshKernel.block103(): Int {
    x17 = 0L
    x8 = (0L and 0xffffffffL)
    x10 = (0L and 0xffffffffL)
    x11 = (sp + 3200L)
    x12 = (sp + (4L shl 12L.toInt()))
    x13 = (sp + 2320L)
    x14 = (sp + 2512L)
    return 104
}

// C source block: L185345038
internal fun CurlMeshKernel.block104(): Int {
    subflags((x8 and 0xffffffffL), (x20 and 0xffffffffL), 32L)
    return if (!(Z)) 107 else 105
}

// C source block: fallthrough105
internal fun CurlMeshKernel.block105(): Int {
    x15 = ld_u64((x11 + (((x10 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())))
    return 106
}

// C source block: L185345044
internal fun CurlMeshKernel.block106(): Int {
    x10 = (((x10 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 113
}

// C source block: L18534504c
internal fun CurlMeshKernel.block107(): Int {
    return if ((((x15 and 0xffffffffL) and (1L shl 0L.toInt())) == 0L)) 110 else 108
}

// C source block: fallthrough108
internal fun CurlMeshKernel.block108(): Int {
    v4.d(0L.toInt(), ld_d((x11 + (((x10 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))))
    v3.d(0L.toInt(), ld_d((x13 + (((x8 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))))
    fcmp(v4.d(0L.toInt()), v3.d(0L.toInt()))
    return if ((Z || (N != V))) 111 else 109
}

// C source block: fallthrough109
internal fun CurlMeshKernel.block109(): Int {
    x15 = v4.u64(0L.toInt())
    return 106
}

// C source block: L185345068
internal fun CurlMeshKernel.block110(): Int {
    x16 = ((x8 and 0xffffffffL)).toInt().toLong()
    x15 = ld_u64((x13 + (((x8 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())))
    return 112
}

// C source block: L185345074
internal fun CurlMeshKernel.block111(): Int {
    x16 = ((x8 and 0xffffffffL)).toInt().toLong()
    x15 = v3.u64(0L.toInt())
    return 112
}

// C source block: L18534507c
internal fun CurlMeshKernel.block112(): Int {
    st_u32((x14 + (x16 shl 2L.toInt())), (x17 and 0xffffffffL))
    x8 = (((x8 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 113
}

// C source block: L185345084
internal fun CurlMeshKernel.block113(): Int {
    st_u64((x12 + (x17 shl 3L.toInt())), x15)
    x17 = (x17 + 1L)
    subflags((x10 and 0xffffffffL), (x9 and 0xffffffffL), 32L)
    x15 = ((if ((N != V)) 1L else 0L) and 0xffffffffL)
    return if ((N != V)) 104 else 114
}

// C source block: fallthrough114
internal fun CurlMeshKernel.block114(): Int {
    subflags((x8 and 0xffffffffL), (x20 and 0xffffffffL), 32L)
    return if ((N != V)) 104 else 115
}

// C source block: L1853450a0
internal fun CurlMeshKernel.block115(): Int {
    x30 = (((x17 and 0xffffffffL) - 2L) and 0xffffffffL)
    x14 = (((x20 and 0xffffffffL) + 1L) and 0xffffffffL)
    x8 = (sp + 2512L)
    st_u32((x8 + (((x20 and 0xffffffffL) and 0xffffffffL) shl 2L.toInt())), (x30 and 0xffffffffL))
    x9 = (sp + 2496L)
    x10 = (sp + (4L shl 12L.toInt()))
    x11 = ((0L or 1L) and 0xffffffffL)
    x12 = ((0L or 2L) and 0xffffffffL)
    x13 = ((0L or 4L) and 0xffffffffL)
    st_u64((sp + 560L), x14)
    return 116
}

// C source block: L1853450c8
internal fun CurlMeshKernel.block116(): Int {
    x15 = (ld_u32(x8)).toInt().toLong()
    v3.d(0L.toInt(), ld_d((x10 + (x15 shl 3L.toInt()))))
    fcmp(v3.d(0L.toInt()), v0.d(0L.toInt()))
    return if (!(Z)) 118 else 117
}

// C source block: fallthrough117
internal fun CurlMeshKernel.block117(): Int {
    st_u32(x9, (x11 and 0xffffffffL))
    return 123
}

// C source block: L1853450e0
internal fun CurlMeshKernel.block118(): Int {
    fcmp(v3.d(0L.toInt()), v1.d(0L.toInt()))
    return if (!(Z)) 120 else 119
}

// C source block: fallthrough119
internal fun CurlMeshKernel.block119(): Int {
    st_u32(x9, 0L)
    return 123
}

// C source block: L1853450f0
internal fun CurlMeshKernel.block120(): Int {
    fcmp(v3.d(0L.toInt()), v2.d(0L.toInt()))
    return if (!(Z)) 122 else 121
}

// C source block: fallthrough121
internal fun CurlMeshKernel.block121(): Int {
    st_u32(x9, (x12 and 0xffffffffL))
    return 123
}

// C source block: L185345100
internal fun CurlMeshKernel.block122(): Int {
    st_u32(x9, (x13 and 0xffffffffL))
    return 123
}

// C source block: L185345104
internal fun CurlMeshKernel.block123(): Int {
    x9 = (x9 + 4L)
    x8 = (x8 + 4L)
    subflags(x14, 1L, 64L)
    x14 = (x14 - 1L)
    return if (!(Z)) 116 else 124
}

// C source block: fallthrough124
internal fun CurlMeshKernel.block124(): Int {
    st_d((sp + 320L), v20.d(0L.toInt()))
    st_d(((sp + 320L) + 8L), v19.d(0L.toInt()))
    v0.d(0L.toInt(), ld_d(x24))
    v1.d(0L.toInt(), 1.350000023841858)
    v1.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v2.d(0L.toInt(), 1.0)
    fcmp(v1.d(0L.toInt()), v2.d(0L.toInt()))
    x8 = ((if ((!(Z) && (N == V))) 1L else 0L) and 0xffffffffL)
    v1.d(0L.toInt(), (if ((!(Z) && (N == V))) v2.d(0L.toInt()) else v1.d(0L.toInt())))
    v2.d(0L.toInt(), 0.4000000059604645)
    fcmp(v1.d(0L.toInt()), v2.d(0L.toInt()))
    x9 = ((if (N) 1L else 0L) and 0xffffffffL)
    x8 = (((x8 and 0xffffffffL) or (x9 and 0xffffffffL)) and 0xffffffffL)
    x10 = CLAMP_STEPS
    v3.d(0L.toInt(), ld_d((x10 + (((x9 and 0xffffffffL) and 0xffffffffL) shl 3L.toInt()))))
    v4.d(0L.toInt(), 229.50000405311584)
    v4.d(0L.toInt(), (v0.d(0L.toInt()) * v4.d(0L.toInt())))
    subflags((x8 and 0xffffffffL), 0L, 32L)
    v3.d(0L.toInt(), (if (!(Z)) v3.d(0L.toInt()) else v4.d(0L.toInt())))
    st_d((sp + 568L), v3.d(0L.toInt()))
    subflags((x17 and 0xffffffffL), 0L, 32L)
    st_u64((sp + 288L), x17)
    return if ((Z || (N != V))) 156 else 125
}

// C source block: fallthrough125
internal fun CurlMeshKernel.block125(): Int {
    x25 = x30
    x21 = 0L
    v3.d(0L.toInt(), 0.30000001192092896)
    v4.d(0L.toInt(), 30.0)
    v3.d(0L.toInt(), fma(v13.d(0L.toInt()), v3.d(0L.toInt()), v4.d(0L.toInt())))
    v4.d(0L.toInt(), 50.0)
    v4.d(0L.toInt(), (v13.d(0L.toInt()) + v4.d(0L.toInt())))
    v3.d(0L.toInt(), (v3.d(0L.toInt()) / v4.d(0L.toInt())))
    v3.d(0L.toInt(), (v13.d(0L.toInt()) * v3.d(0L.toInt())))
    fcmp(v1.d(0L.toInt()), v2.d(0L.toInt()))
    x9 = ((if (N) 1L else 0L) and 0xffffffffL)
    x10 = CLAMP_STEPS2
    v1.d(0L.toInt(), ld_d((x10 + (((x9 and 0xffffffffL) and 0xffffffffL) shl 3L.toInt()))))
    v2.d(0L.toInt(), 243.00000429153442)
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v2.d(0L.toInt())))
    subflags((x8 and 0xffffffffL), 0L, 32L)
    v0.d(0L.toInt(), (if (!(Z)) v1.d(0L.toInt()) else v0.d(0L.toInt())))
    v1.d(0L.toInt(), 1.0)
    v2.d(0L.toInt(), (v1.d(0L.toInt()) / v13.d(0L.toInt())))
    st_d((sp + 424L), v2.d(0L.toInt()))
    v2.d(0L.toInt(), ld_d((sp + 568L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) - v2.d(0L.toInt())))
    st_d((sp + 416L), v0.d(0L.toInt()))
    st_d((sp + 472L), v3.d(0L.toInt()))
    v0.d(0L.toInt(), (v1.d(0L.toInt()) / v3.d(0L.toInt())))
    st_d((sp + 400L), v0.d(0L.toInt()))
    x24 = ((x17 and 0xffffffffL) and 0xffffffffL)
    x22 = (sp + (4L shl 12L.toInt()))
    x23 = (sp + (7L shl 12L.toInt()))
    x23 = (x23 + 896L)
    v0.d(0L.toInt(), 0.00392156862745098)
    st_d((sp + 392L), v0.d(0L.toInt()))
    run {
        var t = v8.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    v0.store(memory, (sp + 368L))
    x26 = (sp + (28L shl 12L.toInt()))
    x26 = (x26 + 272L)
    v0.u64(0L.toInt(), 4575657221408423936L)
    v0.u64(1L.toInt(), 0L)
    v0.store(memory, (sp + 352L))
    x28 = (sp + (21L shl 12L.toInt()))
    x28 = (x28 + 3600L)
    x27 = x24
    st_d((sp + 544L), v21.d(0L.toInt()))
    v12.d(0L.toInt(), ld_d((sp + 528L)))
    v11.d(0L.toInt(), ld_d((sp + 520L)))
    return 126
}

// C source block: L185345244
internal fun CurlMeshKernel.block126(): Int {
    v8.d(0L.toInt(), ld_d(x22))
    x22 = (x22 + 8L)
    v4.set(v15)
    v15.d(0L.toInt(), ld_d((sp + 536L)))
    v9.d(0L.toInt(), fma(v15.d(0L.toInt()), v8.d(0L.toInt()), v22.d(0L.toInt())))
    v10.d(0L.toInt(), fma(v12.d(0L.toInt()), v8.d(0L.toInt()), v21.d(0L.toInt())))
    v0.set(v8)
    v1.set(v11)
    v2.set(v13)
    v3.d(0L.toInt(), ld_d((sp + 496L)))
    x0 = 0L
    v14.set(v22)
    v0.f(0L.toInt(), displace(v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), x0, v3.d(0L.toInt()), v4.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v13.d(0L.toInt(), fma(v15.d(0L.toInt()), v0.d(0L.toInt()), v9.d(0L.toInt())))
    v15.d(0L.toInt(), fma(v12.d(0L.toInt()), v0.d(0L.toInt()), v10.d(0L.toInt())))
    x0 = (sp + 648L)
    x1 = (sp + 640L)
    v2.d(0L.toInt(), ld_d((sp + 456L)))
    v0.d(0L.toInt(), ld_d(((sp + 456L) + 8L)))
    v3.d(0L.toInt(), ld_d((sp + 432L)))
    v1.d(0L.toInt(), ld_d(((sp + 432L) + 8L)))
    v4.set(v9)
    v9.d(0L.toInt(), ld_d((sp + 480L)))
    v5.set(v10)
    v7.d(0L.toInt(), ld_d((sp + 224L)))
    v6.d(0L.toInt(), ld_d(((sp + 224L) + 8L)))
    line_clip(x0, x1, v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt()), v4.d(0L.toInt()), v5.d(0L.toInt()), v6.d(0L.toInt()), v7.d(0L.toInt()))
    v22.set(v14)
    v21.d(0L.toInt(), ld_d((sp + 544L)))
    v0.d(0L.toInt(), (v11.d(0L.toInt()) - v8.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 424L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 448L)))
    v0.d(0L.toInt(), min(v0.d(0L.toInt()), v1.d(0L.toInt())))
    v0.f(0L.toInt(), (v0.d(0L.toInt())).toFloat())
    v1.f(0L.toInt(), (v0.f(0L.toInt()) * v0.f(0L.toInt())))
    v3.f(0L.toInt(), ld_f((sp + 152L)))
    v2.f(0L.toInt(), ld_f(((sp + 152L) + 4L)))
    v2.f(0L.toInt(), fma(v1.f(0L.toInt()), v3.f(0L.toInt()), v2.f(0L.toInt())))
    v3.f(0L.toInt(), ld_f((sp + 148L)))
    v1.f(0L.toInt(), fma(v1.f(0L.toInt()), v2.f(0L.toInt()), v3.f(0L.toInt())))
    v0.f(0L.toInt(), (v1.f(0L.toInt()) * v0.f(0L.toInt())))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v1.d(0L.toInt(), ld_d((sp + 472L)))
    v0.d(0L.toInt(), (v1.d(0L.toInt()) * v0.d(0L.toInt())))
    st_d(x23, v0.d(0L.toInt()))
    x23 = (x23 + 8L)
    v1.d(0L.toInt(), 1.0)
    v2.d(0L.toInt(), ld_d((sp + 400L)))
    v0.d(0L.toInt(), fma(-(v0.d(0L.toInt())), v2.d(0L.toInt()), v1.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 568L)))
    v2.d(0L.toInt(), ld_d((sp + 416L)))
    v0.d(0L.toInt(), fma(v2.d(0L.toInt()), v0.d(0L.toInt()), v1.d(0L.toInt())))
    v1.d(0L.toInt(), ld_d((sp + 392L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v0.f(0L.toInt(), (v0.d(0L.toInt())).toFloat())
    v1.d(0L.toInt(), ld_d((sp + 648L)))
    v4.d(0L.toInt(), ld_d((sp + 488L)))
    v2.d(0L.toInt(), fma(v4.d(0L.toInt()), v1.d(0L.toInt()), v13.d(0L.toInt())))
    v1.d(0L.toInt(), fma(v9.d(0L.toInt()), v1.d(0L.toInt()), v15.d(0L.toInt())))
    v3.d(0L.toInt(), ld_d((sp + 640L)))
    v4.d(0L.toInt(), fma(v4.d(0L.toInt()), v3.d(0L.toInt()), v13.d(0L.toInt())))
    v5.load(memory, (sp + 368L))
    run {
        var ta = v5.copy()
        var tb = v0.copy()
        var tc = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    v3.d(0L.toInt(), fma(v9.d(0L.toInt()), v3.d(0L.toInt()), v15.d(0L.toInt())))
    v15.d(0L.toInt(), ld_d((sp + 504L)))
    v13.d(0L.toInt(), ld_d(((sp + 504L) + 8L)))
    v2.f(0L.toInt(), (v2.d(0L.toInt())).toFloat())
    x8 = (x26 + x21)
    v1.f(0L.toInt(), (v1.d(0L.toInt())).toFloat())
    st_f((x8 + 64L), v2.f(0L.toInt()))
    st_f(((x8 + 64L) + 4L), v1.f(0L.toInt()))
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    v5.load(memory, (sp + 352L))
    v5.store(memory, (x8 + 72L))
    v1.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    x9 = (x28 + x21)
    v2.f(0L.toInt(), (v3.d(0L.toInt())).toFloat())
    st_d((x8 + 104L), v0.d(0L.toInt()))
    st_f(x9, v1.f(0L.toInt()))
    st_f((x9 + 4L), v2.f(0L.toInt()))
    v5.store(memory, (x9 + 8L))
    st_d((x9 + 40L), v0.d(0L.toInt()))
    x21 = (x21 + 128L)
    subflags(x27, 1L, 64L)
    x27 = (x27 - 1L)
    return if (!(Z)) 126 else 127
}

// C source block: fallthrough127
internal fun CurlMeshKernel.block127(): Int {
    x8 = 0L
    x9 = ld_u64((sp + 288L))
    x10 = (((x9 and 0xffffffffL) - 1L) and 0xffffffffL)
    x9 = (sp + (10L shl 12L.toInt()))
    x9 = (x9 + 1792L)
    st_u32(x9, 0L)
    x30 = x25
    x9 = ((x25 and 0xffffffffL)).toInt().toLong()
    x10 = ((x10 and 0xffffffffL)).toInt().toLong()
    v7.u64(0L.toInt(), 0L)
    x11 = ((0L or 1L) and 0xffffffffL)
    st_u64((sp + 568L), x11)
    x11 = (sp + (28L shl 12L.toInt()))
    x11 = (x11 + 272L)
    x12 = (sp + (21L shl 12L.toInt()))
    x12 = (x12 + 3600L)
    x13 = (sp + (10L shl 12L.toInt()))
    x13 = (x13 + 1792L)
    x14 = (sp + 2512L)
    x15 = (sp + 2496L)
    x16 = ((0L or 3L) and 0xffffffffL)
    x17 = (5L and 0xffffffffL)
    x1 = ((0L or 2L) and 0xffffffffL)
    x2 = ((0L or 4L) and 0xffffffffL)
    x3 = (sp + (7L shl 12L.toInt()))
    x3 = (x3 + 896L)
    v0.d(0L.toInt(), 0.0078125)
    x4 = (8448L and 0xffffffffL)
    x5 = (sp + (21L shl 12L.toInt()))
    x5 = (x5 + 3600L)
    x6 = (sp + (28L shl 12L.toInt()))
    x6 = (x6 + 272L)
    v16.u64(0L.toInt(), 0L)
    x7 = FADE_WEIGHTS
    v2.u64(0L.toInt(), 0L)
    v1.u64(0L.toInt(), 0L)
    x28 = FADE_STEPS
    v25.d(0L.toInt(), ld_d((sp + 536L)))
    v26.d(0L.toInt(), ld_d((sp + 528L)))
    v14.d(0L.toInt(), 1.0)
    return 128
}

// C source block: L18534542c
internal fun CurlMeshKernel.block128(): Int {
    x21 = 0L
    x25 = ld_u64((sp + 568L))
    x23 = ((x25 and 0xffffffffL)).toInt().toLong()
    x25 = (x13 + (((x25 and 0xffffffffL)).toInt().toLong() shl 2L.toInt()))
    x26 = (ld_u32((x25 + -(4L))) and 0xffffffffL)
    x26 = (((x26 and 0xffffffffL) + 1L) and 0xffffffffL)
    st_u32((x25 + -(4L)), (x26 and 0xffffffffL))
    return 129
}

// C source block: L185345448
internal fun CurlMeshKernel.block129(): Int {
    x25 = (ld_u32((x14 + (x21 shl 2L.toInt()))) and 0xffffffffL)
    subflags(x8, x25, 64L)
    return if (Z) 132 else 130
}

// C source block: fallthrough130
internal fun CurlMeshKernel.block130(): Int {
    x25 = (x21 + 1L)
    subflags(x21, x20, 64L)
    x21 = x25
    return if (!(C)) 129 else 131
}

// C source block: fallthrough131
internal fun CurlMeshKernel.block131(): Int {
    x23 = (0L and 0xffffffffL)
    x21 = ((0L or 4L) and 0xffffffffL)
    return 133
}

// C source block: L185345470
internal fun CurlMeshKernel.block132(): Int {
    x21 = (ld_u32((x15 + (x21 shl 2L.toInt()))) and 0xffffffffL)
    x25 = ld_u64((sp + 568L))
    x25 = (((x25 and 0xffffffffL) + 1L) and 0xffffffffL)
    st_u64((sp + 568L), x25)
    st_u32((x13 + (x23 shl 2L.toInt())), 0L)
    x23 = ((0L or 1L) and 0xffffffffL)
    return 133
}

// C source block: L185345488
internal fun CurlMeshKernel.block133(): Int {
    subflags(x8, x10, 64L)
    return if ((N == V)) 145 else 134
}

// C source block: fallthrough134
internal fun CurlMeshKernel.block134(): Int {
    x26 = (((x23 and 0xffffffffL) xor 1L) and 0xffffffffL)
    subflags((x21 and 0xffffffffL), 2L, 32L)
    return if (Z) 136 else 135
}

// C source block: fallthrough135
internal fun CurlMeshKernel.block135(): Int {
    return if (((x26 and 0xffffffffL) == 0L)) 139 else 136
}

// C source block: L1853454a0
internal fun CurlMeshKernel.block136(): Int {
    subflags((x21 and 0xffffffffL), 2L, 32L)
    x25 = ((if (Z) 1L else 0L) and 0xffffffffL)
    x25 = (((x23 and 0xffffffffL) and (x25 and 0xffffffffL)) and 0xffffffffL)
    subflags(x8, x9, 64L)
    x27 = ((if ((N != V)) 1L else 0L) and 0xffffffffL)
    N = (0L != 0L)
    Z = (((x27 and 0xffffffffL) and (x25 and 0xffffffffL)) == 0L)
    V = (0L != 0L)
    C = V
    x25 = ((if (!(Z)) (x17 and 0xffffffffL) else (x16 and 0xffffffffL)) and 0xffffffffL)
    x25 = (((x25 and 0xffffffffL) + ((x8 and 0xffffffffL) shl 1L.toInt())) and 0xffffffffL)
    x27 = ((0L or 64L) and 0xffffffffL)
    x27 = ((x27 and (-128L).inv()) or ((x8 and 144115188075855871L) shl 7L.toInt()))
    x27 = (x11 + x27)
    x25 = (x11 + (x25 shl 6L.toInt()))
    v5.f(0L.toInt(), ld_f(x27))
    v6.f(0L.toInt(), ld_f((x27 + 4L)))
    v17.f(0L.toInt(), ld_f(x25))
    v18.f(0L.toInt(), ld_f((x25 + 4L)))
    v6.d(0L.toInt(), (v6.f(0L.toInt())).toDouble())
    v5.d(0L.toInt(), (v5.f(0L.toInt())).toDouble())
    v18.d(0L.toInt(), (v18.f(0L.toInt())).toDouble())
    v17.d(0L.toInt(), (v17.f(0L.toInt())).toDouble())
    v18.d(0L.toInt(), (v18.d(0L.toInt()) - v6.d(0L.toInt())))
    v6.d(0L.toInt(), (v5.d(0L.toInt()) - v17.d(0L.toInt())))
    v5.d(0L.toInt(), (v6.d(0L.toInt()) * v6.d(0L.toInt())))
    v5.d(0L.toInt(), fma(v18.d(0L.toInt()), v18.d(0L.toInt()), v5.d(0L.toInt())))
    v5.d(0L.toInt(), sqrt(v5.d(0L.toInt())))
    v17.d(0L.toInt(), (v14.d(0L.toInt()) / v5.d(0L.toInt())))
    v5.d(0L.toInt(), (v18.d(0L.toInt()) * v17.d(0L.toInt())))
    fcmp(v5.d(0L.toInt()), v5.d(0L.toInt()))
    return if (V) 139 else 137
}

// C source block: fallthrough137
internal fun CurlMeshKernel.block137(): Int {
    v6.d(0L.toInt(), (v6.d(0L.toInt()) * v17.d(0L.toInt())))
    v18.d(0L.toInt(), abs(v5.d(0L.toInt())))
    v17.d(0L.toInt(), (Double.POSITIVE_INFINITY).toDouble())
    fcmp(v18.d(0L.toInt()), v17.d(0L.toInt()))
    if (!(Z)) {
        run {
            fcmp(v6.d(0L.toInt()), v6.d(0L.toInt()))
        }
    } else {
        run {
            N = (0L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (1L != 0L)
        }
    }
    return if (V) 139 else 138
}

// C source block: fallthrough138
internal fun CurlMeshKernel.block138(): Int {
    v18.d(0L.toInt(), abs(v6.d(0L.toInt())))
    fcmp(v18.d(0L.toInt()), v17.d(0L.toInt()))
    return if (!(Z)) 140 else 139
}

// C source block: L185345530
internal fun CurlMeshKernel.block139(): Int {
    v5.set(v16)
    v6.set(v7)
    return 140
}

// C source block: L185345538
internal fun CurlMeshKernel.block140(): Int {
    subflags((x21 and 0xffffffffL), 0L, 32L)
    x25 = ((if (!(Z)) 1L else 0L) and 0xffffffffL)
    x26 = (((x26 and 0xffffffffL) xor 1L) and 0xffffffffL)
    x25 = (((x25 and 0xffffffffL) and (x26 and 0xffffffffL)) and 0xffffffffL)
    return if ((((x25 and 0xffffffffL) and (1L shl 0L.toInt())) != 0L)) 146 else 141
}

// C source block: fallthrough141
internal fun CurlMeshKernel.block141(): Int {
    subflags((x21 and 0xffffffffL), 0L, 32L)
    x21 = ((if (Z) 1L else 0L) and 0xffffffffL)
    x21 = (((x23 and 0xffffffffL) and (x21 and 0xffffffffL)) and 0xffffffffL)
    subflags(x8, x9, 64L)
    x23 = ((if ((N != V)) 1L else 0L) and 0xffffffffL)
    N = (0L != 0L)
    Z = (((x23 and 0xffffffffL) and (x21 and 0xffffffffL)) == 0L)
    V = (0L != 0L)
    C = V
    x21 = ((if (!(Z)) (x2 and 0xffffffffL) else (x1 and 0xffffffffL)) and 0xffffffffL)
    x21 = (((x21 and 0xffffffffL) + ((x8 and 0xffffffffL) shl 1L.toInt())) and 0xffffffffL)
    x23 = (x12 + (x8 shl 7L.toInt()))
    x21 = (x12 + (x21 shl 6L.toInt()))
    v7.f(0L.toInt(), ld_f(x23))
    v16.f(0L.toInt(), ld_f((x23 + 4L)))
    v17.f(0L.toInt(), ld_f(x21))
    v18.f(0L.toInt(), ld_f((x21 + 4L)))
    v19.d(0L.toInt(), (v16.f(0L.toInt())).toDouble())
    v7.d(0L.toInt(), (v7.f(0L.toInt())).toDouble())
    v18.d(0L.toInt(), (v18.f(0L.toInt())).toDouble())
    v16.d(0L.toInt(), (v17.f(0L.toInt())).toDouble())
    v16.d(0L.toInt(), (v16.d(0L.toInt()) - v7.d(0L.toInt())))
    v7.d(0L.toInt(), (v19.d(0L.toInt()) - v18.d(0L.toInt())))
    v17.d(0L.toInt(), (v16.d(0L.toInt()) * v16.d(0L.toInt())))
    v17.d(0L.toInt(), fma(v7.d(0L.toInt()), v7.d(0L.toInt()), v17.d(0L.toInt())))
    v17.d(0L.toInt(), sqrt(v17.d(0L.toInt())))
    v17.d(0L.toInt(), (v14.d(0L.toInt()) / v17.d(0L.toInt())))
    v7.d(0L.toInt(), (v7.d(0L.toInt()) * v17.d(0L.toInt())))
    fcmp(v7.d(0L.toInt()), v7.d(0L.toInt()))
    return if (V) 146 else 142
}

// C source block: fallthrough142
internal fun CurlMeshKernel.block142(): Int {
    v16.d(0L.toInt(), (v16.d(0L.toInt()) * v17.d(0L.toInt())))
    v18.d(0L.toInt(), abs(v7.d(0L.toInt())))
    v17.d(0L.toInt(), (Double.POSITIVE_INFINITY).toDouble())
    fcmp(v18.d(0L.toInt()), v17.d(0L.toInt()))
    if (!(Z)) {
        run {
            fcmp(v16.d(0L.toInt()), v16.d(0L.toInt()))
        }
    } else {
        run {
            N = (0L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (1L != 0L)
        }
    }
    return if (V) 146 else 143
}

// C source block: fallthrough143
internal fun CurlMeshKernel.block143(): Int {
    v18.d(0L.toInt(), abs(v16.d(0L.toInt())))
    fcmp(v18.d(0L.toInt()), v17.d(0L.toInt()))
    return if (Z) 146 else 144
}
