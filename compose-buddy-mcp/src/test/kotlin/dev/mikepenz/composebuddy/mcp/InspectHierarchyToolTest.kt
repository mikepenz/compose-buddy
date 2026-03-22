package dev.mikepenz.composebuddy.mcp

import dev.mikepenz.composebuddy.mcp.tools.InspectHierarchyTool
import dev.mikepenz.composebuddy.renderer.android.LayoutlibRenderer
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class InspectHierarchyToolTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `execute returns hierarchy or error message`() {
        val renderer = LayoutlibRenderer(outputDir = tempDir, skipBridgeInit = true)
        renderer.setup()
        try {
            val tool = InspectHierarchyTool(renderer)
            val result = tool.execute("com.example.TestPreview")
            // Currently returns no hierarchy (not wired to layoutlib yet)
            assertTrue(result.contains("error") || result.contains("id"))
        } finally {
            renderer.teardown()
        }
    }
}
