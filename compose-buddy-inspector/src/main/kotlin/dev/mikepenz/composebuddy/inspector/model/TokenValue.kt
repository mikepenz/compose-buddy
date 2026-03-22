package dev.mikepenz.composebuddy.inspector.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenValue(
    val type: String,
    val value: String,
    val category: String? = null,
)
