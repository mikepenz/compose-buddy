package dev.mikepenz.composebuddy.renderer.android

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutlibRendererIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `render produces image file with correct dimensions`() {
        val renderer = LayoutlibRenderer(outputDir = tempDir, skipBridgeInit = true)
        renderer.setup()
        try {
            val preview = Preview(fullyQualifiedName = "com.example.TestPreview")
            val config = RenderConfiguration(widthDp = -1, heightDp = -1)
            val result = renderer.render(preview, config)

            assertNull(result.error, "Expected no error but got: ${result.error?.message}")
            assertTrue(File(result.imagePath).exists(), "Image file should exist")
            assertEquals(1080, result.imageWidth)
            assertEquals(1920, result.imageHeight)
            assertTrue(result.renderDurationMs >= 0)
        } finally {
            renderer.teardown()
        }
    }

    @Test
    fun `render with custom dimensions`() {
        val renderer = LayoutlibRenderer(outputDir = tempDir, skipBridgeInit = true)
        renderer.setup()
        try {
            val preview = Preview(fullyQualifiedName = "com.example.CustomSize")
            val config = RenderConfiguration(widthDp = 200, heightDp = 400, device = "id:pixel_5")
            val result = renderer.render(preview, config)

            assertNull(result.error)
            // 200dp * 440dpi/160 = 550px, 400dp * 440dpi/160 = 1100px
            assertEquals(550, result.imageWidth)
            assertEquals(1100, result.imageHeight)
        } finally {
            renderer.teardown()
        }
    }

    @Test
    fun `render without setup throws`() {
        val renderer = LayoutlibRenderer(outputDir = tempDir, skipBridgeInit = true)
        val preview = Preview(fullyQualifiedName = "com.example.Test")
        val config = RenderConfiguration()
        try {
            renderer.render(preview, config)
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not initialized") == true)
        }
    }

    @Test
    fun `render with background color produces image`() {
        val renderer = LayoutlibRenderer(outputDir = tempDir, skipBridgeInit = true)
        renderer.setup()
        try {
            val preview = Preview(fullyQualifiedName = "com.example.WithBg")
            val config = RenderConfiguration(showBackground = true, backgroundColor = 0xFFFFFFFF)
            val result = renderer.render(preview, config)
            assertNull(result.error)
            assertTrue(File(result.imagePath).exists())
        } finally {
            renderer.teardown()
        }
    }

    @Test
    fun `file name uses FQN with underscores`() {
        val renderer = LayoutlibRenderer(outputDir = tempDir, skipBridgeInit = true)
        renderer.setup()
        try {
            val preview = Preview(fullyQualifiedName = "com.example.ui.ButtonPreview")
            val config = RenderConfiguration()
            val result = renderer.render(preview, config)
            assertTrue(result.imagePath.endsWith("com_example_ui_ButtonPreview.png"))
        } finally {
            renderer.teardown()
        }
    }
}
