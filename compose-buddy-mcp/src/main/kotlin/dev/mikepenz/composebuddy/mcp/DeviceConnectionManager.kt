package dev.mikepenz.composebuddy.mcp

import dev.mikepenz.composebuddy.client.BuddyWebSocketClient
import dev.mikepenz.composebuddy.client.FrameMapper
import dev.mikepenz.composebuddy.client.model.BuddyFrame
import dev.mikepenz.composebuddy.core.model.RenderResult
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Manages a single shared WebSocket connection to a device running BuddyPreviewActivity.
 * MCP tools call [awaitNextFrame] to get the latest frame as a [RenderResult].
 */
class DeviceConnectionManager(host: String = "localhost", port: Int = 7890) {
    private val client = BuddyWebSocketClient(host = host, port = port)
    private val frames = client.frames()

    /** Wait for the next render frame from the device, with a 10s timeout. */
    suspend fun awaitNextFrame(densityDpi: Int = 420): RenderResult =
        withTimeout(10_000L) {
            val buddyFrame = frames.filterIsInstance<BuddyFrame.RenderFrame>().first()
            FrameMapper.toRenderResult(buddyFrame, densityDpi)
        }

    fun close() = client.close()

    companion object {
        @Volatile
        var shared: DeviceConnectionManager? = null
    }
}
