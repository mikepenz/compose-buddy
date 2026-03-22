package dev.mikepenz.composebuddy.renderer.desktop

import dev.mikepenz.composebuddy.core.model.HierarchyNode
import java.awt.image.BufferedImage

/**
 * Extracts colors from the rendered image by sampling pixels at node bounds.
 * Bounds are in dp — converted to px using densityDpi for pixel sampling.
 */
object ColorExtractor {

    fun enrich(node: HierarchyNode, image: BufferedImage, densityDpi: Int): HierarchyNode {
        val scale = densityDpi / 160f
        val extraProps = mutableMapOf<String, String>()

        // Convert dp bounds to px for sampling
        val lPx = (node.bounds.left * scale).toInt()
        val tPx = (node.bounds.top * scale).toInt()
        val rPx = (node.bounds.right * scale).toInt()
        val bPx = (node.bounds.bottom * scale).toInt()

        if (rPx > 0 && bPx > 0 && lPx < image.width && tPx < image.height) {
            val bgColor = sampleBackground(image, lPx, tPx, rPx, bPx)
            if (bgColor != null) extraProps["backgroundColor"] = fmt(bgColor)

            val centerColor = sampleCenter(image, lPx, tPx, rPx, bPx)
            if (centerColor != null && centerColor != bgColor) {
                extraProps["foregroundColor"] = fmt(centerColor)
            }
            if (node.children.isEmpty() && centerColor != null) {
                extraProps["dominantColor"] = fmt(centerColor)
            }
        }

        val merged = if (extraProps.isNotEmpty()) (node.semantics ?: emptyMap()) + extraProps else node.semantics

        return node.copy(
            semantics = merged,
            children = node.children.map { enrich(it, image, densityDpi) },
        )
    }

    private fun sampleBackground(img: BufferedImage, l: Int, t: Int, r: Int, b: Int): Int? {
        val samples = mutableListOf<Int>()
        val inset = 2
        val cl = l.coerceIn(0, img.width - 1)
        val ct = t.coerceIn(0, img.height - 1)
        val cr = (r - 1).coerceIn(0, img.width - 1)
        val cb = (b - 1).coerceIn(0, img.height - 1)

        for ((x, y) in listOf(
            Pair((cl + inset).coerceAtMost(cr), (ct + inset).coerceAtMost(cb)),
            Pair((cr - inset).coerceAtLeast(cl), (ct + inset).coerceAtMost(cb)),
            Pair((cl + inset).coerceAtMost(cr), (cb - inset).coerceAtLeast(ct)),
            Pair((cr - inset).coerceAtLeast(cl), (cb - inset).coerceAtLeast(ct)),
        )) {
            if (x in 0 until img.width && y in 0 until img.height) samples.add(img.getRGB(x, y))
        }
        return samples.groupBy { it }.maxByOrNull { it.value.size }?.key
    }

    private fun sampleCenter(img: BufferedImage, l: Int, t: Int, r: Int, b: Int): Int? {
        val cx = ((l + r) / 2).coerceIn(0, img.width - 1)
        val cy = ((t + b) / 2).coerceIn(0, img.height - 1)
        return img.getRGB(cx, cy)
    }

    private fun fmt(argb: Int) = "#%08X".format(argb)
}
