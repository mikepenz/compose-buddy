package dev.mikepenz.composebuddy.inspector.ui

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComponentTreeTest {

    private val testHierarchy = HierarchyNode(
        id = 0,
        name = "Column",
        bounds = Bounds(0.0, 0.0, 360.0, 640.0),
        size = Size(360.0, 640.0),
        children = listOf(
            HierarchyNode(
                id = 1,
                name = "Text",
                bounds = Bounds(16.0, 16.0, 200.0, 40.0),
                size = Size(184.0, 24.0),
                semantics = mapOf("text" to "Hello"),
            ),
            HierarchyNode(
                id = 2,
                name = "Button",
                bounds = Bounds(16.0, 56.0, 200.0, 104.0),
                size = Size(184.0, 48.0),
                semantics = mapOf("onClick" to "true"),
                children = listOf(
                    HierarchyNode(
                        id = 3,
                        name = "Text",
                        bounds = Bounds(32.0, 68.0, 120.0, 92.0),
                        size = Size(88.0, 24.0),
                        semantics = mapOf("text" to "Click me"),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `findNode returns correct node by id`() {
        val node = findNode(testHierarchy, 2)
        assertEquals("Button", node?.name)
    }

    @Test
    fun `findNode returns nested child node`() {
        val node = findNode(testHierarchy, 3)
        assertEquals("Text", node?.name)
        assertEquals("Click me", node?.semantics?.get("text"))
    }

    @Test
    fun `findNode returns null for non-existent id`() {
        val node = findNode(testHierarchy, 999)
        assertNull(node)
    }

    @Test
    fun `findNode returns root node`() {
        val node = findNode(testHierarchy, 0)
        assertEquals("Column", node?.name)
    }
}
