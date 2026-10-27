// Generated from the verified C11 curl geometry; regenerate with tools/Port-CurlKernel.py.
// Direct Kotlin arithmetic and managed scratch storage. No machine instructions are executed.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")
package org.peyilo.pagecurl.mesh.kernel
import kotlin.math.*
import org.peyilo.pagecurl.math.AppleMath
import org.peyilo.pagecurl.math.BinaryMath.fma

// C source block: entry
internal fun CurlMeshKernel.block0(): Int {
    st_u64((sp + 88L), x4)
    st_u64((sp + 128L), x3)
    x20 = x2
    x21 = x1
    x19 = x0
    x8 = ld_u64((x0 + 224L))
    v0.d(0L.toInt(), ld_d((x2 + 232L)))
    v1.d(0L.toInt(), ld_d(((x2 + 232L) + 8L)))
    v2.d(0L.toInt(), ld_d((x2 + 248L)))
    v3.d(0L.toInt(), ld_d(((x2 + 248L) + 8L)))
    v4.d(0L.toInt(), ld_d((x2 + 272L)))
    x9 = (ld_u8((x2 + 264L)) and 0xffffffffL)
    st_u32((sp + 440L), (x9 and 0xffffffffL))
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    st_u64((sp + 80L), x8)
    v5.d(0L.toInt(), ld_d((x8 + 32L)))
    run {
        var t = v5.copy()
        for (k in 0L until 4L) {
            v5.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    run {
        var ta = v0.copy()
        var tb = v5.copy()
        var tc = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), (ta.f(k.toInt()) * tb.f(k.toInt())))
        }
    }
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    st_d((sp + 520L), v0.d(0L.toInt()))
    run {
        var t = v1.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    run {
        var ta = v0.copy()
        var tb = v5.copy()
        var tc = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), (ta.f(k.toInt()) * tb.f(k.toInt())))
        }
    }
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    st_d((sp + 72L), v0.d(0L.toInt()))
    run {
        var t = v3.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    run {
        var ta = v0.copy()
        var tb = v5.copy()
        var tc = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), (ta.f(k.toInt()) * tb.f(k.toInt())))
        }
    }
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    st_d((sp + 240L), v0.d(0L.toInt()))
    run {
        var t = v2.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    run {
        var ta = v0.copy()
        var tb = v5.copy()
        var tc = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), (ta.f(k.toInt()) * tb.f(k.toInt())))
        }
    }
    v0.store(memory, (sp + 400L))
    run {
        var t = v4.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    run {
        var ta = v0.copy()
        var tb = v5.copy()
        var tc = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), (ta.f(k.toInt()) * tb.f(k.toInt())))
        }
    }
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    st_d((sp + 208L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), ld_d((x1 + 16L)))
    v14.d(0L.toInt(), ld_d(((x1 + 16L) + 8L)))
    st_d((sp + 512L), v0.d(0L.toInt()))
    v15.d(0L.toInt(), 1.5707963267948966)
    v0.d(0L.toInt(), ld_d((x1 + 32L)))
    st_d((sp + 504L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), ld_d((x1 + 40L)))
    st_d((sp + 536L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), ld_d((x1 + 48L)))
    v4.d(0L.toInt(), ld_d(((x1 + 48L) + 8L)))
    st_d((sp + 528L), v0.d(0L.toInt()))
    v5.d(0L.toInt(), ld_d((x1 + 64L)))
    v3.d(0L.toInt(), ld_d((x2 + 216L)))
    v1.d(0L.toInt(), ld_d(((x2 + 216L) + 8L)))
    fcmp(v1.d(0L.toInt()), 0.0)
    v0.d(0L.toInt(), 0.44999998807907104)
    st_d((sp + 544L), v1.d(0L.toInt()))
    v1.d(0L.toInt(), (if (!(Z)) v0.d(0L.toInt()) else v1.d(0L.toInt())))
    v2.d(0L.toInt(), -(v1.d(0L.toInt())))
    v1.d(0L.toInt(), (if ((!(Z) && (N == V))) v1.d(0L.toInt()) else v2.d(0L.toInt())))
    st_d((sp + 304L), v1.d(0L.toInt()))
    v7.d(0L.toInt(), ld_d((x2 + 168L)))
    v6.d(0L.toInt(), ld_d(((x2 + 168L) + 8L)))
    v1.d(0L.toInt(), ld_d((x2 + 184L)))
    v2.d(0L.toInt(), ld_d(((x2 + 184L) + 8L)))
    v1.d(0L.toInt(), (v7.d(0L.toInt()) + v1.d(0L.toInt())))
    v17.d(0L.toInt(), (if ((!(Z) && (N == V))) v7.d(0L.toInt()) else v1.d(0L.toInt())))
    st_d((sp + 568L), v17.d(0L.toInt()))
    v16.d(0L.toInt(), (if ((!(Z) && (N == V))) v1.d(0L.toInt()) else v7.d(0L.toInt())))
    st_d((sp + 472L), v16.d(0L.toInt()))
    st_d(((sp + 472L) + 8L), v5.d(0L.toInt()))
    fcmp(v3.d(0L.toInt()), 0.0)
    st_d((sp + 560L), v3.d(0L.toInt()))
    v0.d(0L.toInt(), (if (!(Z)) v0.d(0L.toInt()) else v3.d(0L.toInt())))
    v3.d(0L.toInt(), -(v0.d(0L.toInt())))
    v13.d(0L.toInt(), (if (N) v3.d(0L.toInt()) else v0.d(0L.toInt())))
    v0.d(0L.toInt(), (v6.d(0L.toInt()) + v2.d(0L.toInt())))
    v10.d(0L.toInt(), (if (N) v6.d(0L.toInt()) else v0.d(0L.toInt())))
    st_d((sp + 320L), v6.d(0L.toInt()))
    st_d((sp + 344L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), (if (N) v0.d(0L.toInt()) else v6.d(0L.toInt())))
    st_d((sp + 416L), v0.d(0L.toInt()))
    v16.d(0L.toInt(), (if (!(N)) v7.d(0L.toInt()) else v1.d(0L.toInt())))
    st_d((sp + 392L), v7.d(0L.toInt()))
    v6.d(0L.toInt(), (if (!(N)) v1.d(0L.toInt()) else v7.d(0L.toInt())))
    v0.d(0L.toInt(), ld_d((x2 + 136L)))
    v1.d(0L.toInt(), ld_d(((x2 + 136L) + 8L)))
    v2.d(0L.toInt(), ld_d((x2 + 152L)))
    v3.d(0L.toInt(), ld_d(((x2 + 152L) + 8L)))
    v2.d(0L.toInt(), (v0.d(0L.toInt()) + v2.d(0L.toInt())))
    fcmp(v5.d(0L.toInt()), 0.0)
    v12.d(0L.toInt(), (if ((!(Z) && (N == V))) v0.d(0L.toInt()) else v2.d(0L.toInt())))
    v3.d(0L.toInt(), (v1.d(0L.toInt()) + v3.d(0L.toInt())))
    v0.d(0L.toInt(), (if ((!(Z) && (N == V))) v2.d(0L.toInt()) else v0.d(0L.toInt())))
    st_d((sp + 336L), v0.d(0L.toInt()))
    st_d((sp + 488L), v4.d(0L.toInt()))
    fcmp(v4.d(0L.toInt()), 0.0)
    v11.d(0L.toInt(), (if (N) v1.d(0L.toInt()) else v3.d(0L.toInt())))
    v9.d(0L.toInt(), (if (N) v3.d(0L.toInt()) else v1.d(0L.toInt())))
    v0.d(0L.toInt(), ld_d((x2 + 200L)))
    st_d((sp + 464L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), ld_d((x2 + 208L)))
    st_d((sp + 456L), v0.d(0L.toInt()))
    st_d((sp + 672L), v10.d(0L.toInt()))
    st_d((sp + 664L), v17.d(0L.toInt()))
    x0 = (x2 + 8L)
    v8.d(0L.toInt(), max(v14.d(0L.toInt()), v15.d(0L.toInt())))
    st_d((sp + 368L), v16.d(0L.toInt()))
    v0.d(0L.toInt(), (v16.d(0L.toInt()) - v13.d(0L.toInt())))
    st_d((sp + 432L), v0.d(0L.toInt()))
    st_d((sp + 352L), v6.d(0L.toInt()))
    v0.d(0L.toInt(), (v6.d(0L.toInt()) - v13.d(0L.toInt())))
    st_d((sp + 424L), v0.d(0L.toInt()))
    x1 = (sp + 664L)
    apply_point(x0, x1)
    v30.set(v8)
    v29.d(0L.toInt(), ld_d((sp + 304L)))
    v0.d(0L.toInt(), ld_d((sp + 672L)))
    v1.d(0L.toInt(), ld_d((sp + 664L)))
    v2.d(0L.toInt(), (v12.d(0L.toInt()) - v1.d(0L.toInt())))
    v3.d(0L.toInt(), (v11.d(0L.toInt()) - v0.d(0L.toInt())))
    v23.d(0L.toInt(), ld_d((sp + 536L)))
    v24.d(0L.toInt(), ld_d((sp + 528L)))
    v3.d(0L.toInt(), (v24.d(0L.toInt()) * v3.d(0L.toInt())))
    v2.d(0L.toInt(), fma(v23.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt())))
    v3.d(0L.toInt(), (v15.d(0L.toInt()) - v14.d(0L.toInt())))
    v21.d(0L.toInt(), ld_d((sp + 512L)))
    v25.d(0L.toInt(), (v21.d(0L.toInt()) * v3.d(0L.toInt())))
    v2.d(0L.toInt(), (v2.d(0L.toInt()) - v25.d(0L.toInt())))
    v3.d(0L.toInt(), ld_d((sp + 336L)))
    v1.d(0L.toInt(), (v3.d(0L.toInt()) - v1.d(0L.toInt())))
    v8.set(v14)
    v0.d(0L.toInt(), (v9.d(0L.toInt()) - v0.d(0L.toInt())))
    v0.d(0L.toInt(), (v24.d(0L.toInt()) * v0.d(0L.toInt())))
    v0.d(0L.toInt(), fma(v23.d(0L.toInt()), v1.d(0L.toInt()), v0.d(0L.toInt())))
    v1.f(0L.toInt(), (v30.d(0L.toInt())).toFloat())
    v3.f(0L.toInt(), (v1.f(0L.toInt()) * v1.f(0L.toInt())))
    v26.f(0L.toInt(), -(0.15346269309520721f))
    v27.f(0L.toInt(), 0.0054653966799378395f)
    v4.f(0L.toInt(), fma(v3.f(0L.toInt()), v27.f(0L.toInt()), v26.f(0L.toInt())))
    v28.f(0L.toInt(), 0.9844167828559875f)
    v3.f(0L.toInt(), fma(v3.f(0L.toInt()), v4.f(0L.toInt()), v28.f(0L.toInt())))
    v1.f(0L.toInt(), (v3.f(0L.toInt()) * v1.f(0L.toInt())))
    v1.d(0L.toInt(), (v1.f(0L.toInt())).toDouble())
    v1.d(0L.toInt(), (v1.d(0L.toInt()) - v30.d(0L.toInt())))
    v3.f(0L.toInt(), (v14.d(0L.toInt())).toFloat())
    v4.f(0L.toInt(), (v3.f(0L.toInt()) * v3.f(0L.toInt())))
    v5.f(0L.toInt(), fma(v4.f(0L.toInt()), v27.f(0L.toInt()), v26.f(0L.toInt())))
    v4.f(0L.toInt(), fma(v4.f(0L.toInt()), v5.f(0L.toInt()), v28.f(0L.toInt())))
    v3.f(0L.toInt(), (v4.f(0L.toInt()) * v3.f(0L.toInt())))
    v3.d(0L.toInt(), (v3.f(0L.toInt())).toDouble())
    v3.d(0L.toInt(), (v14.d(0L.toInt()) - v3.d(0L.toInt())))
    v1.d(0L.toInt(), (v3.d(0L.toInt()) + v1.d(0L.toInt())))
    v3.d(0L.toInt(), -(1.5707963267948966))
    v3.d(0L.toInt(), (v30.d(0L.toInt()) + v3.d(0L.toInt())))
    v1.d(0L.toInt(), (v3.d(0L.toInt()) + v1.d(0L.toInt())))
    v1.d(0L.toInt(), fma(v21.d(0L.toInt()), v1.d(0L.toInt()), v0.d(0L.toInt())))
    v0.d(0L.toInt(), ld_d(x21))
    v1.d(0L.toInt(), (v1.d(0L.toInt()) - v2.d(0L.toInt())))
    v4.d(0L.toInt(), ld_d((sp + 472L)))
    v5.d(0L.toInt(), ld_d((sp + 568L)))
    v4.d(0L.toInt(), (v4.d(0L.toInt()) - v5.d(0L.toInt())))
    st_d((sp + 472L), v10.d(0L.toInt()))
    v5.d(0L.toInt(), ld_d((sp + 416L)))
    v5.d(0L.toInt(), (v5.d(0L.toInt()) - v10.d(0L.toInt())))
    v6.f(0L.toInt(), (v13.d(0L.toInt())).toFloat())
    v7.f(0L.toInt(), (v4.d(0L.toInt())).toFloat())
    for (k in 0L until 4L) {
        v16.u32(k.toInt(), (128L shl 24L.toInt()))
    }
    for (k in 0L until 2L) {
        v6.u64(k.toInt(), ((v6.u64(k.toInt()) and (v16.u64(k.toInt())).inv()) or (v7.u64(k.toInt()) and v16.u64(k.toInt()))))
    }
    v6.d(0L.toInt(), (v6.f(0L.toInt())).toDouble())
    v7.f(0L.toInt(), (v29.d(0L.toInt())).toFloat())
    v17.f(0L.toInt(), (v5.d(0L.toInt())).toFloat())
    for (k in 0L until 2L) {
        v7.u64(k.toInt(), ((v7.u64(k.toInt()) and (v16.u64(k.toInt())).inv()) or (v17.u64(k.toInt()) and v16.u64(k.toInt()))))
    }
    v7.d(0L.toInt(), (v7.f(0L.toInt())).toDouble())
    v16.d(0L.toInt(), -(v6.d(0L.toInt())))
    v18.d(0L.toInt(), (v4.d(0L.toInt()) + v6.d(0L.toInt())))
    v19.d(0L.toInt(), (v5.d(0L.toInt()) + v7.d(0L.toInt())))
    st_d((sp + 2536L), v19.d(0L.toInt()))
    st_d((sp + 2528L), v18.d(0L.toInt()))
    v4.d(0L.toInt(), (v4.d(0L.toInt()) - v6.d(0L.toInt())))
    v5.d(0L.toInt(), (v5.d(0L.toInt()) - v7.d(0L.toInt())))
    st_d((sp + 2552L), v5.d(0L.toInt()))
    st_d((sp + 2544L), v4.d(0L.toInt()))
    st_d((sp + 2568L), v19.d(0L.toInt()))
    st_d((sp + 2560L), v16.d(0L.toInt()))
    x8 = (sp + 2528L)
    x8 = (x8 + 40L)
    st_d((sp + 2584L), v5.d(0L.toInt()))
    st_d((sp + 2576L), v6.d(0L.toInt()))
    v17.d(0L.toInt(), -(v7.d(0L.toInt())))
    st_d((sp + 2600L), v17.d(0L.toInt()))
    st_d((sp + 2592L), v18.d(0L.toInt()))
    st_d((sp + 2616L), v7.d(0L.toInt()))
    st_d((sp + 2608L), v4.d(0L.toInt()))
    st_d((sp + 2632L), v7.d(0L.toInt()))
    st_d((sp + 2624L), v6.d(0L.toInt()))
    st_d((sp + 2648L), v17.d(0L.toInt()))
    st_d((sp + 2640L), v16.d(0L.toInt()))
    v6.d(0L.toInt(), ld_d((x20 + 8L)))
    v7.d(0L.toInt(), ld_d(((x20 + 8L) + 8L)))
    v16.d(0L.toInt(), ld_d((x20 + 40L)))
    v17.d(0L.toInt(), ld_d(((x20 + 40L) + 8L)))
    v20.d(0L.toInt(), (v16.d(0L.toInt()) * v19.d(0L.toInt())))
    v20.d(0L.toInt(), fma(v6.d(0L.toInt()), v18.d(0L.toInt()), v20.d(0L.toInt())))
    v19.d(0L.toInt(), (v17.d(0L.toInt()) * v19.d(0L.toInt())))
    v18.d(0L.toInt(), fma(v7.d(0L.toInt()), v18.d(0L.toInt()), v19.d(0L.toInt())))
    v18.d(0L.toInt(), (v24.d(0L.toInt()) * v18.d(0L.toInt())))
    v18.d(0L.toInt(), fma(v23.d(0L.toInt()), v20.d(0L.toInt()), v18.d(0L.toInt())))
    st_d((sp + 2656L), v18.d(0L.toInt()))
    x9 = ((0L or 8L) and 0xffffffffL)
    x10 = (sp + 2656L)
    return 2
}

