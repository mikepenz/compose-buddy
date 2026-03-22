package dev.mikepenz.composebuddy.core.serialization

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HierarchySerializationTest {

    private val json = ComposeBuddyJson

    @Test
    fun `leaf node round-trips`() {
        val node = HierarchyNode(
            id = 1, name = "Text",
            bounds = Bounds(0.0, 0.0, 100.0, 50.0),
            size = Size(100.0, 50.0),
        )
        val encoded = json.encodeToString(HierarchyNode.serializer(), node)
        val decoded = json.decodeFromString(HierarchyNode.serializer(), encoded)
        assertEquals(node, decoded)
    }

    @Test
    fun `node with children round-trips`() {
        val child = HierarchyNode(
            id = 2, name = "Text",
            bounds = Bounds(16.0, 16.0, 200.0, 50.0),
            size = Size(184.0, 34.0),
            semantics = mapOf("contentDescription" to "Hello"),
        )
        val parent = HierarchyNode(
            id = 1, name = "Box",
            bounds = Bounds(0.0, 0.0, 216.0, 66.0),
            size = Size(216.0, 66.0),
            children = listOf(child),
        )
        val encoded = json.encodeToString(HierarchyNode.serializer(), parent)
        val decoded = json.decodeFromString(HierarchyNode.serializer(), encoded)
        assertEquals(parent, decoded)
        assertEquals(1, decoded.children.size)
        assertEquals("Text", decoded.children[0].name)
    }

    @Test
    fun `node with all optional fields round-trips`() {
        val node = HierarchyNode(
            id = 1, name = "Button",
            sourceFile = "MyScreen.kt", sourceLine = 42,
            bounds = Bounds(0.0, 0.0, 200.0, 48.0),
            boundsInParent = Bounds(10.0, 10.0, 210.0, 58.0),
            size = Size(200.0, 48.0),
            offsetFromParent = Bounds(10.0, 10.0, 6.0, 2.0),
            semantics = mapOf("role" to "Button", "contentDescription" to "Submit"),
        )
        val encoded = json.encodeToString(HierarchyNode.serializer(), node)
        val decoded = json.decodeFromString(HierarchyNode.serializer(), encoded)
        assertEquals(node, decoded)
    }

    @Test
    fun `hierarchy JSON contains expected structure`() {
        val node = HierarchyNode(
            id = 1, name = "Column",
            bounds = Bounds(0.0, 0.0, 360.0, 640.0),
            size = Size(360.0, 640.0),
        )
        val encoded = json.encodeToString(HierarchyNode.serializer(), node)
        assertTrue("\"id\"" in encoded, "Missing id field")
        assertTrue("\"name\"" in encoded, "Missing name field")
        assertTrue("\"bounds\"" in encoded, "Missing bounds field")
        assertTrue("\"size\"" in encoded, "Missing size field")
    }
}
