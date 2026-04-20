package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.inspector.comparison.deltaE76
import dev.mikepenz.composebuddy.inspector.comparison.parseHexColor
import dev.mikepenz.composebuddy.inspector.comparison.toHexString
import dev.mikepenz.composebuddy.inspector.comparison.toRgbString
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

private data class ParsedColor(val hex: String, val rgb: String, val composeColor: Color)

private fun tryParseColor(value: String): ParsedColor? = try {
    val c = parseHexColor(value)
    ParsedColor(toHexString(c.r, c.g, c.b), toRgbString(c.r, c.g, c.b), Color(c.r, c.g, c.b))
} catch (_: Exception) { null }

private fun tryDeltaE(c1: String, c2: String): Double? =
    try { deltaE76(c1, c2) } catch (_: Exception) { null }

private fun deltaELabel(value: Double): String = when {
    value < 1.0 -> "imperceptible"
    value < 2.0 -> "barely perceptible"
    value < 10.0 -> "noticeable"
    else -> "clearly different"
}

@Composable
fun PropertiesPanel(
    hierarchy: HierarchyNode?,
    selectedNodeId: Int?,
    showRgbValues: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = InspectorTokens.current
    val node = selectedNodeId?.let { findNode(hierarchy, it) }
    Column(modifier = modifier.background(tokens.bgPanel)) {
        PanelHeader(title = "Properties")
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.line1))
        if (node == null) {
            EmptyProperties(modifier = Modifier.fillMaxSize())
        } else {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    PropertiesContent(
                        node = node,
                        root = hierarchy,
                        showRgbValues = showRgbValues,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyProperties(modifier: Modifier = Modifier) {
    val tokens = InspectorTokens.current
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(InspectorRadius.lg))
                .background(tokens.bgPanelMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tokens.fg4,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Select a component",
            color = tokens.fg3,
            fontSize = 12.sp,
        )
        Text(
            text = "to inspect its properties",
            color = tokens.fg3,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PropertiesContent(
    node: HierarchyNode,
    root: HierarchyNode?,
    showRgbValues: Boolean,
) {
    val tokens = InspectorTokens.current

    // Kind/Size/Position section
    Section(title = node.name) {
        KV("Size", "${"%.1f".format(node.size.width)} × ${"%.1f".format(node.size.height)} dp", mono = true)
        KV("Position", "${"%.1f".format(node.bounds.left)}, ${"%.1f".format(node.bounds.top)}", mono = true)
    }

    // Margins section (distance from screen edges)
    if (root != null && node.id != root.id) {
        val mL = node.bounds.left - root.bounds.left
        val mT = node.bounds.top - root.bounds.top
        val mR = root.bounds.right - node.bounds.right
        val mB = root.bounds.bottom - node.bounds.bottom
        Section(title = "Margins (from screen)") {
            KV("L", "${mL.toInt()} dp", mono = true)
            KV("T", "${mT.toInt()} dp", mono = true)
            KV("R", "${mR.toInt()} dp", mono = true)
            KV("B", "${mB.toInt()} dp", mono = true)
        }
    }

    // Padding section (offset from parent)
    node.offsetFromParent?.let { off ->
        if (off.left > 0.5 || off.top > 0.5 || off.right > 0.5 || off.bottom > 0.5) {
            Section(title = "Padding (from parent)") {
                KV("L", "${off.left.toInt()} dp", mono = true)
                KV("T", "${off.top.toInt()} dp", mono = true)
                KV("R", "${off.right.toInt()} dp", mono = true)
                KV("B", "${off.bottom.toInt()} dp", mono = true)
            }
        }
    }

    // Typography
    node.semantics?.let { sem ->
        val typoEntries = sem.filter { (k, _) -> k in setOf("fontSize", "fontWeight", "fontFamily", "lineHeight", "letterSpacing") }
        if (typoEntries.isNotEmpty()) {
            Section(title = "Typography") {
                typoEntries.forEach { (k, v) -> KV(k, v, mono = true) }
            }
        }
    }

    // Colors
    node.semantics?.let { sem ->
        val backgroundColor = sem["backgroundColor"] ?: sem["background"]
        val foregroundColor = sem["foregroundColor"] ?: sem["contentColor"] ?: sem["foreground"]
        val colorEntries = sem.filter { (k, _) -> k.contains("Color", ignoreCase = true) }

        if (colorEntries.isNotEmpty() || backgroundColor != null || foregroundColor != null) {
            Section(title = "Colors") {
                colorEntries.forEach { (k, v) -> ColorRow(k, v, showRgbValues) }
                if (backgroundColor != null && !colorEntries.containsKey("backgroundColor")) {
                    ColorRow("backgroundColor", backgroundColor, showRgbValues)
                }
                if (foregroundColor != null && !colorEntries.containsKey("foregroundColor")) {
                    ColorRow("foregroundColor", foregroundColor, showRgbValues)
                }
                if (backgroundColor != null && foregroundColor != null) {
                    val delta = tryDeltaE(backgroundColor, foregroundColor)
                    if (delta != null) {
                        Spacer(Modifier.height(6.dp))
                        DeltaECard(value = delta)
                    }
                }
            }
        }
    }

    // Semantics — KV list of remaining entries
    node.semantics?.let { sem ->
        if (sem.isNotEmpty()) {
            Section(title = "Semantics") {
                sem.forEach { (k, v) -> KV(k, v, mono = true) }
            }
        }
    }
}

@Composable
private fun ColorRow(label: String, value: String, showRgb: Boolean) {
    val tokens = InspectorTokens.current
    val parsed = tryParseColor(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = tokens.fg3, fontSize = InspectorType.kvBody)
        if (parsed != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (showRgb) parsed.rgb else parsed.hex,
                    color = tokens.fg1,
                    fontSize = InspectorType.kvBody,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { copyToClipboard(parsed.hex) },
                )
                Swatch(color = parsed.composeColor)
            }
        } else {
            Text(value, color = tokens.fg1, fontSize = InspectorType.kvBody, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DeltaECard(value: Double) {
    val tokens = InspectorTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InspectorRadius.sm))
            .background(tokens.bgPanelMuted)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Delta E", color = tokens.fg3, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "%.2f".format(value),
                color = tokens.fg1,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "(${deltaELabel(value)})",
                color = tokens.fg3,
                fontSize = 11.sp,
            )
        }
    }
}

internal fun findNode(root: HierarchyNode?, id: Int): HierarchyNode? {
    if (root == null) return null
    if (root.id == id) return root
    for (child in root.children) {
        findNode(child, id)?.let { return it }
    }
    return null
}
