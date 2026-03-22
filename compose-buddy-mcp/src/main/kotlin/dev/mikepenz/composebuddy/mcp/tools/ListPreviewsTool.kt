package dev.mikepenz.composebuddy.mcp.tools

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import dev.mikepenz.composebuddy.renderer.PreviewDiscovery
import kotlinx.serialization.encodeToString
import java.io.File

class ListPreviewsTool(
    private val discovery: PreviewDiscovery = PreviewDiscovery(),
    private val classpath: List<File> = emptyList(),
) {
    fun execute(module: String?, filter: String?): String {
        val previews = discovery.discover(classpath, filter)
        return ComposeBuddyJson.encodeToString<List<Preview>>(previews)
    }
}
