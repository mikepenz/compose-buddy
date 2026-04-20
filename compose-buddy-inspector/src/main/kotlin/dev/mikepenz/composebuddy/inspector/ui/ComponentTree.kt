package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size

fun createCanvasRoot(
    hierarchy: HierarchyNode,
    imageWidth: Int,
    imageHeight: Int,
    screenWidthPx: Int,
    densityDpi: Int,
): HierarchyNode {
    val canvasWidthDp: Double
    val canvasHeightDp: Double
    if (screenWidthPx > 0 && densityDpi > 0) {
        val screenDpW = screenWidthPx.toDouble() * 160.0 / densityDpi
        val screenDpH = screenDpW * imageHeight.toDouble() / imageWidth.toDouble()
        canvasWidthDp = screenDpW
        canvasHeightDp = screenDpH
    } else {
        val pxPerDp = if (densityDpi > 0) densityDpi / 160.0 else 1.0
        canvasWidthDp = imageWidth / pxPerDp
        canvasHeightDp = imageHeight / pxPerDp
    }
    return HierarchyNode(
        id = -1,
        name = "Screen",
        bounds = Bounds(0.0, 0.0, canvasWidthDp, canvasHeightDp),
        size = Size(canvasWidthDp, canvasHeightDp),
        children = listOf(hierarchy),
    )
}

@Composable
fun ComponentTree(
    hierarchy: HierarchyNode?,
    selectedNodeId: Int?,
    hoveredNodeId: Int?,
    previewName: String?,
    imageWidth: Int,
    imageHeight: Int,
    onNodeSelected: (Int) -> Unit,
    onNodeHovered: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = InspectorTokens.current
    Column(modifier = modifier.background(tokens.bgPanel)) {
        PanelHeader(
            title = "Component Tree",
            meta = previewName?.substringAfterLast('.'),
            submeta = if (imageWidth > 0 && imageHeight > 0) "$imageWidth × $imageHeight px" else null,
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.line1))
        // Horizontal scroll wrapper: rows are at least the panel viewport wide so
        // backgrounds (selection / hover) fill the visible area, but a deeply
        // indented or long-named row can grow past the viewport, exposing the
        // horizontal scrollbar.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            val viewportWidth = maxWidth
            val hScroll = rememberScrollState()
            val vScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                if (hierarchy != null) {
                    TreeNode(
                        node = hierarchy,
                        depth = 0,
                        viewportWidth = viewportWidth,
                        selectedNodeId = selectedNodeId,
                        hoveredNodeId = hoveredNodeId,
                        onNodeSelected = onNodeSelected,
                        onNodeHovered = onNodeHovered,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = "No hierarchy available",
                            color = tokens.fg3,
                            fontSize = InspectorType.kvBody,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TreeNode(
    node: HierarchyNode,
    depth: Int,
    viewportWidth: Dp,
    selectedNodeId: Int?,
    hoveredNodeId: Int?,
    onNodeSelected: (Int) -> Unit,
    onNodeHovered: (Int?) -> Unit,
) {
    val tokens = InspectorTokens.current
    val hasChildren = node.children.isNotEmpty()
    var expanded by remember(node.id) { mutableStateOf(true) }

    val isSelected = node.id == selectedNodeId
    val isHovered = node.id == hoveredNodeId && !isSelected

    val rowBg = when {
        isSelected -> tokens.bgSelected
        isHovered -> tokens.bgHover
        else -> Color.Transparent
    }
    val labelColor = if (isSelected) tokens.accent else tokens.fg1
    val sizeColor = if (isSelected) tokens.accent.copy(alpha = 0.85f) else tokens.fg3

    TreeRowLayout(
        minWidth = viewportWidth,
        rowBg = rowBg,
        onClick = { onNodeSelected(node.id) },
        onEnter = { onNodeHovered(node.id) },
        onExit = { onNodeHovered(null) },
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Spacer(Modifier.width((8 + depth * 12).dp))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clickable(enabled = hasChildren) { expanded = !expanded },
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasChildren) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier
                                .size(12.dp)
                                .rotate(if (expanded) 90f else 0f),
                            tint = tokens.fg3,
                        )
                    }
                }
                TypeGlyph(kind = glyphKindFor(node.name))
            }
        },
        name = {
            // No weight, no ellipsis — long names push the size badge right and
            // trigger the parent's horizontal scroll instead of clipping.
            Text(
                text = node.name,
                modifier = Modifier.padding(start = 6.dp, end = 16.dp),
                fontSize = InspectorType.treeRow,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                maxLines = 1,
                softWrap = false,
            )
        },
        size = {
            Text(
                text = "${node.size.width.toInt()}×${node.size.height.toInt()}",
                modifier = Modifier.padding(end = 8.dp),
                fontSize = InspectorType.treeSize,
                fontFamily = FontFamily.Monospace,
                color = sizeColor,
            )
        },
    )
    if (hasChildren && expanded) {
        for (child in node.children) {
            TreeNode(
                node = child,
                depth = depth + 1,
                viewportWidth = viewportWidth,
                selectedNodeId = selectedNodeId,
                hoveredNodeId = hoveredNodeId,
                onNodeSelected = onNodeSelected,
                onNodeHovered = onNodeHovered,
            )
        }
    }
}

/**
 * Custom row layout for tree rows. Lays out three slots:
 *  - `leading` (caret + glyph + indent) at the row's left edge
 *  - `name` immediately after `leading`
 *  - `size` right-aligned within the row
 *
 * Row width = `max(leading + name + size, minWidth)` — when the content fits
 * within `minWidth`, the size badge stays pinned to the right edge as in the
 * design HTML. When the content overflows, the row grows past `minWidth`
 * (size still right-aligned), and the parent's `horizontalScroll` exposes a
 * scrollbar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TreeRowLayout(
    minWidth: Dp,
    rowBg: Color,
    onClick: () -> Unit,
    onEnter: () -> Unit,
    onExit: () -> Unit,
    leading: @Composable () -> Unit,
    name: @Composable () -> Unit,
    size: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 26.dp.roundToPx() }
    val minWidthPx = with(density) { minWidth.roundToPx() }
    Layout(
        modifier = Modifier
            .height(26.dp)
            .background(rowBg)
            .clickable(onClick = onClick)
            .onPointerEvent(PointerEventType.Enter) { onEnter() }
            .onPointerEvent(PointerEventType.Exit) { onExit() },
        content = {
            Box { leading() }
            Box { name() }
            Box { size() }
        },
    ) { measurables, _ ->
        val unbounded = Constraints()
        val l = measurables[0].measure(unbounded)
        val n = measurables[1].measure(unbounded)
        val s = measurables[2].measure(unbounded)
        val contentWidth = l.width + n.width + s.width
        val rowWidth = maxOf(contentWidth, minWidthPx)
        layout(rowWidth, rowHeightPx) {
            l.placeRelative(0, (rowHeightPx - l.height) / 2)
            n.placeRelative(l.width, (rowHeightPx - n.height) / 2)
            s.placeRelative(rowWidth - s.width, (rowHeightPx - s.height) / 2)
        }
    }
}
