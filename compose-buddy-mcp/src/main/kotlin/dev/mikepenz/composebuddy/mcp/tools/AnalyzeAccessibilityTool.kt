package dev.mikepenz.composebuddy.mcp.tools

import dev.mikepenz.composebuddy.core.analysis.AccessibilityAnalyzer
import dev.mikepenz.composebuddy.core.model.DesignToken
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class AnalyzeAccessibilityTool(
    private val renderer: PreviewRenderer,
    private val analyzer: AccessibilityAnalyzer = AccessibilityAnalyzer(),
) {

    @Serializable
    data class AnalysisResponse(
        val status: String,
        val totalFindings: Int,
        val findings: List<dev.mikepenz.composebuddy.core.model.AccessibilityFinding>,
    )

    fun execute(
        previewFqn: String,
        checks: Set<String> = setOf("ALL"),
        designTokens: List<DesignToken> = emptyList(),
    ): String {
        val preview = Preview(fullyQualifiedName = previewFqn)
        val config = RenderConfiguration.resolve(preview)
        val hierarchy = renderer.extractHierarchy(preview, config)

        if (hierarchy == null) {
            return ComposeBuddyJson.encodeToString(
                AnalysisResponse(status = "ERROR", totalFindings = 0, findings = emptyList()),
            )
        }

        val result = analyzer.analyze(hierarchy, checks, designTokens = designTokens)
        return ComposeBuddyJson.encodeToString(
            AnalysisResponse(
                status = result.status,
                totalFindings = result.totalFindings,
                findings = result.findings,
            ),
        )
    }
}
