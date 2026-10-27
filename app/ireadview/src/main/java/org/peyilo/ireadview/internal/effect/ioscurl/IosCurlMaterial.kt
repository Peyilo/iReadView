package org.peyilo.ireadview.internal.effect.ioscurl

import android.graphics.Bitmap
import android.graphics.Color
import org.peyilo.ireadview.api.effect.PageSurface
import org.peyilo.ireadview.internal.effect.PageBackTint
import org.peyilo.pagecurl.mesh.CurlColor
import org.peyilo.pagecurl.mesh.CurlMaterial

/** Resolve the turning sheet's paper color once, before its CPU snapshot lease is returned.
 * Front texture/lighting and shadow colors remain neutral: tinting those would color the
 * already-colored page twice. Only the reverse paper contribution uses the sampled RGB.
 */
internal object IosCurlMaterial {
    fun resolve(front: PageSurface, snapshot: Bitmap): CurlMaterial {
        val background = PageBackTint.resolve(front, snapshot)
        val material = CurlMaterial()
        return material.copy(back = CurlColor(
            Color.red(background) / 255.0,
            Color.green(background) / 255.0,
            Color.blue(background) / 255.0,
            material.back.alpha
        ))
    }
}
