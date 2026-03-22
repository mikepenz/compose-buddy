package dev.mikepenz.composebuddy.core.hierarchy

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import dev.mikepenz.composebuddy.core.model.boundsOf
import dev.mikepenz.composebuddy.core.model.sizeOf

/**
 * Extracts HierarchyNode trees from layoutlib ViewInfo data.
 * All bounds/sizes are converted to dp using the provided density.
 */
object ViewInfoExtractor {

    data class ViewInfoData(
        val className: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val children: List<ViewInfoData> = emptyList(),
        val semantics: Map<String, String>? = null,
    )

    private class IdCounter { var value = 0; fun next() = value++ }

    fun extract(viewInfos: List<ViewInfoData>, parentBounds: Bounds? = null, densityDpi: Int = 420): List<HierarchyNode> {
        val counter = IdCounter()
        return viewInfos.map { extractNode(it, parentBounds, densityDpi, counter) }
    }

    private fun extractNode(info: ViewInfoData, parentBounds: Bounds?, dpi: Int, counter: IdCounter): HierarchyNode {
        val id = counter.next()
        val s = 160.0 / dpi
        val bounds = Bounds(info.left.toDouble() * s, info.top.toDouble() * s, info.right.toDouble() * s, info.bottom.toDouble() * s)
        val boundsInParent = parentBounds?.let {
            boundsOf(bounds.left - it.left, bounds.top - it.top, bounds.right - it.left, bounds.bottom - it.top)
        }
        val size = sizeOf(bounds.right - bounds.left, bounds.bottom - bounds.top)

        return HierarchyNode(
            id = id,
            name = info.className.substringAfterLast('.'),
            bounds = bounds,
            boundsInParent = boundsInParent,
            size = size,
            semantics = info.semantics,
            children = info.children.map { extractNode(it, bounds, dpi, counter) },
        )
    }
}
