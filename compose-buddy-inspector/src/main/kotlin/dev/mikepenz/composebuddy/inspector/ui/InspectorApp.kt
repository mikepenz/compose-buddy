package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.inspector.InspectorSession
import dev.mikepenz.composebuddy.inspector.InspectorSettings
import dev.mikepenz.composebuddy.inspector.RenderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InspectorApp(
    session: InspectorSession,
    settings: InspectorSettings,
    onSettingsChanged: (InspectorSettings) -> Unit,
    onRendererChanged: (dev.mikepenz.composebuddy.inspector.RendererType) -> Unit = {},
    showSpotlight: Boolean = false,
    onShowSpotlightChanged: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var selectedNodeId by remember { mutableStateOf(session.selectedNodeId) }
    var currentFrameIndex by remember { mutableIntStateOf(session.currentFrameIndex) }
    var selectedPreviewName by remember { mutableStateOf(session.selectedPreviewName) }
    var frameVersion by remember { mutableIntStateOf(0) }
    var hoveredNodeId by remember { mutableStateOf<Int?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showGuides by remember { mutableStateOf(true) }
    val panelState = rememberPanelLayoutState()

    // Poll session for new frames from live device feeds
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var lastSeenFrameCount = session.frames.size
        while (true) {
            kotlinx.coroutines.delay(500)
            val frameCount = session.frames.size
            if (frameCount != lastSeenFrameCount && session.frames.isNotEmpty()) {
                lastSeenFrameCount = frameCount
                currentFrameIndex = session.currentFrameIndex
                frameVersion++
            }
        }
    }

    // Use preview defs (lazy mode) if available, otherwise fall back to rendered results
    val spotlightItems = remember(session.previewDefs, session.availablePreviews) {
        if (session.previewDefs.isNotEmpty()) {
            buildSpotlightItemsFromDefs(session.previewDefs)
        } else {
            buildSpotlightItems(session.availablePreviews)
        }
    }

    fun refreshAfterRerender() {
        currentFrameIndex = session.currentFrameIndex
        selectedNodeId = null
        hoveredNodeId = null
        frameVersion++
    }

    fun selectAndRender(name: String) {
        selectedPreviewName = name

        // If cached, select immediately
        if (session.getCachedResult(name, 0) != null) {
            session.selectPreview(name, 0)
            refreshAfterRerender()
            return
        }

        // Clear current view while rendering
        session.selectPreview(name, 0)
        refreshAfterRerender()

        // Trigger on-demand render with current preview theme
        val themeUiMode = when (settings.previewTheme) {
            dev.mikepenz.composebuddy.inspector.PreviewTheme.LIGHT -> 0
            dev.mikepenz.composebuddy.inspector.PreviewTheme.DARK -> 0x21
        }
        val renderConfig = dev.mikepenz.composebuddy.inspector.RenderConfig(uiMode = themeUiMode)
        scope.launch {
            withContext(Dispatchers.IO) {
                // navigate() sends a Navigate command for device sources, or falls back to rerender()
                // for static renderers — either way a new frame is produced.
                // The feed collector (launchInspectorMode) adds the frame to the session; don't add it here too.
                val result = session.renderTrigger.navigate(name, renderConfig)
                session.cacheResult(name, 0, result)
            }
            refreshAfterRerender()
        }
    }

    val tokens = InspectorTokens.current
    Surface(modifier = Modifier.fillMaxSize(), color = tokens.bgWindow) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Toolbar(
                    session = session,
                    selectedPreviewName = selectedPreviewName,
                    settings = settings,
                    onPreviewSelected = { name ->
                        selectAndRender(name)
                    },
                    onRerendered = { refreshAfterRerender() },
                    showSettings = showSettings,
                    onToggleSettings = { showSettings = !showSettings },
                    onOpenSpotlight = { onShowSpotlightChanged(true) },
                    showGuides = showGuides,
                    onToggleGuides = { showGuides = !showGuides },
                    leftPanelVisible = panelState.leftUserVisible,
                    onToggleLeftPanel = { panelState.leftUserVisible = !panelState.leftUserVisible },
                    rightPanelVisible = panelState.rightUserVisible,
                    onToggleRightPanel = { panelState.rightUserVisible = !panelState.rightUserVisible },
                )

                // Use key to force full recomposition when frame content changes
                key(frameVersion, currentFrameIndex) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val available = maxWidth
                    val leftW = panelState.leftWidth
                    val rightW = panelState.rightWidth
                    // Auto-collapse: keep CenterMin. Drop right first, then left.
                    val canShowLeft = panelState.leftUserVisible
                    val canShowRight = panelState.rightUserVisible
                    val needBoth = (if (canShowLeft) leftW else 0.dp) +
                        (if (canShowRight) rightW else 0.dp) +
                        PanelLayoutState.CenterMin
                    val dropRight = needBoth > available && canShowRight
                    val needAfterDrop = (if (canShowLeft) leftW else 0.dp) + PanelLayoutState.CenterMin
                    val dropLeft = dropRight && needAfterDrop > available && canShowLeft
                    val showLeft = canShowLeft && !dropLeft
                    val showRight = canShowRight && !dropRight

                    Row(modifier = Modifier.fillMaxSize()) {
                    val currentFrame = session.frames.getOrNull(currentFrameIndex)
                    val renderResult = currentFrame?.renderResult

                    val displayHierarchy = renderResult?.hierarchy?.let { h ->
                        createCanvasRoot(
                            hierarchy = h,
                            imageWidth = renderResult.imageWidth,
                            imageHeight = renderResult.imageHeight,
                            screenWidthPx = renderResult.configuration.screenWidthPx,
                            densityDpi = renderResult.configuration.densityDpi.let { if (it > 0) it else 160 },
                        )
                    }

                    // Left: Component tree
                    if (showLeft) {
                        ComponentTree(
                            hierarchy = displayHierarchy,
                            selectedNodeId = selectedNodeId,
                            hoveredNodeId = hoveredNodeId,
                            previewName = renderResult?.previewName,
                            imageWidth = renderResult?.imageWidth ?: 0,
                            imageHeight = renderResult?.imageHeight ?: 0,
                            onNodeSelected = { nodeId ->
                                selectedNodeId = nodeId
                                session.selectedNodeId = nodeId
                            },
                            onNodeHovered = { nodeId -> hoveredNodeId = nodeId },
                            modifier = Modifier.width(panelState.leftWidth).fillMaxHeight(),
                        )

                        PanelDragHandle { delta ->
                            panelState.leftWidth = (panelState.leftWidth + delta)
                                .coerceIn(PanelLayoutState.LeftMin, PanelLayoutState.LeftMax)
                        }
                    } else {
                        VerticalDivider()
                    }

                    // Center: Preview pane
                    PreviewPane(
                        frame = currentFrame,
                        displayHierarchy = displayHierarchy,
                        selectedNodeId = selectedNodeId,
                        hoveredNodeIdFromTree = hoveredNodeId,
                        onNodeSelected = { nodeId ->
                            selectedNodeId = nodeId
                            session.selectedNodeId = nodeId
                        },
                        onNodeHovered = { nodeId -> hoveredNodeId = nodeId },
                        overlays = session.overlays,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        showGuides = showGuides,
                    )

                    if (showRight) {
                        PanelDragHandle { delta ->
                            panelState.rightWidth = (panelState.rightWidth - delta)
                                .coerceIn(PanelLayoutState.RightMin, PanelLayoutState.RightMax)
                        }

                        // Right: Tabbed Properties / Settings panel
                        Column(modifier = Modifier.width(panelState.rightWidth).fillMaxHeight()) {
                        RightPanelTabRow(
                            showSettings = showSettings,
                            onSelectProperties = { showSettings = false },
                            onSelectSettings = { showSettings = true },
                        )
                        HorizontalDivider()
                        if (!showSettings) {
                            PropertiesPanel(
                                hierarchy = displayHierarchy,
                                selectedNodeId = selectedNodeId,
                                showRgbValues = settings.showRgbValues,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        } else {
                            SettingsPanel(
                                settings = settings,
                                onSettingsChanged = onSettingsChanged,
                                onApplyRenderConfig = { config ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            session.rerenderWithNewConfig(config)
                                        }
                                        refreshAfterRerender()
                                    }
                                },
                                onRendererChanged = { type ->
                                    session.clearRenderCache()
                                    onRendererChanged(type)
                                    val name = selectedPreviewName ?: return@SettingsPanel
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            session.triggerRerender()
                                        }
                                        session.currentFrame?.renderResult?.let { result ->
                                            session.cacheResult(name, 0, result)
                                        }
                                        refreshAfterRerender()
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                        }
                    }
                    }
                }
                } // key

                Timeline(
                    frames = session.frames,
                    currentFrameIndex = currentFrameIndex,
                    onFrameSelected = { index ->
                        session.navigateToFrame(index)
                        currentFrameIndex = index
                        selectedNodeId = null
                        session.selectedNodeId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SpotlightSearch(
                visible = showSpotlight,
                items = spotlightItems,
                selectedPreviewName = selectedPreviewName,
                onItemSelected = { name, _ ->
                    selectAndRender(name)
                },
                onDismiss = { onShowSpotlightChanged(false) },
            )

            if (session.isRerendering) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Text("Re-rendering...", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RightPanelTabRow(
    showSettings: Boolean,
    onSelectProperties: () -> Unit,
    onSelectSettings: () -> Unit,
) {
    val tokens = InspectorTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.bgPanel)
            .padding(horizontal = 8.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        RightPanelTab(
            label = "Properties",
            icon = Icons.Default.Tune,
            active = !showSettings,
            onClick = onSelectProperties,
        )
        RightPanelTab(
            label = "Settings",
            icon = Icons.Default.Settings,
            active = showSettings,
            onClick = onSelectSettings,
        )
    }
}

@Composable
private fun RightPanelTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tokens = InspectorTokens.current
    val color = if (active) tokens.fg1 else tokens.fg3
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .drawBehind {
                if (active) {
                    drawLine(
                        color = tokens.accent,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color,
            )
            Text(
                text = label,
                fontSize = InspectorType.toolbarText,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}
