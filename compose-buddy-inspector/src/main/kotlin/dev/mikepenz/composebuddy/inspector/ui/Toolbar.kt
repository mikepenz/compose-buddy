package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    val scope = rememberCoroutineScope()
    var isRerendering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val currentPreset = DEVICE_PRESETS.getOrNull(settings.selectedPresetIndex)

    Column(modifier = modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // Preview selector (opens spotlight search)
        OutlinedButton(onClick = onOpenSpotlight) {
            val displayName = selectedPreviewName?.substringAfterLast('.') ?: "Select Preview"
            Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }

        Spacer(Modifier.width(8.dp))

        // Re-render button
        Button(
            onClick = {
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
            },
            enabled = !isRerendering,
        ) {
            Text(if (isRerendering) "Rendering..." else "Re-render", fontSize = 12.sp)
        }

        if (isRerendering) {
            Spacer(Modifier.width(4.dp))
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }

        errorMessage?.let { error ->
            Spacer(Modifier.width(4.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 1)
        }

        Spacer(Modifier.weight(1f))

        // Device label
        if (currentPreset != null && currentPreset.name != "Default") {
            Text(currentPreset.name, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(4.dp))
        }

        val previewCount = session.previewDefs.size.takeIf { it > 0 } ?: session.availablePreviews.size
        Text(
            "$previewCount previews · ${session.frames.size} frames",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
        )

        // Settings toggle
        IconButton(onClick = onToggleSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(20.dp),
                tint = if (showSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
    }
}