// C source block: L18534454c
internal fun CurlMeshKernel.block1(): Int {
    v4.d(0L.toInt(), ld_d((x8 + -(8L))))
    v5.d(0L.toInt(), ld_d(((x8 + -(8L)) + 8L)))
    x8 = (x8 + 16L)
    x9 = (x9 + 8L)
    return 2
}

// C source block: L185344558
internal fun CurlMeshKernel.block2(): Int {
    v18.d(0L.toInt(), (v16.d(0L.toInt()) * v5.d(0L.toInt())))
    v18.d(0L.toInt(), fma(v6.d(0L.toInt()), v4.d(0L.toInt()), v18.d(0L.toInt())))
    v5.d(0L.toInt(), (v17.d(0L.toInt()) * v5.d(0L.toInt())))
    v4.d(0L.toInt(), fma(v7.d(0L.toInt()), v4.d(0L.toInt()), v5.d(0L.toInt())))
    v4.d(0L.toInt(), (v24.d(0L.toInt()) * v4.d(0L.toInt())))
    v4.d(0L.toInt(), fma(v23.d(0L.toInt()), v18.d(0L.toInt()), v4.d(0L.toInt())))
    st_d((x10 + x9), v4.d(0L.toInt()))
    subflags(x9, 56L, 64L)
    return if (!(Z)) 1 else 3
}

