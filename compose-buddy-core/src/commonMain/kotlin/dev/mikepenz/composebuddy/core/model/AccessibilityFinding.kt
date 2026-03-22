package dev.mikepenz.composebuddy.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AccessibilityFinding(
    val type: AccessibilityFindingType,
    val severity: FindingSeverity,
    val nodeId: Int,
    val nodeName: String,
    val description: String,
    val actualValue: String? = null,
    val expectedValue: String? = null,
)

@Serializable
enum class AccessibilityFindingType {
    MISSING_CONTENT_DESCRIPTION,
    TOUCH_TARGET_TOO_SMALL,
    LOW_CONTRAST,
    DESIGN_SPEC_DEVIATION,
}

@Serializable
enum class FindingSeverity {
    ERROR,
    WARNING,
    INFO,
}

@Serializable
data class DesignToken(
    val name: String,
    val type: DesignTokenType,
    val value: String,
    val description: String? = null,
)

@Serializable
enum class DesignTokenType {
    COLOR,
    SPACING,
    TYPOGRAPHY,
    SIZING,
}
