package dev.mikepenz.composebuddy.core.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.round

/**
 * A node in the Compose layout hierarchy.
 * All dimensional values are in dp (density-independent pixels).
 * To convert to px: `px = dp * densityDpi / 160`
 */
@Serializable
data class HierarchyNode(
    val id: Int,
    /** Composable name (e.g., "Text", "Button", "Column", "Layout") */
    val name: String,
    /** Source file where this composable is defined */
    val sourceFile: String? = null,
    /** Source line number */
    val sourceLine: Int? = null,
    /** Absolute bounds in dp within the rendered image */
    val bounds: Bounds,
    /** Bounds relative to parent node in dp */
    val boundsInParent: Bounds? = null,
    /** Size in dp */
    val size: Size,
    /** Distance from each parent edge in dp (left, top, right, bottom) */
    val offsetFromParent: Bounds? = null,
    /** Semantic properties: text, contentDescription, role, onClick, colors, etc. */
    val semantics: Map<String, String>? = null,
    /** Child nodes */
    val children: List<HierarchyNode> = emptyList(),
)

/** Rectangular bounds in dp. All values rounded to max 3 decimals. */
@Serializable
data class Bounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

/** Size in dp. All values rounded to max 3 decimals. */
@Serializable
data class Size(
    val width: Double,
    val height: Double,
)

/** Round to max 3 decimal places, stripping floating point noise. */
fun r3(v: Double): Double = round(v * 1000) / 1000.0

/** Create Bounds with all values rounded to 3 decimals. */
fun boundsOf(left: Double, top: Double, right: Double, bottom: Double) =
    Bounds(r3(left), r3(top), r3(right), r3(bottom))

/** Create Size with values rounded to 3 decimals. */
fun sizeOf(width: Double, height: Double) =
    Size(r3(width), r3(height))

/**
 * Convert a pixel value to dp, snapping to the nearest integer dp
 * when the result is within 0.3dp of an integer (pixel grid snapping artifact).
 */
fun pxToDp(px: Double, densityDpi: Int): Double {
    val dp = px * 160.0 / densityDpi
    val rounded = round(dp)
    return if (abs(dp - rounded) < 0.3) rounded else r3(dp)
}

/** Create dp Bounds from pixel values with grid-snap rounding. */
fun pxBoundsToDp(leftPx: Double, topPx: Double, rightPx: Double, bottomPx: Double, dpi: Int): Bounds =
    Bounds(pxToDp(leftPx, dpi), pxToDp(topPx, dpi), pxToDp(rightPx, dpi), pxToDp(bottomPx, dpi))