// C source block: fallthrough3
internal fun CurlMeshKernel.block3(): Int {
    v4.d(0L.toInt(), ld_d((sp + 520L)))
    st_d((sp + 416L), v4.d(0L.toInt()))
    v4.load(memory, (sp + 400L))
    run {
        var t = v4.copy()
        for (k in 0L until 4L) {
            v4.h(k.toInt(), floatHalf(t.f(k.toInt())))
        }
    }
    v4.store(memory, (sp + 112L))
    v16.d(0L.toInt(), 1.0)
    v14.d(0L.toInt(), ld_d((sp + 488L)))
    v18.d(0L.toInt(), (v16.d(0L.toInt()) / v14.d(0L.toInt())))
    v4.d(0L.toInt(), ld_d((sp + 560L)))
    v4.d(0L.toInt(), (v16.d(0L.toInt()) / v4.d(0L.toInt())))
    st_d((sp + 400L), v4.d(0L.toInt()))
    v4.d(0L.toInt(), ld_d((sp + 568L)))
    v5.d(0L.toInt(), ld_d((sp + 392L)))
    v4.d(0L.toInt(), (v4.d(0L.toInt()) - v5.d(0L.toInt())))
    st_d((sp + 392L), v4.d(0L.toInt()))
    v4.d(0L.toInt(), ld_d((sp + 368L)))
    v4.d(0L.toInt(), (v13.d(0L.toInt()) + v4.d(0L.toInt())))
    st_d((sp + 368L), v4.d(0L.toInt()))
    v4.d(0L.toInt(), ld_d((sp + 352L)))
    v4.d(0L.toInt(), (v13.d(0L.toInt()) + v4.d(0L.toInt())))
    st_d((sp + 352L), v4.d(0L.toInt()))
    v6.d(0L.toInt(), (v0.d(0L.toInt()) * v1.d(0L.toInt())))
    v17.d(0L.toInt(), ld_d((sp + 544L)))
    fcmp(v17.d(0L.toInt()), 0.0)
    v20.d(0L.toInt(), ld_d((sp + 320L)))
    v0.d(0L.toInt(), ld_d((sp + 344L)))
    v4.d(0L.toInt(), (if ((!(Z) && (N == V))) v20.d(0L.toInt()) else v0.d(0L.toInt())))
    v5.d(0L.toInt(), (if ((!(Z) && (N == V))) v0.d(0L.toInt()) else v20.d(0L.toInt())))
    v0.d(0L.toInt(), (v29.d(0L.toInt()) + v4.d(0L.toInt())))
    st_d((sp + 344L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), (v29.d(0L.toInt()) + v5.d(0L.toInt())))
    st_d((sp + 336L), v0.d(0L.toInt()))
    x8 = (sp + 2656L)
    v0.d(0L.toInt(), ld_d((sp + 2704L)))
    v1.d(0L.toInt(), ld_d((sp + 2664L)))
    x9 = ((0L or 1L) and 0xffffffffL)
    x10 = (sp + 2656L)
    v7.set(v1)
    v16.d(0L.toInt(), (v16.d(0L.toInt()) / v17.d(0L.toInt())))
    st_d((sp + 328L), v16.d(0L.toInt()))
    v17.d(0L.toInt(), ld_d((sp + 504L)))
    v9.d(0L.toInt(), ld_d((sp + 480L)))
    return 5
}

