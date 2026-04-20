package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.inspector.DEVICE_PRESETS
import dev.mikepenz.composebuddy.inspector.InspectorSession
import dev.mikepenz.composebuddy.inspector.InspectorSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PreviewGroup(
    val baseName: String,
    val variants: List<RenderResult>,
    val labels: List<String>,
)

data class PreviewDefGroup(
    val baseName: String,
    val variants: List<Preview>,
    val labels: List<String>,
)

internal fun groupPreviewDefs(previews: List<Preview>): List<PreviewDefGroup> {
    return previews.groupBy { it.fullyQualifiedName }.map { (name, variants) ->
        PreviewDefGroup(
            baseName = name,
            variants = variants,
            labels = deriveVariantLabelsFromPreviews(variants),
        )
    }
}

internal fun groupPreviews(results: List<RenderResult>): List<PreviewGroup> {
    return results.groupBy { it.previewName }.map { (name, variants) ->
        PreviewGroup(
            baseName = name,
            variants = variants,
            labels = deriveVariantLabels(variants),
        )
    }
}

data class SpotlightItem(
    val previewName: String,
    val variantIndex: Int,
    val shortName: String,
    val variantLabel: String?,
    val packageName: String,
    val hasError: Boolean,
)

internal fun buildSpotlightItems(results: List<RenderResult>): List<SpotlightItem> {
    return results.groupBy { it.previewName }.flatMap { (name, variants) ->
        val labels = deriveVariantLabels(variants)
        variants.mapIndexed { index, result ->
            SpotlightItem(
                previewName = name,
                variantIndex = index,
                shortName = name.substringAfterLast('.'),
                variantLabel = labels[index].takeIf { it.isNotBlank() },
                packageName = name.substringBeforeLast('.', "").substringAfterLast('.'),
                hasError = result.error != null,
            )
        }
    }
}

/**
 * Build spotlight items from discovered preview definitions (lazy mode — no rendering needed).
 */
internal fun buildSpotlightItemsFromDefs(previews: List<Preview>): List<SpotlightItem> {
    return previews.groupBy { it.fullyQualifiedName }.flatMap { (fqn, variants) ->
        val labels = deriveVariantLabelsFromPreviews(variants)
        variants.mapIndexed { index, _ ->
            SpotlightItem(
                previewName = fqn,
                variantIndex = index,
                shortName = fqn.substringAfterLast('.'),
                variantLabel = labels[index].takeIf { it.isNotBlank() },
                packageName = fqn.substringBeforeLast('.', "").substringAfterLast('.'),
                hasError = false,
            )
        }
    }
}

/**
 * Derives a human-readable label for a preview variant from its [Preview] definition.
 * Only includes dimensions that actually differ across siblings in the group.
 */
internal fun deriveVariantLabelFromPreview(preview: Preview, index: Int, totalVariants: Int): String {
    if (totalVariants <= 1) return ""
    if (preview.name.isNotBlank()) return preview.name
    return "Variant ${index + 1}"
}

/**
 * Derives labels for all variants in a group, showing only dimensions that differ.
 */
internal fun deriveVariantLabelsFromPreviews(variants: List<Preview>): List<String> {
    if (variants.size <= 1) return variants.map { "" }

    // Detect which dimensions vary across the group
    val uiModes = variants.map { it.uiMode and 0x30 }.toSet()
    val locales = variants.map { it.locale }.toSet()
    val fontScales = variants.map { it.fontScale }.toSet()
    val sizes = variants.map { "${it.widthDp}x${it.heightDp}" }.toSet()
    val devices = variants.map { it.device }.toSet()

    return variants.mapIndexed { index, preview ->
        // Always prefer the @Preview name — it was chosen deliberately by the engineer
        if (preview.name.isNotBlank()) return@mapIndexed preview.name

        val parts = mutableListOf<String>()
        // Fall back to showing dimensions that vary across variants
        if (uiModes.size > 1) {
            parts.add(if ((preview.uiMode and 0x30) == 0x20) "Dark" else "Light")
        }
        if (locales.size > 1 && preview.locale.isNotBlank()) parts.add(preview.locale)
        if (fontScales.size > 1 && preview.fontScale != 1f) parts.add("${preview.fontScale}x")
        if (sizes.size > 1 && preview.widthDp > 0 && preview.heightDp > 0) parts.add("${preview.widthDp}×${preview.heightDp}")
        if (devices.size > 1 && preview.device.isNotBlank()) parts.add(preview.device.substringAfter("id:").substringBefore(","))

        if (parts.isNotEmpty()) parts.joinToString(" · ") else "Variant ${index + 1}"
    }
}

/**
 * Derives labels for all variants in a group of render results, showing only dimensions that differ.
 */
internal fun deriveVariantLabels(variants: List<RenderResult>): List<String> {
    if (variants.size <= 1) return variants.map { "" }

    val configs = variants.map { it.configuration }

    // Detect which dimensions vary
    val uiModes = configs.map { it.uiMode and 0x30 }.toSet()
    val locales = configs.map { it.locale }.toSet()
    val fontScales = configs.map { it.fontScale }.toSet()
    val sizes = configs.map { "${it.widthDp}x${it.heightDp}" }.toSet()
    val densities = configs.map { it.densityDpi }.toSet()
    val paramIndices = variants.map { it.parameterIndex }.toSet()

    return variants.mapIndexed { index, result ->
        val config = result.configuration
        val parts = mutableListOf<String>()

        if (uiModes.size > 1) {
            parts.add(if ((config.uiMode and 0x30) == 0x20) "Dark" else "Light")
        }
        if (locales.size > 1 && config.locale.isNotBlank()) parts.add(config.locale)
        if (fontScales.size > 1 && config.fontScale != 1f) parts.add("${config.fontScale}x")
        if (sizes.size > 1 && config.widthDp > 0 && config.heightDp > 0) parts.add("${config.widthDp}×${config.heightDp}")
        if (densities.size > 1 && config.densityDpi > 0) parts.add("${config.densityDpi}dpi")
        if (paramIndices.size > 1 && result.parameterIndex != null) parts.add("#${result.parameterIndex}")

        if (parts.isNotEmpty()) parts.joinToString(" · ") else "Variant ${index + 1}"
    }
}

