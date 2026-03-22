package dev.mikepenz.composebuddy.core.hierarchy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ViewInfoExtractorTest {

    // Use density 160 so 1px = 1dp for test simplicity
    private val testDpi = 160

    @Test
    fun `extract single node`() {
        val info = ViewInfoExtractor.ViewInfoData(
            className = "android.widget.FrameLayout",
            left = 0, top = 0, right = 100, bottom = 200,
        )
        val nodes = ViewInfoExtractor.extract(listOf(info), densityDpi = testDpi)
        assertEquals(1, nodes.size)
        val node = nodes[0]
        assertEquals("FrameLayout", node.name)
        assertEquals(0.0, node.bounds.left)
        assertEquals(100.0, node.bounds.right)
        assertEquals(100.0, node.size.width)
        assertEquals(200.0, node.size.height)
        assertNull(node.boundsInParent)
    }

    @Test
    fun `extract parent with child computes boundsInParent`() {
        val child = ViewInfoExtractor.ViewInfoData(
            className = "android.widget.TextView",
            left = 16, top = 16, right = 84, bottom = 50,
        )
        val parent = ViewInfoExtractor.ViewInfoData(
            className = "android.widget.LinearLayout",
            left = 0, top = 0, right = 100, bottom = 200,
            children = listOf(child),
        )
        val nodes = ViewInfoExtractor.extract(listOf(parent), densityDpi = testDpi)
        assertEquals(1, nodes.size)
        assertEquals(1, nodes[0].children.size)

        val childNode = nodes[0].children[0]
        assertEquals("TextView", childNode.name)
        assertNotNull(childNode.boundsInParent)
        assertEquals(16.0, childNode.boundsInParent!!.left)
        assertEquals(16.0, childNode.boundsInParent!!.top)
    }

    @Test
    fun `extract preserves semantics`() {
        val info = ViewInfoExtractor.ViewInfoData(
            className = "ComposeView",
            left = 0, top = 0, right = 360, bottom = 640,
            semantics = mapOf("contentDescription" to "Hello"),
        )
        val nodes = ViewInfoExtractor.extract(listOf(info), densityDpi = testDpi)
        assertEquals(mapOf("contentDescription" to "Hello"), nodes[0].semantics)
    }

    @Test
    fun `extract assigns sequential ids`() {
        val child1 = ViewInfoExtractor.ViewInfoData("View", 0, 0, 50, 50)
        val child2 = ViewInfoExtractor.ViewInfoData("View", 50, 0, 100, 50)
        val parent = ViewInfoExtractor.ViewInfoData("ViewGroup", 0, 0, 100, 50, children = listOf(child1, child2))
        val nodes = ViewInfoExtractor.extract(listOf(parent), densityDpi = testDpi)
        assertEquals(0, nodes[0].id)
        assertEquals(1, nodes[0].children[0].id)
        assertEquals(2, nodes[0].children[1].id)
    }

    @Test
    fun `padding can be inferred from bounds gap`() {
        val child = ViewInfoExtractor.ViewInfoData("Text", 16, 16, 84, 84)
        val parent = ViewInfoExtractor.ViewInfoData("Box", 0, 0, 100, 100, children = listOf(child))
        val nodes = ViewInfoExtractor.extract(listOf(parent), densityDpi = testDpi)
        val childNode = nodes[0].children[0]
        assertEquals(16.0, childNode.boundsInParent!!.left)
        assertEquals(16.0, childNode.boundsInParent!!.top)
    }

    @Test
    fun `extract empty list returns empty`() {
        val nodes = ViewInfoExtractor.extract(emptyList(), densityDpi = testDpi)
        assertEquals(0, nodes.size)
    }
}
