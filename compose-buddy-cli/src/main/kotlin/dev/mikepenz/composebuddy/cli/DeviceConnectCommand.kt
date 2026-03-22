package dev.mikepenz.composebuddy.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import dev.mikepenz.composebuddy.client.BuddyWebSocketClient
import dev.mikepenz.composebuddy.client.DevicePreviewSource
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import dev.mikepenz.composebuddy.inspector.InspectorFeed
import dev.mikepenz.composebuddy.inspector.InspectorSession
import dev.mikepenz.composebuddy.inspector.launchInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

class DeviceConnectCommand : CliktCommand(name = "connect") {
    override fun help(context: Context) = "Connect to a device running BuddyPreviewActivity and open the inspector"

    private val port by option("--port", help = "WebSocket port (default 7890)").int().default(7890)
    private val host by option("--host", help = "Device host (default localhost)").default("localhost")
    private val format by option("--format", help = "Output format: inspector|json|png").default("inspector")
    private val preview by option("--preview", help = "Preview FQN being displayed on device")
    private val output by option("--output", "-o", help = "Output directory — capture one frame, write JSON + PNG, and exit")
    private val stream by option("--stream", help = "Keep streaming frames (default for json/png without --output)").flag()

    override fun run() {
        val client = BuddyWebSocketClient(host = host, port = port)
        val feed = InspectorFeed()
        val source = DevicePreviewSource(client = client, feed = feed)
        source.start()

        // If --output is set, capture a single frame and write to disk
        if (output != null) {
            captureToFiles(source, feed, File(output!!))
            return
        }

        when (format) {
            "inspector" -> launchInspectorMode(source, feed)
            "json" -> streamJson(source, feed)
            "png" -> streamPng(source, feed)
            else -> error("Unknown format: $format. Use inspector, json, or png.")
        }
    }

    /** Capture a single frame from the device, write JSON + PNG to [outputDir], then exit. */
    private fun captureToFiles(source: DevicePreviewSource, feed: InspectorFeed, outputDir: File) {
        outputDir.mkdirs()
        runBlocking {
            // Request capture after connection is established
            launch {
                kotlinx.coroutines.delay(500)
                source.requestCapture()
            }

            val result = withTimeout(15_000L) {
                feed.renders.first()
            }

            // Derive filenames from the preview name
            val safeName = result.previewName.replace('.', '_').ifEmpty { "device-preview" }
            val pngFile = File(outputDir, "$safeName.png")
            val jsonFile = File(outputDir, "$safeName.json")

            // Write PNG
            result.imageBase64?.let { base64 ->
                val bytes = Base64.getDecoder().decode(base64)
                pngFile.writeBytes(bytes)
                echo("Saved image: ${pngFile.absolutePath} (${bytes.size} bytes)")
            } ?: echo("No image data in frame")

            // Write JSON (without imageBase64 to keep it readable, reference the PNG instead)
            val jsonForFile = result.copy(
                imageBase64 = null,
                imagePath = pngFile.absolutePath,
            )
            val json = Json { prettyPrint = true }
            jsonFile.writeText(json.encodeToString(RenderResult.serializer(), jsonForFile))
            echo("Saved frame: ${jsonFile.absolutePath}")

            source.stop()
        }
    }

    private fun launchInspectorMode(source: DevicePreviewSource, feed: InspectorFeed) {
        val previewName = preview ?: "device-preview"
        val session = InspectorSession(
            projectPath = "device:$host:$port",
            modulePath = "device",
            renderTrigger = source,
        )
        session.setPreviewDefs(listOf(Preview(fullyQualifiedName = previewName)))
        session.selectPreview(previewName, 0)

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            feed.renders.collect { result ->
                session.addFrame(result)
                session.cacheResult(result.previewName, 0, result)
            }
        }
        scope.launch {
            kotlinx.coroutines.delay(500)
            source.requestCapture()
        }

        launchInspector(session)
    }

    private fun streamJson(source: DevicePreviewSource, feed: InspectorFeed) {
        val json = Json { prettyPrint = true }
        runBlocking {
            launch {
                kotlinx.coroutines.delay(500)
                source.requestCapture()
            }
            feed.renders.collect { result ->
                println(json.encodeToString(RenderResult.serializer(), result))
            }
        }
    }

    private fun streamPng(source: DevicePreviewSource, feed: InspectorFeed) {
        var frameCount = 0
        runBlocking {
            launch {
                kotlinx.coroutines.delay(500)
                source.requestCapture()
            }
            feed.renders.collect { result ->
                val bytes = result.imageBase64?.let { Base64.getDecoder().decode(it) }
                val file = File("frame-${frameCount++}.png")
                bytes?.let { file.writeBytes(it) }
                println("Saved ${file.name}")
            }
        }
    }
}
