package dev.mikepenz.composebuddy.inspector.comparison

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Per-pixel RGB difference computation for image overlay comparison.
 */
object ImageDiff {

    /**
     * Compute a difference image between two images.
     * Pixels that differ are highlighted; identical pixels are black.
     * Images must be the same size; if not, the result is sized to the smaller dimensions.
     */
    fun computeDifference(imageA: BufferedImage, imageB: BufferedImage): BufferedImage {
        val width = minOf(imageA.width, imageB.width)
        val height = minOf(imageA.height, imageB.height)
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgbA = imageA.getRGB(x, y)
                val rgbB = imageB.getRGB(x, y)

                val rDiff = abs(((rgbA shr 16) and 0xFF) - ((rgbB shr 16) and 0xFF))
                val gDiff = abs(((rgbA shr 8) and 0xFF) - ((rgbB shr 8) and 0xFF))
                val bDiff = abs((rgbA and 0xFF) - (rgbB and 0xFF))

                val diffRgb = (rDiff shl 16) or (gDiff shl 8) or bDiff
                result.setRGB(x, y, diffRgb)
            }
        }

        return result
    }

    /**
     * Load an image from a file path.
     */
    fun loadImage(path: String): BufferedImage? {
        return try {
            ImageIO.read(File(path))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calculate the percentage of pixels that differ between two images.
     * A pixel is considered different if any channel differs by more than the threshold.
     */
    fun diffPercentage(imageA: BufferedImage, imageB: BufferedImage, threshold: Int = 5): Double {
        val width = minOf(imageA.width, imageB.width)
        val height = minOf(imageA.height, imageB.height)
        val totalPixels = width * height
        if (totalPixels == 0) return 0.0

        var diffCount = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgbA = imageA.getRGB(x, y)
                val rgbB = imageB.getRGB(x, y)

                val rDiff = abs(((rgbA shr 16) and 0xFF) - ((rgbB shr 16) and 0xFF))
                val gDiff = abs(((rgbA shr 8) and 0xFF) - ((rgbB shr 8) and 0xFF))
                val bDiff = abs((rgbA and 0xFF) - (rgbB and 0xFF))

                if (rDiff > threshold || gDiff > threshold || bDiff > threshold) {
                    diffCount++
                }
            }
        }

        return diffCount.toDouble() / totalPixels * 100
    }
}
