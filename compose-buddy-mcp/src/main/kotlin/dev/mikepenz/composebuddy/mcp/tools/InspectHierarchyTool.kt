package dev.mikepenz.composebuddy.mcp.tools

import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import dev.mikepenz.composebuddy.mcp.DeviceConnectionManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

class InspectHierarchyTool(
    private val renderer: PreviewRenderer,
) {
    fun execute(
        previewFqn: String,
        includeSemantics: Boolean = true,
        includeModifiers: Boolean = false,
        includeSourceLocations: Boolean = false,
        widthDp: Int? = null,
        heightDp: Int? = null,
        locale: String? = null,
        fontScale: Float? = null,
        darkMode: Boolean? = null,
        source: String = "jvm",
    ): String {
        if (source == "device") {
            val result = runBlocking {
                DeviceConnectionManager.shared?.awaitNextFrame()
                    ?: error("No device connected. Run buddyPreviewDeploy and then compose-buddy device connect.")
            }
            val hierarchy = result.hierarchy
            return if (hierarchy != null) {
                ComposeBuddyJson.encodeToString(hierarchy)
            } else {
                """{"error": "No hierarchy data available from device"}"""
            }
        }

        val preview = Preview(fullyQualifiedName = previewFqn)
        val overrides = RenderConfiguration(
            widthDp = widthDp ?: -1,
            heightDp = heightDp ?: -1,
            locale = locale ?: "",
            fontScale = fontScale ?: 1f,
            uiMode = if (darkMode == true) 0x21 else 0,
        )
        val config = RenderConfiguration.resolve(preview, overrides)
        val hierarchy = renderer.extractHierarchy(preview, config)
        return if (hierarchy != null) {
            ComposeBuddyJson.encodeToString(hierarchy)
        } else {
            """{"error": "No hierarchy data available"}"""
        }
    }
}
