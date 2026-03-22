package dev.mikepenz.composebuddy.inspector.ui

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PreviewPaneTest {

    @Test
    fun `calculateDistance returns correct horizontal and vertical distances`() {
        val nodeA = HierarchyNode(
            id = 1, name = "A",
            bounds = Bounds(10.0, 10.0, 50.0, 30.0),
            size = Size(40.0, 20.0),
        )
        val nodeB = HierarchyNode(
            id = 2, name = "B",
            bounds = Bounds(100.0, 60.0, 200.0, 100.0),
            size = Size(100.0, 40.0),
        )
        val distance = calculateDistance(nodeA, nodeB)
        // Horizontal: nodeB.left - nodeA.right = 100 - 50 = 50
        assertEquals(50.0, distance.horizontal)
        // Vertical: nodeB.top - nodeA.bottom = 60 - 30 = 30
        assertEquals(30.0, distance.vertical)
    }

    @Test
    fun `calculateDistance with overlapping nodes returns zero or negative`() {
        val nodeA = HierarchyNode(
            id = 1, name = "A",
            bounds = Bounds(10.0, 10.0, 100.0, 100.0),
            size = Size(90.0, 90.0),
        )
        val nodeB = HierarchyNode(
            id = 2, name = "B",
            bounds = Bounds(50.0, 50.0, 150.0, 150.0),
            size = Size(100.0, 100.0),
        )
        val distance = calculateDistance(nodeA, nodeB)
        // Overlapping: nodeB.left - nodeA.right = 50 - 100 = -50
        assertEquals(-50.0, distance.horizontal)
        assertEquals(-50.0, distance.vertical)
    }
}
