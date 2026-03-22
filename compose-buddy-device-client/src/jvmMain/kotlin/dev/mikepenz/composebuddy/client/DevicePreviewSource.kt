package dev.mikepenz.composebuddy.client

import dev.mikepenz.composebuddy.client.model.BuddyCommand
import dev.mikepenz.composebuddy.client.model.BuddyFrame
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.inspector.InspectorFeed
import dev.mikepenz.composebuddy.inspector.RenderConfig
import dev.mikepenz.composebuddy.inspector.RenderTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Bridges the device WebSocket stream into the inspector's [InspectorFeed].
 * Implements [RenderTrigger] so the inspector UI's re-render button sends a
 * "rerender" command to the device.
 */
class DevicePreviewSource(
    private val client: BuddyWebSocketClient,
    private val feed: InspectorFeed,
    private val densityDpi: Int = 420,
) : RenderTrigger {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _previewList = MutableSharedFlow<List<String>>(replay = 1)
    /** Emits whenever the device responds to a [BuddyCommand.RequestPreviewList] command. */
    val previewList: SharedFlow<List<String>> = _previewList.asSharedFlow()

    fun start() {
        scope.launch {
            client.connected.collect {
                client.sendCommand(BuddyCommand.Rerender)
            }
        }
        scope.launch {
            client.frames().collect { buddyFrame ->
                when (buddyFrame) {
                    is BuddyFrame.RenderFrame -> {
                        val result = FrameMapper.toRenderResult(buddyFrame, densityDpi)
                        feed.emit(result)
                    }
                    is BuddyFrame.PreviewListFrame -> {
                        _previewList.emit(buddyFrame.previews)
                    }
                }
            }
        }
    }

    /** Request the device to capture and send a frame. */
    suspend fun requestCapture() {
        client.sendCommand(BuddyCommand.Rerender)
    }

    /**
     * Request the list of available previews from the device.
     * Returns the list once the device responds (timeout 5s).
     */
    suspend fun fetchPreviewList(): List<String> {
        client.sendCommand(BuddyCommand.RequestPreviewList)
        return withTimeout(5_000L) {
            previewList.first()
        }
    }

    fun stop() {
        scope.cancel()
        client.close()
    }

    override suspend fun rerender(config: RenderConfig?): RenderResult {
        // Capture the replay-buffered frame before sending so we can skip it below.
        val priorFrame = feed.renders.replayCache.firstOrNull()
        client.sendCommand(BuddyCommand.Rerender)
        return withTimeout(10_000L) {
            feed.renders.filter { it !== priorFrame }.first()
        }
    }

    override suspend fun navigate(fqn: String, config: RenderConfig?): RenderResult {
        // Capture the replay-buffered frame before sending so we can skip it below.
        val priorFrame = feed.renders.replayCache.firstOrNull()
        client.sendCommand(BuddyCommand.Navigate(fqn))
        return withTimeout(10_000L) {
            feed.renders.filter { it !== priorFrame && it.previewName == fqn }.first()
        }
    }
}
