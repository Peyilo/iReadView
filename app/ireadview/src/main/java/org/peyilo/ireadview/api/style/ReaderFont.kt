package org.peyilo.ireadview.api.style

import android.graphics.Typeface
import androidx.annotation.FontRes

/** Immutable font identity. Resource fonts are loaded off the UI thread while preparing layout. */
sealed interface ReaderFont {
    data class System(val family: String, val style: Int = Typeface.NORMAL) : ReaderFont {
        init { require(family.isNotBlank()); require(style in Typeface.NORMAL..Typeface.BOLD_ITALIC) }
    }
    /** A packaged font file/family resource; downloadable provider families are not supported. */
    data class Resource(@FontRes val resourceId: Int) : ReaderFont { init { require(resourceId != 0) } }
    data class Runtime(val typeface: Typeface) : ReaderFont
}
