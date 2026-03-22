package dev.mikepenz.composebuddy.mcp

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes render requests to enforce layoutlib's single-session constraint.
 * Multiple MCP connections can queue requests; they are executed one at a time.
 */
class RenderQueue(private val renderer: PreviewRenderer) {
    private val mutex = Mutex()

    suspend fun render(preview: Preview, configuration: RenderConfiguration): RenderResult {
        return mutex.withLock {
            renderer.render(preview, configuration)
        }
    }
}
