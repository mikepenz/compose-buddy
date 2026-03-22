package dev.mikepenz.composebuddy.device.capture

import android.graphics.Bitmap
import dev.mikepenz.composebuddy.device.model.BuddyBounds
import dev.mikepenz.composebuddy.device.model.BuddySemanticNode

object ColorSampler {

    /**
     * Sample background color from 4 corners and foreground from center pixel of each node's bounds.
     * Background = most common corner color; foreground = center color if different from background.
     * Returns a new tree with colors filled in.
     */
    fun enrich(node: BuddySemanticNode, bitmap: Bitmap): BuddySemanticNode {
        val (bgColor, fgColor) = sampleColors(bitmap, node.bounds)
        return node.copy(
            colors = node.colors.copy(background = bgColor, foreground = fgColor),
            children = node.children.map { child -> enrich(child, bitmap) },
        )
    }

    /**
     * Sample 4 corners (inset by 2px) + center.
     * Background = most common corner color.
     * Foreground = center color if different from background.
     */
    private fun sampleColors(bitmap: Bitmap, bounds: BuddyBounds): Pair<String?, String?> {
        val l = bounds.left.toInt().coerceIn(0, bitmap.width - 1)
        val t = bounds.top.toInt().coerceIn(0, bitmap.height - 1)
        val r = (bounds.right.toInt() - 1).coerceIn(0, bitmap.width - 1)
        val b = (bounds.bottom.toInt() - 1).coerceIn(0, bitmap.height - 1)

        if (l > r || t > b) return Pair(null, null)

        val inset = 2
        val cornerSamples = mutableListOf<Int>()
        for ((x, y) in listOf(
            Pair((l + inset).coerceAtMost(r), (t + inset).coerceAtMost(b)),  // top-left
            Pair((r - inset).coerceAtLeast(l), (t + inset).coerceAtMost(b)),  // top-right
            Pair((l + inset).coerceAtMost(r), (b - inset).coerceAtLeast(t)),  // bottom-left
            Pair((r - inset).coerceAtLeast(l), (b - inset).coerceAtLeast(t)), // bottom-right
        )) {
            if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                cornerSamples.add(bitmap.getPixel(x, y))
            }
        }

        val bgPixel = cornerSamples.groupBy { it }.maxByOrNull { it.value.size }?.key
        val bgColor = bgPixel?.let { "#%08X".format(it) }

        // Center pixel for foreground
        val cx = ((bounds.left + bounds.right) / 2).toInt().coerceIn(0, bitmap.width - 1)
        val cy = ((bounds.top + bounds.bottom) / 2).toInt().coerceIn(0, bitmap.height - 1)
        val centerPixel = if (cx in 0 until bitmap.width && cy in 0 until bitmap.height) {
            bitmap.getPixel(cx, cy)
        } else null

        val fgColor = if (centerPixel != null && centerPixel != bgPixel) {
            "#%08X".format(centerPixel)
        } else null

        return Pair(bgColor, fgColor)
    }
}
