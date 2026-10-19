package org.peyilo.ireadview.internal.source

import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.peyilo.ireadview.api.model.ContentBlock
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.api.model.HeadingBlock
import org.peyilo.ireadview.api.model.TextBlock
import org.peyilo.ireadview.api.model.TextContentBlock
import org.peyilo.ireadview.api.model.LinkedTextBlock
import org.peyilo.ireadview.api.model.TextLink
import org.peyilo.ireadview.api.model.LinkTarget
import org.peyilo.ireadview.api.model.ImageBlock
import org.xmlpull.v1.XmlPullParser

internal class EpubTextDocument(blocks: List<ContentBlock>, anchors: Map<String, ContentAnchor>, diagnostics: List<EpubPackageDiagnostic>) {
    val blocks: List<ContentBlock> = Collections.unmodifiableList(ArrayList(blocks))
    val anchors: Map<String, ContentAnchor> = Collections.unmodifiableMap(LinkedHashMap(anchors))
    val diagnostics: List<EpubPackageDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))
}

/** Extracts semantic flow blocks; the Widget owns presentation, never the XHTML DOM or CSS. */
internal object EpubXhtmlParser {
    private const val XHTML = "http://www.w3.org/1999/xhtml"
    private val boundaries = setOf("p", "div", "section", "article", "aside", "header", "footer", "main", "nav",
        "blockquote", "li", "ul", "ol", "dl", "dt", "dd", "pre", "table", "tr", "td", "th", "hr", "h1", "h2", "h3", "h4", "h5", "h6")
    private val omitted = setOf("script", "style", "iframe", "object", "embed", "canvas", "audio", "video", "rt", "rp")
    private data class Mode(val depth: Int, val heading: Int, val preformatted: Boolean)

