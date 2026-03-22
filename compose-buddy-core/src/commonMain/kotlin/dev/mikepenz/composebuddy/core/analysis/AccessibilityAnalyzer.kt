package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFinding
import dev.mikepenz.composebuddy.core.model.DesignToken
import dev.mikepenz.composebuddy.core.model.HierarchyNode

class AccessibilityAnalyzer {

    data class AnalysisResult(
        val status: String,
        val totalFindings: Int,
        val findings: List<AccessibilityFinding>,
    )

    fun analyze(
        hierarchy: HierarchyNode,
        checks: Set<String> = setOf("ALL"),
        densityDpi: Int = 420,
        designTokens: List<DesignToken> = emptyList(),
    ): AnalysisResult {
        val allFindings = mutableListOf<AccessibilityFinding>()

        val runAll = "ALL" in checks

        if (runAll || "CONTENT_DESCRIPTION" in checks) {
            allFindings.addAll(ContentDescriptionChecker.check(hierarchy))
        }

        if (runAll || "TOUCH_TARGET" in checks) {
            allFindings.addAll(TouchTargetChecker.check(hierarchy, densityDpi))
        }

        if (runAll || "CONTRAST" in checks) {
            allFindings.addAll(ContrastChecker.check(hierarchy))
        }

        if (designTokens.isNotEmpty()) {
            allFindings.addAll(DesignTokenChecker.check(hierarchy, designTokens))
        }

        return AnalysisResult(
            status = if (allFindings.isEmpty()) "PASS" else "FAIL",
            totalFindings = allFindings.size,
            findings = allFindings,
        )
    }
}
