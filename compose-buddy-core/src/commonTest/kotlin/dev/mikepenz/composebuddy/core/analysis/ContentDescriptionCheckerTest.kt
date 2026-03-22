package dev.mikepenz.composebuddy.core.analysis

import dev.mikepenz.composebuddy.core.model.AccessibilityFindingType
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentDescriptionCheckerTest {
    private fun node(id: Int, name: String, semantics: Map<String, String>? = null, children: List<HierarchyNode> = emptyList()) =
        HierarchyNode(id = id, name = name, bounds = Bounds(0.0, 0.0, 100.0, 100.0), size = Size(100.0, 100.0), semantics = semantics, children = children)

    @Test
    fun `clickable without contentDescription reports error`() {
        val root = node(0, "IconButton", semantics = mapOf("role" to "Button"))
        val findings = ContentDescriptionChecker.check(root)
        assertEquals(1, findings.size)
        assertEquals(AccessibilityFindingType.MISSING_CONTENT_DESCRIPTION, findings[0].type)
    }

    @Test
    fun `clickable with contentDescription passes`() {
        val root = node(0, "IconButton", semantics = mapOf("role" to "Button", "contentDescription" to "Settings"))
        val findings = ContentDescriptionChecker.check(root)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `clickable with text passes`() {
        val root = node(0, "Button", semantics = mapOf("role" to "Button", "text" to "Submit"))
        val findings = ContentDescriptionChecker.check(root)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `non-clickable without contentDescription passes`() {
        val root = node(0, "Box", semantics = mapOf("testTag" to "container"))
        val findings = ContentDescriptionChecker.check(root)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `nested clickable without contentDescription found`() {
        val child = node(1, "IconButton", semantics = mapOf("onClick" to "true"))
        val root = node(0, "Column", children = listOf(child))
        val findings = ContentDescriptionChecker.check(root)
        assertEquals(1, findings.size)
        assertEquals(1, findings[0].nodeId)
    }
}
