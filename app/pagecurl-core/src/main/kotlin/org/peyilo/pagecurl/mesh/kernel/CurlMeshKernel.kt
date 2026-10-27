@file:Suppress("PropertyName")
package org.peyilo.pagecurl.mesh.kernel

import org.peyilo.pagecurl.mesh.CurlDrawBatch
import org.peyilo.pagecurl.model.CurlRect

/** Reusable managed numeric workspace; all addresses are array offsets. */
internal class CurlMeshKernel {
    val memory = KernelMemory()
    val sp = 0x4000L
    var N=false; var Z=false; var C=false; var V=false; var clipped=0L
    var x0 = 0L
    var x1 = 0L
    var x2 = 0L
    var x3 = 0L
    var x4 = 0L
    var x5 = 0L
    var x6 = 0L
    var x7 = 0L
    var x8 = 0L
    var x9 = 0L
    var x10 = 0L
    var x11 = 0L
    var x12 = 0L
    var x13 = 0L
    var x14 = 0L
    var x15 = 0L
    var x16 = 0L
    var x17 = 0L
    var x18 = 0L
    var x19 = 0L
    var x20 = 0L
    var x21 = 0L
    var x22 = 0L
    var x23 = 0L
    var x24 = 0L
    var x25 = 0L
    var x26 = 0L
    var x27 = 0L
    var x28 = 0L
    var x29 = 0L
    var x30 = 0L
    val v0 = KernelVector()
    val v1 = KernelVector()
    val v2 = KernelVector()
    val v3 = KernelVector()
    val v4 = KernelVector()
    val v5 = KernelVector()
    val v6 = KernelVector()
    val v7 = KernelVector()
    val v8 = KernelVector()
    val v9 = KernelVector()
    val v10 = KernelVector()
    val v11 = KernelVector()
    val v12 = KernelVector()
    val v13 = KernelVector()
    val v14 = KernelVector()
    val v15 = KernelVector()
    val v16 = KernelVector()
    val v17 = KernelVector()
    val v18 = KernelVector()
    val v19 = KernelVector()
    val v20 = KernelVector()
    val v21 = KernelVector()
    val v22 = KernelVector()
    val v23 = KernelVector()
    val v24 = KernelVector()
    val v25 = KernelVector()
    val v26 = KernelVector()
    val v27 = KernelVector()
    val v28 = KernelVector()
    val v29 = KernelVector()
    val v30 = KernelVector()
    val v31 = KernelVector()
    val FADE_WEIGHTS = 0x100L; val FADE_STEPS=0x120L
    val CLAMP_STEPS=0x140L; val CLAMP_STEPS2=0x160L
    var clip = CurlRect(0.0,0.0,0.0,0.0)
    val batches=ArrayList<CurlDrawBatch>(32)
    fun resetWorkspace() {
        memory.clear();batches.clear();clipped=0;N=false;Z=false;C=false;V=false
        x0=0;         x1=0;         x2=0;         x3=0;         x4=0;         x5=0;         x6=0;         x7=0;         x8=0;         x9=0;         x10=0;         x11=0;         x12=0;         x13=0;         x14=0;         x15=0;         x16=0;         x17=0;         x18=0;         x19=0;         x20=0;         x21=0;         x22=0;         x23=0;         x24=0;         x25=0;         x26=0;         x27=0;         x28=0;         x29=0;         x30=0
        v0.clear();         v1.clear();         v2.clear();         v3.clear();         v4.clear();         v5.clear();         v6.clear();         v7.clear();         v8.clear();         v9.clear();         v10.clear();         v11.clear();         v12.clear();         v13.clear();         v14.clear();         v15.clear();         v16.clear();         v17.clear();         v18.clear();         v19.clear();         v20.clear();         v21.clear();         v22.clear();         v23.clear();         v24.clear();         v25.clear();         v26.clear();         v27.clear();         v28.clear();         v29.clear();         v30.clear();         v31.clear()
    }
}
