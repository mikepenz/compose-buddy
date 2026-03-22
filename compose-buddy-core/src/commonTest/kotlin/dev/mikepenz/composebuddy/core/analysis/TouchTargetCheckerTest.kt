package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFindingType
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TouchTargetCheckerTest {
    // Sizes are now in dp directly
    private fun clickableNode(widthDp: Double, heightDp: Double) = HierarchyNode(
        id = 0, name = "Button",
        bounds = Bounds(0.0, 0.0, widthDp, heightDp),
        size = Size(widthDp, heightDp),
        semantics = mapOf("role" to "Button"),
    )

    @Test
    fun `adequate touch target passes`() {
        val findings = TouchTargetChecker.check(clickableNode(48.0, 48.0))
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `small touch target reports warning`() {
        val findings = TouchTargetChecker.check(clickableNode(35.0, 35.0))
        assertEquals(1, findings.size)
        assertEquals(AccessibilityFindingType.TOUCH_TARGET_TOO_SMALL, findings[0].type)
    }

    @Test
    fun `non-clickable small element passes`() {
        val node = HierarchyNode(
            id = 0, name = "Icon",
            bounds = Bounds(0.0, 0.0, 24.0, 24.0),
            size = Size(24.0, 24.0),
        )
        val findings = TouchTargetChecker.check(node)
        assertTrue(findings.isEmpty())
    }
}
