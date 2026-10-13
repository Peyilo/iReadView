package org.peyilo.ireadview.api.style

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/** Each factory invocation must return a fresh Drawable owned by that reading page. */
fun interface DrawableFactory { fun create(context: Context): Drawable }

sealed interface ReaderBackground {
    data class Color(@ColorInt val color: Int) : ReaderBackground
    data class Resource(@DrawableRes val resourceId: Int) : ReaderBackground { init { require(resourceId != 0) } }
    data class Factory(val factory: DrawableFactory) : ReaderBackground
}
