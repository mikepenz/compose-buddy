package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.model.Size
import dev.mikepenz.composebuddy.inspector.InspectorSettings
import dev.mikepenz.composebuddy.inspector.PreviewTheme as InspectorPreviewTheme
import dev.mikepenz.composebuddy.inspector.RendererType
import dev.mikepenz.composebuddy.inspector.ThemeMode
import dev.mikepenz.composebuddy.inspector.model.Frame

// ─────────────────────────────────────────────────────────────────────────────
// Sample data — mirrors the TREE / PROPERTIES fixtures from Agent Buddy.html
// so each preview can be eyeballed against the design spec.
// ─────────────────────────────────────────────────────────────────────────────

private fun sampleHierarchy(): HierarchyNode = HierarchyNode(
    id = 0,
    name = "Screen",
    bounds = Bounds(0.0, 0.0, 1280.0, 800.0),
    size = Size(1280.0, 800.0),
    children = listOf(
        HierarchyNode(
            id = 1,
            name = "Layout",
            bounds = Bounds(0.0, 0.0, 1280.0, 800.0),
            size = Size(1280.0, 800.0),
            children = listOf(
                HierarchyNode(
                    id = 2,
                    name = "Layout",
                    bounds = Bounds(0.0, 0.0, 1280.0, 68.0),
                    size = Size(1280.0, 68.0),
                    children = listOf(
                        HierarchyNode(
                            id = 3,
                            name = "Text",
                            bounds = Bounds(28.0, 16.0, 78.0, 40.0),
                            size = Size(50.0, 24.0),
                            semantics = mapOf("text" to "Stats"),
                        ),
                        HierarchyNode(
                            id = 4,
                            name = "Text",
                            bounds = Bounds(28.0, 46.0, 368.0, 70.0),
                            size = Size(340.0, 24.0),
                            semantics = mapOf("text" to "Statistics overview…"),
                        ),
                        HierarchyNode(
                            id = 5,
                            name = "Layout",
                            bounds = Bounds(1038.0, 24.0, 1252.0, 64.0),
                            size = Size(214.0, 40.0),
                            children = listOf(
                                HierarchyNode(
                                    id = 6,
                                    name = "Text",
                                    bounds = Bounds(1041.0, 27.0, 1104.0, 61.0),
                                    size = Size(63.0, 34.0),
                                    semantics = mapOf(
                                        "role" to "Tab",
                                        "text" to "7 days",
                                        "selected" to "true",
                                        "backgroundColor" to "#FF2F2F35",
                                        "foregroundColor" to "#FF44444B",
                                    ),
                                ),
                                HierarchyNode(
                                    id = 7,
                                    name = "Text",
                                    bounds = Bounds(1106.0, 27.0, 1178.0, 61.0),
                                    size = Size(72.0, 34.0),
                                    semantics = mapOf(
                                        "role" to "Tab",
                                        "text" to "30 days",
                                        "selected" to "false",
                                        "backgroundColor" to "#FF212126",
                                        "foregroundColor" to "#FF47474E",
                                    ),
                                ),
                                HierarchyNode(
                                    id = 8,
                                    name = "Text",
                                    bounds = Bounds(1180.0, 27.0, 1249.0, 61.0),
                                    size = Size(69.0, 34.0),
                                    semantics = mapOf(
                                        "role" to "Tab",
                                        "text" to "All time",
                                        "selected" to "false",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                HierarchyNode(
                    id = 9,
                    name = "Layout",
                    bounds = Bounds(28.0, 105.0, 223.0, 223.0),
                    size = Size(195.0, 118.0),
                    semantics = mapOf(
                        "role" to "Card",
                        "text" to "48 Invocations today",
                        "shape" to "RoundedCorner(12dp)",
                        "backgroundColor" to "#FF1A1A1F",
                        "foregroundColor" to "#FFE9E9EF",
                    ),
                    children = listOf(
                        HierarchyNode(
                            id = 10,
                            name = "Text",
                            bounds = Bounds(42.0, 121.0, 139.0, 145.0),
                            size = Size(97.0, 24.0),
                            semantics = mapOf("text" to "48"),
                        ),
                    ),
                ),
            ),
        ),
    ),
)

private fun sampleRenderResult(name: String): RenderResult = RenderResult(
    previewName = "com.mikepenz.agentbuddy.ui.statistics.StatisticsScreenKt.$name",
    configuration = RenderConfiguration(widthDp = 1280, heightDp = 800, densityDpi = 160),
    imageWidth = 1280,
    imageHeight = 800,
    rendererUsed = "desktop",
    hierarchy = sampleHierarchy(),
)

private fun sampleFrames(): List<Frame> = listOf(
    Frame(id = 0, timestamp = 1_734_557_434_000L, renderResult = sampleRenderResult("PreviewStatisticsScreen"), label = "19:20:34"),
    Frame(id = 1, timestamp = 1_734_557_456_000L, renderResult = sampleRenderResult("PreviewStatisticsScreen"), label = "19:20:56"),
    Frame(id = 2, timestamp = 1_734_557_464_000L, renderResult = sampleRenderResult("PreviewStatisticsScreen"), label = "19:21:04"),
)

private fun sampleSpotlightItems(): List<SpotlightItem> {
    fun mk(file: String, pkg: String, name: String) = SpotlightItem(
        previewName = "com.mikepenz.agentbuddy.ui.$pkg.${file}Kt.$name",
        variantIndex = 0,
        shortName = name,
        variantLabel = null,
        packageName = pkg,
        hasError = false,
    )
    return listOf(
        mk("StatisticsScreen", "statistics", "PreviewStatisticsScreen"),
        mk("StatisticsScreen", "statistics", "PreviewStatisticsScreenHighVolume"),
        mk("StatisticsScreen", "statistics", "PreviewRangePills7d"),
        mk("StatisticsScreen", "statistics", "PreviewKpiGrid"),
        mk("AgentScreen", "agents", "PreviewAgentCard"),
        mk("AgentScreen", "agents", "PreviewAgentCardActive"),
        mk("AgentScreen", "agents", "PreviewAgentList"),
        mk("InboxScreen", "inbox", "PreviewInboxRow"),
        mk("InboxScreen", "inbox", "PreviewInboxEmpty"),
        mk("SettingsScreen", "settings", "PreviewSettingsScreen"),
    )
}

@Composable
private fun PreviewSurface(dark: Boolean = true, content: @Composable () -> Unit) {
    InspectorTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

// Visual stand-in for the private RightPanelTabRow in InspectorApp.kt. Mirrors
// the same composition so the styling can be inspected in isolation.
@Composable
private fun RightPanelTabsStandIn(showSettings: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        InlineTab("Properties", Icons.Default.Tune, !showSettings, accent, onSurface, outline)
        Spacer(Modifier.width(2.dp))
        InlineTab("Settings", Icons.Default.Settings, showSettings, accent, onSurface, outline)
    }
}

@Composable
private fun InlineTab(
    label: String,
    icon: ImageVector,
    active: Boolean,
    accent: Color,
    activeColor: Color,
    inactiveColor: Color,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .drawBehind {
                if (active) drawLine(
                    color = accent,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (active) activeColor else inactiveColor,
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) activeColor else inactiveColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Component Tree previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 260, heightDp = 460)
@Composable
private fun PreviewComponentTreeSelected() {
    PreviewSurface {
        ComponentTree(
            hierarchy = sampleHierarchy(),
            selectedNodeId = 9,
            hoveredNodeId = null,
            previewName = "com.mikepenz.agentbuddy.ui.statistics.StatisticsScreenKt.PreviewStatisticsScreen",
            imageWidth = 1280,
            imageHeight = 800,
            onNodeSelected = {},
            onNodeHovered = {},
            modifier = Modifier.fillMaxHeight().width(260.dp).background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview(widthDp = 260, heightDp = 460)
@Composable
private fun PreviewComponentTreeHovered() {
    PreviewSurface(dark = false) {
        ComponentTree(
            hierarchy = sampleHierarchy(),
            selectedNodeId = 6,
            hoveredNodeId = 7,
            previewName = "com.mikepenz.agentbuddy.ui.statistics.StatisticsScreenKt.PreviewRangePills7d",
            imageWidth = 1280,
            imageHeight = 800,
            onNodeSelected = {},
            onNodeHovered = {},
            modifier = Modifier.fillMaxHeight().width(260.dp).background(MaterialTheme.colorScheme.surface),
        )
    }
}

// Deep tree with long names to exercise the horizontal scroll path: backgrounds
// still extend across the full panel width, while overflowing rows make the
// horizontal scrollbar appear.
private fun deepHierarchy(): HierarchyNode {
    fun leaf(id: Int, name: String) = HierarchyNode(
        id = id,
        name = name,
        bounds = Bounds(0.0, 0.0, 200.0, 24.0),
        size = Size(200.0, 24.0),
    )
    val deepest = HierarchyNode(
        id = 100,
        name = "VeryDeeplyNestedLayoutWithOutrageouslyLongName",
        bounds = Bounds(0.0, 0.0, 1000.0, 24.0),
        size = Size(1000.0, 24.0),
        children = listOf(leaf(101, "AnotherLongNameToForceHorizontalScroll")),
    )
    return (1..10).fold(deepest) { acc, i ->
        HierarchyNode(
            id = i,
            name = "Layout",
            bounds = Bounds(0.0, 0.0, 1000.0, 24.0),
            size = Size(1000.0, 24.0),
            children = listOf(acc, leaf(i * 100 + 1, "SiblingText")),
        )
    }
}

@Preview(widthDp = 260, heightDp = 460)
@Composable
private fun PreviewComponentTreeDeep() {
    PreviewSurface {
        ComponentTree(
            hierarchy = deepHierarchy(),
            selectedNodeId = 100,
            hoveredNodeId = null,
            previewName = "PreviewDeepTree",
            imageWidth = 1280,
            imageHeight = 800,
            onNodeSelected = {},
            onNodeHovered = {},
            modifier = Modifier.fillMaxHeight().width(260.dp).background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview(widthDp = 260, heightDp = 200)
@Composable
private fun PreviewComponentTreeEmpty() {
    PreviewSurface {
        ComponentTree(
            hierarchy = null,
            selectedNodeId = null,
            hoveredNodeId = null,
            previewName = null,
            imageWidth = 0,
            imageHeight = 0,
            onNodeSelected = {},
            onNodeHovered = {},
            modifier = Modifier.fillMaxHeight().width(260.dp).background(MaterialTheme.colorScheme.surface),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Properties Panel previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 280, heightDp = 720)
@Composable
private fun PreviewPropertiesPanelCard() {
    PreviewSurface {
        PropertiesPanel(
            hierarchy = sampleHierarchy(),
            selectedNodeId = 9,
            showRgbValues = false,
            modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview(widthDp = 280, heightDp = 720)
@Composable
private fun PreviewPropertiesPanelTab() {
    PreviewSurface {
        PropertiesPanel(
            hierarchy = sampleHierarchy(),
            selectedNodeId = 6,
            showRgbValues = false,
            modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview(widthDp = 280, heightDp = 360)
@Composable
private fun PreviewPropertiesPanelEmpty() {
    PreviewSurface {
        PropertiesPanel(
            hierarchy = sampleHierarchy(),
            selectedNodeId = null,
            showRgbValues = false,
            modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview(widthDp = 280, heightDp = 720)
@Composable
private fun PreviewPropertiesPanelLight() {
    PreviewSurface(dark = false) {
        PropertiesPanel(
            hierarchy = sampleHierarchy(),
            selectedNodeId = 9,
            showRgbValues = true,
            modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Panel previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 280, heightDp = 720)
@Composable
private fun PreviewSettingsPanelDark() {
    PreviewSurface {
        SettingsPanel(
            settings = InspectorSettings(),
            onSettingsChanged = {},
            onApplyRenderConfig = {},
            onRendererChanged = {},
            modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Preview(widthDp = 280, heightDp = 720)
@Composable
private fun PreviewSettingsPanelLight() {
    PreviewSurface(dark = false) {
        SettingsPanel(
            settings = InspectorSettings(
                themeMode = ThemeMode.LIGHT,
                previewTheme = InspectorPreviewTheme.LIGHT,
                rendererType = RendererType.DESKTOP,
                showRgbValues = true,
                selectedPresetIndex = 1,
            ),
            onSettingsChanged = {},
            onApplyRenderConfig = {},
            onRendererChanged = {},
            modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Right-panel tab row previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 280, heightDp = 80)
@Composable
private fun PreviewRightPanelTabsProperties() {
    PreviewSurface {
        Column(modifier = Modifier.width(280.dp).background(MaterialTheme.colorScheme.surface)) {
            RightPanelTabRow(showSettings = false, onSelectProperties = {}, onSelectSettings = {})
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(widthDp = 280, heightDp = 80)
@Composable
private fun PreviewRightPanelTabsSettings() {
    PreviewSurface {
        Column(modifier = Modifier.width(280.dp).background(MaterialTheme.colorScheme.surface)) {
            RightPanelTabRow(showSettings = true, onSelectProperties = {}, onSelectSettings = {})
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(widthDp = 280, heightDp = 80)
@Composable
private fun PreviewRightPanelTabsLight() {
    PreviewSurface(dark = false) {
        Column(modifier = Modifier.width(280.dp).background(MaterialTheme.colorScheme.surface)) {
            RightPanelTabRow(showSettings = false, onSelectProperties = {}, onSelectSettings = {})
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Timeline previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 1280, heightDp = 80)
@Composable
private fun PreviewTimelineFrames() {
    PreviewSurface {
        Timeline(
            frames = sampleFrames(),
            currentFrameIndex = 0,
            onFrameSelected = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 1280, heightDp = 80)
@Composable
private fun PreviewTimelineEmpty() {
    PreviewSurface {
        Timeline(
            frames = emptyList(),
            currentFrameIndex = -1,
            onFrameSelected = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview pane — exercises the new device-label + zoom pill overlay
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 760, heightDp = 480)
@Composable
private fun PreviewPreviewPaneOverlay() {
    PreviewSurface {
        PreviewPane(
            frame = sampleFrames().first(),
            displayHierarchy = sampleHierarchy(),
            selectedNodeId = 9,
            hoveredNodeIdFromTree = null,
            onNodeSelected = {},
            onNodeHovered = {},
            overlays = emptyList(),
            modifier = Modifier.fillMaxSize(),
            showGuides = true,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spotlight (command palette) preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewSpotlightSearch() {
    PreviewSurface {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SpotlightSearch(
                visible = true,
                items = sampleSpotlightItems(),
                selectedPreviewName = sampleSpotlightItems().first().previewName,
                onItemSelected = { _, _ -> },
                onDismiss = {},
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composite — full inspector body (tree + preview placeholder + tabbed panel)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewInspectorBody() {
    PreviewSurface {
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ComponentTree(
                hierarchy = sampleHierarchy(),
                selectedNodeId = 9,
                hoveredNodeId = null,
                previewName = "com.mikepenz.agentbuddy.ui.statistics.StatisticsScreenKt.PreviewStatisticsScreen",
                imageWidth = 1280,
                imageHeight = 800,
                onNodeSelected = {},
                onNodeHovered = {},
                modifier = Modifier.width(260.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
            )
            VerticalDivider()
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("Preview surface", color = MaterialTheme.colorScheme.outline)
            }
            VerticalDivider()
            Column(modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                RightPanelTabRow(showSettings = false, onSelectProperties = {}, onSelectSettings = {})
                HorizontalDivider()
                PropertiesPanel(
                    hierarchy = sampleHierarchy(),
                    selectedNodeId = 9,
                    showRgbValues = false,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewInspectorBodySettingsTab() {
    PreviewSurface {
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ComponentTree(
                hierarchy = sampleHierarchy(),
                selectedNodeId = 9,
                hoveredNodeId = null,
                previewName = "com.mikepenz.agentbuddy.ui.statistics.StatisticsScreenKt.PreviewStatisticsScreen",
                imageWidth = 1280,
                imageHeight = 800,
                onNodeSelected = {},
                onNodeHovered = {},
                modifier = Modifier.width(260.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
            )
            VerticalDivider()
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("Preview surface", color = MaterialTheme.colorScheme.outline)
            }
            VerticalDivider()
            Column(modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                RightPanelTabRow(showSettings = true, onSelectProperties = {}, onSelectSettings = {})
                HorizontalDivider()
                SettingsPanel(
                    settings = InspectorSettings(),
                    onSettingsChanged = {},
                    onApplyRenderConfig = {},
                    onRendererChanged = {},
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}
