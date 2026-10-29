package org.peyilo.ireadview.internal.render

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import org.peyilo.ireadview.spi.content.BlockLayoutFragment
import org.peyilo.ireadview.spi.content.BlockTextRange
import org.peyilo.ireadview.api.model.ContentAnchor
import org.peyilo.ireadview.R
import android.view.View
import android.view.accessibility.AccessibilityEvent
import kotlin.math.ceil
import kotlin.math.floor

/** Virtual text nodes reuse fragment geometry; no second pagination or source lookup. */
internal class BodyAccessibilityHelper(private val body: TextReaderBodyView) :
    ExploreByTouchHelper(body) {
    private val nodes = LinkedHashMap<Int, BlockLayoutFragment>()
    private val failed =
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap<BlockLayoutFragment, Boolean>())
    private var nextId = 1

    private data class Slice(val text: String, val start: Int?, val end: Int?)
    private data class Cursor(
        val slice: Slice,
        var offset: Int?,
        var anchor: Int?,
        var selectionVersion: Long
    )

    private data class TraversalEvent(
        val start: Int,
        val end: Int,
        val action: Int,
        val granularity: Int,
        val length: Int
    )

    private data class TraversalTarget(
        val id: Int, val fragment: BlockLayoutFragment, val value: String,
        val range: BlockTextRange?, val cursor: Cursor, val step: AccessibilityTextTraversal.Step
    )

    private val cursors = HashMap<Int, Cursor>()
    private var traversalEvent: Pair<Int, TraversalEvent>? = null

    private data class Extension(
        val fixed: AccessibilityTraversalAnchor,
        val caretId: Int,
        val version: Long
    )

    private var extension: Extension? = null

    fun replace(fragments: List<BlockLayoutFragment>) {
        clearFocus()
        nodes.clear()
        cursors.clear()
        failed.clear()
        fragments.forEach { fragment ->
            if (nextId == Int.MAX_VALUE) nextId = 1
            nodes[nextId++] = fragment
        }
        if (body.isShown) invalidateRoot()
    }

    fun clearFocus() {
        extension = null
        cursors.clear()
        traversalEvent = null
        val accessibilityId = accessibilityFocusedVirtualViewId
        try {
            if (accessibilityId != INVALID_ID) getAccessibilityNodeProvider(body)?.performAction(
                accessibilityId, AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null
            )
        } catch (error: Exception) {
            Log.w("iReadView", "Clearing body accessibility focus failed", error)
        }
        val keyboardId = keyboardFocusedVirtualViewId
        try {
            if (keyboardId != INVALID_ID) clearKeyboardFocusForVirtualView(keyboardId)
        } catch (error: Exception) {
            Log.w("iReadView", "Clearing body keyboard focus failed", error)
        }
    }

    private fun bounds(fragment: BlockLayoutFragment): Rect = Rect(
        floor(body.paddingLeft + fragment.leftPx).toInt(),
        floor(body.paddingTop + fragment.topPx).toInt(),
        body.width - body.paddingRight,
        ceil(body.paddingTop + fragment.topPx + fragment.heightPx).toInt()
    )

    private fun visibleBounds(fragment: BlockLayoutFragment): Rect? {
        if (!body.isShown) return null
        val visible = Rect()
        if (!body.getLocalVisibleRect(visible)) return null
        if (!visible.intersect(
                body.paddingLeft,
                body.paddingTop,
                body.width - body.paddingRight,
                body.height - body.paddingBottom
            )
        ) return null
        val result = bounds(fragment)
        return result.takeIf { it.intersect(visible) && !it.isEmpty }
    }

    private fun text(fragment: BlockLayoutFragment, rect: Rect): CharSequence? {
        if (fragment in failed) return null
        return try {
            fragment.accessibilityTextForVerticalRange(
                rect.top - body.paddingTop - fragment.topPx,
                rect.bottom - body.paddingTop - fragment.topPx
            )?.toString()?.takeIf { it.isNotBlank() }
        } catch (error: Exception) {
            failed.add(fragment)
            Log.w("iReadView", "Fragment accessibility text failed: ${fragment.blockId}", error)
            null
        }
    }

    private fun hierarchyEnabled(): Boolean {
        var ancestor: View? = body
        while (ancestor != null) {
            if (!ancestor.isEnabled) return false; ancestor = ancestor.parent as? View
        }
        return true
    }

    private fun selectionRange(
        fragment: BlockLayoutFragment,
        rect: Rect,
        visibleText: CharSequence
    ): BlockTextRange? {
        if (!hierarchyEnabled()) return null
        if (body.accessibilitySelectText == null) return null
        return try {
            fragment.accessibilityRangeForVerticalRange(
                rect.top - body.paddingTop - fragment.topPx,
                rect.bottom - body.paddingTop - fragment.topPx
            )?.takeIf {
                it.blockId == fragment.blockId && it.endExclusive - it.startOffset == visibleText.length &&
                        visibleText.isNotBlank() && body.accessibilityMatchesText?.invoke(
                    it,
                    visibleText.toString()
                ) == true
            }
        } catch (error: Exception) {
            Log.w(
                "iReadView",
                "Fragment accessibility range failed: ${fragment.blockId}",
                error
            ); null
        }
    }

    private fun cursor(id: Int, value: CharSequence, range: BlockTextRange?): Cursor {
        val slice = Slice(value.toString(), range?.startOffset, range?.endExclusive)
        val existing = cursors[id]
        if (existing != null && existing.slice == slice && existing.selectionVersion == body.accessibilitySelectionVersion) return existing
        val selected = range?.let(body::accessibilitySelection)
        return Cursor(
            slice,
            selected?.second,
            selected?.first,
            body.accessibilitySelectionVersion
        ).also { cursors[id] = it }
    }

    private fun stillSameSlice(
        id: Int,
        fragment: BlockLayoutFragment,
        value: CharSequence,
        range: BlockTextRange?
    ): Boolean {
        if (nodes[id] !== fragment) return false
        val rect = visibleBounds(fragment) ?: return false
        val current = text(fragment, rect) ?: return false
        return current.toString() == value.toString() && selectionRange(
            fragment,
            rect,
            current
        ) == range
    }

    fun refresh() {
        fun readable(id: Int): Boolean {
            val fragment = nodes[id] ?: return false
            val rect = visibleBounds(fragment) ?: return false
            return text(fragment, rect) != null
        }

        val accessibilityId = accessibilityFocusedVirtualViewId
        val keyboardId = keyboardFocusedVirtualViewId
        if (accessibilityId != INVALID_ID && !readable(accessibilityId) ||
            keyboardId != INVALID_ID && !readable(keyboardId)
        ) clearFocus()
        if (body.isShown) invalidateRoot()
    }

    override fun getVirtualViewAt(x: Float, y: Float): Int = nodes.entries.firstOrNull {
        visibleBounds(it.value)?.let { rect ->
            rect.contains(
                floor(x).toInt(),
                floor(y).toInt()
            ) && text(it.value, rect) != null
        } == true
    }?.key ?: INVALID_ID

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        nodes.forEach { (id, fragment) ->
            visibleBounds(fragment)?.let {
                if (text(
                        fragment,
                        it
                    ) != null
                ) virtualViewIds.add(id)
            }
        }
    }

    @Suppress("DEPRECATION") // Parent bounds remain necessary for helper keyboard focus navigation.
    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat
    ) {
        val item = nodes[virtualViewId]
        val rect = item?.let { visibleBounds(it) }
        val visibleText = if (item != null && rect != null) text(item, rect) else null
        node.className = "android.widget.TextView"
        node.isFocusable = true
        if (item == null || rect == null || visibleText == null) {
            // A service may query an ID from the row's previous binding.
            node.text = " "
            node.isEnabled = false
            node.isFocusable = false
            node.setBoundsInParent(Rect(-2, -2, -1, -1))
            setBoundsInScreenFromBoundsInParent(node, Rect(-2, -2, -1, -1))
            node.isVisibleToUser = false
            return
        }
        node.text = visibleText
        node.isEnabled = hierarchyEnabled()
        if (node.isEnabled) {
            node.movementGranularities = AccessibilityTextTraversal.GRANULARITIES
            node.addAction(AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
        }
        val sourceRange = selectionRange(item, rect, visibleText)
        val caret = cursor(virtualViewId, visibleText, sourceRange).offset
        if (caret != null) node.setTextSelection(caret, caret)
        if (sourceRange != null) {
            body.accessibilitySelection(sourceRange)
                ?.let { node.setTextSelection(it.first, it.second) }
            node.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                    body.context.getString(R.string.iread_accessibility_select_visible_text)
                )
            )
            node.addAction(AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)
        }
        node.isHeading = try {
            item.accessibilityHeading
        } catch (error: Exception) {
            Log.w(
                "iReadView",
                "Fragment accessibility heading failed: ${item.blockId}",
                error
            ); false
        }
        node.setBoundsInParent(rect)
        setBoundsInScreenFromBoundsInParent(node, rect)
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {
        if (action == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY ||
            action == AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
        ) return traverse(virtualViewId, action, arguments)
        if (action != AccessibilityNodeInfoCompat.ACTION_LONG_CLICK && action != AccessibilityNodeInfoCompat.ACTION_SET_SELECTION) return false
        val fragment = nodes[virtualViewId] ?: return false
        val rect = visibleBounds(fragment) ?: return false
        val value = text(fragment, rect) ?: return false
        val range = selectionRange(fragment, rect, value) ?: return false
        val selected = if (action == AccessibilityNodeInfoCompat.ACTION_SET_SELECTION) {
            val startKey = AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT
            val endKey = AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT
            if (arguments == null || !arguments.containsKey(startKey) || !arguments.containsKey(
                    endKey
                )
            ) return false
            val start = arguments.getInt(startKey, -1)
            val end = arguments.getInt(endKey, -1)
            if (start < 0 || end < start || end > value.length) return false
            if (!AccessibilityTextTraversal.isCharacterBoundary(value.toString(), start) ||
                !AccessibilityTextTraversal.isCharacterBoundary(value.toString(), end)
            ) return false
            range.copy(
                startOffset = range.startOffset + start,
                endExclusive = range.startOffset + end
            )
        } else range
        val accepted = try {
            if (selected.startOffset == selected.endExclusive) body.accessibilityClearSelection?.invoke() == true
            else body.accessibilitySelectText?.invoke(selected) == true
        } catch (error: Exception) {
            Log.w("iReadView", "Accessibility selection failed", error); false
        }
        if (!accepted || !stillSameSlice(virtualViewId, fragment, value, range)) return false
        extension = null
        if (selected.startOffset != selected.endExclusive && body.accessibilitySelection(range) !=
            ((selected.startOffset - range.startOffset) to (selected.endExclusive - range.startOffset))
        ) return false
        cursor(virtualViewId, value, range).apply {
            offset = selected.endExclusive - range.startOffset
            anchor = selected.startOffset - range.startOffset
            selectionVersion = body.accessibilitySelectionVersion
        }
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)
        return true
    }

    private fun describesWholeBlock(fragment: BlockLayoutFragment): Boolean = try {
        fragment.accessibilityDescribesWholeBlock
    } catch (error: Exception) {
        Log.w("iReadView", "Fragment accessibility block description failed", error); false
    }

    private fun adjacentTarget(
        id: Int,
        granularity: Int,
        forward: Boolean,
        completedBlockId: String?
    ): TraversalTarget? {
        val ordered = nodes.keys.toList()
        val origin = ordered.indexOf(id)
        if (origin < 0) return null
        val indices = if (forward) (origin + 1 until ordered.size) else (origin - 1 downTo 0)
        for (index in indices) {
            val targetId = ordered[index]
            val fragment = nodes[targetId] ?: continue
            if (fragment.blockId == completedBlockId) continue
            val rect = visibleBounds(fragment) ?: continue
            val value = text(fragment, rect)?.toString() ?: continue
            val step =
                AccessibilityTextTraversal.step(value, null, granularity, forward) ?: continue
            val range = selectionRange(fragment, rect, value)
            // Enter a neighbouring slice at its boundary, independent of its old caret.
            val state = Cursor(
                Slice(value.toString(), range?.startOffset, range?.endExclusive),
                null, null, body.accessibilitySelectionVersion
            )
            return TraversalTarget(targetId, fragment, value, range, state, step)
        }
        return null
    }

    fun traverseFromBoundary(
        action: Int,
        arguments: Bundle,
        fixed: AccessibilityTraversalAnchor?
    ): Boolean {
        val extend = arguments.getBoolean(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
            false
        )
        if (!hierarchyEnabled() || extend && (fixed == null || !fixed.isValid())) return false
        val forward = when (action) {
            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> true
            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> false
            else -> return false
        }
        val granularity = arguments.getInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            0
        )
        val ordered = nodes.keys.toList().let { if (forward) it else it.asReversed() }
        for (id in ordered) {
            val fragment = nodes[id] ?: continue
            val rect = visibleBounds(fragment) ?: continue
            val value = text(fragment, rect)?.toString() ?: continue
            if (AccessibilityTextTraversal.step(value, null, granularity, forward) == null) continue
            val range = selectionRange(fragment, rect, value)
            if (extend && range == null) continue
            cursors[id] = Cursor(
                Slice(value.toString(), range?.startOffset, range?.endExclusive),
                null, null, body.accessibilitySelectionVersion
            )
            return traverse(id, action, arguments, entering = true, incoming = fixed)
        }
        return false
    }

    fun traverseAtBlock(action: Int, arguments: Bundle, blockId: String): Boolean {
        if (!hierarchyEnabled() || arguments.getBoolean(
                AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                false
            )
        ) return false
        val forward = action == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
        if (!forward && action != AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) return false
        val granularity = arguments.getInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            0
        )
        val ordered = nodes.keys.toList().let { if (forward) it else it.asReversed() }
        for (id in ordered) {
            val fragment = nodes[id]?.takeIf { it.blockId == blockId } ?: continue
            val rect = visibleBounds(fragment) ?: continue
            val value = text(fragment, rect)?.toString() ?: continue
            if (AccessibilityTextTraversal.step(value, null, granularity, forward) == null) continue
            val range = selectionRange(fragment, rect, value)
            cursors[id] = Cursor(
                Slice(value.toString(), range?.startOffset, range?.endExclusive),
                null, null, body.accessibilitySelectionVersion
            )
            return traverse(id, action, arguments, entering = true)
        }
        return false
    }

    fun traverseAtAnchor(
        action: Int,
        arguments: Bundle,
        anchor: ContentAnchor,
        fixed: AccessibilityTraversalAnchor?
    ): Boolean {
        val extend = arguments.getBoolean(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
            false
        )
        if (!hierarchyEnabled() || extend && (fixed == null || !fixed.isValid())) return false
        val forward = action == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
        if (!forward && action != AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) return false
        val granularity = arguments.getInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            0
        )
        for ((id, fragment) in nodes) {
            val rect = visibleBounds(fragment) ?: continue
            val value = text(fragment, rect)?.toString() ?: continue
            val range = selectionRange(fragment, rect, value) ?: continue
            if (range.blockId != anchor.blockId || anchor.offset !in range.startOffset..range.endExclusive) continue
            val offset = anchor.offset - range.startOffset
            if (AccessibilityTextTraversal.step(
                    value,
                    offset,
                    granularity,
                    forward
                ) == null
            ) continue
            cursors[id] = Cursor(
                Slice(value.toString(), range.startOffset, range.endExclusive),
                offset, null, body.accessibilitySelectionVersion
            )
            return traverse(id, action, arguments, entering = true, incoming = fixed)
        }
        return false
    }

    private fun traverse(
        sourceId: Int, action: Int, arguments: Bundle?, entering: Boolean = false,
        incoming: AccessibilityTraversalAnchor? = null
    ): Boolean {
        if (!hierarchyEnabled()) return false
        var id = sourceId
        var fragment = nodes[id] ?: return false
        val rect = visibleBounds(fragment) ?: return false
        var value = text(fragment, rect)?.toString() ?: return false
        var range = selectionRange(fragment, rect, value)
        var state = cursor(id, value, range)
        val granularity = arguments?.getInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            0
        ) ?: return false
        val forward = action == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
        val extend = arguments.getBoolean(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
            false
        )
        val fixed = if (extend) {
            if (range == null || body.accessibilitySelectAnchors == null) return false
            incoming?.takeIf { it.isValid() } ?: extension?.takeIf {
                it.caretId == sourceId && it.version == body.accessibilitySelectionVersion && it.fixed.isValid()
            }?.fixed ?: run {
                val original = fragment
                val originalText = value
                val originalRange = range
                AccessibilityTraversalAnchor(
                    ContentAnchor(
                        range.blockId, range.startOffset +
                                (state.anchor ?: state.offset ?: if (forward) 0 else value.length)
                    )
                ) {
                    hierarchyEnabled() && stillSameSlice(
                        sourceId,
                        original,
                        originalText,
                        originalRange
                    )
                }
            }
        } else null
        var step = AccessibilityTextTraversal.step(value, state.offset, granularity, forward)
        if (step == null) {
            val completedBlockId =
                if (!extend && range == null && describesWholeBlock(fragment)) fragment.blockId else null
            val target = adjacentTarget(id, granularity, forward, completedBlockId)
            if (target == null) {
                if (entering) return false
                val moved = try {
                    body.accessibilityMoveToAdjacent?.invoke(action, arguments, fixed) == true
                } catch (error: Exception) {
                    Log.w("iReadView", "Accessibility adjacent row movement failed", error); false
                }
                if (moved && nodes[sourceId] === fragment) clearFocus()
                if (moved) return true
                val original = fragment
                val originalText = value
                val originalRange = range
                val originalVersion = body.accessibilitySelectionVersion
                return try {
                    val retainedFixed = if (extend && fixed?.isValid() == true)
                        body.accessibilityPreserveAnchor?.invoke(fixed.anchor) else null
                    if (extend && retainedFixed == null) return false
                    val boundary = range?.let {
                        AccessibilityTraversalBoundary.Source(
                            ContentAnchor(
                                it.blockId,
                                if (forward) it.endExclusive else it.startOffset
                            )
                        )
                    } ?: completedBlockId?.let(AccessibilityTraversalBoundary::CompletedBlock)
                    body.accessibilityMoveOffscreen?.invoke(
                        action,
                        Bundle(arguments),
                        boundary,
                        retainedFixed
                    ) {
                        hierarchyEnabled() && body.accessibilitySelectionVersion == originalVersion &&
                                (completedBlockId == null || describesWholeBlock(original)) &&
                                (!extend || fixed?.isValid() == true && retainedFixed?.isValid() == true) &&
                                stillSameSlice(sourceId, original, originalText, originalRange) &&
                                (accessibilityFocusedVirtualViewId == sourceId || keyboardFocusedVirtualViewId == sourceId)
                    } == true
                } catch (error: Exception) {
                    Log.w("iReadView", "Accessibility offscreen movement failed", error); false
                }
            }
            id = target.id; fragment = target.fragment; value = target.value
            range = target.range; state = target.cursor; step = target.step
        }
        var expected: Pair<Int, Int>? = null
        if (extend) {
            if (range == null) return false
            val moving = ContentAnchor(range.blockId, range.startOffset + step.caret)
            val anchor = fixed!!.anchor
            val localFixed =
                if (anchor.blockId == range.blockId) (anchor.offset - range.startOffset).coerceIn(
                    0,
                    value.length
                )
                else {
                    val order = try {
                        body.accessibilityCompareAnchors?.invoke(anchor, moving)
                    } catch (error: Exception) {
                        Log.w("iReadView", "Accessibility anchor comparison failed", error); null
                    }
                        ?: return false
                    if (order < 0) 0 else value.length
                }
            expected = (minOf(localFixed, step.caret) to maxOf(
                localFixed,
                step.caret
            )).takeIf { it.first != it.second }
            val accepted = try {
                if (anchor == moving) body.accessibilityClearSelection?.invoke() == true
                else body.accessibilitySelectAnchors?.invoke(anchor, moving) == true
            } catch (error: Exception) {
                Log.w("iReadView", "Accessibility selection extension failed", error); false
            }
            if (!accepted) return false
        } else if (body.accessibilityClearSelection != null) {
            val cleared = try {
                body.accessibilityClearSelection?.invoke() == true
            } catch (error: Exception) {
                Log.w("iReadView", "Accessibility selection clearing failed", error); false
            }
            if (!cleared) return false
        }
        if (!stillSameSlice(id, fragment, value, range)) return false
        if (extend && (range == null || body.accessibilitySelection(range) != expected ||
                    !fixed!!.isValid())
        ) return false
        state.offset = step.caret
        state.anchor = if (extend && fixed!!.anchor.blockId == range?.blockId)
            (fixed.anchor.offset - range.startOffset).takeIf { it in 0..value.length } else null
        state.selectionVersion = body.accessibilitySelectionVersion
        extension = fixed?.let { Extension(it, id, body.accessibilitySelectionVersion) }
        cursors[id] = state
        invalidateVirtualView(id)
        if (id != sourceId || entering) {
            // Transfer the service focus only after the text operation was accepted.
            if (accessibilityFocusedVirtualViewId != id &&
                getAccessibilityNodeProvider(body)?.performAction(
                    id,
                    AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null
                ) != true
            ) return false
            if (!hierarchyEnabled() || state.selectionVersion != body.accessibilitySelectionVersion ||
                !stillSameSlice(id, fragment, value, range)
            ) return false
        }
        traversalEvent =
            id to TraversalEvent(step.start, step.end, action, granularity, value.length)
        try {
            sendEventForVirtualView(
                id,
                AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY
            )
        } finally {
            traversalEvent = null
        }
        sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)
        return true
    }

    override fun onPopulateEventForVirtualView(virtualViewId: Int, event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY) {
            traversalEvent?.takeIf { it.first == virtualViewId }?.second?.let {
                event.fromIndex = it.start; event.toIndex = it.end
                event.action = it.action; event.movementGranularity =
                it.granularity; event.itemCount = it.length
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            val fragment = nodes[virtualViewId] ?: return
            val rect = visibleBounds(fragment) ?: return
            val value = text(fragment, rect) ?: return
            val range = selectionRange(fragment, rect, value)
            val selected = range?.let(body::accessibilitySelection)
            val caret = cursor(virtualViewId, value, range).offset ?: -1
            event.fromIndex = selected?.first ?: caret; event.toIndex = selected?.second ?: caret
            event.itemCount = value.length
        }
    }
}
