package dev.mikepenz.composebuddy.core.serialization

import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderManifest
import dev.mikepenz.composebuddy.core.model.RenderResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestSerializationTest {

    private val json = ComposeBuddyJson

    @Test
    fun `empty manifest round-trips`() {
        val manifest = RenderManifest(
            timestamp = "2026-03-22T12:00:00Z",
            projectPath = "/path/to/project",
        )
        val encoded = json.encodeToString(RenderManifest.serializer(), manifest)
        val decoded = json.decodeFromString(RenderManifest.serializer(), encoded)
        assertEquals(manifest, decoded)
    }

    @Test
    fun `manifest with results round-trips`() {
        val manifest = RenderManifest(
            timestamp = "2026-03-22T12:00:00Z",
            projectPath = "/path/to/project",
            modulePath = ":app",
            totalPreviews = 2,
            totalRendered = 1,
            totalErrors = 1,
            results = listOf(
                RenderResult(
                    previewName = "com.example.ButtonPreview",
                    configuration = RenderConfiguration(widthDp = 360, heightDp = 640),
                    imagePath = "ButtonPreview.png",
                    imageWidth = 1080,
                    imageHeight = 1920,
                    renderDurationMs = 1500,
                ),
            ),
        )
        val encoded = json.encodeToString(RenderManifest.serializer(), manifest)
        val decoded = json.decodeFromString(RenderManifest.serializer(), encoded)
        assertEquals(manifest, decoded)
    }

    @Test
    fun `manifest JSON contains expected fields`() {
        val manifest = RenderManifest(
            timestamp = "2026-03-22T12:00:00Z",
            projectPath = "/test",
        )
        val encoded = json.encodeToString(RenderManifest.serializer(), manifest)
        assertTrue("\"version\"" in encoded, "Missing version field")
        assertTrue("\"timestamp\"" in encoded, "Missing timestamp field")
        assertTrue("\"projectPath\"" in encoded, "Missing projectPath field")
        assertTrue("\"totalPreviews\"" in encoded, "Missing totalPreviews field")
        assertTrue("\"results\"" in encoded, "Missing results field")
    }
}
