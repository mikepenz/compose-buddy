package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFindingType
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastCheckerTest {

    @Test
    fun `black on white has high contrast`() {
        val ratio = ContrastChecker.contrastRatio(Triple(0, 0, 0), Triple(255, 255, 255))
        assertTrue(ratio >= 21.0)
    }

    @Test
    fun `white on white has ratio 1`() {
        val ratio = ContrastChecker.contrastRatio(Triple(255, 255, 255), Triple(255, 255, 255))
        assertTrue(ratio < 1.1)
    }

    @Test
    fun `parseColor handles 6-digit hex`() {
        val color = ContrastChecker.parseColor("#FF0000")
        assertEquals(Triple(255, 0, 0), color)
    }

    @Test
    fun `parseColor handles 8-digit hex (ARGB)`() {
        val color = ContrastChecker.parseColor("#FF00FF00")
        assertEquals(Triple(0, 255, 0), color)
    }

    @Test
    fun `low contrast text reports finding`() {
        val node = HierarchyNode(
            id = 0, name = "Text",
            bounds = Bounds(0.0, 0.0, 100.0, 20.0),
            size = Size(100.0, 20.0),
            semantics = mapOf("foregroundColor" to "#999999", "backgroundColor" to "#AAAAAA"),
        )
        val findings = ContrastChecker.check(node)
        assertEquals(1, findings.size)
        assertEquals(AccessibilityFindingType.LOW_CONTRAST, findings[0].type)
    }

    @Test
    fun `high contrast text passes`() {
        val node = HierarchyNode(
            id = 0, name = "Text",
            bounds = Bounds(0.0, 0.0, 100.0, 20.0),
            size = Size(100.0, 20.0),
            semantics = mapOf("foregroundColor" to "#000000", "backgroundColor" to "#FFFFFF"),
        )
        val findings = ContrastChecker.check(node)
        assertTrue(findings.isEmpty())
    }
}
