package dev.mikepenz.composebuddy.device

import dev.mikepenz.composebuddy.device.model.BuddyCommand
import dev.mikepenz.composebuddy.device.model.BuddyFrame
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class BuddyWebSocketServer(
    private val port: Int = 7890,
    private val onCommand: (BuddyCommand) -> Unit,
) {
    private val sessions = mutableSetOf<WebSocketSession>()
    private val sessionsLock = Any()
    private val frameJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }
    private val commandJson = Json {
        classDiscriminator = "cmd"
        ignoreUnknownKeys = true
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val server = embeddedServer(CIO, port = port) {
        install(WebSockets)
        routing {
            webSocket("/") {
                synchronized(sessionsLock) { sessions.add(this) }
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            runCatching {
                                val cmd = commandJson.decodeFromString(BuddyCommand.serializer(), frame.readText())
                                onCommand(cmd)
                            }
                        }
                    }
                } finally {
                    synchronized(sessionsLock) { sessions.remove(this) }
                }
            }
        }
    }

    fun start() {
        server.start(wait = false)
    }

    fun stop() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 500)
    }

    fun broadcast(buddyFrame: BuddyFrame) {
        val json = frameJson.encodeToString(BuddyFrame.serializer(), buddyFrame)
        scope.launch {
            synchronized(sessionsLock) { sessions.toList() }.forEach { session ->
                runCatching { session.send(Frame.Text(json)) }
            }
        }
    }

    companion object {
        @Volatile
        var instance: BuddyWebSocketServer? = null
    }
}
