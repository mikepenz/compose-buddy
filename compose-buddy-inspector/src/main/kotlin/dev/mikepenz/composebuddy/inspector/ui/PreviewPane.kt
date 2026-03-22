package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.inspector.model.DesignOverlay
import dev.mikepenz.composebuddy.inspector.model.Frame
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

data class ComponentDistance(
    val horizontal: Double,
    val vertical: Double,
)

internal fun calculateDistance(a: HierarchyNode, b: HierarchyNode): ComponentDistance {
    val horizontal = b.bounds.left - a.bounds.right
    val vertical = b.bounds.top - a.bounds.bottom
    return ComponentDistance(horizontal, vertical)
}

/**
 * Computes the scale factor to convert dp coordinates to image pixel coordinates.
 *
 * If screenWidthPx is known (android renderer): scale = imageWidthPx / screenWidthDp
 * Otherwise (desktop renderer): scale = densityDpi / 160 (typically 1.0 at 160dpi)
 */
/**
 * Computes the scale factor to convert hierarchy dp coordinates to image pixel coordinates.
 *
 * Hierarchy bounds are always in dp (both renderers use pxBoundsToDp).
 * To overlay bounds on the image, we need dp→imagePx.
 *
 * Desktop renderer: image is widthPx pixels. Bounds are in dp.
 *   dp * dpi/160 = px in scene = px in image. So scale = dpi/160.
 *
 * Android renderer: image is downscaled from screenWidthPx.
 *   dp * (imageWidth / screenWidthDp) where screenWidthDp = screenWidthPx * 160/dpi.
 *   Simplifies to: dp * imageWidth * dpi / (screenWidthPx * 160).
 */
