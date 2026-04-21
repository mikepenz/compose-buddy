package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.core.VERSION
import dev.mikepenz.composebuddy.inspector.DEVICE_PRESETS
import dev.mikepenz.composebuddy.inspector.InspectorSettings
import dev.mikepenz.composebuddy.inspector.PreviewTheme
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
    val tokens = InspectorTokens.current
    Column(modifier = modifier.background(tokens.bgPanel)) {
        PanelHeader(title = "Settings")
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.line1))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Theme
            Section(title = "Theme") {
                Segmented(
                    value = settings.themeMode,
                    options = listOf(
                        SegmentedOption(ThemeMode.AUTO, "Auto"),
                        SegmentedOption(ThemeMode.LIGHT, "Light"),
                        SegmentedOption(ThemeMode.DARK, "Dark"),
                    ),
                    onChange = { onSettingsChanged(settings.copy(themeMode = it)) },
                )
            }

            // Preview Theme
            Section(title = "Preview Theme") {
                Segmented(
                    value = settings.previewTheme,
                    options = listOf(
                        SegmentedOption(PreviewTheme.LIGHT, "Light"),
                        SegmentedOption(PreviewTheme.DARK, "Dark"),
                    ),
                    onChange = { onSettingsChanged(settings.copy(previewTheme = it)) },
                )
            }

            // Renderer
            Section(title = "Renderer") {
                Segmented(
                    value = settings.rendererType,
                    options = listOf(
                        SegmentedOption(RendererType.AUTO, "Auto"),
                        SegmentedOption(RendererType.DESKTOP, "Desktop"),
                        SegmentedOption(RendererType.ANDROID, "Android"),
                    ),
                    onChange = {
                        onSettingsChanged(settings.copy(rendererType = it))
                        onRendererChanged(it)
                    },
                )
            }

            // Display
            Section(title = "Display") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show RGB values",
                            color = tokens.fg1,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "On hover in the preview",
                            color = tokens.fg3,
                            fontSize = 11.sp,
                        )
                    }
                    Toggle(
                        value = settings.showRgbValues,
                        onChange = { onSettingsChanged(settings.copy(showRgbValues = it)) },
                    )
                }
            }

            // Device Preset
            Section(title = "Device Preset") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DEVICE_PRESETS.forEachIndexed { index, preset ->
                        DevicePresetRow(
                            name = preset.name,
                            spec = if (preset.widthDp > 0) "${preset.widthDp}×${preset.heightDp}dp · ${preset.densityDpi}dpi" else null,
                            selected = index == settings.selectedPresetIndex,
                            onClick = {
                                when (preset.name) {
                                    "Custom" -> onSettingsChanged(settings.copy(selectedPresetIndex = index))
                                    "Default" -> {
                                        val newConfig = RenderConfig()
                                        onSettingsChanged(settings.copy(selectedPresetIndex = index, renderConfig = newConfig))
                                        onApplyRenderConfig(newConfig)
                                    }
                                    else -> {
                                        val newConfig = RenderConfig(
                                            widthDp = preset.widthDp,
                                            heightDp = preset.heightDp,
                                            densityDpi = preset.densityDpi,
                                        )
                                        onSettingsChanged(settings.copy(selectedPresetIndex = index, renderConfig = newConfig))
                                        onApplyRenderConfig(newConfig)
                                    }
                                }
                            },
                        )
                    }
                }

                val isCustom = DEVICE_PRESETS.getOrNull(settings.selectedPresetIndex)?.name == "Custom"
                if (isCustom) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = if (settings.renderConfig.widthDp > 0) settings.renderConfig.widthDp.toString() else "",
                            onValueChange = { v ->
                                onSettingsChanged(settings.copy(renderConfig = settings.renderConfig.copy(widthDp = v.toIntOrNull() ?: -1)))
                            },
                            label = { Text("Width dp", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = if (settings.renderConfig.heightDp > 0) settings.renderConfig.heightDp.toString() else "",
                            onValueChange = { v ->
                                onSettingsChanged(settings.copy(renderConfig = settings.renderConfig.copy(heightDp = v.toIntOrNull() ?: -1)))
                            },
                            label = { Text("Height dp", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = if (settings.renderConfig.densityDpi > 0) settings.renderConfig.densityDpi.toString() else "",
                        onValueChange = { v ->
                            onSettingsChanged(settings.copy(renderConfig = settings.renderConfig.copy(densityDpi = v.toIntOrNull() ?: -1)))
                        },
                        label = { Text("Density DPI", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
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
            }

            // Footer — version stamp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Compose Buddy v$VERSION",
                    color = tokens.fg4,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DevicePresetRow(
    name: String,
    spec: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = InspectorTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InspectorRadius.sm))
            .background(if (selected) tokens.bgSelected else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Radio(checked = selected)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = tokens.fg1,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (spec != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = spec,
                    color = tokens.fg3,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
