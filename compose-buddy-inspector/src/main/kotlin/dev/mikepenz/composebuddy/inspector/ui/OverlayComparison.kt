package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.inspector.model.ComparisonMode
import dev.mikepenz.composebuddy.inspector.model.DesignOverlay

/**
 * Overlay comparison composable supporting slider, transparency, side-by-side, and difference modes.
 * Full Canvas-based rendering will be implemented when image loading is integrated.
 */
@Composable
fun OverlayComparison(
    overlay: DesignOverlay,
    onOverlayUpdated: (DesignOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text("Overlay: ${overlay.filePath}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Mode: ${overlay.comparisonMode}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)

        Spacer(Modifier.height(8.dp))

        when (overlay.comparisonMode) {
            ComparisonMode.TRANSPARENCY -> {
                Text("Opacity", fontSize = 11.sp)
                var opacity by remember { mutableStateOf(overlay.opacity) }
                Slider(
                    value = opacity,
                    onValueChange = {
                        opacity = it
                        onOverlayUpdated(overlay.copy(opacity = it))
                    },
                    valueRange = 0f..1f,
                )
            }

            ComparisonMode.SLIDER -> {
                Text("Slide to compare", fontSize = 11.sp)
                var position by remember { mutableStateOf(0.5f) }
                Slider(
                    value = position,
                    onValueChange = { position = it },
                    valueRange = 0f..1f,
                )
            }

            ComparisonMode.SIDE_BY_SIDE -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Implementation", fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Design", fontSize = 11.sp)
                    }
                }
            }

            ComparisonMode.DIFFERENCE -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Pixel difference view", fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Alignment controls
        Text("Scale: ${"%.2f".format(overlay.scale)}", fontSize = 11.sp)
        var scale by remember { mutableStateOf(overlay.scale) }
        Slider(
            value = scale,
            onValueChange = {
                scale = it
                onOverlayUpdated(overlay.copy(scale = it))
            },
            valueRange = 0.1f..3f,
        )
    }
}
