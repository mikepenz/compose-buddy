package dev.mikepenz.composebuddy.inspector

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.model.Size
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class InspectorFeedTest {

    private fun makeResult(name: String) = RenderResult(
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
    fun `emit publishes RenderResult to renders flow`() = runBlocking {
        val feed = InspectorFeed()
        feed.emit(makeResult("TestPreview"))
        val result = feed.renders.first()
        assertEquals("TestPreview", result.previewName)
    }
}
