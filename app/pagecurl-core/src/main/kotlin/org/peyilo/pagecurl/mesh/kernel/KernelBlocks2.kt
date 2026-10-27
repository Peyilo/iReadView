// Generated from the verified C11 curl geometry; regenerate with tools/Port-CurlKernel.py.
// Direct Kotlin arithmetic and managed scratch storage. No machine instructions are executed.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")
package org.peyilo.pagecurl.mesh.kernel
import kotlin.math.*
import org.peyilo.pagecurl.math.AppleMath
import org.peyilo.pagecurl.math.BinaryMath.fma

// C source block: L185344b08
internal fun CurlMeshKernel.block48(): Int {
    v14.d(0L.toInt(), ld_d((sp + 488L)))
    v0.d(0L.toInt(), ld_d((sp + 392L)))
    v2.d(0L.toInt(), (v0.d(0L.toInt()) + v9.d(0L.toInt())))
    v0.d(0L.toInt(), ld_d((sp + 320L)))
    v3.d(0L.toInt(), (v0.d(0L.toInt()) + v10.d(0L.toInt())))
    v0.d(0L.toInt(), ld_d((sp + 536L)))
    v0.d(0L.toInt(), fma(v0.d(0L.toInt()), v1.d(0L.toInt()), v13.d(0L.toInt())))
    v6.d(0L.toInt(), ld_d((sp + 280L)))
    v1.d(0L.toInt(), fma(v5.d(0L.toInt()), v1.d(0L.toInt()), v6.d(0L.toInt())))
    v4.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    v5.f(0L.toInt(), (1.0).toFloat())
    v5.f(0L.toInt(), (v5.f(0L.toInt()) - v4.f(0L.toInt())))
    v6.load(memory, (sp + 240L))
    run {
        var ta = v6.copy()
        var tb = v4.copy()
        var tc = v4.copy()
        for (k in 0L until 4L) {
            v4.f(k.toInt(), (ta.f(k.toInt()) * tb.f(0L.toInt())))
        }
    }
    v6.load(memory, (sp + 256L))
    run {
        var ta = v6.copy()
        var tb = v5.copy()
        var tc = v4.copy()
        for (k in 0L until 4L) {
            v4.f(k.toInt(), fma(ta.f(k.toInt()), tb.f(0L.toInt()), tc.f(k.toInt())))
        }
    }
    run {
        var t = v4.copy()
        for (k in 0L until 4L) {
            v4.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    x8 = v4.u64(0L.toInt())
    x9 = ld_u64((sp + 288L))
    subflags(x20, x9, 64L)
    return if (!(C)) 50 else 49
}

// C source block: fallthrough49
internal fun CurlMeshKernel.block49(): Int {
    x9 = ld_u64((sp + 216L))
    subflags(x20, x9, 64L)
    return if ((!(C) || Z)) 51 else 50
}

// C source block: L185344b68
internal fun CurlMeshKernel.block50(): Int {
    x9 = (sp + (21L shl 12L.toInt()))
    x9 = (x9 + 2832L)
    v4.d(0L.toInt(), ld_d((x9 + 768L)))
    v5.d(0L.toInt(), ld_d((sp + 16384L)))
    v5.d(0L.toInt(), (v5.d(0L.toInt()) - v4.d(0L.toInt())))
    v6.d(0L.toInt(), ld_d((sp + 160L)))
    v4.d(0L.toInt(), fma(v6.d(0L.toInt()), v5.d(0L.toInt()), v4.d(0L.toInt())))
    st_d((sp + 29568L), v4.d(0L.toInt()))
    st_d((x9 + 26112L), v4.d(0L.toInt()))
    return 51
}

// C source block: L185344b8c
internal fun CurlMeshKernel.block51(): Int {
    subflags(x20, 0L, 64L)
    x9 = ((if (Z) 1L else 0L) and 0xffffffffL)
    subflags(x21, x20, 64L)
    x10 = ((if (Z) 1L else 0L) and 0xffffffffL)
    x9 = (((x9 and 0xffffffffL) or (x10 and 0xffffffffL)) and 0xffffffffL)
    subflags((x9 and 0xffffffffL), 0L, 32L)
    x8 = (if (!(Z)) 0L else x8)
    x9 = ld_u64((sp + 416L))
    x9 = (if (!(Z)) 0L else x9)
    subflags(x20, x25, 64L)
    x8 = (if (!(C)) x9 else x8)
    st_u64((sp + 656L), x8)
    x8 = (sp + (21L shl 12L.toInt()))
    x8 = (x8 + 2832L)
    v4.d(0L.toInt(), ld_d((x8 + 768L)))
    v4.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    v5.d(0L.toInt(), ld_d((sp + 16384L)))
    v5.f(0L.toInt(), (v5.d(0L.toInt())).toFloat())
    v6.d(0L.toInt(), ld_d((x8 + 26112L)))
    v6.f(0L.toInt(), (v6.d(0L.toInt())).toFloat())
    v7.d(0L.toInt(), ld_d((sp + 29568L)))
    v7.f(0L.toInt(), (v7.d(0L.toInt())).toFloat())
    st_f((sp + 8L), v6.f(0L.toInt()))
    st_f(((sp + 8L) + 4L), v7.f(0L.toInt()))
    x0 = (sp + (39L shl 12L.toInt()))
    x0 = (x0 + 2320L)
    x1 = (sp + (38L shl 12L.toInt()))
    x1 = (x1 + 2064L)
    x2 = (sp + (37L shl 12L.toInt()))
    x2 = (x2 + 1808L)
    x4 = (sp + 656L)
    st_f(sp, v4.f(0L.toInt()))
    st_f((sp + 4L), v5.f(0L.toInt()))
    v4.set(v14)
    v9.d(0L.toInt(), ld_d((sp + 480L)))
    v5.set(v9)
    v6.d(0L.toInt(), ld_d((sp + 560L)))
    v7.d(0L.toInt(), ld_d((sp + 544L)))
    x3 = x20
    strip_emit(x0, x1, x2, x3, x4, v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt()), v4.d(0L.toInt()), v5.d(0L.toInt()), v6.d(0L.toInt()), v7.d(0L.toInt()), sp)
    x20 = (x20 + 1L)
    x8 = ld_u64((sp + 296L))
    subflags(x8, x20, 64L)
    return if (!(Z)) 42 else 52
}

// C source block: L185344c34
internal fun CurlMeshKernel.block52(): Int {
    x8 = (ld_u32((sp + 68L)) and 0xffffffffL)
    x8 = (((x23 and 0xffffffffL) and ((((x8 and 0xffffffffL)).toInt().toLong() shr 31L.toInt())).inv()) and 0xffffffffL)
    st_u32((sp + 280L), (x8 and 0xffffffffL))
    x23 = ld_u64((sp + 184L))
    x21 = (ld_u8((x23 + 1L)) and 0xffffffffL)
    v8.d(0L.toInt(), ld_d((sp + 208L)))
    v15.d(0L.toInt(), ld_d((sp + 504L)))
    v13.d(0L.toInt(), ld_d(((sp + 504L) + 8L)))
    v11.d(0L.toInt(), ld_d((sp + 448L)))
    v10.d(0L.toInt(), ld_d((sp + 72L)))
    return if (((x21 and 0xffffffffL) == 0L)) 59 else 53
}

// C source block: fallthrough53
internal fun CurlMeshKernel.block53(): Int {
    st_u32((sp + 568L), (x21 and 0xffffffffL))
    x20 = (sp + (36L shl 12L.toInt()))
    x20 = (x20 + 1552L)
    x9 = ld_u64((sp + 104L))
    x0 = (x20 + x9)
    x8 = (sp + (39L shl 12L.toInt()))
    x8 = (x8 + 2320L)
    x1 = (x8 + x9)
    x21 = (ld_u32((sp + 280L)) and 0xffffffffL)
    x8 = (((x21 and 0xffffffffL) shl 1L.toInt()) and 0xffffffffL)
    x2 = ((x8).toInt().toLong() shl 6L.toInt())
    memory.copy(x0, x1, x2)
    subflags((x21 and 0xffffffffL), 1L, 32L)
    return if ((N != V)) 56 else 54
}

// C source block: fallthrough54
internal fun CurlMeshKernel.block54(): Int {
    x8 = 0L
    x9 = (ld_u32((sp + 280L)) and 0xffffffffL)
    x9 = ((x9 and 0xffffffffL) and 0xffffffffL)
    return 55
}

// C source block: L185344ca0
internal fun CurlMeshKernel.block55(): Int {
    x10 = (((x22 and 0xffffffffL) - 1L) and 0xffffffffL)
    x10 = (x20 + (x10 shl 6L.toInt()))
    st_d((x10 + 40L), v10.d(0L.toInt()))
    x10 = ((x22 and 0xffffffffL) and 0xffffffffL)
    x10 = (x20 + (x10 shl 6L.toInt()))
    st_d((x10 + 40L), v10.d(0L.toInt()))
    x8 = (x8 + 1L)
    x22 = (((x22 and 0xffffffffL) + 2L) and 0xffffffffL)
    subflags(x8, x9, 64L)
    return if (!(C)) 55 else 56
}

// C source block: L185344cc8
internal fun CurlMeshKernel.block56(): Int {
    v0.load(memory, (sp + 112L))
    v0.h(0L.toInt(), v0.h(3L.toInt()))
    v0.f(0L.toInt(), halfFloat(v0.h(0L.toInt())))
    v1.h(0L.toInt(), 0L)
    v1.f(0L.toInt(), halfFloat(v1.h(0L.toInt())))
    fcmp((v0.f(0L.toInt())).toDouble(), (v1.f(0L.toInt())).toDouble())
    x21 = (ld_u32((sp + 568L)) and 0xffffffffL)
    return if (!(Z)) 59 else 57
}

// C source block: fallthrough57
internal fun CurlMeshKernel.block57(): Int {
    x8 = (sp + (35L shl 12L.toInt()))
    x8 = (x8 + 1296L)
    x22 = ld_u64((sp + 104L))
    x0 = (x8 + x22)
    x8 = (sp + (38L shl 12L.toInt()))
    x8 = (x8 + 2064L)
    x1 = (x8 + x22)
    x8 = ld_u64((sp + 176L))
    x20 = ((x8).toInt().toLong() shl 6L.toInt())
    x2 = x20
    memory.copy(x0, x1, x2)
    x8 = (sp + (34L shl 12L.toInt()))
    x8 = (x8 + 1040L)
    x0 = (x8 + x22)
    x8 = (sp + (37L shl 12L.toInt()))
    x8 = (x8 + 1808L)
    x1 = (x8 + x22)
    x2 = x20
    memory.copy(x0, x1, x2)
    subflags((x27 and 0xffffffffL), (x25 and 0xffffffffL), 32L)
    return if ((Z || (N != V))) 59 else 58
}

// C source block: L185344d40
internal fun CurlMeshKernel.block58(): Int {
    st_d(x26, v10.d(0L.toInt()))
    x26 = (x26 + 128L)
    st_d(x24, v10.d(0L.toInt()))
    x24 = (x24 + 128L)
    subflags(x28, 1L, 64L)
    x28 = (x28 - 1L)
    return if (!(Z)) 58 else 59
}

// C source block: L185344d50
internal fun CurlMeshKernel.block59(): Int {
    x24 = ld_u64((sp + 96L))
    v0.d(0L.toInt(), ld_d((x24 + 72L)))
    v1.d(0L.toInt(), ld_d(((x24 + 72L) + 8L)))
    v2.d(0L.toInt(), ld_d((x24 + 88L)))
    v3.d(0L.toInt(), ld_d(((x24 + 88L) + 8L)))
    v2.d(0L.toInt(), (v0.d(0L.toInt()) + v2.d(0L.toInt())))
    v3.d(0L.toInt(), (v1.d(0L.toInt()) + v3.d(0L.toInt())))
    fcmp(v14.d(0L.toInt()), 0.0)
    v5.d(0L.toInt(), (if (N) v1.d(0L.toInt()) else v3.d(0L.toInt())))
    v4.d(0L.toInt(), (if (N) v3.d(0L.toInt()) else v1.d(0L.toInt())))
    v7.d(0L.toInt(), (if (!(N)) v0.d(0L.toInt()) else v2.d(0L.toInt())))
    v16.d(0L.toInt(), (if (!(N)) v2.d(0L.toInt()) else v0.d(0L.toInt())))
    fcmp(v9.d(0L.toInt()), 0.0)
    v20.d(0L.toInt(), (if ((!(Z) && (N == V))) v0.d(0L.toInt()) else v2.d(0L.toInt())))
    v19.d(0L.toInt(), (if ((!(Z) && (N == V))) v2.d(0L.toInt()) else v0.d(0L.toInt())))
    v17.d(0L.toInt(), (if ((!(Z) && (N == V))) v1.d(0L.toInt()) else v3.d(0L.toInt())))
    v18.d(0L.toInt(), (if ((!(Z) && (N == V))) v3.d(0L.toInt()) else v1.d(0L.toInt())))
    st_d((sp + 456L), v16.d(0L.toInt()))
    st_d(((sp + 456L) + 8L), v7.d(0L.toInt()))
    st_d((sp + 432L), v18.d(0L.toInt()))
    st_d(((sp + 432L) + 8L), v17.d(0L.toInt()))
    return if (((x21 and 0xffffffffL) == 0L)) 65 else 60
}

// C source block: fallthrough60
internal fun CurlMeshKernel.block60(): Int {
    v21.d(0L.toInt(), ld_d((sp + 672L)))
    v22.d(0L.toInt(), ld_d((sp + 664L)))
    v0.d(0L.toInt(), (v20.d(0L.toInt()) - v22.d(0L.toInt())))
    v1.d(0L.toInt(), (v5.d(0L.toInt()) - v21.d(0L.toInt())))
    v2.d(0L.toInt(), ld_d((sp + 528L)))
    v1.d(0L.toInt(), (v2.d(0L.toInt()) * v1.d(0L.toInt())))
    v2.d(0L.toInt(), ld_d((sp + 536L)))
    v3.d(0L.toInt(), fma(v2.d(0L.toInt()), v0.d(0L.toInt()), v1.d(0L.toInt())))
    v12.d(0L.toInt(), ld_d((sp + 520L)))
    fcmp(v12.d(0L.toInt()), v3.d(0L.toInt()))
    return if ((Z || (N != V))) 66 else 61
}

// C source block: fallthrough61
internal fun CurlMeshKernel.block61(): Int {
    x8 = 0L
    v0.d(0L.toInt(), ld_d((sp + 16L)))
    v6.d(0L.toInt(), ld_d(((sp + 16L) + 8L)))
    v0.d(0L.toInt(), (v13.d(0L.toInt()) * v0.d(0L.toInt())))
    v1.d(0L.toInt(), 0.02)
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v1.d(0L.toInt(), (v11.d(0L.toInt()) - v15.d(0L.toInt())))
    v1.d(0L.toInt(), fma(v13.d(0L.toInt()), v1.d(0L.toInt()), v12.d(0L.toInt())))
    fcmp(v3.d(0L.toInt()), v1.d(0L.toInt()))
    v2.d(0L.toInt(), (if ((!(Z) && (N == V))) v3.d(0L.toInt()) else v1.d(0L.toInt())))
    x11 = (sp + 680L)
    x12 = (x11 + 8L)
    return 62
}

// C source block: L185344df4
internal fun CurlMeshKernel.block62(): Int {
    x9 = x8
    x10 = x12
    v1.f(0L.toInt(), (((x9 and 0xffffffffL)).toInt().toLong()).toFloat())
    v1.d(0L.toInt(), (v1.f(0L.toInt())).toDouble())
    v1.d(0L.toInt(), fma(-(v0.d(0L.toInt())), v1.d(0L.toInt()), v6.d(0L.toInt())))
    x8 = (x8 + 1L)
    fcmp(v1.d(0L.toInt()), v2.d(0L.toInt()))
    return if ((!(C) || Z)) 67 else 63
}

// C source block: fallthrough63
internal fun CurlMeshKernel.block63(): Int {
    st_d((x11 + (x9 shl 3L.toInt())), v1.d(0L.toInt()))
    x12 = (x10 + 8L)
    subflags((x8 and 0xffffffffL), 50L, 32L)
    return if (!(C)) 62 else 64
}

// C source block: fallthrough64
internal fun CurlMeshKernel.block64(): Int {
    return 68
}

// C source block: L185344e28
internal fun CurlMeshKernel.block65(): Int {
    st_u64((sp + 560L), 0L)
    st_u32((sp + 544L), 0L)
    st_u64((sp + 568L), 0L)
    st_u64((sp + 288L), 0L)
    v14.d(0L.toInt(), 1.0)
    v12.d(0L.toInt(), ld_d((sp + 520L)))
    return 180
}

// C source block: L185344e44
internal fun CurlMeshKernel.block66(): Int {
    st_u64((sp + 560L), 0L)
    st_u32((sp + 544L), 0L)
    st_u64((sp + 568L), 0L)
    st_u64((sp + 288L), 0L)
    v14.d(0L.toInt(), 1.0)
    return 180
}

// C source block: L185344e5c
internal fun CurlMeshKernel.block67(): Int {
    x11 = (sp + 680L)
    st_d((x11 + (x9 shl 3L.toInt())), v2.d(0L.toInt()))
    v1.set(v2)
    return 68
}

// C source block: L185344e68
internal fun CurlMeshKernel.block68(): Int {
    v0.d(0L.toInt(), (v1.d(0L.toInt()) - v3.d(0L.toInt())))
    v2.d(0L.toInt(), 0.1)
    v2.d(0L.toInt(), (v0.d(0L.toInt()) * v2.d(0L.toInt())))
    fcmp(v2.d(0L.toInt()), 0.0)
    st_d((sp + 344L), v4.d(0L.toInt()))
    return if ((Z || (N != V))) 73 else 69
}

// C source block: fallthrough69
internal fun CurlMeshKernel.block69(): Int {
    x11 = ((0L or 2L) and 0xffffffffL)
    return 70
}

// C source block: L185344e88
internal fun CurlMeshKernel.block70(): Int {
    x8 = x11
    x11 = (((x8 and 0xffffffffL) - 1L) and 0xffffffffL)
    v0.f(0L.toInt(), (((x11 and 0xffffffffL)).toInt().toLong()).toFloat())
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v0.d(0L.toInt(), fma(-(v2.d(0L.toInt())), v0.d(0L.toInt()), v1.d(0L.toInt())))
    fcmp(v0.d(0L.toInt()), v3.d(0L.toInt()))
    return if ((!(C) || Z)) 74 else 71
}

// C source block: fallthrough71
internal fun CurlMeshKernel.block71(): Int {
    st_d(x10, v0.d(0L.toInt()))
    x10 = (x10 + 8L)
    x11 = (x8 + 1L)
    x12 = (((x11 and 0xffffffffL) - 1L) and 0xffffffffL)
    subflags((x12 and 0xffffffffL), 11L, 32L)
    return if (!(C)) 70 else 72
}

// C source block: fallthrough72
internal fun CurlMeshKernel.block72(): Int {
    x8 = (x9 + x8)
    return 75
}

// C source block: L185344ec0
internal fun CurlMeshKernel.block73(): Int {
    v0.set(v1)
    return 75
}

// C source block: L185344ec8
internal fun CurlMeshKernel.block74(): Int {
    st_d(x10, v3.d(0L.toInt()))
    x8 = (x9 + x8)
    v0.set(v3)
    return 75
}

// C source block: L185344ed4
internal fun CurlMeshKernel.block75(): Int {
    x9 = ((x8).toInt().toLong() shl 3L.toInt())
    x10 = (sp + 680L)
    st_d((x10 + x9), v0.d(0L.toInt()))
    v1.d(0L.toInt(), (v7.d(0L.toInt()) - v22.d(0L.toInt())))
    v2.d(0L.toInt(), (v17.d(0L.toInt()) - v21.d(0L.toInt())))
    v4.d(0L.toInt(), ld_d((sp + 528L)))
    v2.d(0L.toInt(), (v4.d(0L.toInt()) * v2.d(0L.toInt())))
    v4.d(0L.toInt(), ld_d((sp + 536L)))
    v1.d(0L.toInt(), fma(v4.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt())))
    fcmp(v1.d(0L.toInt()), v3.d(0L.toInt()))
    if ((!(Z) && (N == V))) {
        run {
            fcmp(v1.d(0L.toInt()), v6.d(0L.toInt()))
        }
    } else {
        run {
            N = (0L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (0L != 0L)
        }
    }
    st_d((sp + 336L), v5.d(0L.toInt()))
    return if (N) 77 else 76
}

// C source block: fallthrough76
internal fun CurlMeshKernel.block76(): Int {
    x20 = (0L and 0xffffffffL)
    return 78
}

// C source block: L185344f10
internal fun CurlMeshKernel.block77(): Int {
    st_d((sp + 2320L), v1.d(0L.toInt()))
    x20 = ((0L or 1L) and 0xffffffffL)
    return 78
}

// C source block: L185344f18
internal fun CurlMeshKernel.block78(): Int {
    v2.d(0L.toInt(), (v16.d(0L.toInt()) - v22.d(0L.toInt())))
    v4.d(0L.toInt(), (v18.d(0L.toInt()) - v21.d(0L.toInt())))
    v5.d(0L.toInt(), ld_d((sp + 528L)))
    v4.d(0L.toInt(), (v5.d(0L.toInt()) * v4.d(0L.toInt())))
    v5.d(0L.toInt(), ld_d((sp + 536L)))
    v2.d(0L.toInt(), fma(v5.d(0L.toInt()), v2.d(0L.toInt()), v4.d(0L.toInt())))
    fcmp(v2.d(0L.toInt()), v3.d(0L.toInt()))
    if ((!(Z) && (N == V))) {
        run {
            fcmp(v2.d(0L.toInt()), v6.d(0L.toInt()))
        }
    } else {
        run {
            N = (0L != 0L)
            Z = (0L != 0L)
            C = (0L != 0L)
            V = (0L != 0L)
        }
    }
    return if (N) 82 else 79
}

// C source block: fallthrough79
internal fun CurlMeshKernel.block79(): Int {
    x9 = (((x8 and 0xffffffffL) shr 31L.toInt()) and 0xffffffffL)
    x15 = (((x9 and 0xffffffffL) xor 1L) and 0xffffffffL)
    return if ((((x8 and 0xffffffffL) and (1L shl 31L.toInt())) == 0L)) 88 else 80
}

// C source block: fallthrough80
internal fun CurlMeshKernel.block80(): Int {
    return if (((x20 and 0xffffffffL) != 0L)) 88 else 81
}

// C source block: fallthrough81
internal fun CurlMeshKernel.block81(): Int {
    x9 = (0L and 0xffffffffL)
    return 100
}

// C source block: L185344f54
internal fun CurlMeshKernel.block82(): Int {
    x9 = (sp + 2320L)
    st_d((x9 + (((x20 and 0xffffffffL) and 0xffffffffL) shl 3L.toInt())), v2.d(0L.toInt()))
    return if (((x20 and 0xffffffffL) == 0L)) 86 else 83
}

// C source block: fallthrough83
internal fun CurlMeshKernel.block83(): Int {
    v4.d(0L.toInt(), ld_d((sp + 2328L)))
    v3.d(0L.toInt(), ld_d((sp + 2320L)))
    fcmp(v4.d(0L.toInt()), v3.d(0L.toInt()))
    return if ((Z || (N != V))) 85 else 84
}

// C source block: fallthrough84
internal fun CurlMeshKernel.block84(): Int {
    st_d((sp + 2328L), v3.d(0L.toInt()))
    st_d((sp + 2320L), v2.d(0L.toInt()))
    return 85
}

// C source block: L185344f78
internal fun CurlMeshKernel.block85(): Int {
    x20 = ((0L or 2L) and 0xffffffffL)
    return 87
}

// C source block: L185344f80
internal fun CurlMeshKernel.block86(): Int {
    x20 = ((0L or 1L) and 0xffffffffL)
    return 87
}

// C source block: L185344f84
internal fun CurlMeshKernel.block87(): Int {
    x9 = (((x8 and 0xffffffffL) shr 31L.toInt()) and 0xffffffffL)
    x15 = (((x9 and 0xffffffffL) xor 1L) and 0xffffffffL)
    return 88
}

// C source block: L185344f8c
internal fun CurlMeshKernel.block88(): Int {
    x9 = 0L
    x10 = (0L and 0xffffffffL)
    x11 = (0L and 0xffffffffL)
    x12 = (sp + 680L)
    x13 = (sp + 3200L)
    x14 = (sp + 2320L)
    return 89
}

// C source block: L185344fa4
internal fun CurlMeshKernel.block89(): Int {
    subflags((x10 and 0xffffffffL), (x20 and 0xffffffffL), 32L)
    return if (!(Z)) 92 else 90
}

// C source block: fallthrough90
internal fun CurlMeshKernel.block90(): Int {
    x15 = ld_u64((x12 + (((x11 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())))
    return 91
}

// C source block: L185344fb0
internal fun CurlMeshKernel.block91(): Int {
    x11 = (((x11 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 98
}

// C source block: L185344fb8
internal fun CurlMeshKernel.block92(): Int {
    return if ((((x15 and 0xffffffffL) and (1L shl 0L.toInt())) == 0L)) 95 else 93
}

// C source block: fallthrough93
internal fun CurlMeshKernel.block93(): Int {
    v3.d(0L.toInt(), ld_d((x12 + (((x11 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))))
    v4.d(0L.toInt(), ld_d((x14 + (((x10 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))))
    fcmp(v3.d(0L.toInt()), v4.d(0L.toInt()))
    return if ((Z || (N != V))) 96 else 94
}

// C source block: fallthrough94
internal fun CurlMeshKernel.block94(): Int {
    x15 = v3.u64(0L.toInt())
    return 91
}

// C source block: L185344fd4
internal fun CurlMeshKernel.block95(): Int {
    x15 = ld_u64((x14 + (((x10 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())))
    return 97
}
