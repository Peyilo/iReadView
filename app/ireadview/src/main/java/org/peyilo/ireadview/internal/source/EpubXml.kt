package org.peyilo.ireadview.internal.source

import android.util.Xml
import java.io.IOException
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

internal data class EpubXmlLimits(val maxDepth: Int = 64, val maxElements: Int = 100000, val maxCharacters: Int = 32 * 1024 * 1024, val maxFallbackDepth: Int = 64) {
    init { require(maxDepth > 0 && maxElements > 0 && maxCharacters > 0 && maxFallbackDepth > 0) }
}

/** Pull parsing without DTD processing or external resource loading. */
internal object EpubXml {
    fun parse(input: InputStream, limits: EpubXmlLimits, checkActive: () -> Unit, token: (XmlPullParser, Int) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) throw IOException("EPUB parser cannot disable DTD processing")
        parser.setInput(input, null)
        // Common XHTML entities are fixed replacements, never declarations supplied by the book.
        mapOf("nbsp" to "\u00a0", "copy" to "©", "reg" to "®", "trade" to "™", "mdash" to "—", "ndash" to "–",
            "hellip" to "…", "laquo" to "«", "raquo" to "»", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
            "ensp" to "\u2002", "emsp" to "\u2003", "thinsp" to "\u2009").forEach { (name, text) -> parser.defineEntityReplacementText(name, text) }
        var elements = 0
        var characters = 0L
        while (true) {
            checkActive()
            val event = parser.nextToken()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.START_TAG) {
                if (++elements > limits.maxElements || parser.depth > limits.maxDepth || parser.attributeCount > 128) throw IOException("EPUB XML exceeds structural budget")
                for (index in 0 until parser.attributeCount) characters += parser.getAttributeValue(index).length
            }
            if (event == XmlPullParser.DOCDECL && parser.text?.contains('[') == true) throw IOException("EPUB internal DTD declarations are unsupported")
            if (event == XmlPullParser.ENTITY_REF && parser.text == null) throw IOException("Unresolved EPUB XML entity: ${parser.name}")
            if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT || event == XmlPullParser.ENTITY_REF || event == XmlPullParser.IGNORABLE_WHITESPACE)
                characters += parser.text?.length ?: 0
            if (characters > limits.maxCharacters) throw IOException("EPUB XML exceeds character budget")
            token(parser, event)
        }
    }
    fun required(parser: XmlPullParser, name: String): String = parser.getAttributeValue(null, name)
        ?.takeIf { it.isNotBlank() } ?: throw IOException("Missing EPUB ${parser.name}/$name")
}
