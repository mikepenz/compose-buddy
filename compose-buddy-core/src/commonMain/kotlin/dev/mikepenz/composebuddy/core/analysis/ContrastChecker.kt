package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFinding
import dev.mikepenz.composebuddy.core.model.AccessibilityFindingType
import dev.mikepenz.composebuddy.core.model.FindingSeverity
import dev.mikepenz.composebuddy.core.model.HierarchyNode

object ContrastChecker {

    private const val WCAG_AA_NORMAL_TEXT = 4.5
    private const val WCAG_AA_LARGE_TEXT = 3.0

    fun check(root: HierarchyNode): List<AccessibilityFinding> {
        val findings = mutableListOf<AccessibilityFinding>()
        walkTree(root, findings)
        return findings
    }

    private fun walkTree(node: HierarchyNode, findings: MutableList<AccessibilityFinding>) {
        val semantics = node.semantics ?: emptyMap()
        val foregroundColor = semantics["foregroundColor"]
        val backgroundColor = semantics["backgroundColor"]

        if (foregroundColor != null && backgroundColor != null) {
            val fg = parseColor(foregroundColor)
            val bg = parseColor(backgroundColor)
            if (fg != null && bg != null) {
                val ratio = contrastRatio(fg, bg)
                if (ratio < WCAG_AA_NORMAL_TEXT) {
                    findings.add(
                        AccessibilityFinding(
                            type = AccessibilityFindingType.LOW_CONTRAST,
                            severity = if (ratio < WCAG_AA_LARGE_TEXT) FindingSeverity.ERROR else FindingSeverity.WARNING,
                            nodeId = node.id,
                            nodeName = node.name,
                            description = "Contrast ratio ${String.format("%.2f", ratio)}:1 below WCAG AA minimum of ${WCAG_AA_NORMAL_TEXT}:1",
                            actualValue = "${String.format("%.2f", ratio)}:1",
                            expectedValue = ">=${WCAG_AA_NORMAL_TEXT}:1",
                        ),
                    )
                }
            }
        }

        for (child in node.children) {
            walkTree(child, findings)
        }
    }

    internal fun contrastRatio(color1: Triple<Int, Int, Int>, color2: Triple<Int, Int, Int>): Double {
        val l1 = relativeLuminance(color1)
        val l2 = relativeLuminance(color2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    internal fun relativeLuminance(color: Triple<Int, Int, Int>): Double {
        fun linearize(c: Int): Double {
            val srgb = c / 255.0
            return if (srgb <= 0.04045) srgb / 12.92
            else Math.pow((srgb + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linearize(color.first) +
            0.7152 * linearize(color.second) +
            0.0722 * linearize(color.third)
    }

    internal fun parseColor(hex: String): Triple<Int, Int, Int>? {
        val clean = hex.removePrefix("#")
        return when (clean.length) {
            6 -> Triple(
                clean.substring(0, 2).toIntOrNull(16) ?: return null,
                clean.substring(2, 4).toIntOrNull(16) ?: return null,
                clean.substring(4, 6).toIntOrNull(16) ?: return null,
            )
            8 -> Triple(
                clean.substring(2, 4).toIntOrNull(16) ?: return null,
                clean.substring(4, 6).toIntOrNull(16) ?: return null,
                clean.substring(6, 8).toIntOrNull(16) ?: return null,
            )
            else -> null
        }
    }
}
