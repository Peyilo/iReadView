// Generated from the verified C11 curl geometry; regenerate with tools/Port-CurlKernel.py.
// Direct Kotlin arithmetic and managed scratch storage. No machine instructions are executed.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")
package org.peyilo.pagecurl.mesh.kernel
import kotlin.math.*
import org.peyilo.pagecurl.math.AppleMath
import org.peyilo.pagecurl.math.BinaryMath.fma

// C source block: fallthrough144
internal fun CurlMeshKernel.block144(): Int {
    v1.set(v7)
    v2.set(v16)
    return 146
}

// C source block: L1853455e0
internal fun CurlMeshKernel.block145(): Int {
    v5.set(v16)
    v6.set(v7)
    return 146
}

// C source block: L1853455e8
internal fun CurlMeshKernel.block146(): Int {
    x21 = 0L
    x23 = 0L
    x26 = (x8 shl 1L.toInt())
    x25 = ((0L or 64L) and 0xffffffffL)
    x25 = ((x25 and (-128L).inv()) or ((x8 and 144115188075855871L) shl 7L.toInt()))
    x25 = (x11 + x25)
    v7.f(0L.toInt(), ld_f(x25))
    v17.f(0L.toInt(), ld_f((x25 + 4L)))
    v16.d(0L.toInt(), (v7.f(0L.toInt())).toDouble())
    v17.d(0L.toInt(), (v17.f(0L.toInt())).toDouble())
    v18.d(0L.toInt(), ld_d((x25 + 40L)))
    v7.d(0L.toInt(), ld_d((x3 + (x8 shl 3L.toInt()))))
    run {
        var t = v18.copy()
        for (k in 0L until 4L) {
            v18.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    return 147
}

// C source block: L185345618
internal fun CurlMeshKernel.block147(): Int {
    v19.d(0L.toInt(), ld_d((x7 + x23)))
    v20.d(0L.toInt(), ld_d((x28 + x23)))
    return if ((x21 == 0L)) 149 else 148
}

// C source block: fallthrough148
internal fun CurlMeshKernel.block148(): Int {
    v4.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    x25 = (x6 + x21)
    v3.f(0L.toInt(), (v3.d(0L.toInt())).toFloat())
    st_f((x25 + 64L), v4.f(0L.toInt()))
    st_f(((x25 + 64L) + 4L), v3.f(0L.toInt()))
    st_d((x25 + 72L), v0.d(0L.toInt()))
    st_u64((x25 + 104L), x22)
    return 149
}

// C source block: L18534563c
internal fun CurlMeshKernel.block149(): Int {
    v3.d(0L.toInt(), (v5.d(0L.toInt()) * v20.d(0L.toInt())))
    v20.d(0L.toInt(), (v6.d(0L.toInt()) * v20.d(0L.toInt())))
    v4.d(0L.toInt(), fma(v3.d(0L.toInt()), v7.d(0L.toInt()), v16.d(0L.toInt())))
    v3.d(0L.toInt(), fma(v20.d(0L.toInt()), v7.d(0L.toInt()), v17.d(0L.toInt())))
    v20.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    x22 = (x6 + x21)
    v21.f(0L.toInt(), (v3.d(0L.toInt())).toFloat())
    st_f(x22, v20.f(0L.toInt()))
    st_f((x22 + 4L), v21.f(0L.toInt()))
    st_d((x22 + 8L), v0.d(0L.toInt()))
    v19.f(0L.toInt(), (v19.d(0L.toInt())).toFloat())
    run {
        var ta = v18.copy()
        var tb = v19.copy()
        var tc = v19.copy()
        for (k in 0L until 4L) {
            v19.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    run {
        var t = v19.copy()
        for (k in 0L until 4L) {
            v19.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    st_d((sp + 576L), v19.d(0L.toInt()))
    st_d((x22 + 40L), v19.d(0L.toInt()))
    x22 = v19.u64(0L.toInt())
    x23 = (x23 + 8L)
    x21 = (x21 + x4)
    subflags(x23, 24L, 64L)
    return if (!(Z)) 147 else 150
}

// C source block: fallthrough150
internal fun CurlMeshKernel.block150(): Int {
    x21 = 0L
    x23 = 0L
    x25 = (x12 + (x26 shl 6L.toInt()))
    v16.f(0L.toInt(), ld_f(x25))
    v17.f(0L.toInt(), ld_f((x25 + 4L)))
    v16.d(0L.toInt(), (v16.f(0L.toInt())).toDouble())
    v17.d(0L.toInt(), (v17.f(0L.toInt())).toDouble())
    v18.d(0L.toInt(), ld_d((x25 + 40L)))
    run {
        var t = v18.copy()
        for (k in 0L until 4L) {
            v18.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    return 151
}

// C source block: L1853456a8
internal fun CurlMeshKernel.block151(): Int {
    v19.d(0L.toInt(), ld_d((x7 + x23)))
    v20.d(0L.toInt(), ld_d((x28 + x23)))
    return if ((x21 == 0L)) 153 else 152
}

// C source block: fallthrough152
internal fun CurlMeshKernel.block152(): Int {
    v4.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    x25 = (x5 + x21)
    st_f(x25, v4.f(0L.toInt()))
    v3.f(0L.toInt(), (v3.d(0L.toInt())).toFloat())
    st_f((x25 + 4L), v3.f(0L.toInt()))
    st_d((x25 + 8L), v0.d(0L.toInt()))
    st_u64((x25 + 40L), x22)
    return 153
}

// C source block: L1853456d0
internal fun CurlMeshKernel.block153(): Int {
    v3.d(0L.toInt(), (v1.d(0L.toInt()) * v20.d(0L.toInt())))
    v20.d(0L.toInt(), (v2.d(0L.toInt()) * v20.d(0L.toInt())))
    v4.d(0L.toInt(), fma(v3.d(0L.toInt()), v7.d(0L.toInt()), v16.d(0L.toInt())))
    v3.d(0L.toInt(), fma(v20.d(0L.toInt()), v7.d(0L.toInt()), v17.d(0L.toInt())))
    v20.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    x22 = (x5 + x21)
    v21.f(0L.toInt(), (v3.d(0L.toInt())).toFloat())
    st_f((x22 + 64L), v20.f(0L.toInt()))
    st_f(((x22 + 64L) + 4L), v21.f(0L.toInt()))
    st_d((x22 + 72L), v0.d(0L.toInt()))
    v19.f(0L.toInt(), (v19.d(0L.toInt())).toFloat())
    run {
        var ta = v18.copy()
        var tb = v19.copy()
        var tc = v19.copy()
        for (k in 0L until 4L) {
            v19.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    run {
        var t = v19.copy()
        for (k in 0L until 4L) {
            v19.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    st_d((sp + 576L), v19.d(0L.toInt()))
    st_d((x22 + 104L), v19.d(0L.toInt()))
    x22 = v19.u64(0L.toInt())
    x23 = (x23 + 8L)
    x21 = (x21 + x4)
    subflags(x23, 24L, 64L)
    return if (!(Z)) 151 else 154
}

// C source block: fallthrough154
internal fun CurlMeshKernel.block154(): Int {
    x8 = (x8 + 1L)
    x6 = (x6 + 128L)
    x5 = (x5 + 128L)
    v7.set(v6)
    v16.set(v5)
    subflags(x8, x24, 64L)
    return if (!(Z)) 128 else 155
}

// C source block: fallthrough155
internal fun CurlMeshKernel.block155(): Int {
    return 157
}

// C source block: L18534573c
internal fun CurlMeshKernel.block156(): Int {
    x8 = (sp + (10L shl 12L.toInt()))
    x8 = (x8 + 1792L)
    st_u32(x8, 0L)
    x8 = ((0L or 1L) and 0xffffffffL)
    st_u64((sp + 568L), x8)
    v25.d(0L.toInt(), ld_d((sp + 536L)))
    v26.d(0L.toInt(), ld_d((sp + 528L)))
    v14.d(0L.toInt(), 1.0)
    return 157
}

// C source block: L18534575c
internal fun CurlMeshKernel.block157(): Int {
    fcmp(v25.d(0L.toInt()), 0.0)
    return if (Z) 169 else 158
}

// C source block: fallthrough158
internal fun CurlMeshKernel.block158(): Int {
    v0.d(0L.toInt(), abs(v25.d(0L.toInt())))
    fcmp(v0.d(0L.toInt()), v14.d(0L.toInt()))
    return if (Z) 169 else 159
}

// C source block: fallthrough159
internal fun CurlMeshKernel.block159(): Int {
    x8 = 0L
    x9 = ld_u64((sp + 560L))
    x9 = (x9 shl 4L.toInt())
    x10 = (sp + 2336L)
    x11 = (sp + 2496L)
    x12 = (sp + 2512L)
    x13 = (sp + 1504L)
    x14 = (sp + 2368L)
    x15 = (sp + 2432L)
    x16 = (sp + (28L shl 12L.toInt()))
    x16 = (x16 + 272L)
    x17 = (sp + (21L shl 12L.toInt()))
    x17 = (x17 + 3600L)
    x0 = (16896L and 0xffffffffL)
    x1 = (17024L and 0xffffffffL)
    return 160
}

// C source block: L1853457ac
internal fun CurlMeshKernel.block160(): Int {
    x5 = (ld_u32(x12) and 0xffffffffL)
    x3 = ((x5 and 0xffffffffL)).toInt().toLong()
    x4 = (ld_u32(x11) and 0xffffffffL)
    x2 = (x13 + x8)
    st_u64(x2, 0L)
    st_u64((x2 + 8L), 0L)
    x2 = (x14 + x8)
    x6 = (x15 + x8)
    st_u64(x6, 0L)
    st_u64((x6 + 8L), 0L)
    st_u64(x2, 0L)
    st_u64((x2 + 8L), 0L)
    subflags((x5 and 0xffffffffL), (x30 and 0xffffffffL), 32L)
    if ((N != V)) {
        run {
            subflags((x5 and 0xffffffffL), 1L, 32L)
        }
    } else {
        run {
            N = (1L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (0L != 0L)
        }
    }
    if ((N == V)) {
        run {
            subflags((x4 and 0xffffffffL), 0L, 32L)
        }
    } else {
        run {
            N = (0L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (0L != 0L)
        }
    }
    return if (Z) 164 else 161
}

// C source block: fallthrough161
internal fun CurlMeshKernel.block161(): Int {
    subflags((x3 and 0xffffffffL), (x30 and 0xffffffffL), 32L)
    if ((N != V)) {
        run {
            subflags((x3 and 0xffffffffL), 1L, 32L)
        }
    } else {
        run {
            N = (1L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (0L != 0L)
        }
    }
    if ((N == V)) {
        run {
            subflags((x4 and 0xffffffffL), 2L, 32L)
        }
    } else {
        run {
            N = (0L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (0L != 0L)
        }
    }
    return if (Z) 165 else 162
}

// C source block: fallthrough162
internal fun CurlMeshKernel.block162(): Int {
    subflags((x4 and 0xffffffffL), 1L, 32L)
    return if (!(Z)) 167 else 163
}

// C source block: fallthrough163
internal fun CurlMeshKernel.block163(): Int {
    x4 = (x3 shl 1L.toInt())
    x5 = ((0L or 64L) and 0xffffffffL)
    x5 = ((x5 and (-128L).inv()) or ((x3 and 144115188075855871L) shl 7L.toInt()))
    x3 = (x16 + x5)
    v0.d(0L.toInt(), ld_d(x3))
    run {
        var t = v0.copy()
        for (k in 0L until 2L) {
            v0.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v0.store(memory, (x13 + x8))
    x3 = ld_u64((x3 + 40L))
    st_u64(x10, x3)
    x3 = ((x4 and 0xffffffffL)).toInt().toLong()
    x3 = (x16 + (x3 shl 6L.toInt()))
    v0.d(0L.toInt(), ld_d((x3 + 16896L)))
    run {
        var t = v0.copy()
        for (k in 0L until 2L) {
            v0.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v0.store(memory, (x15 + x8))
    x3 = (x17 + x5)
    x3 = (x3 + x0)
    return 166
}

// C source block: L18534583c
internal fun CurlMeshKernel.block164(): Int {
    x4 = (x3 shl 1L.toInt())
    x5 = ((0L or 64L) and 0xffffffffL)
    x5 = ((x5 and (-128L).inv()) or ((x3 and 144115188075855871L) shl 7L.toInt()))
    x3 = (x16 + x5)
    v0.d(0L.toInt(), ld_d(x3))
    run {
        var t = v0.copy()
        for (k in 0L until 2L) {
            v0.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v0.store(memory, (x13 + x8))
    x3 = ld_u64((x3 + 40L))
    st_u64(x10, x3)
    x3 = ((x4 and 0xffffffffL)).toInt().toLong()
    x3 = (x16 + (x3 shl 6L.toInt()))
    v0.d(0L.toInt(), ld_d((x3 + 16896L)))
    run {
        var t = v0.copy()
        for (k in 0L until 2L) {
            v0.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v0.store(memory, (x15 + x8))
    x3 = (x3 + x1)
    return 166
}

// C source block: L18534587c
internal fun CurlMeshKernel.block165(): Int {
    x4 = (x3 shl 1L.toInt())
    x3 = (x17 + (x3 shl 7L.toInt()))
    v0.d(0L.toInt(), ld_d(x3))
    run {
        var t = v0.copy()
        for (k in 0L until 2L) {
            v0.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v0.store(memory, (x13 + x8))
    x3 = ld_u64((x3 + 40L))
    st_u64(x10, x3)
    x3 = ((x4 and 0xffffffffL)).toInt().toLong()
    x4 = (x3 ushr 1L.toInt())
    x5 = (x17 + x0)
    x3 = (x5 + (x3 shl 6L.toInt()))
    v0.d(0L.toInt(), ld_d((x3 + 192L)))
    run {
        var t = v0.copy()
        for (k in 0L until 2L) {
            v0.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v0.store(memory, (x15 + x8))
    x3 = ((0L or 64L) and 0xffffffffL)
    x3 = ((x3 and (-128L).inv()) or ((x4 and 144115188075855871L) shl 7L.toInt()))
    x3 = (x5 + x3)
    return 166
}

// C source block: L1853458c0
internal fun CurlMeshKernel.block166(): Int {
    v0.f(0L.toInt(), ld_f(x3))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    st_d((x14 + x8), v0.d(0L.toInt()))
    x3 = (x3 or 4L)
    v0.f(0L.toInt(), ld_f(x3))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    st_d((x2 + 8L), v0.d(0L.toInt()))
    return 167
}

// C source block: L1853458dc
internal fun CurlMeshKernel.block167(): Int {
    x8 = (x8 + 16L)
    x10 = (x10 + 8L)
    x11 = (x11 + 4L)
    x12 = (x12 + 4L)
    subflags(x9, x8, 64L)
    return if (!(Z)) 160 else 168
}

// C source block: fallthrough168
internal fun CurlMeshKernel.block168(): Int {
    x25 = (0L and 0xffffffffL)
    return 173
}

// C source block: L1853458fc
internal fun CurlMeshKernel.block169(): Int {
    x10 = 0L
    x8 = (sp + (7L shl 12L.toInt()))
    x8 = (x8 + 896L)
    x9 = ld_u64((sp + 288L))
    x8 = (x8 + (((x9 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))
    v2.d(0L.toInt(), ld_d((x8 + -(8L))))
    x9 = ((x9).toInt().toLong() shl 1L.toInt())
    x8 = (x9 - 1L)
    x11 = (sp + (28L shl 12L.toInt()))
    x11 = (x11 + 272L)
    x12 = (x11 + (x8 shl 6L.toInt()))
    v0.f(0L.toInt(), ld_f(x12))
    v1.f(0L.toInt(), ld_f((x12 + 4L)))
    v3.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v4.d(0L.toInt(), (v1.f(0L.toInt())).toDouble())
    x9 = ((x9 and 0xffffffffL)).toInt().toLong()
    x9 = (x9 - 2L)
    x11 = (sp + (21L shl 12L.toInt()))
    x11 = (x11 + 3600L)
    x13 = (x11 + (x9 shl 6L.toInt()))
    v0.f(0L.toInt(), ld_f(x13))
    v1.f(0L.toInt(), ld_f((x13 + 4L)))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v1.d(0L.toInt(), (v1.f(0L.toInt())).toDouble())
    x11 = ld_u64((x12 + 40L))
    x13 = ld_u64((x13 + 40L))
    v5.u64(0L.toInt(), x13)
    x14 = FADE_WEIGHTS
    run {
        var t = v5.copy()
        for (k in 0L until 4L) {
            v5.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    x15 = FADE_STEPS
    x16 = (sp + (21L shl 12L.toInt()))
    x16 = (x16 + 2832L)
    v6.d(0L.toInt(), 0.0078125)
    x17 = x13
    x0 = x11
    v17.set(v4)
    v18.set(v3)
    v7.set(v1)
    v16.set(v0)
    return 171
}

// C source block: L1853459a0
internal fun CurlMeshKernel.block170(): Int {
    x17 = v20.u64(0L.toInt())
    x0 = v19.u64(0L.toInt())
    x10 = (x10 + 256L)
    x14 = (x14 + 8L)
    x15 = (x15 + 8L)
    return 171
}

// C source block: L1853459b4
internal fun CurlMeshKernel.block171(): Int {
    v19.d(0L.toInt(), ld_d(x14))
    v20.d(0L.toInt(), ld_d(x15))
    v18.f(0L.toInt(), (v18.d(0L.toInt())).toFloat())
    x1 = (x16 + x10)
    v17.f(0L.toInt(), (v17.d(0L.toInt())).toFloat())
    st_f(x1, v18.f(0L.toInt()))
    st_f((x1 + 4L), v17.f(0L.toInt()))
    st_d((x1 + 8L), v6.d(0L.toInt()))
    st_u64((x1 + 40L), x0)
    v21.f(0L.toInt(), (v19.d(0L.toInt())).toFloat())
    v17.u64(0L.toInt(), x11)
    run {
        var t = v17.copy()
        for (k in 0L until 4L) {
            v17.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    run {
        var ta = v17.copy()
        var tb = v21.copy()
        var tc = v17.copy()
        for (k in 0L until 4L) {
            v17.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    run {
        var t = v17.copy()
        for (k in 0L until 4L) {
            v19.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    v17.d(0L.toInt(), (v25.d(0L.toInt()) * v20.d(0L.toInt())))
    v22.d(0L.toInt(), (v2.d(0L.toInt()) * v17.d(0L.toInt())))
    v18.d(0L.toInt(), (v22.d(0L.toInt()) + v3.d(0L.toInt())))
    v17.d(0L.toInt(), (v26.d(0L.toInt()) * v20.d(0L.toInt())))
    v23.d(0L.toInt(), (v2.d(0L.toInt()) * v17.d(0L.toInt())))
    v17.d(0L.toInt(), (v23.d(0L.toInt()) + v4.d(0L.toInt())))
    v20.f(0L.toInt(), (v18.d(0L.toInt())).toFloat())
    v24.f(0L.toInt(), (v17.d(0L.toInt())).toFloat())
    st_f((x1 + 64L), v20.f(0L.toInt()))
    st_f(((x1 + 64L) + 4L), v24.f(0L.toInt()))
    st_d((x1 + 72L), v6.d(0L.toInt()))
    st_d((x1 + 104L), v19.d(0L.toInt()))
    v16.f(0L.toInt(), (v16.d(0L.toInt())).toFloat())
    v7.f(0L.toInt(), (v7.d(0L.toInt())).toFloat())
    st_f((x1 + 128L), v16.f(0L.toInt()))
    st_f(((x1 + 128L) + 4L), v7.f(0L.toInt()))
    st_d((x1 + 136L), v6.d(0L.toInt()))
    st_u64((x1 + 168L), x17)
    run {
        var ta = v5.copy()
        var tb = v21.copy()
        var tc = v7.copy()
        for (k in 0L until 4L) {
            v7.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    run {
        var t = v7.copy()
        for (k in 0L until 4L) {
            v20.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    v16.d(0L.toInt(), (v22.d(0L.toInt()) + v0.d(0L.toInt())))
    v7.d(0L.toInt(), (v23.d(0L.toInt()) + v1.d(0L.toInt())))
    v21.f(0L.toInt(), (v16.d(0L.toInt())).toFloat())
    v22.f(0L.toInt(), (v7.d(0L.toInt())).toFloat())
    st_f((x1 + 192L), v21.f(0L.toInt()))
    st_f(((x1 + 192L) + 4L), v22.f(0L.toInt()))
    st_d((x1 + 200L), v6.d(0L.toInt()))
    st_d((x1 + 232L), v20.d(0L.toInt()))
    subflags(x10, 512L, 64L)
    return if (!(Z)) 170 else 172
}

// C source block: fallthrough172
internal fun CurlMeshKernel.block172(): Int {
    v2.d(0L.toInt(), ld_d(x12))
    run {
        var t = v2.copy()
        for (k in 0L until 2L) {
            v2.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v2.store(memory, (sp + 1504L))
    st_u64((sp + 2344L), x13)
    st_u64((sp + 2336L), x11)
    x10 = (sp + (28L shl 12L.toInt()))
    x10 = (x10 + 272L)
    x9 = (x10 + (x9 shl 6L.toInt()))
    v2.d(0L.toInt(), ld_d((x9 + 16896L)))
    run {
        var t = v2.copy()
        for (k in 0L until 2L) {
            v2.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    x9 = (sp + (21L shl 12L.toInt()))
    x9 = (x9 + 2832L)
    v3.d(0L.toInt(), ld_d((x9 + 704L)))
    run {
        var t = v3.copy()
        for (k in 0L until 2L) {
            v3.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v3.store(memory, (sp + 2448L))
    v2.store(memory, (sp + 2432L))
    v2.d(0L.toInt(), ld_d((x9 + 576L)))
    run {
        var t = v2.copy()
        for (k in 0L until 2L) {
            v2.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    x9 = (sp + (21L shl 12L.toInt()))
    x9 = (x9 + 3600L)
    x8 = (x9 + (x8 shl 6L.toInt()))
    v3.d(0L.toInt(), ld_d((x8 + 16896L)))
    run {
        var t = v3.copy()
        for (k in 0L until 2L) {
            v3.d(k.toInt(), (t.f(k.toInt())).toDouble())
        }
    }
    v3.store(memory, (sp + 2384L))
    v2.store(memory, (sp + 2368L))
    st_d((sp + 1528L), v1.d(0L.toInt()))
    st_d((sp + 1520L), v0.d(0L.toInt()))
    x25 = ((0L or 1L) and 0xffffffffL)
    x8 = ((0L or 2L) and 0xffffffffL)
    st_u64((sp + 560L), x8)
    return 173
}

// C source block: L185345acc
internal fun CurlMeshKernel.block173(): Int {
    x21 = 0L
    x22 = (sp + (10L shl 12L.toInt()))
    x22 = (x22 + 1808L)
    v8.d(0L.toInt(), 0.034482758620689655)
    x27 = (sp + 2336L)
    x28 = FADE_WEIGHTS
    v20.u64(0L.toInt(), 4575657221408423936L)
    v20.u64(1L.toInt(), 0L)
    x20 = (11528L and 0xffffffffL)
    x23 = (11592L and 0xffffffffL)
    x24 = (11520L and 0xffffffffL)
    x26 = FADE_STEPS
    v20.store(memory, (sp + 544L))
    return 174
}

// C source block: L185345b0c
internal fun CurlMeshKernel.block174(): Int {
    x8 = (sp + 2432L)
    x8 = (x8 + (x21 shl 4L.toInt()))
    x9 = (sp + 1504L)
    x9 = (x9 + (x21 shl 4L.toInt()))
    v0.d(0L.toInt(), ld_d(x8))
    v1.d(0L.toInt(), ld_d((x8 + 8L)))
    v9.d(0L.toInt(), ld_d(x9))
    v10.d(0L.toInt(), ld_d((x9 + 8L)))
    v12.d(0L.toInt(), (v0.d(0L.toInt()) - v9.d(0L.toInt())))
    v11.d(0L.toInt(), (v1.d(0L.toInt()) - v10.d(0L.toInt())))
    x8 = (sp + 2368L)
    x8 = (x8 + (x21 shl 4L.toInt()))
    v0.d(0L.toInt(), ld_d(x8))
    v1.d(0L.toInt(), ld_d((x8 + 8L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) - v9.d(0L.toInt())))
    v1.d(0L.toInt(), (v1.d(0L.toInt()) - v10.d(0L.toInt())))
    v2.d(0L.toInt(), (v11.d(0L.toInt()) * v11.d(0L.toInt())))
    v2.d(0L.toInt(), fma(v12.d(0L.toInt()), v12.d(0L.toInt()), v2.d(0L.toInt())))
    v2.d(0L.toInt(), sqrt(v2.d(0L.toInt())))
    v2.d(0L.toInt(), (v14.d(0L.toInt()) / v2.d(0L.toInt())))
    v3.d(0L.toInt(), (v12.d(0L.toInt()) * v2.d(0L.toInt())))
    v2.d(0L.toInt(), (v11.d(0L.toInt()) * v2.d(0L.toInt())))
    v4.d(0L.toInt(), (v1.d(0L.toInt()) * v1.d(0L.toInt())))
    v4.d(0L.toInt(), fma(v0.d(0L.toInt()), v0.d(0L.toInt()), v4.d(0L.toInt())))
    v4.d(0L.toInt(), sqrt(v4.d(0L.toInt())))
    v4.d(0L.toInt(), (v14.d(0L.toInt()) / v4.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v4.d(0L.toInt())))
    v1.d(0L.toInt(), (v1.d(0L.toInt()) * v4.d(0L.toInt())))
    v1.d(0L.toInt(), (v2.d(0L.toInt()) * v1.d(0L.toInt())))
    v0.d(0L.toInt(), fma(v3.d(0L.toInt()), v0.d(0L.toInt()), v1.d(0L.toInt())))
    v0.f(0L.toInt(), (v0.d(0L.toInt())).toFloat())
    v0.f(0L.toInt(), AppleMath.acos(v0.f(0L.toInt())))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v8.d(0L.toInt())))
    x8 = (sp + 592L)
    sincos(v0.d(0L.toInt()), x8)
    v20.load(memory, (sp + 544L))
    x8 = 0L
    x9 = ld_u64((x27 + (x21 shl 3L.toInt())))
    v0.u64(0L.toInt(), x9)
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    v1.d(0L.toInt(), ld_d((sp + 600L)))
    v2.d(0L.toInt(), ld_d((sp + 592L)))
    v3.d(0L.toInt(), ld_d((sp + 616L)))
    v4.d(0L.toInt(), ld_d((sp + 608L)))
    v5.d(0L.toInt(), ld_d((sp + 632L)))
    v6.d(0L.toInt(), ld_d((sp + 624L)))
    x10 = x22
    return 175
}

// C source block: L185345bc0
internal fun CurlMeshKernel.block175(): Int {
    x11 = -11520L
    x12 = x26
    x13 = x28
    v7.set(v10)
    v16.set(v9)
    x14 = x9
    return 176
}

// C source block: L185345bd8
internal fun CurlMeshKernel.block176(): Int {
    v17.d(0L.toInt(), ld_d(x13))
    x13 = (x13 + 8L)
    v18.d(0L.toInt(), ld_d(x12))
    x12 = (x12 + 8L)
    v16.f(0L.toInt(), (v16.d(0L.toInt())).toFloat())
    v19.d(0L.toInt(), fma(v12.d(0L.toInt()), v18.d(0L.toInt()), v9.d(0L.toInt())))
    v7.f(0L.toInt(), (v7.d(0L.toInt())).toFloat())
    v18.d(0L.toInt(), fma(v11.d(0L.toInt()), v18.d(0L.toInt()), v10.d(0L.toInt())))
    v17.f(0L.toInt(), (v17.d(0L.toInt())).toFloat())
    run {
        var ta = v0.copy()
        var tb = v17.copy()
        var tc = v17.copy()
        for (k in 0L until 4L) {
            v17.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    run {
        var t = v17.copy()
        for (k in 0L until 4L) {
            v17.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    x15 = (x10 + x11)
    st_f((x15 + 11520L), v16.f(0L.toInt()))
    st_f((x15 + 11524L), v7.f(0L.toInt()))
    v20.store(memory, (x15 + x20))
    st_u64((x15 + 11560L), x14)
    v7.f(0L.toInt(), (v19.d(0L.toInt())).toFloat())
    st_f((x15 + 11584L), v7.f(0L.toInt()))
    v7.f(0L.toInt(), (v18.d(0L.toInt())).toFloat())
    st_f((x15 + 11588L), v7.f(0L.toInt()))
    v20.store(memory, (x15 + x23))
    st_d((x15 + 11624L), v17.d(0L.toInt()))
    x14 = v17.u64(0L.toInt())
    v7.set(v18)
    v16.set(v19)
    addflags(x11, 3840L, 64L)
    x11 = (x11 + 3840L)
    return if (!(Z)) 176 else 177
}

// C source block: fallthrough177
internal fun CurlMeshKernel.block177(): Int {
    v7.d(0L.toInt(), (v11.d(0L.toInt()) * v4.d(0L.toInt())))
    v7.d(0L.toInt(), fma(v12.d(0L.toInt()), v2.d(0L.toInt()), v7.d(0L.toInt())))
    v16.d(0L.toInt(), (v11.d(0L.toInt()) * v3.d(0L.toInt())))
    v16.d(0L.toInt(), fma(v12.d(0L.toInt()), v1.d(0L.toInt()), v16.d(0L.toInt())))
    v12.d(0L.toInt(), (v6.d(0L.toInt()) + v7.d(0L.toInt())))
    v11.d(0L.toInt(), (v5.d(0L.toInt()) + v16.d(0L.toInt())))
    x8 = (x8 + 1L)
    x10 = (x10 + 128L)
    subflags(x8, 30L, 64L)
    return if (!(Z)) 175 else 178
}

// C source block: fallthrough178
internal fun CurlMeshKernel.block178(): Int {
    x21 = (x21 + 1L)
    x22 = (x22 + x24)
    x8 = ld_u64((sp + 560L))
    subflags(x21, x8, 64L)
    return if (!(Z)) 174 else 179
}

// C source block: fallthrough179
internal fun CurlMeshKernel.block179(): Int {
    subflags((x25 and 0xffffffffL), 0L, 32L)
    x8 = ((if (!(Z)) 1L else 0L) and 0xffffffffL)
    st_u32((sp + 544L), (x8 and 0xffffffffL))
    x23 = ld_u64((sp + 184L))
    x24 = ld_u64((sp + 96L))
    v11.d(0L.toInt(), ld_d((sp + 448L)))
    v16.d(0L.toInt(), ld_d(((sp + 448L) + 8L)))
    v12.d(0L.toInt(), ld_d((sp + 520L)))
    v7.d(0L.toInt(), ld_d((sp + 464L)))
    v18.d(0L.toInt(), ld_d((sp + 432L)))
    v17.d(0L.toInt(), ld_d(((sp + 432L) + 8L)))
    v5.d(0L.toInt(), ld_d((sp + 336L)))
    v4.d(0L.toInt(), ld_d(((sp + 336L) + 8L)))
    v20.d(0L.toInt(), ld_d((sp + 320L)))
    v19.d(0L.toInt(), ld_d(((sp + 320L) + 8L)))
    return 180
}

// C source block: L185345ca4
internal fun CurlMeshKernel.block180(): Int {
    x8 = (ld_u8(x23) and 0xffffffffL)
    v6.d(0L.toInt(), ld_d((sp + 528L)))
    v9.d(0L.toInt(), ld_d((sp + 496L)))
    return if (((x8 and 0xffffffffL) == 0L)) 183 else 181
}

// C source block: fallthrough181
internal fun CurlMeshKernel.block181(): Int {
    v0.d(0L.toInt(), ld_d((sp + 56L)))
    v8.d(0L.toInt(), (v0.d(0L.toInt()) + v12.d(0L.toInt())))
    v22.d(0L.toInt(), ld_d((sp + 672L)))
    v23.d(0L.toInt(), ld_d((sp + 664L)))
    v0.d(0L.toInt(), (v20.d(0L.toInt()) - v23.d(0L.toInt())))
    v1.d(0L.toInt(), (v5.d(0L.toInt()) - v22.d(0L.toInt())))
    v1.d(0L.toInt(), (v6.d(0L.toInt()) * v1.d(0L.toInt())))
    v3.d(0L.toInt(), ld_d((sp + 536L)))
    v0.d(0L.toInt(), fma(v3.d(0L.toInt()), v0.d(0L.toInt()), v1.d(0L.toInt())))
    v1.d(0L.toInt(), (v19.d(0L.toInt()) - v23.d(0L.toInt())))
    v2.d(0L.toInt(), (v4.d(0L.toInt()) - v22.d(0L.toInt())))
    v2.d(0L.toInt(), (v6.d(0L.toInt()) * v2.d(0L.toInt())))
    v1.d(0L.toInt(), fma(v3.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt())))
    v2.d(0L.toInt(), ld_d((sp + 48L)))
    v2.d(0L.toInt(), (v13.d(0L.toInt()) * v2.d(0L.toInt())))
    v3.d(0L.toInt(), 0.01)
    fcmp(v0.d(0L.toInt()), v12.d(0L.toInt()))
    v0.d(0L.toInt(), (if ((!(Z) && (N == V))) v0.d(0L.toInt()) else v12.d(0L.toInt())))
    fcmp(v1.d(0L.toInt()), v8.d(0L.toInt()))
    v1.d(0L.toInt(), (if (N) v1.d(0L.toInt()) else v8.d(0L.toInt())))
    v4.d(0L.toInt(), (v1.d(0L.toInt()) - v12.d(0L.toInt())))
    v5.u64(0L.toInt(), 0L)
    v4.d(0L.toInt(), max(v4.d(0L.toInt()), v5.d(0L.toInt())))
    v5.d(0L.toInt(), -((v2.d(0L.toInt()) * v3.d(0L.toInt()))))
    v4.d(0L.toInt(), (v4.d(0L.toInt()) / v5.d(0L.toInt())))
    v4.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    v4.f(0L.toInt(), floor(v4.f(0L.toInt())))
    v5.f(0L.toInt(), -(100.0f))
    v4.f(0L.toInt(), max(v4.f(0L.toInt()), v5.f(0L.toInt())))
    x9 = (((v4.f(0L.toInt())).toLong()).toInt().toLong() and 0xffffffffL)
    subflags((x9 and 0xffffffffL), 0L, 32L)
    return if ((Z || (N != V))) 184 else 182
}

// C source block: fallthrough182
internal fun CurlMeshKernel.block182(): Int {
    x8 = (0L and 0xffffffffL)
    return 186
}

// C source block: L185345d44
internal fun CurlMeshKernel.block183(): Int {
    st_u32((sp + 528L), 0L)
    return 215
}

// C source block: L185345d4c
internal fun CurlMeshKernel.block184(): Int {
    x8 = (0L and 0xffffffffL)
    v2.d(0L.toInt(), (v2.d(0L.toInt()) * v3.d(0L.toInt())))
    x10 = (sp + 1504L)
    return 185
}

// C source block: L185345d58
internal fun CurlMeshKernel.block185(): Int {
    x11 = (((x9 and 0xffffffffL) + (x8 and 0xffffffffL)) and 0xffffffffL)
    v3.f(0L.toInt(), (((x11 and 0xffffffffL)).toInt().toLong()).toFloat())
    v3.d(0L.toInt(), (v3.f(0L.toInt())).toDouble())
    v3.d(0L.toInt(), fma(-(v2.d(0L.toInt())), v3.d(0L.toInt()), v12.d(0L.toInt())))
    fcmp(v1.d(0L.toInt()), v3.d(0L.toInt()))
    v3.d(0L.toInt(), (if (N) v1.d(0L.toInt()) else v3.d(0L.toInt())))
    fcmp(v0.d(0L.toInt()), v3.d(0L.toInt()))
    v3.d(0L.toInt(), (if ((!(Z) && (N == V))) v0.d(0L.toInt()) else v3.d(0L.toInt())))
    st_d(x10, v3.d(0L.toInt()))
    x10 = (x10 + 8L)
    x8 = (((x8 and 0xffffffffL) + 1L) and 0xffffffffL)
    x11 = (((x9 and 0xffffffffL) + (x8 and 0xffffffffL)) and 0xffffffffL)
    subflags((x11 and 0xffffffffL), 1L, 32L)
    return if (N) 185 else 186
}

// C source block: L185345d8c
internal fun CurlMeshKernel.block186(): Int {
    v2.d(0L.toInt(), (v7.d(0L.toInt()) - v23.d(0L.toInt())))
    v3.d(0L.toInt(), (v17.d(0L.toInt()) - v22.d(0L.toInt())))
    v3.d(0L.toInt(), (v6.d(0L.toInt()) * v3.d(0L.toInt())))
    v4.d(0L.toInt(), ld_d((sp + 536L)))
    v2.d(0L.toInt(), fma(v4.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt())))
    fcmp(v2.d(0L.toInt()), v0.d(0L.toInt()))
    if ((!(Z) && (N == V))) {
        run {
            fcmp(v2.d(0L.toInt()), v1.d(0L.toInt()))
        }
    } else {
        run {
            N = (0L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (0L != 0L)
        }
    }
    return if (N) 188 else 187
}

// C source block: fallthrough187
internal fun CurlMeshKernel.block187(): Int {
    x9 = (0L and 0xffffffffL)
    return 189
}

// C source block: L185345db4
internal fun CurlMeshKernel.block188(): Int {
    st_d((sp + 2432L), v2.d(0L.toInt()))
    x9 = ((0L or 1L) and 0xffffffffL)
    return 189
}

// C source block: L185345dbc
internal fun CurlMeshKernel.block189(): Int {
    v2.d(0L.toInt(), (v16.d(0L.toInt()) - v23.d(0L.toInt())))
    v3.d(0L.toInt(), (v18.d(0L.toInt()) - v22.d(0L.toInt())))
    v3.d(0L.toInt(), (v6.d(0L.toInt()) * v3.d(0L.toInt())))
    v4.d(0L.toInt(), ld_d((sp + 536L)))
    v2.d(0L.toInt(), fma(v4.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt())))
    fcmp(v2.d(0L.toInt()), v0.d(0L.toInt()))
    if ((!(Z) && (N == V))) {
        run {
            fcmp(v2.d(0L.toInt()), v1.d(0L.toInt()))
        }
    } else {
        run {
            N = (0L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (0L != 0L)
        }
    }
    st_d((sp + 416L), v23.d(0L.toInt()))
    st_d(((sp + 416L) + 8L), v22.d(0L.toInt()))
    return if (N) 193 else 190
}

// C source block: fallthrough190
internal fun CurlMeshKernel.block190(): Int {
    subflags((x8 and 0xffffffffL), 0L, 32L)
    x15 = ((if ((!(Z) && (N == V))) 1L else 0L) and 0xffffffffL)
    return if ((!(Z) && (N == V))) 199 else 191
}

// C source block: fallthrough191
internal fun CurlMeshKernel.block191(): Int {
    return if (((x9 and 0xffffffffL) != 0L)) 199 else 192
}