// C source block: L185344628
internal fun CurlMeshKernel.block4(): Int {
    v7.d(0L.toInt(), ld_d((x8 + (x9 shl 3L.toInt()))))
    x10 = (x10 + 8L)
    return 5
}

// C source block: L185344630
internal fun CurlMeshKernel.block5(): Int {
    x11 = 0L
    return 6
}

// C source block: L185344634
internal fun CurlMeshKernel.block6(): Int {
    v16.d(0L.toInt(), ld_d((x10 + (x11 shl 3L.toInt()))))
    fcmp(v7.d(0L.toInt()), v16.d(0L.toInt()))
    return if ((Z || (N != V))) 9 else 7
}

// C source block: fallthrough7
internal fun CurlMeshKernel.block7(): Int {
    x12 = (x10 + (x11 shl 3L.toInt()))
    st_d((x12 + 8L), v16.d(0L.toInt()))
    x11 = (x11 - 1L)
    x12 = (x9 + x11)
    subflags(x12, 0L, 64L)
    return if ((!(Z) && (N == V))) 6 else 8
}

// C source block: fallthrough8
internal fun CurlMeshKernel.block8(): Int {
    return 10
}

// C source block: L18534465c
internal fun CurlMeshKernel.block9(): Int {
    x12 = (x9 + x11)
    return 10
}

// C source block: L185344660
internal fun CurlMeshKernel.block10(): Int {
    x11 = ((x12).toInt().toLong() shl 3L.toInt())
    st_d((x8 + x11), v7.d(0L.toInt()))
    x9 = (x9 + 1L)
    subflags(x9, 8L, 64L)
    return if (!(Z)) 4 else 11
}

