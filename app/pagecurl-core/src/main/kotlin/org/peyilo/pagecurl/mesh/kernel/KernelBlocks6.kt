// Generated from the verified C11 curl geometry; regenerate with tools/Port-CurlKernel.py.
// Direct Kotlin arithmetic and managed scratch storage. No machine instructions are executed.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")
package org.peyilo.pagecurl.mesh.kernel
import kotlin.math.*
import org.peyilo.pagecurl.math.AppleMath
import org.peyilo.pagecurl.math.BinaryMath.fma

// C source block: fallthrough240
internal fun CurlMeshKernel.block240(): Int {
    x8 = ld_u64((x19 + 8L))
    x9 = (ld_u8((x8 + 417L)) and 0xffffffffL)
    x9 = (((x9 and 0xffffffffL) and 4294967293L) and 0xffffffffL)
    st_u8((x8 + 417L), (x9 and 0xffffffffL))
    return 241
}

// C source block: L18534674c
internal fun CurlMeshKernel.block241(): Int {
    st_u64((x19 + 552L), 0L)
    x8 = (ld_u32((x19 + 232L)) and 0xffffffffL)
    x8 = (((x8 and 0xffffffffL) + 1L) and 0xffffffffL)
    st_u32((x19 + 232L), (x8 and 0xffffffffL))
    x8 = ld_u64((x19 + 8L))
    st_u8((x8 + 16L), 0L)
    x8 = ld_u64((x19 + 8L))
    x9 = (ld_u8((x8 + 417L)) and 0xffffffffL)
    x9 = (((x9 and 0xffffffffL) and 254L) and 0xffffffffL)
    st_u8((x8 + 417L), (x9 and 0xffffffffL))
    return 242
}

// C source block: L185346774
internal fun CurlMeshKernel.block242(): Int {
    return -1
}

// C source block: L1853467c0
internal fun CurlMeshKernel.block243(): Int {
    return -2
}
