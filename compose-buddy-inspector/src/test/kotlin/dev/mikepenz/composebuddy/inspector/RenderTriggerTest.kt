package dev.mikepenz.composebuddy.inspector

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.model.Size
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RenderTriggerTest {

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

    @Test
    fun `triggerRerender on success returns current frame without appending`() = runBlocking {
        // Frames are appended by the feed collector (e.g. launchInspectorMode), not by triggerRerender itself.
        val trigger = object : RenderTrigger {
            override suspend fun rerender(config: RenderConfig?) = makeResult("rerendered")
        }
        val session = InspectorSession(".", "", trigger)
        session.addFrame(makeResult("initial"))
        assertEquals(1, session.frames.size)

        val frame = session.triggerRerender()
        assertNotNull(frame)
        assertEquals(1, session.frames.size)
        assertEquals("initial", session.frames.last().renderResult.previewName)
        assertNull(session.lastRenderError)
    }

    @Test
    fun `triggerRerender on failure sets error without appending frame`() = runBlocking {
        val trigger = object : RenderTrigger {
            override suspend fun rerender(config: RenderConfig?): dev.mikepenz.composebuddy.core.model.RenderResult = throw RenderException("Compilation failed")
        }
        val session = InspectorSession(".", "", trigger)
        session.addFrame(makeResult("initial"))

        val frame = session.triggerRerender()
        assertNull(frame)
        assertEquals(1, session.frames.size)
        assertEquals("Compilation failed", session.lastRenderError)
    }
}