private fun computeDpToImagePxScale(
    imageWidth: Int,
    screenWidthPx: Int,
    densityDpi: Int,
): Float {
    if (densityDpi > 0 && screenWidthPx > 0) {
        val screenWidthDp = screenWidthPx.toFloat() * 160f / densityDpi
        return imageWidth.toFloat() / screenWidthDp
    }
    // Desktop at 160dpi (no screenWidthPx): 1dp = 1px
    return if (densityDpi > 0) densityDpi / 160f else 1f
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PreviewPane(
    frame: Frame?,
    displayHierarchy: HierarchyNode?,
    selectedNodeId: Int?,
    hoveredNodeIdFromTree: Int?,
    onNodeSelected: (Int) -> Unit,
    onNodeHovered: (Int?) -> Unit,
    overlays: List<DesignOverlay>,
    modifier: Modifier = Modifier,
) {
    if (frame == null) {
        Box(modifier = modifier.background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
            Text("No preview available", color = Color.Gray)
        }
        return
    }

    val renderResult = frame.renderResult
    val imagePath = renderResult.imagePath
    val imageWidth = renderResult.imageWidth
    val imageHeight = renderResult.imageHeight
    val renderError = renderResult.error
    val densityDpi = renderResult.configuration.densityDpi.let { if (it > 0) it else 160 }
    val screenWidthPx = renderResult.configuration.screenWidthPx

    val dpScale = remember(imageWidth, screenWidthPx, densityDpi) {
        computeDpToImagePxScale(imageWidth, screenWidthPx, densityDpi)
    }

    if (renderError != null) {
        Box(modifier = modifier.background(Color(0xFFFFF3E0)).padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Render Error", color = Color(0xFFE65100), fontSize = 16.sp)
                Text(renderError.message, color = Color(0xFFBF360C), fontSize = 12.sp)
            }
        }
        return
    }

    val imageBitmap = remember(imagePath) {
        if (imagePath.isBlank()) return@remember null
        try {
            val file = File(imagePath)
            if (file.exists()) ImageIO.read(file)?.toComposeImageBitmap() else null
        } catch (_: Exception) { null }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Pan and zoom state — reset when preview or image size changes
    var zoom by remember(imagePath, imageWidth, imageHeight) { mutableStateOf(0f) } // 0 = needs fit
    var panOffset by remember(imagePath, imageWidth, imageHeight) { mutableStateOf(Offset.Zero) }

    // Auto-fit zoom when canvas size is known and zoom hasn't been set yet
    if (zoom == 0f && canvasSize.width > 0 && imageWidth > 0 && imageHeight > 0) {
        val padding = 32f
        val availW = canvasSize.width - padding * 2
        val availH = canvasSize.height - padding * 2
        zoom = minOf(availW / imageWidth, availH / imageHeight).coerceIn(0.05f, 5f)
    }
    var hoveredNodeIdFromPointer by remember { mutableStateOf<Int?>(null) }
    // Track drag state
    var isDragging by remember { mutableStateOf(false) }
    var lastDragPos by remember { mutableStateOf(Offset.Zero) }

    val effectiveHoveredId = hoveredNodeIdFromTree ?: hoveredNodeIdFromPointer
    val textMeasurer = rememberTextMeasurer()

    fun screenToDp(screenPos: Offset): Offset? {
        if (canvasSize == IntSize.Zero || imageWidth <= 0 || dpScale <= 0) return null
        val cx = canvasSize.width / 2f
        val cy = canvasSize.height / 2f
        val imgPx = (screenPos.x - cx - panOffset.x) / zoom + imageWidth / 2f
        val imgPy = (screenPos.y - cy - panOffset.y) / zoom + imageHeight / 2f
        return Offset(imgPx / dpScale, imgPy / dpScale)
    }

    Box(
        modifier = modifier
            .background(Color(0xFFF5F5F5))
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            // Scroll/pinch to zoom
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val scrollDelta = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                val zoomFactor = if (scrollDelta.y < 0) 1.08f else 1f / 1.08f
                zoom = (zoom * zoomFactor).coerceIn(0.05f, 20f)
                event.changes.forEach { it.consume() }
            }
            // Mouse press — start drag
            .onPointerEvent(PointerEventType.Press) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                if (event.button == PointerButton.Primary) {
                    isDragging = true
                    lastDragPos = change.position
                }
            }
            // Mouse release — end drag, detect click
            .onPointerEvent(PointerEventType.Release) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val dragDist = (change.position - lastDragPos).getDistance()
                if (dragDist < 5f && displayHierarchy != null) {
                    // This was a click, not a drag
                    val dp = screenToDp(change.position)
                    if (dp != null) {
                        val tapped = findNodeAtPosition(displayHierarchy!!, dp.x.toDouble(), dp.y.toDouble())
                        if (tapped != null) onNodeSelected(tapped.id)
                    }
                }
                isDragging = false
            }
            // Mouse move — drag to pan + hover detection
            .onPointerEvent(PointerEventType.Move) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                if (isDragging && change.pressed) {
                    // Drag to pan
                    val delta = change.position - change.previousPosition
                    panOffset += delta
                    change.consume()
                } else if (!change.pressed && displayHierarchy != null && canvasSize != IntSize.Zero) {
                    // Hover — find node under cursor
                    val dp = screenToDp(change.position)
                    if (dp != null) {
                        val hovered = findNodeAtPosition(displayHierarchy!!, dp.x.toDouble(), dp.y.toDouble())
                        hoveredNodeIdFromPointer = hovered?.id
                        onNodeHovered(hovered?.id)
                    }
                }
            },
    ) {
        if (imageBitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f

                withTransform({
                    translate(cx + panOffset.x, cy + panOffset.y)
                    scale(zoom, zoom, Offset.Zero)
                    translate(-imageWidth / 2f, -imageHeight / 2f)
                }) {
                    drawImage(imageBitmap)

                    if (displayHierarchy != null) {
                        val selectedNode = selectedNodeId?.let { findNode(displayHierarchy, it) }
                        val hoveredNode = effectiveHoveredId?.let { findNode(displayHierarchy, it) }

                        if (selectedNode != null) {
                            drawNodeHighlight(selectedNode, Color(0xFF2196F3), zoom, dpScale, textMeasurer)
                            // Draw distance to screen/root edges
                            drawEdgeDistances(selectedNode, displayHierarchy!!, zoom, dpScale, textMeasurer)
                        }
                        if (hoveredNode != null && hoveredNode.id != selectedNodeId) {
                            drawNodeHighlight(hoveredNode, Color(0xFFFF9800), zoom, dpScale, textMeasurer)
                            if (selectedNode != null) {
                                drawDistanceGuides(selectedNode, hoveredNode, zoom, dpScale, textMeasurer)
                            }
                        }
                    }
                }
            }
        } else if (imagePath.isNotBlank()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Could not load image:\n$imagePath", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No image path available", color = Color.Gray)
            }
        }
    }
}

private fun dpToImgPx(dp: Double, scale: Float): Float = (dp * scale).toFloat()

