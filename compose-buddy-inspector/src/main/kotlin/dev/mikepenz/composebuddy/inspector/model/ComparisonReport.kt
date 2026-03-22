package dev.mikepenz.composebuddy.inspector.model

import kotlinx.serialization.Serializable

@Serializable
data class ComparisonReport(
    val timestamp: Long,
    val matched: List<TokenMatch>,
    val mismatched: List<TokenMismatch>,
    val missingInCompose: List<String>,
    val missingInFigma: List<String>,
    val summary: ComparisonSummary,
)

@Serializable
data class TokenMatch(
    val tokenName: String,
    val value: String,
    val componentName: String? = null,
)

@Serializable
data class TokenMismatch(
    val tokenName: String,
    val figmaValue: String,
    val composeValue: String,
    val deltaE: Double? = null,
    val componentName: String? = null,
)

@Serializable
data class ComparisonSummary(
    val totalTokens: Int,
    val matchCount: Int,
    val mismatchCount: Int,
    val matchPercentage: Double,
)
