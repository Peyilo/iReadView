package org.peyilo.ireadview.internal.effect.ioscurl

import android.graphics.Bitmap
import android.graphics.Color
import org.peyilo.ireadview.api.effect.PageSurface
import org.peyilo.ireadview.internal.effect.PageBackTint
import org.peyilo.pagecurl.mesh.CurlColor
import org.peyilo.pagecurl.mesh.CurlMaterial

/** Resolve the turning sheet's paper color once, before its CPU snapshot lease is returned.
 * Front texture/lighting remain neutral. The cast shadow uses translucent black:
 * source-over attenuates the receiving page's RGB without tinting it or brightening black.
 */
internal object IosCurlMaterial {
    fun resolve(front: PageSurface, snapshot: Bitmap, shadowOpacity: Float = .55f): CurlMaterial {
        val background = PageBackTint.resolve(front, snapshot)
        val material = CurlMaterial()
        return material.copy(
            shadow = CurlColor(0.0, 0.0, 0.0, shadowOpacity.toDouble()), back = CurlColor(
                Color.red(background) / 255.0,
                Color.green(background) / 255.0,
                Color.blue(background) / 255.0,
                material.back.alpha
            )
        )
    }
}