private fun DrawScope.drawNodeHighlight(
    node: HierarchyNode, color: Color, currentZoom: Float, dpScale: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val b = node.bounds
    val left = dpToImgPx(b.left, dpScale)
    val top = dpToImgPx(b.top, dpScale)
    val w = dpToImgPx(b.right - b.left, dpScale)
    val h = dpToImgPx(b.bottom - b.top, dpScale)
    drawRect(color, Offset(left, top), Size(w, h), style = Stroke(width = 2f / currentZoom))
    drawRect(color.copy(alpha = 0.08f), Offset(left, top), Size(w, h))

    // Dimension label (WxH dp)
    val widthDp = (b.right - b.left).toInt()
    val heightDp = (b.bottom - b.top).toInt()
    if (widthDp > 0 && heightDp > 0) {
        val label = "${widthDp}x${heightDp}"
        val labelStyle = TextStyle(fontSize = (9 / currentZoom).coerceAtLeast(0.5f).sp, color = color)
        val tr = textMeasurer.measure(label, labelStyle)
        // Position: bottom-right outside the rect, flip if near edge
        val lx = left + w - tr.size.width
        val ly = top + h + 2f / currentZoom
        drawText(tr, topLeft = Offset(lx.coerceAtLeast(left), ly))
    }
}

/**
 * Draw distance lines from selected node to screen/root edges (purple dashed lines).
 */
private fun DrawScope.drawEdgeDistances(
    node: HierarchyNode, root: HierarchyNode,
    currentZoom: Float, dpScale: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    if (node.id == root.id) return // Don't show for root itself

    val nb = node.bounds
    val rb = root.bounds
    val edgeColor = Color(0xFF9C27B0) // Purple
    val dash = PathEffect.dashPathEffect(floatArrayOf(3f / currentZoom, 3f / currentZoom))
    val lw = 1f / currentZoom

    // Convert to image px
    val nL = dpToImgPx(nb.left, dpScale); val nT = dpToImgPx(nb.top, dpScale)
    val nR = dpToImgPx(nb.right, dpScale); val nB = dpToImgPx(nb.bottom, dpScale)
    val rL = dpToImgPx(rb.left, dpScale); val rT = dpToImgPx(rb.top, dpScale)
    val rR = dpToImgPx(rb.right, dpScale); val rB = dpToImgPx(rb.bottom, dpScale)
    val nMidY = (nT + nB) / 2; val nMidX = (nL + nR) / 2

    fun drawEdgeLine(start: Offset, end: Offset, dpValue: Double) {
        if (dpValue < 0.5) return
        drawLine(edgeColor, start, end, lw, pathEffect = dash)
        val label = "${"%.0f".format(dpValue)}"
        val tr = textMeasurer.measure(label, TextStyle(fontSize = (8 / currentZoom).coerceAtLeast(0.5f).sp, color = edgeColor))
        val mid = Offset((start.x + end.x) / 2 - tr.size.width / 2, (start.y + end.y) / 2 - tr.size.height / 2)
        drawText(tr, topLeft = mid)
    }

    // Left edge distance
    drawEdgeLine(Offset(rL, nMidY), Offset(nL, nMidY), nb.left - rb.left)
    // Right edge distance
    drawEdgeLine(Offset(nR, nMidY), Offset(rR, nMidY), rb.right - nb.right)
    // Top edge distance
    drawEdgeLine(Offset(nMidX, rT), Offset(nMidX, nT), nb.top - rb.top)
    // Bottom edge distance
    drawEdgeLine(Offset(nMidX, nB), Offset(nMidX, rB), rb.bottom - nb.bottom)
}

