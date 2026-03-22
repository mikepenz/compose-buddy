package dev.mikepenz.composebuddy.inspector.comparison

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorDeltaTest {

    @Test
    fun `parseHexColor parses 6-digit hex correctly`() {
        val (r, g, b) = parseHexColor("#FF0000")
        assertEquals(255, r)
        assertEquals(0, g)
        assertEquals(0, b)
    }

    @Test
    fun `parseHexColor parses 8-digit ARGB hex correctly`() {
        val (r, g, b) = parseHexColor("#FF00FF00")
        assertEquals(0, r)
        assertEquals(255, g)
        assertEquals(0, b)
    }

    @Test
    fun `deltaE76 returns zero for identical colors`() {
        val delta = deltaE76("#FF0000", "#FF0000")
        assertEquals(0.0, delta, 0.001)
    }

    @Test
    fun `deltaE76 returns positive value for different colors`() {
        val delta = deltaE76("#FF0000", "#00FF00")
        assertTrue(delta > 0)
    }

    @Test
    fun `deltaE76 black vs white has large delta`() {
        val delta = deltaE76("#000000", "#FFFFFF")
        assertTrue(delta > 100)
    }

    @Test
    fun `toHexString formats correctly`() {
        assertEquals("#FF0000", toHexString(255, 0, 0))
        assertEquals("#00FF00", toHexString(0, 255, 0))
        assertEquals("#0000FF", toHexString(0, 0, 255))
    }

    @Test
    fun `toRgbString formats correctly`() {
        assertEquals("rgb(255, 0, 0)", toRgbString(255, 0, 0))
    }
}
