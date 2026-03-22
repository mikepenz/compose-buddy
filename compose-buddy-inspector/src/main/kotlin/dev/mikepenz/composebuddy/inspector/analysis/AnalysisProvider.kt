package dev.mikepenz.composebuddy.inspector.analysis

import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.inspector.model.AiFinding
import dev.mikepenz.composebuddy.inspector.model.TokenValue

/**
 * Pluggable interface for AI-assisted design token analysis.
 * The CLI provides a default implementation using MCP infrastructure;
 * IDE plugin can wire its own AI backend.
 */
interface AnalysisProvider {
    suspend fun analyze(
        hierarchy: HierarchyNode,
        figmaTokens: Map<String, TokenValue>? = null,
    ): List<AiFinding>
}
