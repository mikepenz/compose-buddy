package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFinding
import dev.mikepenz.composebuddy.core.model.AccessibilityFindingType
import dev.mikepenz.composebuddy.core.model.DesignToken
import dev.mikepenz.composebuddy.core.model.FindingSeverity
import dev.mikepenz.composebuddy.core.model.HierarchyNode

object DesignTokenChecker {

    fun check(root: HierarchyNode, tokens: List<DesignToken>): List<AccessibilityFinding> {
        if (tokens.isEmpty()) return emptyList()
        val findings = mutableListOf<AccessibilityFinding>()
        val tokenMap = tokens.associateBy { it.name }
        walkTree(root, tokenMap, findings)
        return findings
    }

    private fun walkTree(
        node: HierarchyNode,
        tokens: Map<String, DesignToken>,
        findings: MutableList<AccessibilityFinding>,
    ) {
        val semantics = node.semantics ?: emptyMap()

        // Check color tokens
        for ((propName, propValue) in semantics) {
            val tokenName = when (propName) {
                "backgroundColor" -> "color.background"
                "foregroundColor" -> "color.foreground"
                "textColor" -> "color.text"
                else -> continue
            }
            val token = tokens[tokenName] ?: continue
            if (!propValue.equals(token.value, ignoreCase = true)) {
                findings.add(
                    AccessibilityFinding(
                        type = AccessibilityFindingType.DESIGN_SPEC_DEVIATION,
                        severity = FindingSeverity.WARNING,
                        nodeId = node.id,
                        nodeName = node.name,
                        description = "Property '$propName' deviates from design token '$tokenName'",
                        actualValue = propValue,
                        expectedValue = token.value,
                    ),
                )
            }
        }

        for (child in node.children) {
            walkTree(child, tokens, findings)
        }
    }
}
