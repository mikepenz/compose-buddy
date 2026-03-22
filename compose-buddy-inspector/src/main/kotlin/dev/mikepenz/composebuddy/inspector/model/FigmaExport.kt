package dev.mikepenz.composebuddy.inspector.model

import kotlinx.serialization.Serializable

/**
 * Imported Figma design data for semantic comparison.
 * Schema version 1.0.0 per contracts/figma-schema.md.
 */
@Serializable
data class FigmaExport(
    val version: String,
    val tokens: Map<String, TokenValue>,
    val components: List<FigmaComponent> = emptyList(),
)

@Serializable
data class FigmaComponent(
    val name: String,
    val bounds: List<Double> = emptyList(),
    val appliedTokens: Map<String, String> = emptyMap(),
    val children: List<FigmaComponent> = emptyList(),
)
