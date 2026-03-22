package dev.mikepenz.composebuddy.mcp

import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import dev.mikepenz.composebuddy.mcp.tools.AnalyzeAccessibilityTool
import dev.mikepenz.composebuddy.mcp.tools.InspectHierarchyTool
import dev.mikepenz.composebuddy.mcp.tools.ListPreviewsTool
import dev.mikepenz.composebuddy.mcp.tools.RenderPreviewTool
import dev.mikepenz.composebuddy.renderer.PreviewDiscovery
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * MCP server for Compose Buddy.
 * Exposes listPreviews, renderPreview, inspectHierarchy, and analyzeAccessibility tools via the MCP protocol.
 */
class ComposeBuddyMcpServer(
    private val renderer: PreviewRenderer,
    private val classpath: List<File> = emptyList(),
) {
    private val renderQueue = RenderQueue(renderer)
    private val discovery = PreviewDiscovery()

    private val listPreviewsTool = ListPreviewsTool(discovery, classpath)
    private val renderPreviewTool = RenderPreviewTool(renderQueue)
    private val inspectHierarchyTool = InspectHierarchyTool(renderer)
    private val analyzeAccessibilityTool = AnalyzeAccessibilityTool(renderer)

    fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "compose-buddy",
                version = "0.1.1",
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )

        server.addTool(
            name = "list_previews",
            description = "List all @Preview composables discovered in the project. Returns JSON array of preview metadata including FQN, annotation parameters, and source locations.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("module", buildJsonObject { put("type", "string"); put("description", "Gradle module to scan") })
                    put("filter", buildJsonObject { put("type", "string"); put("description", "FQN glob filter") })
                },
            ),
        ) { request: CallToolRequest ->
            val args = request.arguments ?: buildJsonObject {}
            val module = args["module"]?.jsonPrimitive?.content
            val filter = args["filter"]?.jsonPrimitive?.content
            val result = listPreviewsTool.execute(module, filter)
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        server.addTool(
            name = "render_preview",
            description = "Render a @Preview composable to a PNG image. Returns JSON with image path, dimensions, render duration, and optional hierarchy data.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("preview_fqn", buildJsonObject { put("type", "string"); put("description", "Fully qualified name of the @Preview function") })
                    put("width_dp", buildJsonObject { put("type", "integer"); put("description", "Override width in dp") })
                    put("height_dp", buildJsonObject { put("type", "integer"); put("description", "Override height in dp") })
                    put("locale", buildJsonObject { put("type", "string"); put("description", "Override locale (BCP 47)") })
                    put("font_scale", buildJsonObject { put("type", "number"); put("description", "Override font scale") })
                    put("dark_mode", buildJsonObject { put("type", "boolean"); put("description", "Enable dark mode") })
                    put("device", buildJsonObject { put("type", "string"); put("description", "Device ID or spec") })
                    put("include_hierarchy", buildJsonObject { put("type", "boolean"); put("description", "Include layout hierarchy (default: true)") })
                },
                required = listOf("preview_fqn"),
            ),
        ) { request: CallToolRequest ->
            val args = request.arguments ?: buildJsonObject {}
            val fqn = args["preview_fqn"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(content = listOf(TextContent(text = """{"error": "preview_fqn is required"}""")), isError = true)
            val result = renderPreviewTool.execute(
                previewFqn = fqn,
                widthDp = args["width_dp"]?.jsonPrimitive?.content?.toIntOrNull(),
                heightDp = args["height_dp"]?.jsonPrimitive?.content?.toIntOrNull(),
                locale = args["locale"]?.jsonPrimitive?.content,
                fontScale = args["font_scale"]?.jsonPrimitive?.content?.toFloatOrNull(),
                darkMode = args["dark_mode"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
                device = args["device"]?.jsonPrimitive?.content,
                includeHierarchy = args["include_hierarchy"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
            )
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        server.addTool(
            name = "inspect_hierarchy",
            description = "Inspect the layout and semantics hierarchy of a @Preview composable. Returns JSON tree with component names, bounds, and semantic properties.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("preview_fqn", buildJsonObject { put("type", "string"); put("description", "Fully qualified name of the @Preview function") })
                    put("width_dp", buildJsonObject { put("type", "integer"); put("description", "Override width in dp") })
                    put("height_dp", buildJsonObject { put("type", "integer"); put("description", "Override height in dp") })
                    put("dark_mode", buildJsonObject { put("type", "boolean"); put("description", "Enable dark mode") })
                },
                required = listOf("preview_fqn"),
            ),
        ) { request: CallToolRequest ->
            val args = request.arguments ?: buildJsonObject {}
            val fqn = args["preview_fqn"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(content = listOf(TextContent(text = """{"error": "preview_fqn is required"}""")), isError = true)
            val result = inspectHierarchyTool.execute(
                previewFqn = fqn,
                widthDp = args["width_dp"]?.jsonPrimitive?.content?.toIntOrNull(),
                heightDp = args["height_dp"]?.jsonPrimitive?.content?.toIntOrNull(),
                darkMode = args["dark_mode"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
            )
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        server.addTool(
            name = "analyze_accessibility",
            description = "Run accessibility analysis on a @Preview composable. Checks for missing content descriptions, small touch targets, low contrast ratios, and design token compliance.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("preview_fqn", buildJsonObject { put("type", "string"); put("description", "Fully qualified name of the @Preview function") })
                    put("checks", buildJsonObject { put("type", "string"); put("description", "Comma-separated checks: CONTENT_DESCRIPTION, TOUCH_TARGET, CONTRAST, ALL (default: ALL)") })
                },
                required = listOf("preview_fqn"),
            ),
        ) { request: CallToolRequest ->
            val args = request.arguments ?: buildJsonObject {}
            val fqn = args["preview_fqn"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(content = listOf(TextContent(text = """{"error": "preview_fqn is required"}""")), isError = true)
            val checks = args["checks"]?.jsonPrimitive?.content?.split(",")?.map { it.trim() }?.toSet() ?: setOf("ALL")
            val result = analyzeAccessibilityTool.execute(previewFqn = fqn, checks = checks)
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        return server
    }

    fun start() {
        renderer.setup()
    }

    fun stop() {
        renderer.teardown()
    }

    /**
     * Run the MCP server over stdio transport.
     * This blocks until the transport is closed.
     */
    suspend fun runStdio() {
        start()
        try {
            val server = createServer()
            val transport = StdioServerTransport(
                inputStream = System.`in`.asSource().buffered(),
                outputStream = System.out.asSink().buffered(),
            )
            val session = server.createSession(transport)
            // Wait until the session is closed (stdin EOF or client disconnect)
            val closeJob = Job()
            session.onClose { closeJob.complete() }
            closeJob.join()
        } finally {
            stop()
        }
    }
}
