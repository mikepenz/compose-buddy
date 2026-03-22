package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    // Hierarchy bounds are in dp. Canvas root should also be in dp.
    // Convert image pixel dimensions to dp.
    // For desktop (imageWidth == screenWidthPx): scene width in dp = imageWidth * 160 / dpi
    // For android (image downscaled): screen dp = screenWidthPx * 160 / dpi
    val canvasWidthDp: Double
    val canvasHeightDp: Double
    if (screenWidthPx > 0 && densityDpi > 0) {
        // screenWidthPx in dp = the scene/screen width
        val screenDpW = screenWidthPx.toDouble() * 160.0 / densityDpi
        val screenDpH = screenDpW * imageHeight.toDouble() / imageWidth.toDouble()
        canvasWidthDp = screenDpW
        canvasHeightDp = screenDpH
    } else {
        // Desktop at 160dpi: 1px = 1dp
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

private fun nodeTypeIndicator(name: String): String = when {
    name == "Screen" -> "S"
    name == "Text" -> "T"
    name.startsWith("Layout") -> "L"
    name == "Element" -> "E"
    name == "Box" -> "B"
    name == "Column" -> "C"
    name == "Row" -> "R"
    name == "Image" || name == "Icon" -> "I"
    name == "Button" -> "Btn"
    else -> name.take(1)
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
    Column(
        modifier = modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Component Tree", style = MaterialTheme.typography.titleSmall)
        if (previewName != null) {
            Text(previewName.substringAfterLast('.'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${imageWidth}x${imageHeight}px", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.padding(top = 4.dp))
        if (hierarchy != null) {
            TreeNode(hierarchy, selectedNodeId, hoveredNodeId, onNodeSelected, onNodeHovered, depth = 0, isLast = true)
        } else {
            Text("No hierarchy available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TreeNode(
    node: HierarchyNode,
    selectedNodeId: Int?,
    hoveredNodeId: Int?,
    onNodeSelected: (Int) -> Unit,
    onNodeHovered: (Int?) -> Unit,
    depth: Int,
    isLast: Boolean,
) {
    val isSelected = node.id == selectedNodeId
    val isHovered = node.id == hoveredNodeId
    val guideColor = MaterialTheme.colorScheme.outlineVariant

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isHovered -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable { onNodeSelected(node.id) }
            .onPointerEvent(PointerEventType.Enter) { onNodeHovered(node.id) }
            .onPointerEvent(PointerEventType.Exit) { onNodeHovered(null) }
            .drawBehind {
                // Draw vertical indentation guides
                for (d in 0 until depth) {
                    val x = -(depth - d) * 14.dp.toPx() + 6.dp.toPx()
                    drawLine(
                        color = guideColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        // Type indicator badge
        Text(
            text = nodeTypeIndicator(node.name),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isHovered -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.width(24.dp),
        )
        // Node name
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isHovered -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${node.size.width.toInt()}x${node.size.height.toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 10.sp,
        )
        if (node.bounds.left != 0.0 || node.bounds.top != 0.0) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = "@${node.bounds.left.toInt()},${node.bounds.top.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
                fontSize = 9.sp,
            )
        }
    }
    for ((i, child) in node.children.withIndex()) {
        TreeNode(child, selectedNodeId, hoveredNodeId, onNodeSelected, onNodeHovered, depth + 1, isLast = i == node.children.lastIndex)
    }
}
