package org.peyilo.ireadview.internal.source

import java.io.IOException
import java.net.URI
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser

internal class EpubNavigationEntry(val label: String, val resource: EpubArchive.Resource?, children: List<EpubNavigationEntry>) {
    val children: List<EpubNavigationEntry> = Collections.unmodifiableList(ArrayList(children))
}
internal class EpubNavigation(entries: List<EpubNavigationEntry>, diagnostics: List<EpubPackageDiagnostic>) {
    val entries: List<EpubNavigationEntry> = Collections.unmodifiableList(ArrayList(entries))
    val diagnostics: List<EpubPackageDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))

    /** Chapter labels do not create extra chapters for multiple fragment links into the same file. */
    fun chapterLabels(): Map<String, String> {
        val labels = LinkedHashMap<String, String>()
        val wholeDocument = HashSet<String>()
        fun visit(items: List<EpubNavigationEntry>) {
            items.forEach { item ->
                item.resource?.let { resource ->
                    if (item.label.isNotBlank() && (resource.path !in labels || resource.fragment == null && resource.path !in wholeDocument)) {
                        labels[resource.path] = item.label
                        if (resource.fragment == null) wholeDocument.add(resource.path)
                    }
                }
                visit(item.children)
            }
        }
        visit(entries)
        return Collections.unmodifiableMap(labels)
    }
}

internal object EpubNavigationParser {
    private const val XHTML = "http://www.w3.org/1999/xhtml"
    private const val EPUB = "http://www.idpf.org/2007/ops"
    private const val NCX = "http://www.daisy.org/z3986/2005/ncx/"
    private val whitespace = Regex("\\s+")
    private class Frame(val depth: Int) {
        val label = StringBuilder()
        val children = ArrayList<EpubNavigationEntry>()
        var resource: EpubArchive.Resource? = null
        var labelDepth = -1
        var contentSeen = false
        fun finish() = EpubNavigationEntry(label.toString().replace(whitespace, " ").trim(), resource, children)
    }

    suspend fun load(archive: EpubArchive, book: EpubPackage, limits: EpubXmlLimits = EpubXmlLimits()): EpubNavigation {
        val diagnostics = ArrayList<EpubPackageDiagnostic>()
        if (book.navigationPath != null) {
            try { return EpubNavigation(parseNav(archive, book.navigationPath, limits, diagnostics), diagnostics) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (book.ncxPath == null) throw failure
                diagnostics.add(EpubPackageDiagnostic("NavigationFallbackToNcx", book.navigationPath, failure.message ?: "Navigation document failed"))
            }
        }
        if (book.ncxPath != null) return EpubNavigation(parseNcx(archive, book.ncxPath, limits, diagnostics), diagnostics)
        diagnostics.add(EpubPackageDiagnostic("NavigationUnavailable", book.path, "Chapter names will use spine identities"))
        return EpubNavigation(emptyList(), diagnostics)
    }

