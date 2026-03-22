package dev.mikepenz.composebuddy.inspector.comparison

import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageDiffTest {

    private fun createImage(width: Int, height: Int, rgb: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, rgb)
            }
        }
        return image
    }

    @Test
    fun `computeDifference of identical images produces all-black result`() {
        val imageA = createImage(10, 10, 0xFF0000)
        val imageB = createImage(10, 10, 0xFF0000)
        val diff = ImageDiff.computeDifference(imageA, imageB)

        for (y in 0 until diff.height) {
            for (x in 0 until diff.width) {
                assertEquals(0x000000, diff.getRGB(x, y) and 0xFFFFFF)
            }
        }
    }

    @Test
    fun `computeDifference of different images produces non-black result`() {
        val imageA = createImage(10, 10, 0xFF0000) // red
        val imageB = createImage(10, 10, 0x00FF00) // green
        val diff = ImageDiff.computeDifference(imageA, imageB)

        val pixel = diff.getRGB(0, 0) and 0xFFFFFF
        assertTrue(pixel != 0, "Difference pixel should be non-zero")
    }

    @Test
    fun `diffPercentage returns zero for identical images`() {
        val imageA = createImage(10, 10, 0x808080)
        val imageB = createImage(10, 10, 0x808080)
        assertEquals(0.0, ImageDiff.diffPercentage(imageA, imageB))
    }

    @Test
    fun `diffPercentage returns 100 for completely different images`() {
        val imageA = createImage(10, 10, 0x000000)
        val imageB = createImage(10, 10, 0xFFFFFF)
        assertEquals(100.0, ImageDiff.diffPercentage(imageA, imageB))
    }
}
