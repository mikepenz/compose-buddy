package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private fun tryParseColor(value: String): ParsedColor? {
    return try {
        val c = parseHexColor(value)
        ParsedColor(
            hex = toHexString(c.r, c.g, c.b),
            rgb = toRgbString(c.r, c.g, c.b),
            composeColor = Color(c.r, c.g, c.b),
        )
    } catch (_: Exception) {
        null
    }
}

private fun tryDeltaE(color1: String, color2: String): Double? {
    return try { deltaE76(color1, color2) } catch (_: Exception) { null }
}

@Composable
fun PropertiesPanel(
    hierarchy: HierarchyNode?,
    selectedNodeId: Int?,
    showRgbValues: Boolean = false,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Column(modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("Properties", style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            val node = selectedNodeId?.let { findNode(hierarchy, it) }
            if (node != null) {
                Text(node.name, style = MaterialTheme.typography.labelLarge)
                Text("${node.size.width} x ${node.size.height} dp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Position: (${node.bounds.left}, ${node.bounds.top})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Margins from root/screen
                val root = hierarchy
                if (root != null && node.id != root.id) {
                    val mL = node.bounds.left - root.bounds.left
                    val mT = node.bounds.top - root.bounds.top
                    val mR = root.bounds.right - node.bounds.right
                    val mB = root.bounds.bottom - node.bounds.bottom
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Margins (from screen)", style = MaterialTheme.typography.labelMedium)
                    if (mL == mR && mT == mB && mL == mT) {
                        Text("all: ${"%.0f".format(mL)}dp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (mL == mR && mT == mB) {
                        Text("horizontal: ${"%.0f".format(mL)}dp  vertical: ${"%.0f".format(mT)}dp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("L: ${"%.0f".format(mL)}  T: ${"%.0f".format(mT)}  R: ${"%.0f".format(mR)}  B: ${"%.0f".format(mB)} dp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Inferred padding (from offsetFromParent)
                node.offsetFromParent?.let { off ->
                    if (off.left > 0.5 || off.top > 0.5 || off.right > 0.5 || off.bottom > 0.5) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Padding (from parent)", style = MaterialTheme.typography.labelMedium)
                        if (off.left == off.right && off.top == off.bottom && off.left == off.top) {
                            Text("all: ${"%.0f".format(off.left)}dp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (off.left == off.right && off.top == off.bottom) {
                            Text("horizontal: ${"%.0f".format(off.left)}dp  vertical: ${"%.0f".format(off.top)}dp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("L: ${"%.0f".format(off.left)}  T: ${"%.0f".format(off.top)}  R: ${"%.0f".format(off.right)}  B: ${"%.0f".format(off.bottom)} dp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Typography (if text properties available)
                node.semantics?.let { sem ->
                    val typographyKeys = listOf("fontSize", "fontWeight", "fontFamily", "lineHeight", "letterSpacing")
                    val typoEntries = sem.filter { (key, _) -> key in typographyKeys }
                    if (typoEntries.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Typography", style = MaterialTheme.typography.labelMedium)
                        for ((key, value) in typoEntries) {
                            Text("$key: $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                node.semantics?.let { sem ->
                    val backgroundColor = sem["backgroundColor"] ?: sem["background"]
                    val foregroundColor = sem["foregroundColor"] ?: sem["contentColor"] ?: sem["foreground"]
                    val colorEntries = sem.filter { (key, _) -> key.contains("Color", ignoreCase = true) }

                    if (colorEntries.isNotEmpty() || backgroundColor != null || foregroundColor != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Text("Colors", style = MaterialTheme.typography.labelMedium)

                        for ((key, value) in colorEntries) {
                            ColorRow(key, value, showRgbValues)
                        }

                        if (backgroundColor != null && !colorEntries.containsKey("backgroundColor")) {
                            ColorRow("backgroundColor", backgroundColor, showRgbValues)
                        }
                        if (foregroundColor != null && !colorEntries.containsKey("foregroundColor")) {
                            ColorRow("foregroundColor", foregroundColor, showRgbValues)
                        }

                        if (backgroundColor != null && foregroundColor != null) {
                            val delta = tryDeltaE(backgroundColor, foregroundColor)
                            if (delta != null) {
                                val perception = when {
                                    delta < 1.0 -> "imperceptible"
                                    delta < 2.0 -> "barely perceptible"
                                    delta < 10.0 -> "noticeable"
                                    else -> "clearly different"
                                }
                                Text(
                                    "Delta E: ${"%.2f".format(delta)} ($perception)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text("Semantics", style = MaterialTheme.typography.labelMedium)
                    for ((key, value) in sem) {
                        Text("$key: $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("Select a component to inspect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ColorRow(label: String, value: String, showRgb: Boolean) {
    val parsed = tryParseColor(value)
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        if (parsed != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(parsed.composeColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .clickable { copyToClipboard(parsed.hex) },
                )
                Spacer(Modifier.width(6.dp))
                Text(parsed.hex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showRgb) {
                Text(parsed.rgb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
