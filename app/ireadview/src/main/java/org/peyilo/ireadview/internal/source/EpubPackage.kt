package org.peyilo.ireadview.internal.source

import java.io.IOException
import java.net.URI
import java.util.Collections
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser

internal data class EpubPackageDiagnostic(val code: String, val path: String, val message: String)
internal data class EpubManifestItem(val id: String, val path: String?, val mediaType: String, val fallbackId: String?, val properties: Set<String>)
internal data class EpubReadingItem(val id: String, val path: String, val linear: Boolean)

internal class EpubPackage(
    val path: String, val title: String?, val version: String,
    manifest: Map<String, EpubManifestItem>, readingOrder: List<EpubReadingItem>,
    val navigationPath: String?, val ncxPath: String?, diagnostics: List<EpubPackageDiagnostic>
) {
    val manifest: Map<String, EpubManifestItem> = Collections.unmodifiableMap(LinkedHashMap(manifest))
    val readingOrder: List<EpubReadingItem> = Collections.unmodifiableList(ArrayList(readingOrder))
    val diagnostics: List<EpubPackageDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))
}

/** Metadata only: no chapter text is loaded while determining spine order. */
internal object EpubPackageParser {
    private const val CONTAINER = "urn:oasis:names:tc:opendocument:xmlns:container"
    private const val OPF = "http://www.idpf.org/2007/opf"
    private const val DC = "http://purl.org/dc/elements/1.1/"
    private data class Spine(val id: String, val linear: Boolean)

