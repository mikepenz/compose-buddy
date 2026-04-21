package com.example.sample.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun AppToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun TogglePreviewFrame(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        content()
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
internal fun AppTogglePreview_Off() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        TogglePreviewFrame {
            AppToggle(label = "Enable notifications", checked = false, onCheckedChange = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
internal fun AppTogglePreview_On() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        TogglePreviewFrame {
            AppToggle(label = "Enable notifications", checked = true, onCheckedChange = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
internal fun AppTogglePreview_Disabled_Off() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        TogglePreviewFrame {
            AppToggle(label = "Enable notifications", checked = false, onCheckedChange = {}, enabled = false)
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
internal fun AppTogglePreview_Disabled_On() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        TogglePreviewFrame {
            AppToggle(label = "Enable notifications", checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 320)
@Composable
internal fun AppTogglePreview_Dark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        TogglePreviewFrame {
            AppToggle(label = "Enable notifications", checked = true, onCheckedChange = {})
        }
    }
}
