package dev.mikepenz.composebuddy.mcp

import dev.mikepenz.composebuddy.mcp.tools.RenderPreviewTool
import dev.mikepenz.composebuddy.renderer.android.LayoutlibRenderer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RenderPreviewToolTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `execute returns JSON with previewName`() = runBlocking {
        val renderer = LayoutlibRenderer(outputDir = tempDir, skipBridgeInit = true)
        renderer.setup()
        try {
            val queue = RenderQueue(renderer)
            val tool = RenderPreviewTool(queue)
            val result = tool.execute("com.example.TestPreview")
            assertTrue(result.contains("\"previewName\""))
            assertTrue(result.contains("com.example.TestPreview"))
        } finally {
            renderer.teardown()
        }
    }

    @Test
    fun `execute with overrides includes configuration`() = runBlocking {
        val renderer = LayoutlibRenderer(outputDir = tempDir, skipBridgeInit = true)
        renderer.setup()
        try {
            val queue = RenderQueue(renderer)
            val tool = RenderPreviewTool(queue)
            val result = tool.execute("com.example.Test", widthDp = 360, locale = "ja")
            assertTrue(result.contains("\"widthDp\""))
            assertTrue(result.contains("\"locale\""))
        } finally {
            renderer.teardown()
        }
    }
}
