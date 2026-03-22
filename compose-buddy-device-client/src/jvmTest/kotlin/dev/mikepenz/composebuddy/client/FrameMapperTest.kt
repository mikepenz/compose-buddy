package dev.mikepenz.composebuddy.client

import dev.mikepenz.composebuddy.client.model.BuddyBounds
import dev.mikepenz.composebuddy.client.model.BuddyFrame
import dev.mikepenz.composebuddy.client.model.BuddyFrameTiming
import dev.mikepenz.composebuddy.client.model.BuddySemanticNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FrameMapperTest {

    private val minimalFrame = BuddyFrame.RenderFrame(
        frameId = 1,
        timestamp = 1712345678901L,
        preview = "com.example.FooKt.MyPreview",
        image = "base64png==",
        semantics = BuddySemanticNode(
            id = 0, name = "Root",
            bounds = BuddyBounds(0.0, 0.0, 360.0, 640.0),
            children = emptyList(),
        ),
        slotTable = emptyList(),
        recompositions = mapOf("MyPreview" to 1),
        frameTiming = BuddyFrameTiming(1712345678890L, 4.2, 1.1, 2.8),
    )

    @Test
    fun `toRenderResult maps previewName from frame preview field`() {
        val result = FrameMapper.toRenderResult(minimalFrame)
        assertEquals("com.example.FooKt.MyPreview", result.previewName)
    }

    @Test
    fun `toRenderResult maps imageBase64 from frame image field`() {
        val result = FrameMapper.toRenderResult(minimalFrame)
        assertEquals("base64png==", result.imageBase64)
    }

    @Test
    fun `toRenderResult maps renderDurationMs to totalMs from frameTiming`() {
        val result = FrameMapper.toRenderResult(minimalFrame)
        assertEquals(8L, result.renderDurationMs) // 4.2 + 1.1 + 2.8 = 8.1 → 8
    }

    @Test
    fun `toRenderResult maps hierarchy root node`() {
        val result = FrameMapper.toRenderResult(minimalFrame)
        assertNotNull(result.hierarchy)
        assertEquals("Root", result.hierarchy!!.name)
    }

    @Test
    fun `toRenderResult sets rendererUsed to device`() {
        val result = FrameMapper.toRenderResult(minimalFrame)
        assertEquals("device", result.rendererUsed)
    }

    @Test
    fun `mapSemanticNode preserves contentDescription`() {
        val node = FrameMapper.mapSemanticNode(
            BuddySemanticNode(
                1, "Image",
                bounds = BuddyBounds(0.0, 0.0, 100.0, 100.0),
                children = emptyList(),
                contentDescription = "Profile photo",
            ),
            densityDpi = 160,
        )
        assertEquals("Profile photo", node.semantics?.get("contentDescription"))
    }
}
