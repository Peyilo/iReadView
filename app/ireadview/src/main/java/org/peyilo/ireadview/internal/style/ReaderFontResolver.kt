package org.peyilo.ireadview.internal.style

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import org.peyilo.ireadview.api.style.ReaderFont
import org.peyilo.ireadview.api.style.ReaderStyle
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.peyilo.ireadview.api.state.*

/** Called by layout preparation on IO; no provider requests are started. */
internal class ReaderFontResolver(private val context: Context) {
    suspend fun prepareChrome(style: ReaderStyle): ReaderResult<ReaderStyle> = try {
        ReaderResult.Success(withContext(Dispatchers.IO) { resolveChrome(style) })
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { ReaderResult.Failure(ReaderError(ReaderStage.LAYOUT, "FontLoadFailed", failure.message ?: "Unable to load chrome font", cause = failure)) }

    fun resolve(style: ReaderStyle): ReaderStyle {
        return style.copy(body = style.body.copy(font = font(style.body.readerFont, style.body.font)),
            title = style.title.copy(font = font(style.title.readerFont, style.title.font)))
    }

    fun resolveChrome(style: ReaderStyle): ReaderStyle = style.copy(
        header = style.header.copy(font = font(style.header.readerFont, style.header.font)),
        footer = style.footer.copy(font = font(style.footer.readerFont, style.footer.font))
    )

    private fun font(value: ReaderFont?, fallback: Typeface?): Typeface? = when (value) {
            null -> fallback
            is ReaderFont.System -> Typeface.create(value.family, value.style)
            is ReaderFont.Runtime -> value.typeface
            is ReaderFont.Resource -> resource(value.resourceId)
    }

    private fun resource(id: Int): Typeface {
        require(context.resources.getResourceTypeName(id) == "font") { "Reader font resource must be @font" }
        val value = TypedValue()
        context.resources.getValue(id, value, true)
        if (value.string?.toString()?.endsWith(".xml") == true) {
            context.resources.getXml(id).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        for (index in 0 until parser.attributeCount) {
                            require(!parser.getAttributeName(index).startsWith("fontProvider")) { "Downloadable font providers are unsupported; supply a host-loaded Typeface" }
                        }
                    }
                }
            }
        }
        return requireNotNull(ResourcesCompat.getFont(context, id)) { "Unable to load Reader font resource" }
    }
}
