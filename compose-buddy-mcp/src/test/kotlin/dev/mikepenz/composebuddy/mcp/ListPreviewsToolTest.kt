package dev.mikepenz.composebuddy.mcp

import dev.mikepenz.composebuddy.mcp.tools.ListPreviewsTool
import kotlin.test.Test
import kotlin.test.assertTrue

class ListPreviewsToolTest {

    @Test
    fun `execute returns valid JSON array`() {
        val tool = ListPreviewsTool()
        val result = tool.execute(null, null)
        assertTrue(result.startsWith("["))
        assertTrue(result.endsWith("]"))
    }

    @Test
    fun `execute with empty classpath returns empty array`() {
        val tool = ListPreviewsTool()
        val result = tool.execute(null, null)
        assertTrue(result.contains("[]") || result.trim() == "[ ]" || result.trim() == "[\n]" || result.trim().replace("\\s".toRegex(), "") == "[]")
    }
}
