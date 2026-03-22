package dev.mikepenz.composebuddy.inspector.comparison

import kotlin.math.pow
import kotlin.math.sqrt

data class RgbColor(val r: Int, val g: Int, val b: Int)

/**
 * Parse hex color string (#RRGGBB or #AARRGGBB) to RGB components.
 */
internal fun parseHexColor(hex: String): RgbColor {
    val clean = hex.removePrefix("#")
    return when (clean.length) {
        6 -> RgbColor(
            r = clean.substring(0, 2).toInt(16),
            g = clean.substring(2, 4).toInt(16),
            b = clean.substring(4, 6).toInt(16),
        )
        8 -> RgbColor(
            r = clean.substring(2, 4).toInt(16),
            g = clean.substring(4, 6).toInt(16),
            b = clean.substring(6, 8).toInt(16),
        )
        else -> RgbColor(0, 0, 0)
    }
}

internal fun toHexString(r: Int, g: Int, b: Int): String =
    "#%02X%02X%02X".format(r, g, b)

internal fun toRgbString(r: Int, g: Int, b: Int): String =
    "rgb($r, $g, $b)"

/**
 * Calculate CIE76 Delta E between two hex colors.
 * Lower values = more similar. 0 = identical.
 * <1 = imperceptible, 1-2 = barely perceptible, >10 = clearly different.
 */
internal fun deltaE76(hex1: String, hex2: String): Double {
    val lab1 = rgbToLab(parseHexColor(hex1))
    val lab2 = rgbToLab(parseHexColor(hex2))
    return sqrt(
        (lab1[0] - lab2[0]).pow(2) +
            (lab1[1] - lab2[1]).pow(2) +
            (lab1[2] - lab2[2]).pow(2),
    )
}

private fun rgbToLab(color: RgbColor): DoubleArray {
    val xyz = rgbToXyz(color)
    return xyzToLab(xyz)
}

private fun rgbToXyz(color: RgbColor): DoubleArray {
    var r = color.r / 255.0
    var g = color.g / 255.0
    var b = color.b / 255.0

    r = if (r > 0.04045) ((r + 0.055) / 1.055).pow(2.4) else r / 12.92
    g = if (g > 0.04045) ((g + 0.055) / 1.055).pow(2.4) else g / 12.92
    b = if (b > 0.04045) ((b + 0.055) / 1.055).pow(2.4) else b / 12.92

    r *= 100; g *= 100; b *= 100

    return doubleArrayOf(
        r * 0.4124564 + g * 0.3575761 + b * 0.1804375,
        r * 0.2126729 + g * 0.7151522 + b * 0.0721750,
        r * 0.0193339 + g * 0.1191920 + b * 0.9503041,
    )
}

private fun xyzToLab(xyz: DoubleArray): DoubleArray {
    var x = xyz[0] / 95.047
    var y = xyz[1] / 100.000
    var z = xyz[2] / 108.883

    x = if (x > 0.008856) x.pow(1.0 / 3.0) else (7.787 * x) + (16.0 / 116.0)
    y = if (y > 0.008856) y.pow(1.0 / 3.0) else (7.787 * y) + (16.0 / 116.0)
    z = if (z > 0.008856) z.pow(1.0 / 3.0) else (7.787 * z) + (16.0 / 116.0)

    return doubleArrayOf(
        (116.0 * y) - 16.0,
        500.0 * (x - y),
        200.0 * (y - z),
    )
}