@Composable
fun Toolbar(
    session: InspectorSession,
    selectedPreviewName: String?,
    onPreviewSelected: (String) -> Unit,
    onRerendered: () -> Unit,
    settings: InspectorSettings,
    showSettings: Boolean,
    onToggleSettings: () -> Unit,
    onOpenSpotlight: () -> Unit,
    modifier: Modifier = Modifier,
    showGuides: Boolean = true,
    onToggleGuides: () -> Unit = {},
    leftPanelVisible: Boolean = true,
    onToggleLeftPanel: () -> Unit = {},
    rightPanelVisible: Boolean = true,
    onToggleRightPanel: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val tokens = InspectorTokens.current
    var isRerendering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val currentPreset = DEVICE_PRESETS.getOrNull(settings.selectedPresetIndex)

    Column(modifier = modifier.background(tokens.bgWindow)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(InspectorSpacing.toolbarHeight)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Preview selector (opens spotlight search) — layers icon + name + ⌘K badge
        val displayName = selectedPreviewName?.substringAfterLast('.') ?: "Select Preview"
        Row(
            modifier = Modifier
                .height(30.dp)
                .widthIn(min = 160.dp, max = 260.dp)
                .clip(RoundedCornerShape(InspectorRadius.md))
                .background(tokens.bgPanel)
                .border(0.5.dp, tokens.line2, RoundedCornerShape(InspectorRadius.md))
                .clickable(onClick = onOpenSpotlight)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Layers,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tokens.fg3,
            )
            Text(
                displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = InspectorType.toolbarText,
                fontWeight = FontWeight.Medium,
                color = tokens.fg1,
                modifier = Modifier.weight(1f),
            )
            KbdBadge("⌘K")
        }

        // Re-render button — outlined "ghost" style per the HTML's primary toolbar btn
        Row(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(InspectorRadius.tab))
                .background(tokens.bgHover)
                .border(0.5.dp, tokens.line2, RoundedCornerShape(InspectorRadius.tab))
                .clickable(enabled = !isRerendering) {
                    scope.launch {
                        isRerendering = true
                        errorMessage = null
                        withContext(Dispatchers.IO) {
                            val config = settings.renderConfig.takeIf { it.widthDp > 0 || it.heightDp > 0 || it.densityDpi > 0 }
                            session.triggerRerender(config)
                        }
                        errorMessage = session.lastRenderError
                        isRerendering = false
                        onRerendered()
                    }
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tokens.fg1,
            )
            Text(
                text = if (isRerendering) "Rendering…" else "Re-render",
                fontSize = InspectorType.toolbarText,
                fontWeight = FontWeight.Medium,
                color = tokens.fg1,
            )
        }

        if (isRerendering) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }

        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }

        Spacer(Modifier.weight(1f))

        // Stats: "127 previews · 3 frames" — bold counts, muted body, dot dividers
        if (currentPreset != null && currentPreset.name != "Default") {
            Text(
                text = currentPreset.name,
                fontSize = 11.sp,
                color = tokens.fg3,
                fontFamily = FontFamily.Monospace,
            )
            Box(modifier = Modifier.size(width = 1.dp, height = 14.dp).background(tokens.line2))
        }

        val previewCount = session.previewDefs.size.takeIf { it > 0 } ?: session.availablePreviews.size
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatChip(count = previewCount, label = "previews")
            Box(modifier = Modifier.size(width = 1.dp, height = 14.dp).background(tokens.line2))
            StatChip(count = session.frames.size, label = "frames")
        }

        Spacer(Modifier.width(4.dp))

        // Left panel toggle
        ToolbarIconButton(active = leftPanelVisible, onClick = onToggleLeftPanel) {
            Icon(
                imageVector = Icons.Default.VerticalSplit,
                contentDescription = "Toggle left panel",
                modifier = Modifier.size(14.dp),
                tint = if (leftPanelVisible) tokens.accent else tokens.fg2,
            )
        }

        // Right panel toggle (mirrored)
        ToolbarIconButton(active = rightPanelVisible, onClick = onToggleRightPanel) {
            Icon(
                imageVector = Icons.Default.VerticalSplit,
                contentDescription = "Toggle right panel",
                modifier = Modifier.size(14.dp).graphicsLayer(scaleX = -1f),
                tint = if (rightPanelVisible) tokens.accent else tokens.fg2,
            )
        }

        // Guides toggle (grid icon)
        ToolbarIconButton(active = showGuides, onClick = onToggleGuides) {
            Icon(
                imageVector = Icons.Default.GridOn,
                contentDescription = "Toggle guides",
                modifier = Modifier.size(14.dp),
                tint = if (showGuides) tokens.accent else tokens.fg2,
            )
        }

        // Settings toggle
        ToolbarIconButton(active = showSettings, onClick = onToggleSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(14.dp),
                tint = if (showSettings) tokens.accent else tokens.fg2,
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.line1))
    }
}

@Composable
private fun StatChip(count: Int, label: String) {
    val tokens = InspectorTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = count.toString(),
            color = tokens.fg1,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = tokens.fg3,
            fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun ToolbarIconButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tokens = InspectorTokens.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(InspectorRadius.tab))
            .background(if (active) tokens.bgActive else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
