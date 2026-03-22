package dev.mikepenz.composebuddy.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RenderResult(
    val previewName: String,
    val parameterIndex: Int? = null,
    val configuration: RenderConfiguration,
    val imagePath: String = "",
    val imageBase64: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val renderDurationMs: Long = 0L,
    val hierarchy: HierarchyNode? = null,
    val error: RenderError? = null,
    val rendererUsed: String = "",
)

@Serializable
data class RenderError(
    val type: RenderErrorType,
    val message: String,
    val stackTrace: String? = null,
)

@Serializable
enum class RenderErrorType {
    COMPOSITION_ERROR,
    LAYOUT_ERROR,
    RESOURCE_ERROR,
    TIMEOUT,
    UNSUPPORTED_PLATFORM,
    UNKNOWN,
}
