package dev.mikepenz.composebuddy.device

import androidx.compose.runtime.Composable
import dev.mikepenz.composebuddy.device.model.PreviewConfig

/**
 * Wraps the user's @Preview composable.
 * Capture is handled by [BuddyPreviewActivity] on demand via commands.
 */
@Composable
fun BuddyInstrumentationWrapper(
    config: PreviewConfig,
    previewFqn: String = "",
    content: @Composable () -> Unit,
) {
    content()
}
