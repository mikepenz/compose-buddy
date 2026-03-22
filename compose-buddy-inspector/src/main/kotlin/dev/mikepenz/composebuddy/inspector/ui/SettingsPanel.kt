package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import dev.mikepenz.composebuddy.core.VERSION
import dev.mikepenz.composebuddy.inspector.DEVICE_PRESETS
import dev.mikepenz.composebuddy.inspector.PreviewTheme
import dev.mikepenz.composebuddy.inspector.InspectorSettings
import dev.mikepenz.composebuddy.inspector.RenderConfig
import dev.mikepenz.composebuddy.inspector.RendererType
import dev.mikepenz.composebuddy.inspector.ThemeMode

@Composable
fun SettingsPanel(
    settings: InspectorSettings,
    onSettingsChanged: (InspectorSettings) -> Unit,
    onApplyRenderConfig: (RenderConfig) -> Unit,
    onRendererChanged: (RendererType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Theme
        Text("Theme", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { onSettingsChanged(settings.copy(themeMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                ) {
                    Text(
                        when (mode) {
                            ThemeMode.AUTO -> "Auto"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Preview Theme (light/dark for rendered previews)
        Text("Preview Theme", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PreviewTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = settings.previewTheme == theme,
                    onClick = { onSettingsChanged(settings.copy(previewTheme = theme)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PreviewTheme.entries.size),
                ) {
                    Text(
                        when (theme) {
                            PreviewTheme.LIGHT -> "Light"
                            PreviewTheme.DARK -> "Dark"
                        },
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Renderer
        Text("Renderer", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            RendererType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = settings.rendererType == type,
                    onClick = {
                        onSettingsChanged(settings.copy(rendererType = type))
                        onRendererChanged(type)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = RendererType.entries.size),
                ) {
                    Text(type.label, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Display options
        Text("Display", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Show RGB values", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.showRgbValues,
                onCheckedChange = { onSettingsChanged(settings.copy(showRgbValues = it)) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Device Presets
        Text("Device Preset", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))

        DEVICE_PRESETS.forEachIndexed { index, preset ->
            val isSelected = index == settings.selectedPresetIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (preset.name == "Custom") {
                            onSettingsChanged(settings.copy(selectedPresetIndex = index))
                        } else if (preset.name == "Default") {
                            val newConfig = RenderConfig()
                            onSettingsChanged(settings.copy(selectedPresetIndex = index, renderConfig = newConfig))
                            onApplyRenderConfig(newConfig)
                        } else {
                            val newConfig = RenderConfig(widthDp = preset.widthDp, heightDp = preset.heightDp, densityDpi = preset.densityDpi)
                            onSettingsChanged(settings.copy(selectedPresetIndex = index, renderConfig = newConfig))
                            onApplyRenderConfig(newConfig)
                        }
                    }
                    .padding(vertical = 2.dp),
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(preset.name, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    if (preset.widthDp > 0) {
                        Text(
                            "${preset.widthDp}x${preset.heightDp}dp · ${preset.densityDpi}dpi",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        // Custom resolution inputs (shown when Custom is selected)
        val isCustom = DEVICE_PRESETS.getOrNull(settings.selectedPresetIndex)?.name == "Custom"
        if (isCustom) {
            Spacer(Modifier.height(8.dp))
            Text("Custom Resolution", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Row {
                OutlinedTextField(
                    value = if (settings.renderConfig.widthDp > 0) settings.renderConfig.widthDp.toString() else "",
                    onValueChange = { v ->
                        val w = v.toIntOrNull() ?: -1
                        onSettingsChanged(settings.copy(renderConfig = settings.renderConfig.copy(widthDp = w)))
                    },
                    label = { Text("Width dp", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    singleLine = true,
                )
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(
                    value = if (settings.renderConfig.heightDp > 0) settings.renderConfig.heightDp.toString() else "",
                    onValueChange = { v ->
                        val h = v.toIntOrNull() ?: -1
                        onSettingsChanged(settings.copy(renderConfig = settings.renderConfig.copy(heightDp = h)))
                    },
                    label = { Text("Height dp", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = if (settings.renderConfig.densityDpi > 0) settings.renderConfig.densityDpi.toString() else "",
                onValueChange = { v ->
                    val d = v.toIntOrNull() ?: -1
                    onSettingsChanged(settings.copy(renderConfig = settings.renderConfig.copy(densityDpi = d)))
                },
                label = { Text("Density DPI", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onApplyRenderConfig(settings.renderConfig) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply & Re-render", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "Compose Buddy v$VERSION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
