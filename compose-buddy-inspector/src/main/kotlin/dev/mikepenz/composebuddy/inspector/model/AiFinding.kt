package dev.mikepenz.composebuddy.inspector.model

import kotlinx.serialization.Serializable

@Serializable
enum class Severity {
    ERROR,
    WARNING,
    INFO,
}

@Serializable
data class AiFinding(
    val id: String,
    val severity: Severity,
    val nodeId: Int,
    val nodeName: String,
    val category: String,
    val message: String,
    val suggestion: String? = null,
)