    private fun target(archive: EpubArchive, path: String, href: String, diagnostics: MutableList<EpubPackageDiagnostic>): EpubArchive.Resource? {
        val uri = try { URI(href) } catch (failure: Exception) { throw IOException("Invalid EPUB navigation target", failure) }
        if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null) {
            diagnostics.add(EpubPackageDiagnostic("NavigationTargetUnavailable", path, "External or queried navigation target is ignored"))
            return null
        }
        val resource = EpubArchive.resolve(path, href)
        if (!archive.contains(resource.path)) diagnostics.add(EpubPackageDiagnostic("NavigationTargetMissing", path, "Target resource is missing: ${resource.path}"))
        return resource
    }

    private suspend fun parseNav(archive: EpubArchive, path: String, limits: EpubXmlLimits, diagnostics: MutableList<EpubPackageDiagnostic>): List<EpubNavigationEntry> {
        val coroutine = currentCoroutineContext()
        return archive.read(path) { input ->
            val roots = ArrayList<EpubNavigationEntry>()
            val frames = ArrayList<Frame>()
            val parents = ArrayList<Pair<String, String>>()
            var tocDepth = -1
            var found = false
            EpubXml.parse(input, limits, { coroutine.ensureActive() }) { parser, event ->
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.depth == 1 && (parser.name != "html" || parser.namespace != XHTML)) throw IOException("Invalid EPUB navigation root")
                        if (parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "base") != null) throw IOException("EPUB navigation xml:base is unsupported")
                        val types = parser.getAttributeValue(EPUB, "type")?.split(whitespace) ?: emptyList()
                        if (parser.namespace == XHTML && parser.name == "nav" && "toc" in types) {
                            if (found) throw IOException("Multiple EPUB toc navigation elements")
                            found = true; tocDepth = parser.depth
                        }
                        if (tocDepth > 0 && parser.namespace == XHTML) {
                            if (parser.name == "li" && parents.lastOrNull() == ("ol" to XHTML)) frames.add(Frame(parser.depth))
                            val frame = frames.lastOrNull()
                            if (frame != null && parser.depth == frame.depth + 1 && (parser.name == "a" || parser.name == "span")) {
                                if (frame.contentSeen) throw IOException("Multiple labels in EPUB navigation item")
                                frame.contentSeen = true; frame.labelDepth = parser.depth
                                if (parser.name == "a") {
                                    val href = parser.getAttributeValue(null, "href") ?: throw IOException("EPUB navigation link has no href")
                                    frame.resource = target(archive, path, href, diagnostics)
                                }
                            }
                        }
                        parents.add(parser.name to (parser.namespace ?: ""))
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF, XmlPullParser.IGNORABLE_WHITESPACE -> {
                        frames.lastOrNull()?.takeIf { it.labelDepth > 0 || parser.depth == it.depth }?.label?.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        val frame = frames.lastOrNull()
                        if (frame != null && parser.depth == frame.depth && parser.name == "li" && parser.namespace == XHTML) {
                            frames.removeAt(frames.lastIndex)
                            val item = frame.finish()
                            if (item.label.isBlank()) diagnostics.add(EpubPackageDiagnostic("NavigationLabelEmpty", path, "Navigation item has an empty label"))
                            (frames.lastOrNull()?.children ?: roots).add(item)
                        } else if (frame != null && parser.depth == frame.labelDepth) frame.labelDepth = -1
                        if (parser.depth == tocDepth && parser.name == "nav" && parser.namespace == XHTML) tocDepth = -1
                        parents.removeAt(parents.lastIndex)
                    }
                }
            }
            if (!found || roots.isEmpty()) throw IOException("EPUB navigation has no toc entries")
            roots
        }
    }

    private suspend fun parseNcx(archive: EpubArchive, path: String, limits: EpubXmlLimits, diagnostics: MutableList<EpubPackageDiagnostic>): List<EpubNavigationEntry> {
        val coroutine = currentCoroutineContext()
        return archive.read(path) { input ->
            val roots = ArrayList<EpubNavigationEntry>()
            val frames = ArrayList<Frame>()
            val ids = HashSet<String>()
            val parents = ArrayList<Pair<String, String>>()
            var mapDepth = -1
            var found = false
            EpubXml.parse(input, limits, { coroutine.ensureActive() }) { parser, event ->
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.depth == 1 && (parser.name != "ncx" || parser.namespace != NCX)) throw IOException("Invalid EPUB NCX root")
                        if (parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "base") != null) throw IOException("EPUB NCX xml:base is unsupported")
                        if (parser.depth == 2 && parser.name == "navMap" && parser.namespace == NCX) {
                            if (found) throw IOException("Multiple EPUB NCX navMap elements")
                            found = true; mapDepth = parser.depth
                        }
                        if (mapDepth > 0 && parser.namespace == NCX) {
                            if (parser.name == "navPoint" && (parents.lastOrNull() == ("navMap" to NCX) || parents.lastOrNull() == ("navPoint" to NCX))) {
                                if (!ids.add(EpubXml.required(parser, "id"))) throw IOException("Duplicate EPUB NCX navigation ID")
                                frames.add(Frame(parser.depth))
                            }
                            val frame = frames.lastOrNull()
                            if (frame != null && parser.depth == frame.depth + 2 && parser.name == "text" && parents.lastOrNull() == ("navLabel" to NCX)) {
                                if (frame.label.isNotBlank()) diagnostics.add(EpubPackageDiagnostic("AdditionalNcxLabel", path, "Additional NCX label is appended"))
                                if (frame.label.isNotEmpty()) frame.label.append(' ')
                                frame.labelDepth = parser.depth
                            }
                            if (frame != null && parser.depth == frame.depth + 1 && parser.name == "content") {
                                if (frame.contentSeen) throw IOException("Multiple EPUB NCX content targets")
                                frame.contentSeen = true
                                frame.resource = target(archive, path, EpubXml.required(parser, "src"), diagnostics)
                            }
                        }
                        parents.add(parser.name to (parser.namespace ?: ""))
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF, XmlPullParser.IGNORABLE_WHITESPACE ->
                        frames.lastOrNull()?.takeIf { it.labelDepth > 0 }?.label?.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> {
                        val frame = frames.lastOrNull()
                        if (frame != null && parser.depth == frame.depth && parser.name == "navPoint" && parser.namespace == NCX) {
                            frames.removeAt(frames.lastIndex)
                            val item = frame.finish()
                            if (item.label.isBlank() || !frame.contentSeen) diagnostics.add(EpubPackageDiagnostic("NcxItemIncomplete", path, "NCX label or target is missing"))
                            (frames.lastOrNull()?.children ?: roots).add(item)
                        } else if (frame != null && parser.depth == frame.labelDepth) frame.labelDepth = -1
                        if (parser.depth == mapDepth && parser.name == "navMap" && parser.namespace == NCX) mapDepth = -1
                        parents.removeAt(parents.lastIndex)
                    }
                }
            }
            if (!found || roots.isEmpty()) throw IOException("EPUB NCX has no navigation entries")
            roots
        }
    }
}
