package dev.mikepenz.composebuddy.inspector

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.model.Size
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InspectorSessionTest {

    private fun makeResult(name: String = "TestPreview") = RenderResult(
        previewName = name,
        configuration = RenderConfiguration(),
        imagePath = "/tmp/test.png",
        imageWidth = 360,
        imageHeight = 640,
        hierarchy = HierarchyNode(
            id = 0,
            name = "Root",
            bounds = Bounds(0.0, 0.0, 360.0, 640.0),
            size = Size(360.0, 640.0),
        ),
    )

    private val noopTrigger = object : RenderTrigger {
        override suspend fun rerender(config: RenderConfig?): dev.mikepenz.composebuddy.core.model.RenderResult = makeResult("rerendered")
    }

    @Test
    fun `addFrame adds frame and updates currentFrameIndex`() {
        val session = InspectorSession(".", "", noopTrigger, maxFrames = 10)
        val frame = session.addFrame(makeResult())
        assertEquals(0, frame.id)
        assertEquals(0, session.currentFrameIndex)
        assertEquals(1, session.frames.size)
    }

    @Test
    fun `addFrame respects maxFrames cap with FIFO eviction`() {
        val session = InspectorSession(".", "", noopTrigger, maxFrames = 3)
        session.addFrame(makeResult("a"))
        session.addFrame(makeResult("b"))
        session.addFrame(makeResult("c"))
        assertEquals(3, session.frames.size)

        session.addFrame(makeResult("d"))
        assertEquals(3, session.frames.size)
        // First frame ("a") should be evicted
        assertEquals("b", session.frames[0].renderResult.previewName)
        assertEquals("d", session.frames[2].renderResult.previewName)
    }

    @Test
    fun `navigateToFrame updates currentFrameIndex`() {
        val session = InspectorSession(".", "", noopTrigger)
        session.addFrame(makeResult("a"))
        session.addFrame(makeResult("b"))
        session.addFrame(makeResult("c"))

        session.navigateToFrame(0)
        assertEquals(0, session.currentFrameIndex)
        assertEquals("a", session.currentFrame?.renderResult?.previewName)
    }

    @Test
    fun `currentFrame returns null when no frames`() {
        val session = InspectorSession(".", "", noopTrigger)
        assertNull(session.currentFrame)
    }
}
