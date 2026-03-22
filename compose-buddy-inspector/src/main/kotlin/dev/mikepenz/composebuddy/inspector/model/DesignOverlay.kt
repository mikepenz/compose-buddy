package dev.mikepenz.composebuddy.inspector.model

import kotlinx.serialization.Serializable

@Serializable
enum class OverlayType {
    IMAGE,
    SEMANTICS,
}

@Serializable
enum class ComparisonMode {
    SLIDER,
    TRANSPARENCY,
    SIDE_BY_SIDE,
    DIFFERENCE,
}

@Serializable
data class DesignOverlay(
    val id: String,
    val type: OverlayType,
    val filePath: String,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 0.5f,
    val comparisonMode: ComparisonMode = ComparisonMode.TRANSPARENCY,
)
