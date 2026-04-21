package dev.mikepenz.composebuddy.renderer.hierarchy

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode

/**
 * Merges a slot-tree-derived hierarchy with a semantics-derived hierarchy.
 *
 * The **semantics hierarchy** is the structural skeleton — it corresponds cleanly to
 * LayoutNodes a user reasons about. The **slot tree** supplies typography / color / shape
 * parameters that don't live in the semantics system. We flatten the slot tree into a flat
 * list of (bounds, props) and graft each entry onto the semantics node it belongs to:
 *
 *  1. Exact-bounds match (a slot Text node whose bounds equal the semantics Text bounds).
 *  2. Text-match fallback: when the semantics tree has collapsed a label into its parent
 *     (e.g. Button merging its child Text), match the slot entry's `text` against the
 *     deepest enclosing semantics node with the same `text`.
 *  3. Containment fallback: otherwise, attach to the smallest semantics node whose bounds
 *     fully contain the slot entry's bounds.
 */
object HierarchyMerger {

    fun merge(slotNode: HierarchyNode, semanticsNode: HierarchyNode): HierarchyNode {
        // Flatten slot tree to a list of props entries.
        data class Entry(val bounds: Bounds, val props: Map<String, String>)
        val entries = mutableListOf<Entry>()
        fun collectSlot(n: HierarchyNode) {
            val s = n.semantics
            if (!s.isNullOrEmpty()) entries += Entry(n.bounds, s)
            n.children.forEach { collectSlot(it) }
        }
        collectSlot(slotNode)

        // Pre-index exact matches for O(1) lookup.
        val exact = mutableMapOf<String, MutableMap<String, String>>()
        for (e in entries) {
            val k = boundsKey(e.bounds)
            val acc = exact.getOrPut(k) { mutableMapOf() }
            // Deeper/later entries override broader ancestors written earlier.
            acc += e.props
        }

        // Slot entries carrying typography — candidates for containment fallback.
        val typographyEntries = entries.filter { e -> e.props.keys.any { it in typographyKeys } }

        fun enrich(n: HierarchyNode): HierarchyNode {
            val base = (n.semantics ?: emptyMap()).toMutableMap()
            exact[boundsKey(n.bounds)]?.let { base += it }

            // Containment fallback: if this semantics node has text but no typography yet,
            // find the tightest slot typography entry whose bounds fit inside this node.
            val needsTypography = base["text"] != null && typographyKeys.none { it in base }
            if (needsTypography) {
                val candidates = typographyEntries
                    .filter { contains(n.bounds, it.bounds) }
                    .sortedBy { area(it.bounds) } // tightest fit first
                // Merge entries with matching (smallest) bounds so typography + color combine.
                val tightest = candidates.firstOrNull()?.bounds
                if (tightest != null) {
                    candidates.filter { it.bounds == tightest }.forEach { base += it.props }
                }
            }

            return n.copy(
                semantics = base.ifEmpty { null },
                children = n.children.map { enrich(it) },
            )
        }
        return enrich(semanticsNode)
    }

    private val typographyKeys = setOf(
        "fontSize", "fontWeight", "fontFamily", "lineHeight", "letterSpacing", "color", "textAlign",
    )

    private fun boundsKey(b: Bounds) = "${b.left},${b.top},${b.right},${b.bottom}"

    private fun contains(outer: Bounds, inner: Bounds): Boolean =
        inner.left >= outer.left - 0.5 && inner.top >= outer.top - 0.5 &&
            inner.right <= outer.right + 0.5 && inner.bottom <= outer.bottom + 0.5

    private fun area(b: Bounds): Double = (b.right - b.left) * (b.bottom - b.top)
}
