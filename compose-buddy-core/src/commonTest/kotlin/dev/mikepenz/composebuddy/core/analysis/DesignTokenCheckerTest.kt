package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFindingType
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.DesignToken
import dev.mikepenz.composebuddy.core.model.DesignTokenType
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignTokenCheckerTest {

    @Test
    fun `matching token passes`() {
        val node = HierarchyNode(
            id = 0, name = "Box",
            bounds = Bounds(0.0, 0.0, 100.0, 100.0), size = Size(100.0, 100.0),
            semantics = mapOf("backgroundColor" to "#1A73E8"),
        )
        val tokens = listOf(DesignToken("color.background", DesignTokenType.COLOR, "#1A73E8"))
        val findings = DesignTokenChecker.check(node, tokens)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `mismatching token reports deviation`() {
        val node = HierarchyNode(
            id = 0, name = "Box",
            bounds = Bounds(0.0, 0.0, 100.0, 100.0), size = Size(100.0, 100.0),
            semantics = mapOf("backgroundColor" to "#FF0000"),
        )
        val tokens = listOf(DesignToken("color.background", DesignTokenType.COLOR, "#1A73E8"))
        val findings = DesignTokenChecker.check(node, tokens)
        assertEquals(1, findings.size)
        assertEquals(AccessibilityFindingType.DESIGN_SPEC_DEVIATION, findings[0].type)
    }

    @Test
    fun `empty tokens returns no findings`() {
        val node = HierarchyNode(
            id = 0, name = "Box",
            bounds = Bounds(0.0, 0.0, 100.0, 100.0), size = Size(100.0, 100.0),
            semantics = mapOf("backgroundColor" to "#FF0000"),
        )
        val findings = DesignTokenChecker.check(node, emptyList())
        assertTrue(findings.isEmpty())
    }
}
