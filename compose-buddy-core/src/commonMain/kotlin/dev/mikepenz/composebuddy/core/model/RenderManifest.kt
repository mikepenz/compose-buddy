package dev.mikepenz.composebuddy.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RenderManifest(
    val version: String = "1.0.0",
    val timestamp: String,
    val projectPath: String,
    val modulePath: String = "",
    val totalPreviews: Int = 0,
    val totalRendered: Int = 0,
    val totalErrors: Int = 0,
    /** Density used for rendering. To convert dp→px: px = dp * densityDpi / 160 */
    val densityDpi: Int = 420,
    /** All semantic property names found across rendered previews.
     *  Agents can request additional fields via CLI --semantics flag. */
    val availableSemantics: List<String> = emptyList(),
    val results: List<RenderResult> = emptyList(),
)
