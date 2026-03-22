package dev.mikepenz.composebuddy.device.model

import androidx.compose.runtime.Composable

data class BuddyPreviewEntry(
    val fqn: String,
    val displayName: String,
    val configs: List<PreviewConfig>,
    val composable: @Composable (PreviewConfig) -> Unit,
)