private fun DrawScope.drawDistanceGuides(
    selected: HierarchyNode, hovered: HierarchyNode,
    currentZoom: Float, dpScale: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val sb = selected.bounds; val hb = hovered.bounds
    val sL = dpToImgPx(sb.left, dpScale); val sT = dpToImgPx(sb.top, dpScale)
    val sR = dpToImgPx(sb.right, dpScale); val sB = dpToImgPx(sb.bottom, dpScale)
    val hL = dpToImgPx(hb.left, dpScale); val hT = dpToImgPx(hb.top, dpScale)
    val hR = dpToImgPx(hb.right, dpScale); val hB = dpToImgPx(hb.bottom, dpScale)

    val dash = PathEffect.dashPathEffect(floatArrayOf(4f / currentZoom, 4f / currentZoom))
    val col = Color(0xFFFF5722); val lw = 1.5f / currentZoom

    // Check if one contains the other (parent-child relationship)
    val selectedInsideHovered = sb.left >= hb.left && sb.top >= hb.top && sb.right <= hb.right && sb.bottom <= hb.bottom
    val hoveredInsideSelected = hb.left >= sb.left && hb.top >= sb.top && hb.right <= sb.right && hb.bottom <= sb.bottom

    if (selectedInsideHovered || hoveredInsideSelected) {
        // Containment: show inset distances from inner to outer edges
        val inner = if (selectedInsideHovered) sb else hb
        val outer = if (selectedInsideHovered) hb else sb
        val iL = if (selectedInsideHovered) sL else hL; val iT = if (selectedInsideHovered) sT else hT
        val iR = if (selectedInsideHovered) sR else hR; val iB = if (selectedInsideHovered) sB else hB
        val oL = if (selectedInsideHovered) hL else sL; val oT = if (selectedInsideHovered) hT else sT
        val oR = if (selectedInsideHovered) hR else sR; val oB = if (selectedInsideHovered) hB else sB
        val iMidY = (iT + iB) / 2; val iMidX = (iL + iR) / 2

        fun drawInset(start: Offset, end: Offset, dpVal: Double) {
            if (dpVal < 0.5) return
            drawLine(col, start, end, lw, pathEffect = dash)
            val tr = textMeasurer.measure("${"%.0f".format(dpVal)}", TextStyle(fontSize = (9 / currentZoom).coerceAtLeast(0.5f).sp, color = col))
            drawText(tr, topLeft = Offset((start.x + end.x) / 2 - tr.size.width / 2, (start.y + end.y) / 2 - tr.size.height / 2))
        }

        drawInset(Offset(oL, iMidY), Offset(iL, iMidY), inner.left - outer.left)
        drawInset(Offset(iR, iMidY), Offset(oR, iMidY), outer.right - inner.right)
        drawInset(Offset(iMidX, oT), Offset(iMidX, iT), inner.top - outer.top)
        drawInset(Offset(iMidX, iB), Offset(iMidX, oB), outer.bottom - inner.bottom)
    } else {
        // Non-overlapping siblings: show gap between them
        val midY = ((sT + sB) / 2 + (hT + hB) / 2) / 2
        val midX = ((sL + sR) / 2 + (hL + hR) / 2) / 2

        val hGapDp: Double; val hS: Float; val hE: Float
        if (hb.left >= sb.right) { hGapDp = hb.left - sb.right; hS = sR; hE = hL }
        else if (sb.left >= hb.right) { hGapDp = sb.left - hb.right; hS = hR; hE = sL }
        else { hGapDp = 0.0; hS = 0f; hE = 0f }

        if (abs(hGapDp) > 0.5) {
            drawLine(col, Offset(hS, midY), Offset(hE, midY), lw, pathEffect = dash)
            val tr = textMeasurer.measure("${"%.0f".format(hGapDp)}dp", TextStyle(fontSize = (10 / currentZoom).coerceAtLeast(0.5f).sp, color = col))
            drawText(tr, topLeft = Offset((hS + hE) / 2 - tr.size.width / 2, midY - tr.size.height))
        }

        val vGapDp: Double; val vS: Float; val vE: Float
        if (hb.top >= sb.bottom) { vGapDp = hb.top - sb.bottom; vS = sB; vE = hT }
        else if (sb.top >= hb.bottom) { vGapDp = sb.top - hb.bottom; vS = hB; vE = sT }
        else { vGapDp = 0.0; vS = 0f; vE = 0f }

        if (abs(vGapDp) > 0.5) {
            drawLine(col, Offset(midX, vS), Offset(midX, vE), lw, pathEffect = dash)
            val tr = textMeasurer.measure("${"%.0f".format(vGapDp)}dp", TextStyle(fontSize = (10 / currentZoom).coerceAtLeast(0.5f).sp, color = col))
            drawText(tr, topLeft = Offset(midX + 4f / currentZoom, (vS + vE) / 2 - tr.size.height / 2))
        }
    }
}

private fun findNodeAtPosition(node: HierarchyNode, x: Double, y: Double): HierarchyNode? {
    for (child in node.children) {
        findNodeAtPosition(child, x, y)?.let { return it }
    }
    val b = node.bounds
    if (x >= b.left && x <= b.right && y >= b.top && y <= b.bottom) return node
    return null
}
