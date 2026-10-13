package org.peyilo.ireadview.spi.content

import android.graphics.Canvas
import android.graphics.Path
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.api.model.ContentBlock
import org.peyilo.ireadview.api.style.ReaderStyle
import org.peyilo.ireadview.spi.presentation.ReadViewport

/** Optional half-open source offsets; TextContentBlock offsets must stay within its text. */
data class BlockLayoutUnit(val heightPx: Float, val startOffset: Int?, val endOffset: Int?) {
    init { require(heightPx.isFinite() && heightPx > 0) }
}

interface BlockLayoutFragment {
    val blockId: String
    /** Optional half-open source endpoints; for TextContentBlock these index its unchanged text. */
    val startOffset: Int?
    val endOffset: Int?
    val heightPx: Float
    val leftPx: Float get() = 0f
    val topPx: Float get() = 0f
    /** Read-only text for this rendered fragment; null opts out of text accessibility. */
    val accessibilityText: CharSequence? get() = null
    /** Opt in only when every accessible vertical slice describes the entire logical block.
     * Finishing that description permits traversal to skip all remaining fragments of this block.
     * This does not supply source text offsets or enable text selection.
     */
    val accessibilityDescribesWholeBlock: Boolean get() = false
    val accessibilityHeading: Boolean get() = false
    /** Visible vertical slice in fragment-local px; default describes a custom fragment as a whole. */
    fun accessibilityTextForVerticalRange(topPx: Float, bottomPx: Float): CharSequence? = accessibilityText
    /** Exact source range of the accessibility text slice in TextContentBlock.text.
     * The slice must equal that substring and its endpoints must be character boundaries.
     * null or a mismatched slice opts out of source selection and traversal.
     */
    fun accessibilityRangeForVerticalRange(topPx: Float, bottomPx: Float): BlockTextRange? = null
    fun draw(canvas: Canvas)
    /** Half-open source offsets, returning geometry in fragment-local pixels; null if unsupported. */
    fun selectionPath(startOffset: Int, endExclusive: Int): Path? = null
    fun anchorAt(xPx: Float, yPx: Float): ContentAnchor? = null
    /** Drag-only clamped hit testing; ordinary long-press hit testing remains strict. */
    fun nearestAnchorAt(xPx: Float, yPx: Float): ContentAnchor? = anchorAt(xPx, yPx)
    fun caretForOffset(offset: Int, endExclusive: Boolean): TextCaret? = null
    /** Source start of the first visible text line, independent of horizontal text direction. */
    fun anchorForVerticalOffset(yPx: Float): ContentAnchor? =
        startOffset?.let { ContentAnchor(blockId, it) }
}

data class TextCaret(val xPx: Float, val topPx: Float, val bottomPx: Float) {
    init { require(xPx.isFinite() && topPx.isFinite() && bottomPx.isFinite() && bottomPx > topPx) }
}

data class BlockTextRange(val blockId: String, val startOffset: Int, val endExclusive: Int) {
    init { require(blockId.isNotBlank() && startOffset >= 0 && endExclusive >= startOffset) }
}

/** Identity, units and geometry remain immutable after measure returns.
 * Units and fragments have nonnegative ordered optional source offsets, bounded by text.length
 * for TextContentBlock. Every fragment retains this blockId and finite origins.
 * Provider callbacks must cooperate with checkCancellation.
 */
interface BlockLayout {
    /** Estimated bytes retained by this measured block, including units and rendering resources.
     * null opts out of layout caching; exclude the separately accounted ChapterContent model.
     * Implementations must return a stable nonnegative estimate for this immutable layout.
     */
    val estimatedRetainedBytes: Long? get() = null
    val blockId: String
    val units: List<BlockLayoutUnit>
    val spacingAfterPx: Float
    fun fragment(firstUnitIndex: Int, endUnitExclusive: Int): BlockLayoutFragment
}

data class BlockLayoutContext(
    val viewport: ReadViewport,
    val style: ReaderStyle,
    val bodyTextSizePx: Float = style.body.textSizeSp * viewport.density * viewport.fontScale,
    val titleTextSizePx: Float = style.title.textSizeSp * viewport.density * viewport.fontScale,
    val checkCancellation: () -> Unit
)

interface BlockLayoutProvider {
    /** Stable registration identity. Mutating layout behaviour requires a new version and
     * re-registering through setBlockLayoutProviders; do not mutate a provider during measure.
     */
    val id: String
    val version: String
    fun supports(block: ContentBlock): Boolean
    /** Worker-thread operation: never create or manipulate Views here.
     * viewport.widthPx already excludes the engine-owned block/title padding.
     * Return content geometry only; the engine applies those insets around the result.
     */
    fun measure(block: ContentBlock, context: BlockLayoutContext): BlockLayout
}

internal val BlockLayoutFragment.anchor: ContentAnchor get() = ContentAnchor(blockId, startOffset ?: 0)