    suspend fun load(archive: EpubArchive, path: String, limits: EpubXmlLimits = EpubXmlLimits(), richContent: Boolean = false): EpubTextDocument {
        val coroutine = currentCoroutineContext()
        return archive.read(path) { input ->
            val blocks = ArrayList<ContentBlock>()
            val diagnostics = LinkedHashMap<String, EpubPackageDiagnostic>()
            val modes = ArrayList<Mode>()
            val text = StringBuilder()
            val anchorOffsets = LinkedHashMap<String, Pair<Int, Int>>()
            val seenIds = HashSet<String>()
            var pendingSpace = false
            var bodyDepth = -1
            var bodySeen = false
            var skippedDepth = -1
            var linkTarget: LinkTarget? = null
            var linkDepth = -1
            var linkStart = 0
            val links = ArrayList<TextLink>()
            fun diagnostic(code: String, message: String) {
                if (!diagnostics.containsKey(code)) diagnostics[code] = EpubPackageDiagnostic(code, path, message)
            }
            fun whitespace(char: Char) = char == ' ' || char == '\t' || char == '\r' || char == '\n' || char == '\u000c'
            fun finishLink() {
                val target = linkTarget
                if (target != null && text.length > linkStart) links.add(TextLink(linkStart, text.length, target))
                linkStart = text.length
            }
            fun target(reference: String): LinkTarget? = try {
                val uri = java.net.URI(reference)
                if (uri.isAbsolute) LinkTarget.External(reference)
                else EpubArchive.resolve(path, reference).let { LinkTarget.Internal(it.path, it.fragment) }
            } catch (_: Exception) {
                diagnostic("UnsupportedLink", "An invalid or unsupported link target was omitted")
                null
            }
            fun flush() {
                val mode = modes.lastOrNull()
                if (mode?.preformatted != true) while (text.isNotEmpty() && whitespace(text.last())) text.setLength(text.length - 1)
                if (text.isNotEmpty()) {
                    val id = "block-${blocks.size}"
                    finishLink()
                    val clippedLinks = links.mapNotNull { link ->
                        val end = minOf(link.endExclusive, text.length)
                        if (end > link.startOffset) link.copy(endExclusive = end) else null
                    }
                    blocks.add(if (clippedLinks.isNotEmpty()) LinkedTextBlock(id, text.toString(), clippedLinks, mode?.heading ?: 0)
                        else if ((mode?.heading ?: 0) > 0) HeadingBlock(id, text.toString(), mode!!.heading) else TextBlock(id, text.toString()))
                }
                text.setLength(0)
                links.clear()
                linkStart = 0
                pendingSpace = false
            }
            fun append(value: String) {
                if (modes.lastOrNull()?.preformatted == true) {
                    text.append(value)
                    return
                }
                for (char in value) {
                    if (whitespace(char)) pendingSpace = text.isNotEmpty()
                    else {
                        if (pendingSpace && text.isNotEmpty() && text.last() != '\n') text.append(' ')
                        text.append(char)
                        pendingSpace = false
                    }
                }
            }
            EpubXml.parse(input, limits, { coroutine.ensureActive() }) { parser, event ->
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        val ids = listOfNotNull(parser.getAttributeValue(null, "id"),
                            parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "id")).filter { it.isNotEmpty() }.distinct()
                        // IDs in head or omitted elements still participate in document uniqueness.
                        ids.forEach { if (!seenIds.add(it)) throw IOException("Duplicate EPUB XHTML ID: $it in $path") }
                        if (parser.depth == 1 && (name != "html" || parser.namespace != XHTML)) throw IOException("Invalid EPUB XHTML root: $path")
                        if (bodyDepth < 0 && parser.depth == 2 && name == "body" && parser.namespace == XHTML) {
                            if (bodySeen) throw IOException("Duplicate EPUB XHTML body: $path")
                            bodySeen = true
                            bodyDepth = parser.depth
                            ids.forEach { anchorOffsets[it] = blocks.size to text.length }
                        } else if (bodyDepth >= 0 && skippedDepth < 0) {
                            if (parser.namespace == XHTML && name !in omitted) {
                                if (name in boundaries || richContent && name == "img") flush()
                                ids.forEach { anchorOffsets[it] = blocks.size to text.length }
                            }
                            if (parser.namespace != XHTML || name in omitted) {
                                diagnostic("ContentOmitted:${parser.namespace}:$name", "Element $name is omitted from text extraction")
                                skippedDepth = parser.depth
                            } else if (name == "img") {
                                val alt = parser.getAttributeValue(null, "alt") ?: ""
                                if (richContent) {
                                    val resource = try {
                                        val reference = parser.getAttributeValue(null, "src") ?: throw IOException("Missing image source")
                                        EpubArchive.resolve(path, reference).path
                                    } catch (_: IOException) { null }
                                    if (resource != null) {
                                        if (!archive.contains(resource)) diagnostic("MissingImageResource", "An image resource is missing; the renderer will use a placeholder")
                                        blocks.add(ImageBlock("block-${blocks.size}", resource, alt, linkTarget))
                                    }
                                    else {
                                        diagnostic("UnsupportedImage", "An image without a supported local resource uses alternative text")
                                        append(alt)
                                    }
                                } else {
                                    diagnostic("ImagesAsAlternativeText", "Images are not rendered; available alternative text is retained")
                                    append(alt)
                                }
                                skippedDepth = parser.depth
                            } else if (richContent && name == "a" && linkDepth < 0) {
                                if (pendingSpace && text.isNotEmpty() && text.last() != '\n') text.append(' ')
                                pendingSpace = false
                                ids.forEach { anchorOffsets[it] = blocks.size to text.length }
                                linkStart = text.length
                                linkDepth = parser.depth
                                linkTarget = parser.getAttributeValue(null, "href")?.let(::target)
                            } else if (name == "br") {
                                if (modes.lastOrNull()?.preformatted != true) while (text.isNotEmpty() && text.last() == ' ') text.setLength(text.length - 1)
                                text.append('\n')
                                pendingSpace = false
                            } else if (name in boundaries) {
                                val previous = modes.lastOrNull()
                                val heading = if (name.length == 2 && name[0] == 'h' && name[1] in '1'..'6') name[1].digitToInt() else previous?.heading ?: 0
                                modes.add(Mode(parser.depth, heading, name == "pre" || previous?.preformatted == true))
                                if (name == "table") diagnostic("TableStructureFlattened", "Table cells are extracted as separate text blocks")
                                if (name == "pre") diagnostic("PreformattedTextOnly", "Preformatted whitespace is retained; the Widget applies its regular text style")
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.depth == skippedDepth) skippedDepth = -1
                        else if (skippedDepth < 0 && bodyDepth >= 0) {
                            if (parser.depth == linkDepth) {
                                finishLink()
                                linkTarget = null
                                linkDepth = -1
                            }
                            if (modes.lastOrNull()?.depth == parser.depth) {
                                flush()
                                modes.removeAt(modes.lastIndex)
                            }
                            if (parser.depth == bodyDepth) {
                                flush()
                                bodyDepth = -1
                            }
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF, XmlPullParser.IGNORABLE_WHITESPACE ->
                        if (bodyDepth >= 0 && skippedDepth < 0) append(parser.text ?: "")
                }
            }
            if (!bodySeen) throw IOException("Missing EPUB XHTML body: $path")
            val anchors = LinkedHashMap<String, ContentAnchor>()
            anchorOffsets.forEach { (id, point) ->
                if (blocks.isNotEmpty()) {
                    val block = blocks[point.first.coerceAtMost(blocks.lastIndex)]
                    val length = (block as? TextContentBlock)?.text?.length ?: 0
                    // Empty elements point to the following emitted block; trailing empty elements to the last end.
                    val offset = if (point.first > blocks.lastIndex) length else point.second.coerceAtMost(length)
                    anchors[id] = ContentAnchor(block.blockId, offset)
                }
            }
            if (richContent) diagnostic("FlowPresentationOnly", "CSS, inline image flow and fixed layout are not represented; images use standalone blocks")
            else diagnostic("TextPresentationOnly", "CSS, inline styling and link behavior are not represented by this text model")
            EpubTextDocument(blocks, anchors, diagnostics.values.toList())
        }
    }
}
