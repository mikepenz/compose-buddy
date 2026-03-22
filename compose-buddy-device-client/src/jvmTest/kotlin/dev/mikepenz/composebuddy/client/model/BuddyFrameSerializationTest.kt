package dev.mikepenz.composebuddy.client.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuddyFrameSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
    private val commandJson = Json { classDiscriminator = "cmd"; ignoreUnknownKeys = true }

    @Test
    fun `BuddyFrame round-trips through JSON`() {
        val frame = BuddyFrame.RenderFrame(
            frameId = 1,
            timestamp = 1712345678901L,
            preview = "com.example.FooKt.MyPreview",
            image = "base64encodedpng==",
            semantics = BuddySemanticNode(
                id = 0,
                name = "Root",
                bounds = BuddyBounds(0.0, 0.0, 360.0, 640.0),
                children = emptyList(),
            ),
            slotTable = listOf(
                BuddySlotEntry(key = 12345, name = "MyPreview", sourceInfo = "Foo.kt:10", depth = 0, children = emptyList()),
            ),
            recompositions = mapOf("MyPreview" to 1),
            frameTiming = BuddyFrameTiming(vsyncTimestamp = 1712345678890L, compositionMs = 4.2, layoutMs = 1.1, drawMs = 2.8),
        )
        val serialized = json.encodeToString(BuddyFrame.serializer(), frame)
        val deserialized = json.decodeFromString(BuddyFrame.serializer(), serialized)
        assertEquals(frame, deserialized)
    }

    @Test
    fun `BuddyFrame has correct type field`() {
        val frame = BuddyFrame.RenderFrame(
            frameId = 1,
            timestamp = 0L,
            preview = "com.example.FooKt.Foo",
            image = "",
            semantics = BuddySemanticNode(0, "Root", bounds = BuddyBounds(0.0, 0.0, 0.0, 0.0), children = emptyList()),
            slotTable = emptyList(),
            recompositions = emptyMap(),
            frameTiming = BuddyFrameTiming(0L, 0.0, 0.0, 0.0),
        )
        val serialized = json.encodeToString(BuddyFrame.serializer(), frame)
        assertTrue(serialized.contains("\"type\":\"frame\""))
    }

    @Test
    fun `PreviewListFrame round-trips through JSON`() {
        val frame = BuddyFrame.PreviewListFrame(
            previews = listOf("com.example.FooKt.Preview1", "com.example.FooKt.Preview2"),
        )
        val serialized = json.encodeToString(BuddyFrame.serializer(), frame)
        assertTrue(serialized.contains("\"type\":\"previewList\""))
        val deserialized = json.decodeFromString(BuddyFrame.serializer(), serialized)
        assertEquals(frame, deserialized)
    }

    @Test
    fun `RerenderCommand serializes to correct JSON`() {
        val cmd: BuddyCommand = BuddyCommand.Rerender
        val serialized = commandJson.encodeToString(BuddyCommand.serializer(), cmd)
        assertEquals("""{"cmd":"rerender"}""", serialized)
    }

    @Test
    fun `TapCommand serializes with coordinates`() {
        val cmd: BuddyCommand = BuddyCommand.Tap(x = 120, y = 340)
        val serialized = commandJson.encodeToString(BuddyCommand.serializer(), cmd)
        assertEquals("""{"cmd":"tap","x":120,"y":340}""", serialized)
    }

    @Test
    fun `RequestPreviewList command serializes correctly`() {
        val cmd: BuddyCommand = BuddyCommand.RequestPreviewList
        val serialized = commandJson.encodeToString(BuddyCommand.serializer(), cmd)
        assertEquals("""{"cmd":"requestPreviewList"}""", serialized)
    }
}
