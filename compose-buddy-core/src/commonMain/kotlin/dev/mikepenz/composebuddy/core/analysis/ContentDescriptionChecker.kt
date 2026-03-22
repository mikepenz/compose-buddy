package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFinding
import dev.mikepenz.composebuddy.core.model.AccessibilityFindingType
import dev.mikepenz.composebuddy.core.model.FindingSeverity
import dev.mikepenz.composebuddy.core.model.HierarchyNode

object ContentDescriptionChecker {

    fun check(root: HierarchyNode): List<AccessibilityFinding> {
        val findings = mutableListOf<AccessibilityFinding>()
        walkTree(root, findings)
        return findings
    }

    private fun walkTree(node: HierarchyNode, findings: MutableList<AccessibilityFinding>) {
        val semantics = node.semantics ?: emptyMap()
        val isClickable = semantics.containsKey("onClick") ||
            semantics.containsKey("clickable") ||
            semantics["role"] == "Button" ||
            semantics["role"] == "Tab" ||
            semantics["role"] == "Checkbox" ||
            semantics["role"] == "Switch" ||
            semantics["role"] == "RadioButton"

        val hasContentDescription = semantics.containsKey("contentDescription") &&
            semantics["contentDescription"]?.isNotBlank() == true

        val hasText = semantics.containsKey("text") &&
            semantics["text"]?.isNotBlank() == true

        if (isClickable && !hasContentDescription && !hasText) {
            findings.add(
                AccessibilityFinding(
                    type = AccessibilityFindingType.MISSING_CONTENT_DESCRIPTION,
                    severity = FindingSeverity.ERROR,
                    nodeId = node.id,
                    nodeName = node.name,
                    description = "Clickable element '${node.name}' missing contentDescription",
                    actualValue = null,
                    expectedValue = "Non-empty contentDescription or text",
                ),
            )
        }

        for (child in node.children) {
            walkTree(child, findings)
        }
    }
}
