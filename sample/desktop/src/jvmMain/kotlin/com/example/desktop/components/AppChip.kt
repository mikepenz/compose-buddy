package com.example.desktop.components

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.desktop.theme.BuddyShapes

@Composable
fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        modifier = modifier,
        shape = BuddyShapes.chip,
        interactionSource = interactionSource,
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Preview
@Composable
private fun AppChipPreview_Unselected() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ChipRow { AppChip(label = "Filter", selected = false, onClick = {}) }
    }
}

@Preview
@Composable
private fun AppChipPreview_Selected() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ChipRow { AppChip(label = "Filter", selected = true, onClick = {}) }
    }
}

@Preview
@Composable
private fun AppChipPreview_Disabled() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ChipRow { AppChip(label = "Filter", selected = false, onClick = {}, enabled = false) }
    }
}

@Preview
@Composable
private fun AppChipPreview_Disabled_Selected() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ChipRow { AppChip(label = "Filter", selected = true, onClick = {}, enabled = false) }
    }
}

@Preview
@Composable
private fun AppChipPreview_Hovered() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        val source = remember { MutableInteractionSource() }
        LaunchedEffect(source) {
            source.emit(HoverInteraction.Enter())
        }
        ChipRow {
            AppChip(
                label = "Hovered",
                selected = false,
                onClick = {},
                interactionSource = source,
            )
        }
    }
}

@Preview
@Composable
private fun AppChipPreview_Pressed() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        val source = remember { MutableInteractionSource() }
        LaunchedEffect(source) {
            source.emit(PressInteraction.Press(Offset.Zero))
        }
        ChipRow {
            AppChip(
                label = "Pressed",
                selected = false,
                onClick = {},
                interactionSource = source,
            )
        }
    }
}

@Preview
@Composable
private fun AppChipPreview_Dark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ChipRow {
            AppChip(label = "Off", selected = false, onClick = {})
            AppChip(label = "On", selected = true, onClick = {})
        }
    }
}
