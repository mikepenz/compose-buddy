package dev.mikepenz.composebuddy.client

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.client.model.BuddyCommand
import dev.mikepenz.composebuddy.client.model.BuddyFrame
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

private val log = Logger.withTag("BuddyWebSocketClient")

class BuddyWebSocketClient(
    private val host: String = "localhost",
    private val port: Int = 7890,
    private val maxRetries: Int = 3,
) {
    private val frameJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
    private val commandJson = Json { classDiscriminator = "cmd"; ignoreUnknownKeys = true }
    private var session: WebSocketSession? = null

    private val _connected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits once each time a WebSocket connection is (re)established. */
    val connected = _connected.asSharedFlow()

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    /**
     * Connect and emit [BuddyFrame] objects as they arrive.
     * Reconnects up to [maxRetries] times on disconnect.
     */
    fun frames(): Flow<BuddyFrame> = flow {
        var attempts = 0
        while (attempts <= maxRetries) {
            try {
                client.webSocket(host = host, port = port, path = "/") {
                    session = this
                    attempts = 0
                    _connected.tryEmit(Unit)
                    log.i { "Connected to device WebSocket at ws://$host:$port" }
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            runCatching {
                                val buddyFrame = frameJson.decodeFromString(BuddyFrame.serializer(), frame.readText())
                                emit(buddyFrame)
                            }.onFailure { e -> log.e(e) { "Failed to parse frame" } }
                        }
                    }
                }
            } catch (e: Exception) {
                log.w(e) { "WebSocket disconnected (attempt $attempts/$maxRetries)" }
                attempts++
                if (attempts <= maxRetries) delay(1_000L * attempts)
            }
        }
        log.e { "Max retries reached — giving up connection to ws://$host:$port" }
    }

    suspend fun sendCommand(cmd: BuddyCommand) {
        val json = commandJson.encodeToString(BuddyCommand.serializer(), cmd)
        session?.send(Frame.Text(json))
    }

    fun close() {
        client.close()
    }
}