// C source block: fallthrough11
internal fun CurlMeshKernel.block11(): Int {
    v7.d(0L.toInt(), (v2.d(0L.toInt()) + v6.d(0L.toInt())))
    v19.d(0L.toInt(), fma(-(v21.d(0L.toInt())), v3.d(0L.toInt()), v7.d(0L.toInt())))
    v2.d(0L.toInt(), (v30.d(0L.toInt()) - v8.d(0L.toInt())))
    v3.d(0L.toInt(), (v17.d(0L.toInt()) - v30.d(0L.toInt())))
    fcmp(v2.d(0L.toInt()), v3.d(0L.toInt()))
    st_d((sp + 48L), v2.d(0L.toInt()))
    st_d((sp + 16L), v3.d(0L.toInt()))
    v2.d(0L.toInt(), (if ((!(Z) && (N == V))) v2.d(0L.toInt()) else v3.d(0L.toInt())))
    v2.d(0L.toInt(), (v21.d(0L.toInt()) * v2.d(0L.toInt())))
    v3.d(0L.toInt(), 0.08333333333333333)
    v2.d(0L.toInt(), (v2.d(0L.toInt()) * v3.d(0L.toInt())))
    fcmp(v2.d(0L.toInt()), 0.0)
    st_d((sp + 520L), v7.d(0L.toInt()))
    return if (!(Z)) 13 else 12
}

// C source block: fallthrough12
internal fun CurlMeshKernel.block12(): Int {
    st_d((sp + 2728L), v19.d(0L.toInt()))
    st_d((sp + 2720L), v19.d(0L.toInt()))
    x8 = ((0L or 2L) and 0xffffffffL)
    return 20
}

// C source block: L1853446c0
internal fun CurlMeshKernel.block13(): Int {
    v3.d(0L.toInt(), (v25.d(0L.toInt()) + v7.d(0L.toInt())))
    fcmp(v1.d(0L.toInt()), v3.d(0L.toInt()))
    v3.d(0L.toInt(), (if (N) v1.d(0L.toInt()) else v3.d(0L.toInt())))
    v6.d(0L.toInt(), (v15.d(0L.toInt()) - v17.d(0L.toInt())))
    v6.d(0L.toInt(), fma(v21.d(0L.toInt()), v6.d(0L.toInt()), v7.d(0L.toInt())))
    fcmp(v0.d(0L.toInt()), v6.d(0L.toInt()))
    v6.d(0L.toInt(), (if ((!(Z) && (N == V))) v0.d(0L.toInt()) else v6.d(0L.toInt())))
    v7.d(0L.toInt(), (v19.d(0L.toInt()) - v3.d(0L.toInt())))
    v7.d(0L.toInt(), (v7.d(0L.toInt()) / v2.d(0L.toInt())))
    v7.f(0L.toInt(), (v7.d(0L.toInt())).toFloat())
    v7.f(0L.toInt(), floor(v7.f(0L.toInt())))
    v16.f(0L.toInt(), (-(12.0)).toFloat())
    v7.f(0L.toInt(), max(v7.f(0L.toInt()), v16.f(0L.toInt())))
    x9 = (((v7.f(0L.toInt())).toLong()).toInt().toLong() and 0xffffffffL)
    v7.d(0L.toInt(), (v19.d(0L.toInt()) - v6.d(0L.toInt())))
    v7.d(0L.toInt(), (v7.d(0L.toInt()) / v2.d(0L.toInt())))
    v7.f(0L.toInt(), (v7.d(0L.toInt())).toFloat())
    v7.f(0L.toInt(), ceil(v7.f(0L.toInt())))
    v16.f(0L.toInt(), (12.0).toFloat())
    v7.f(0L.toInt(), min(v7.f(0L.toInt()), v16.f(0L.toInt())))
    x10 = (((v7.f(0L.toInt())).toLong()).toInt().toLong() and 0xffffffffL)
    subflags((x9 and 0xffffffffL), (x10 and 0xffffffffL), 32L)
    return if ((Z || (N != V))) 15 else 14
}

// C source block: fallthrough14
internal fun CurlMeshKernel.block14(): Int {
    x8 = (0L and 0xffffffffL)
    return 20
}

// C source block: L185344724
internal fun CurlMeshKernel.block15(): Int {
    x8 = (0L and 0xffffffffL)
    x11 = (sp + 2720L)
    return 16
}

// C source block: L18534472c
internal fun CurlMeshKernel.block16(): Int {
    v7.f(0L.toInt(), (((x9 and 0xffffffffL)).toInt().toLong()).toFloat())
    v7.d(0L.toInt(), (v7.f(0L.toInt())).toDouble())
    v7.d(0L.toInt(), fma(-(v2.d(0L.toInt())), v7.d(0L.toInt()), v19.d(0L.toInt())))
    fcmp(v3.d(0L.toInt()), v7.d(0L.toInt()))
    v7.d(0L.toInt(), (if (N) v3.d(0L.toInt()) else v7.d(0L.toInt())))
    fcmp(v6.d(0L.toInt()), v7.d(0L.toInt()))
    v7.d(0L.toInt(), (if ((!(Z) && (N == V))) v6.d(0L.toInt()) else v7.d(0L.toInt())))
    subflags((x9 and 0xffffffffL), 0L, 32L)
    return if ((!(Z) && (N == V))) 18 else 17
}

