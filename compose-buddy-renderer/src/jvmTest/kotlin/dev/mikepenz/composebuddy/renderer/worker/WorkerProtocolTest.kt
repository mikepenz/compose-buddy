package dev.mikepenz.composebuddy.renderer.worker

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkerProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- RenderRequest serialization ---

    @Test
    fun `RenderRequest round-trips through JSON`() {
        val request = WorkerProtocol.RenderRequest(
            previewFqn = "com.example.MyPreviewKt.MyPreview",
            previewName = "MyPreview",
            sourceFile = "MyPreview.kt",
            sourceLine = 42,
            outputDir = "/tmp/output",
            widthPx = 1080,
            heightPx = 1920,
            densityDpi = 420,
            uiMode = 0x21,
            fontScale = 1.5f,
            showBackground = true,
            backgroundColor = 0xFFFF0000,
            layoutlibRuntimeRoot = "/path/to/runtime",
            layoutlibResourcesRoot = "/path/to/resources",
        )
        val jsonStr = json.encodeToString(WorkerProtocol.RenderRequest.serializer(), request)
        val decoded = json.decodeFromString(WorkerProtocol.RenderRequest.serializer(), jsonStr)
        assertEquals(request, decoded)
    }

    @Test
    fun `RenderRequest defaults work for Desktop subset`() {
        // Desktop only sends a subset of fields; others should have sensible defaults
        val jsonStr = """{"previewFqn":"com.example.Test","outputDir":"/tmp","widthPx":1280,"heightPx":800,"densityDpi":160,"fontScale":1.0}"""
        val request = json.decodeFromString(WorkerProtocol.RenderRequest.serializer(), jsonStr)
        assertEquals("com.example.Test", request.previewFqn)
        assertEquals(1280, request.widthPx)
        assertEquals(160, request.densityDpi)
        assertEquals(0, request.uiMode) // default
        assertEquals(false, request.showBackground) // default
        assertEquals("", request.layoutlibRuntimeRoot) // default
    }

    @Test
    fun `RenderRequest ignores unknown keys`() {
        val jsonStr = """{"previewFqn":"test","outputDir":"/tmp","unknownField":"value","anotherUnknown":123}"""
        val request = json.decodeFromString(WorkerProtocol.RenderRequest.serializer(), jsonStr)
        assertEquals("test", request.previewFqn)
    }

    // --- RenderResponse serialization ---

    @Test
    fun `RenderResponse success round-trips`() {
        val response = WorkerProtocol.RenderResponse(
            previewFqn = "com.example.Test",
            success = true,
            imagePath = "/tmp/test.png",
            imageWidth = 1080,
            imageHeight = 1920,
            durationMs = 150,
            densityDpi = 420,
        )
        val jsonStr = json.encodeToString(WorkerProtocol.RenderResponse.serializer(), response)
        val decoded = json.decodeFromString(WorkerProtocol.RenderResponse.serializer(), jsonStr)
        assertEquals(response, decoded)
        assertTrue(decoded.success)
        assertNull(decoded.error)
    }

    @Test
    fun `RenderResponse error round-trips`() {
        val response = WorkerProtocol.RenderResponse(
            previewFqn = "com.example.Test",
            success = false,
            error = "ClassNotFoundException: com.example.Missing",
        )
        val jsonStr = json.encodeToString(WorkerProtocol.RenderResponse.serializer(), response)
        val decoded = json.decodeFromString(WorkerProtocol.RenderResponse.serializer(), jsonStr)
        assertEquals(false, decoded.success)
        assertEquals("ClassNotFoundException: com.example.Missing", decoded.error)
    }

    @Test
    fun `RenderResponse with hierarchy round-trips`() {
        val hierarchy = dev.mikepenz.composebuddy.core.model.HierarchyNode(
            id = 0, name = "Button",
            bounds = dev.mikepenz.composebuddy.core.model.Bounds(0.0, 0.0, 100.0, 48.0),
            size = dev.mikepenz.composebuddy.core.model.Size(100.0, 48.0),
            semantics = mapOf("text" to "Click me", "onClick" to "true"),
            children = emptyList(),
        )
        val response = WorkerProtocol.RenderResponse(
            previewFqn = "com.example.Test",
            success = true,
            hierarchy = hierarchy,
        )
        val jsonStr = json.encodeToString(WorkerProtocol.RenderResponse.serializer(), response)
        val decoded = json.decodeFromString(WorkerProtocol.RenderResponse.serializer(), jsonStr)
        assertEquals("Button", decoded.hierarchy?.name)
        assertEquals("Click me", decoded.hierarchy?.semantics?.get("text"))
    }
}
