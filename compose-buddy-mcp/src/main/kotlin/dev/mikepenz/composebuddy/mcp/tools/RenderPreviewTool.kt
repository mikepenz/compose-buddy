package dev.mikepenz.composebuddy.mcp.tools

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import dev.mikepenz.composebuddy.mcp.DeviceConnectionManager
import dev.mikepenz.composebuddy.mcp.RenderQueue
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.Base64

class RenderPreviewTool(
    private val renderQueue: RenderQueue,
) {
    suspend fun execute(
        previewFqn: String,
        widthDp: Int? = null,
        heightDp: Int? = null,
        locale: String? = null,
        fontScale: Float? = null,
        darkMode: Boolean? = null,
        device: String? = null,
        includeHierarchy: Boolean = true,
        source: String = "jvm",
    ): String {
        if (source == "device") {
            val deviceResult = DeviceConnectionManager.shared?.awaitNextFrame()
                ?: error("No device connected. Run buddyPreviewDeploy and then compose-buddy device connect.")
            return ComposeBuddyJson.encodeToString(deviceResult)
        }

        val preview = Preview(fullyQualifiedName = previewFqn)
        val overrides = RenderConfiguration(
            widthDp = widthDp ?: -1,
            heightDp = heightDp ?: -1,
            locale = locale ?: "",
            fontScale = fontScale ?: 1f,
            uiMode = if (darkMode == true) 0x21 else 0,
            device = device ?: "",
        )
        val config = RenderConfiguration.resolve(preview, overrides)
        val result = renderQueue.render(preview, config)

        // Convert image file to base64 if available
        val resultWithBase64 = if (result.imagePath.isNotEmpty() && File(result.imagePath).exists()) {
            val imageBytes = File(result.imagePath).readBytes()
            val base64 = Base64.getEncoder().encodeToString(imageBytes)
            result.copy(imageBase64 = base64)
        } else {
            result
        }

        return ComposeBuddyJson.encodeToString(resultWithBase64)
    }
}