    suspend fun load(archive: EpubArchive, limits: EpubXmlLimits = EpubXmlLimits()): EpubPackage {
        val coroutine = currentCoroutineContext()
        val diagnostics = ArrayList<EpubPackageDiagnostic>()
        val roots = archive.read("META-INF/container.xml") { input ->
            val paths = ArrayList<String>()
            val parents = ArrayList<Pair<String, String>>()
            EpubXml.parse(input, limits, { coroutine.ensureActive() }) { parser, event ->
                if (event == XmlPullParser.START_TAG) {
                    if (parser.depth == 1 && (parser.name != "container" || parser.namespace != CONTAINER)) throw IOException("Invalid EPUB container root")
                    if (parser.depth == 3 && parents.lastOrNull() == ("rootfiles" to CONTAINER) && parser.name == "rootfile" && parser.namespace == CONTAINER &&
                        parser.getAttributeValue(null, "media-type") == "application/oebps-package+xml") {
                        val resource = EpubArchive.resolve("container-root", EpubXml.required(parser, "full-path"))
                        if (resource.fragment != null) throw IOException("EPUB package path cannot have a fragment")
                        if (!archive.contains(resource.path)) throw IOException("Missing EPUB package: ${resource.path}")
                        if (resource.path in paths) throw IOException("Duplicate EPUB package path")
                        paths.add(resource.path)
                    }
                    parents.add(parser.name to (parser.namespace ?: ""))
                } else if (event == XmlPullParser.END_TAG) parents.removeAt(parents.lastIndex)
            }
            paths
        }
        val path = roots.firstOrNull() ?: throw IOException("No EPUB package document")
        if (roots.size > 1) diagnostics.add(EpubPackageDiagnostic("AdditionalRenditionsIgnored", path, "Only the first package rendition is used"))
        return archive.read(path) { input ->
            val manifest = LinkedHashMap<String, EpubManifestItem>()
            val spine = ArrayList<Spine>()
            val parents = ArrayList<Pair<String, String>>()
            val spineIds = HashSet<String>()
            var version = ""
            var title: String? = null
            var titleDepth = -1
            val titleText = StringBuilder()
            var metaDepth = -1
            val metaText = StringBuilder()
            var ncxId: String? = null
            var manifestSeen = false
            var spineSeen = false
            EpubXml.parse(input, limits, { coroutine.ensureActive() }) { parser, event ->
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val parent = parents.lastOrNull()
                        if (parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "base") != null)
                            throw IOException("EPUB package xml:base is unsupported")
                        if (parser.depth == 1) {
                            if (parser.name != "package" || parser.namespace != OPF) throw IOException("Invalid EPUB package root")
                            version = EpubXml.required(parser, "version")
                            if (version != "2.0" && version != "3.0") throw IOException("Unsupported EPUB package version: $version")
                        }
                        if (parser.depth == 2 && parser.namespace == OPF && parser.name == "manifest") {
                            if (manifestSeen) throw IOException("Duplicate EPUB manifest")
                            manifestSeen = true
                        }
                        if (parser.depth == 2 && parser.namespace == OPF && parser.name == "spine") {
                            if (spineSeen) throw IOException("Duplicate EPUB spine")
                            spineSeen = true
                            ncxId = parser.getAttributeValue(null, "toc")?.takeIf { it.isNotBlank() }
                        }
                        if (parser.depth == 3 && parent == ("metadata" to OPF) && parser.namespace == DC && parser.name == "title" && title == null) {
                            titleDepth = parser.depth; titleText.setLength(0)
                        }
                        if (parser.depth == 3 && parent == ("metadata" to OPF) && parser.namespace == OPF && parser.name == "meta" &&
                            parser.getAttributeValue(null, "property") == "rendition:layout") {
                            metaDepth = parser.depth; metaText.setLength(0)
                        }
                        if (parser.depth == 3 && parent == ("manifest" to OPF) && parser.namespace == OPF && parser.name == "item") {
                            val id = EpubXml.required(parser, "id")
                            if (id in manifest) throw IOException("Duplicate EPUB manifest ID: $id")
                            val href = EpubXml.required(parser, "href")
                            val uri = try { URI(href) } catch (error: Exception) { throw IOException("Invalid EPUB manifest href", error) }
                            val resource = if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null) {
                                diagnostics.add(EpubPackageDiagnostic("ExternalResourceUnavailable", path, "Resource $id will not be fetched")); null
                            } else EpubArchive.resolve(path, href).also { if (it.fragment != null) throw IOException("EPUB manifest href cannot have a fragment") }
                            val properties = Collections.unmodifiableSet(LinkedHashSet(parser.getAttributeValue(null, "properties")
                                ?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: emptyList()))
                            manifest[id] = EpubManifestItem(id, resource?.path, EpubXml.required(parser, "media-type"),
                                parser.getAttributeValue(null, "fallback")?.takeIf { it.isNotBlank() }, properties)
                        }
                        if (parser.depth == 3 && parent == ("spine" to OPF) && parser.namespace == OPF && parser.name == "itemref") {
                            val id = EpubXml.required(parser, "idref")
                            if (!spineIds.add(id)) throw IOException("Duplicate EPUB spine ID: $id")
                            val linear = parser.getAttributeValue(null, "linear") ?: "yes"
                            if (linear != "yes" && linear != "no") throw IOException("Invalid EPUB linear attribute")
                            spine.add(Spine(id, linear == "yes"))
                        }
                        parents.add(parser.name to (parser.namespace ?: ""))
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF, XmlPullParser.IGNORABLE_WHITESPACE -> {
                        if (titleDepth >= 0) titleText.append(parser.text ?: "")
                        if (metaDepth >= 0) metaText.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.depth == titleDepth) { title = titleText.toString().trim().takeIf { it.isNotEmpty() }; titleDepth = -1 }
                        if (parser.depth == metaDepth) {
                            if (metaText.toString().trim() == "pre-paginated") diagnostics.add(EpubPackageDiagnostic("FixedLayoutTextOnly", path, "Fixed layout is reduced to reading-order text"))
                            metaDepth = -1
                        }
                        parents.removeAt(parents.lastIndex)
                    }
                }
            }
            if (!manifestSeen || !spineSeen || spine.isEmpty()) throw IOException("EPUB manifest/spine is missing or empty")
            if (spine.none { it.linear }) throw IOException("EPUB spine has no linear reading item")
            if (spine.any { !it.linear }) diagnostics.add(EpubPackageDiagnostic("NonLinearItemsIncluded", path, "Non-linear items remain in their declared spine order"))
            val order = spine.map { reference ->
                val seen = HashSet<String>()
                var current = reference.id
                var item: EpubManifestItem
                while (true) {
                    coroutine.ensureActive()
                    if (seen.size >= limits.maxFallbackDepth) throw IOException("EPUB fallback chain exceeds depth budget")
                    if (!seen.add(current)) throw IOException("Cyclic EPUB manifest fallback")
                    item = manifest[current] ?: throw IOException("Unknown EPUB manifest ID: $current")
                    if (item.mediaType == "application/xhtml+xml" && item.path != null && archive.contains(item.path)) break
                    current = item.fallbackId ?: throw IOException("Unsupported or missing EPUB spine resource: ${reference.id}")
                }
                if (item.id != reference.id) diagnostics.add(EpubPackageDiagnostic("SpineFallbackUsed", item.path!!, "Fallback for ${reference.id} is used"))
                EpubReadingItem(reference.id, item.path!!, reference.linear)
            }
            val navigation = manifest.values.filter { "nav" in it.properties }
            if (navigation.size > 1) throw IOException("Multiple EPUB navigation documents")
            val nav = navigation.firstOrNull()?.let {
                if (it.mediaType != "application/xhtml+xml" || it.path == null || !archive.contains(it.path)) throw IOException("Invalid EPUB navigation resource")
                it.path
            }
            val ncx = ncxId?.let { id ->
                val item = manifest[id] ?: throw IOException("Unknown EPUB NCX ID")
                if (item.path == null || !archive.contains(item.path)) throw IOException("Missing EPUB NCX resource")
                item.path
            }
            EpubPackage(path, title, version, manifest, order, nav, ncx, diagnostics)
        }
    }
}