// C source block: fallthrough17
internal fun CurlMeshKernel.block17(): Int {
    st_d((x11 + (((x8 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())), v7.d(0L.toInt()))
    x8 = (((x8 and 0xffffffffL) + 1L) and 0xffffffffL)
    return if ((((x9 and 0xffffffffL) and (1L shl 31L.toInt())) != 0L)) 19 else 18
}

// C source block: L18534475c
internal fun CurlMeshKernel.block18(): Int {
    st_d((x11 + (((x8 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())), v7.d(0L.toInt()))
    x8 = (((x8 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 19
}

// C source block: L185344764
internal fun CurlMeshKernel.block19(): Int {
    x12 = (((x9 and 0xffffffffL) + 1L) and 0xffffffffL)
    subflags((x9 and 0xffffffffL), (x10 and 0xffffffffL), 32L)
    x9 = x12
    return if ((N != V)) 16 else 20
}

// C source block: L185344774
internal fun CurlMeshKernel.block20(): Int {
    x9 = 0L
    x27 = (0L and 0xffffffffL)
    x17 = (0L and 0xffffffffL)
    x10 = (0L and 0xffffffffL)
    x11 = (0L and 0xffffffffL)
    v2.d(0L.toInt(), ld_d((sp + 472L)))
    v2.d(0L.toInt(), (v2.d(0L.toInt()) - v20.d(0L.toInt())))
    st_d((sp + 320L), v2.d(0L.toInt()))
    v2.d(0L.toInt(), (v4.d(0L.toInt()) - v29.d(0L.toInt())))
    st_d((sp + 312L), v2.d(0L.toInt()))
    v2.d(0L.toInt(), (v5.d(0L.toInt()) - v29.d(0L.toInt())))
    st_d((sp + 304L), v2.d(0L.toInt()))
    subflags((x8 and 0xffffffffL), 0L, 32L)
    x16 = ((if ((!(Z) && (N == V))) 1L else 0L) and 0xffffffffL)
    x12 = (sp + 2928L)
    x13 = (sp + 2720L)
    x14 = (0L or 4294967296L)
    x15 = (sp + 2656L)
    v2.d(0L.toInt(), 1.0)
    v4.d(0L.toInt(), (v2.d(0L.toInt()) / v9.d(0L.toInt())))
    return 21
}

// C source block: L1853447c4
internal fun CurlMeshKernel.block21(): Int {
    subflags((x10 and 0xffffffffL), 8L, 32L)
    return if (!(Z)) 23 else 22
}

// C source block: fallthrough22
internal fun CurlMeshKernel.block22(): Int {
    x16 = ld_u64((x13 + (((x11 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())))
    return 26
}

// C source block: L1853447d4
internal fun CurlMeshKernel.block23(): Int {
    return if ((((x16 and 0xffffffffL) and (1L shl 0L.toInt())) == 0L)) 27 else 24
}

// C source block: fallthrough24
internal fun CurlMeshKernel.block24(): Int {
    v2.d(0L.toInt(), ld_d((x13 + (((x11 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))))
    v3.d(0L.toInt(), ld_d((x15 + (((x10 and 0xffffffffL)).toInt().toLong() shl 3L.toInt()))))
    fcmp(v2.d(0L.toInt()), v3.d(0L.toInt()))
    return if ((Z || (N != V))) 28 else 25
}

// C source block: fallthrough25
internal fun CurlMeshKernel.block25(): Int {
    x16 = v2.u64(0L.toInt())
    return 26
}

// C source block: L1853447ec
internal fun CurlMeshKernel.block26(): Int {
    x11 = (((x11 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 30
}

// C source block: L1853447f4
internal fun CurlMeshKernel.block27(): Int {
    x16 = ld_u64((x15 + (((x10 and 0xffffffffL)).toInt().toLong() shl 3L.toInt())))
    return 29
}

// C source block: L1853447fc
internal fun CurlMeshKernel.block28(): Int {
    x16 = v3.u64(0L.toInt())
    return 29
}

// C source block: L185344800
internal fun CurlMeshKernel.block29(): Int {
    x10 = (((x10 and 0xffffffffL) + 1L) and 0xffffffffL)
    return 30
}

// C source block: L185344804
internal fun CurlMeshKernel.block30(): Int {
    st_u64(x12, x16)
    x12 = (x12 + 8L)
    x17 = (((x17 and 0xffffffffL) + 2L) and 0xffffffffL)
    x27 = (((x27 and 0xffffffffL) + 1L) and 0xffffffffL)
    x9 = (x9 + x14)
    subflags((x11 and 0xffffffffL), (x8 and 0xffffffffL), 32L)
    x16 = ((if ((N != V)) 1L else 0L) and 0xffffffffL)
    return if ((N != V)) 21 else 31
}

// C source block: fallthrough31
internal fun CurlMeshKernel.block31(): Int {
    subflags((x10 and 0xffffffffL), 8L, 32L)
    return if ((N != V)) 21 else 32
}

// C source block: fallthrough32
internal fun CurlMeshKernel.block32(): Int {
    st_u64((sp + 96L), x21)
    x12 = 0L
    x10 = 0L
    x9 = (x9 shr 32L.toInt())
    x13 = ((0L or 2L) and 0xffffffffL)
    x11 = (sp + 2928L)
    return 33
}

// C source block: L185344840
internal fun CurlMeshKernel.block33(): Int {
    x25 = (x10 + 1L)
    x13 = (((x13 and 0xffffffffL) + 2L) and 0xffffffffL)
    x12 = (x12 + 128L)
    x17 = (((x17 and 0xffffffffL) - 2L) and 0xffffffffL)
    subflags(x25, x9, 64L)
    return if ((N == V)) 35 else 34
}

// C source block: fallthrough34
internal fun CurlMeshKernel.block34(): Int {
    v2.d(0L.toInt(), ld_d((x11 + (x10 shl 3L.toInt()))))
    fcmp(v2.d(0L.toInt()), v19.d(0L.toInt()))
    x10 = x25
    return if ((!(Z) && (N == V))) 33 else 35
}

// C source block: L185344868
internal fun CurlMeshKernel.block35(): Int {
    st_u64((sp + 176L), x17)
    st_u64(((sp + 176L) + 8L), x20)
    x11 = (0L and 0xffffffffL)
    x9 = 0L
    x22 = (((x13 and 0xffffffffL) - 1L) and 0xffffffffL)
    x10 = (sp + (35L shl 12L.toInt()))
    x10 = (x10 + 1296L)
    x10 = (x10 + x12)
    x26 = (x10 + 104L)
    x10 = (sp + (34L shl 12L.toInt()))
    x10 = (x10 + 1040L)
    st_u64((sp + 104L), x12)
    x10 = (x10 + x12)
    x24 = (x10 + 40L)
    x10 = ((x27 and 0xffffffffL) and 0xffffffffL)
    st_u64((sp + 296L), x10)
    x28 = (x10 - x25)
    x14 = (0L or -2L)
    x12 = (sp + 2928L)
    st_u32((sp + 44L), (x13 and 0xffffffffL))
    x10 = x13
    return 36
}

// C source block: L1853448b8
internal fun CurlMeshKernel.block36(): Int {
    v2.d(0L.toInt(), ld_d((x12 + (x9 shl 3L.toInt()))))
    x9 = (x9 + 1L)
    x11 = (((x11 and 0xffffffffL) - 1L) and 0xffffffffL)
    x14 = (x14 + 2L)
    x10 = (((x10 and 0xffffffffL) - 2L) and 0xffffffffL)
    fcmp(v2.d(0L.toInt()), v1.d(0L.toInt()))
    return if ((!(Z) && (N == V))) 36 else 37
}

// C source block: fallthrough37
internal fun CurlMeshKernel.block37(): Int {
    st_d((sp + 24L), v19.d(0L.toInt()))
    st_f((sp + 148L), v28.f(0L.toInt()))
    st_f(((sp + 148L) + 4L), v27.f(0L.toInt()))
    st_f((sp + 156L), v26.f(0L.toInt()))
    st_d((sp + 56L), v25.d(0L.toInt()))
    st_u64((sp + 32L), x14)
    st_d((sp + 224L), v4.d(0L.toInt()))
    st_d(((sp + 224L) + 8L), v18.d(0L.toInt()))
    st_d((sp + 448L), v15.d(0L.toInt()))
    x8 = (((x8 and 0xffffffffL) + 7L) and 0xffffffffL)
    x14 = (((x9 and 0xffffffffL) - 1L) and 0xffffffffL)
    x11 = ((x8 and 0xffffffffL)).toInt().toLong()
    x13 = (((x9 and 0xffffffffL) - (x25 and 0xffffffffL)) and 0xffffffffL)
    x12 = (sp + 2928L)
    st_u64((sp + 288L), x14)
    return 38
}

// C source block: L185344908
internal fun CurlMeshKernel.block38(): Int {
    x8 = x14
    x23 = x13
    subflags(x14, x11, 64L)
    return if ((N == V)) 40 else 39
}

// C source block: fallthrough39
internal fun CurlMeshKernel.block39(): Int {
    x14 = (x8 + 1L)
    x13 = (x12 + (x8 shl 3L.toInt()))
    v1.d(0L.toInt(), ld_d((x13 + 8L)))
    x13 = (((x23 and 0xffffffffL) + 1L) and 0xffffffffL)
    fcmp(v1.d(0L.toInt()), v0.d(0L.toInt()))
    return if ((N == V)) 38 else 40
}

// C source block: L185344930
internal fun CurlMeshKernel.block40(): Int {
    x11 = (((x23 and 0xffffffffL) - 1L) and 0xffffffffL)
    st_u32((sp + 68L), (x11 and 0xffffffffL))
    x9 = (((x25 and 0xffffffffL) - (x9 and 0xffffffffL)) and 0xffffffffL)
    x9 = (((x9 and 0xffffffffL) + 1L) and 0xffffffffL)
    subflags((x9 and 0xffffffffL), 0L, 32L)
    x9 = ((if ((!(Z) && (N == V))) (x10 and 0xffffffffL) else 0L) and 0xffffffffL)
    st_u32((sp + 172L), (x9 and 0xffffffffL))
    subflags((x27 and 0xffffffffL), 1L, 32L)
    x21 = (((x27 and 0xffffffffL) - 1L) and 0xffffffffL)
    st_d((sp + 496L), v8.d(0L.toInt()))
    return if ((N != V)) 52 else 41
}

// C source block: fallthrough41
internal fun CurlMeshKernel.block41(): Int {
    x20 = 0L
    v1.d(0L.toInt(), ld_d((sp + 456L)))
    v0.d(0L.toInt(), ld_d(((sp + 456L) + 8L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) / v1.d(0L.toInt())))
    v0.d(0L.toInt(), abs(v0.d(0L.toInt())))
    v1.d(0L.toInt(), 1.0)
    v0.d(0L.toInt(), (v0.d(0L.toInt()) + v1.d(0L.toInt())))
    v0.d(0L.toInt(), (v1.d(0L.toInt()) / v0.d(0L.toInt())))
    st_d((sp + 160L), v0.d(0L.toInt()))
    x8 = (x8 and 4294967295L)
    st_u64((sp + 216L), x8)
    v0.d(0L.toInt(), ld_d((sp + 672L)))
    st_d((sp + 280L), v0.d(0L.toInt()))
    v13.d(0L.toInt(), ld_d((sp + 664L)))
    v0.load(memory, (sp + 112L))
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    v0.store(memory, (sp + 256L))
    v0.d(0L.toInt(), ld_d((sp + 240L)))
    run {
        var t = v0.copy()
        for (k in 0L until 4L) {
            v0.f(k.toInt(), halfFloat(t.h(k.toInt())))
        }
    }
    v0.store(memory, (sp + 240L))
    v0.d(0L.toInt(), -(1.5000000596046448))
    st_d((sp + 200L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), 0.7549999952316284)
    st_d((sp + 192L), v0.d(0L.toInt()))
    v0.d(0L.toInt(), -(0.8199999928474426))
    st_d((sp + 136L), v0.d(0L.toInt()))
    v11.d(0L.toInt(), ld_d((sp + 400L)))
    v15.d(0L.toInt(), ld_d((sp + 328L)))
    return 42
}

// C source block: L1853449d0
internal fun CurlMeshKernel.block42(): Int {
    x8 = (sp + 2928L)
    v12.d(0L.toInt(), ld_d((x8 + (x20 shl 3L.toInt()))))
    v0.d(0L.toInt(), ld_d((sp + 464L)))
    v9.d(0L.toInt(), (v0.d(0L.toInt()) * v12.d(0L.toInt())))
    v0.d(0L.toInt(), ld_d((sp + 456L)))
    v10.d(0L.toInt(), (v0.d(0L.toInt()) * v12.d(0L.toInt())))
    v0.d(0L.toInt(), ld_d((sp + 568L)))
    v8.d(0L.toInt(), (v0.d(0L.toInt()) + v9.d(0L.toInt())))
    v0.d(0L.toInt(), ld_d((sp + 472L)))
    v14.d(0L.toInt(), (v0.d(0L.toInt()) + v10.d(0L.toInt())))
    x0 = (sp + (28L shl 12L.toInt()))
    x0 = (x0 + 272L)
    x1 = (sp + (7L shl 12L.toInt()))
    x1 = (x1 + 896L)
    v0.d(0L.toInt(), ld_d((sp + 368L)))
    v1.d(0L.toInt(), ld_d((sp + 344L)))
    v2.d(0L.toInt(), ld_d((sp + 424L)))
    v3.d(0L.toInt(), ld_d((sp + 304L)))
    v4.set(v8)
    v5.set(v14)
    v6.set(v11)
    v7.set(v15)
    line_clip(x0, x1, v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt()), v4.d(0L.toInt()), v5.d(0L.toInt()), v6.d(0L.toInt()), v7.d(0L.toInt()))
    x0 = (sp + (21L shl 12L.toInt()))
    x0 = (x0 + 3600L)
    x1 = (sp + (4L shl 12L.toInt()))
    v0.d(0L.toInt(), ld_d((sp + 432L)))
    v1.d(0L.toInt(), ld_d((sp + 312L)))
    v2.d(0L.toInt(), ld_d((sp + 352L)))
    v3.d(0L.toInt(), ld_d((sp + 336L)))
    v4.set(v8)
    v5.set(v14)
    v8.d(0L.toInt(), ld_d((sp + 496L)))
    v6.set(v11)
    v7.set(v15)
    line_clip(x0, x1, v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), v3.d(0L.toInt()), v4.d(0L.toInt()), v5.d(0L.toInt()), v6.d(0L.toInt()), v7.d(0L.toInt()))
    x0 = (sp + (10L shl 12L.toInt()))
    x0 = (x0 + 1808L)
    v0.set(v12)
    v1.d(0L.toInt(), ld_d((sp + 520L)))
    v4.d(0L.toInt(), ld_d((sp + 504L)))
    v2.d(0L.toInt(), ld_d(((sp + 504L) + 8L)))
    v3.set(v8)
    v0.f(0L.toInt(), displace(v0.d(0L.toInt()), v1.d(0L.toInt()), v2.d(0L.toInt()), x0, v3.d(0L.toInt()), v4.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.f(0L.toInt())).toDouble())
    v1.d(0L.toInt(), (v12.d(0L.toInt()) + v0.d(0L.toInt())))
    x8 = (sp + (10L shl 12L.toInt()))
    x8 = (x8 + 1792L)
    v4.d(0L.toInt(), ld_d((x8 + 16L)))
    x9 = (ld_u32((sp + 440L)) and 0xffffffffL)
    return if (((x9 and 0xffffffffL) == 0L)) 45 else 43
}

// C source block: fallthrough43
internal fun CurlMeshKernel.block43(): Int {
    v0.d(0L.toInt(), ld_d((sp + 200L)))
    v2.d(0L.toInt(), 2.5)
    v0.d(0L.toInt(), fma(v4.d(0L.toInt()), v2.d(0L.toInt()), v0.d(0L.toInt())))
    v2.d(0L.toInt(), 1.0)
    v0.d(0L.toInt(), min(v0.d(0L.toInt()), v2.d(0L.toInt())))
    v2.u64(0L.toInt(), 0L)
    v0.d(0L.toInt(), max(v0.d(0L.toInt()), v2.d(0L.toInt())))
    st_d((x8 + 16L), v0.d(0L.toInt()))
    v2.d(0L.toInt(), ld_d((sp + 192L)))
    fcmp(v0.d(0L.toInt()), v2.d(0L.toInt()))
    v5.d(0L.toInt(), ld_d((sp + 528L)))
    return if ((Z || (N != V))) 46 else 44
}

// C source block: fallthrough44
internal fun CurlMeshKernel.block44(): Int {
    v2.d(0L.toInt(), ld_d((sp + 136L)))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) + v2.d(0L.toInt())))
    v0.d(0L.toInt(), (v0.d(0L.toInt()) * v0.d(0L.toInt())))
    v2.d(0L.toInt(), 0.4000000059604645)
    v3.d(0L.toInt(), 15.0)
    return 47
}

// C source block: L185344ae4
internal fun CurlMeshKernel.block45(): Int {
    v5.d(0L.toInt(), ld_d((sp + 528L)))
    return 48
}

// C source block: L185344aec
internal fun CurlMeshKernel.block46(): Int {
    v2.d(0L.toInt(), (v0.d(0L.toInt()) * v0.d(0L.toInt())))
    v3.d(0L.toInt(), 0.375)
    v2.d(0L.toInt(), (v2.d(0L.toInt()) * v3.d(0L.toInt())))
    v3.d(0L.toInt(), 0.3499999940395355)
    return 47
}

// C source block: L185344b00
internal fun CurlMeshKernel.block47(): Int {
    v4.d(0L.toInt(), fma(v0.d(0L.toInt()), v3.d(0L.toInt()), v2.d(0L.toInt())))
    st_d((x8 + 16L), v4.d(0L.toInt()))
    return 48
}
