package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFinding
import dev.mikepenz.composebuddy.core.model.AccessibilityFindingType
import dev.mikepenz.composebuddy.core.model.FindingSeverity
import dev.mikepenz.composebuddy.core.model.HierarchyNode

object TouchTargetChecker {

    private const val MIN_TOUCH_TARGET_DP = 48.0

    /** Size is already in dp — no density conversion needed. */
    fun check(root: HierarchyNode, @Suppress("UNUSED_PARAMETER") densityDpi: Int = 420): List<AccessibilityFinding> {
        val findings = mutableListOf<AccessibilityFinding>()
        walkTree(root, findings)
        return findings
    }

    private fun walkTree(node: HierarchyNode, findings: MutableList<AccessibilityFinding>) {
        val semantics = node.semantics ?: emptyMap()
        val isClickable = semantics.containsKey("onClick") ||
            semantics.containsKey("clickable") ||
            semantics["role"] == "Button" ||
            semantics["role"] == "Tab"

        if (isClickable) {
            val w = node.size.width
            val h = node.size.height
            if (w < MIN_TOUCH_TARGET_DP || h < MIN_TOUCH_TARGET_DP) {
                findings.add(
                    AccessibilityFinding(
                        type = AccessibilityFindingType.TOUCH_TARGET_TOO_SMALL,
                        severity = FindingSeverity.WARNING,
                        nodeId = node.id,
                        nodeName = node.name,
                        description = "Touch target for '${node.name}' is ${w.toInt()}x${h.toInt()}dp, minimum is ${MIN_TOUCH_TARGET_DP.toInt()}x${MIN_TOUCH_TARGET_DP.toInt()}dp",
                        actualValue = "${w.toInt()}x${h.toInt()}dp",
                        expectedValue = ">=${MIN_TOUCH_TARGET_DP.toInt()}x${MIN_TOUCH_TARGET_DP.toInt()}dp",
                    ),
                )
            }
        }

        for (child in node.children) walkTree(child, findings)
    }
}
