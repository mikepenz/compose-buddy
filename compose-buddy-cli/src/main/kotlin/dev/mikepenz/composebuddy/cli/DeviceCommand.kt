package dev.mikepenz.composebuddy.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import dev.mikepenz.composebuddy.client.BuddyWebSocketClient
import dev.mikepenz.composebuddy.client.DevicePreviewSource
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderResult
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Base64

class DeviceCommand : CliktCommand(name = "device") {
    override val invokeWithoutSubcommand = true
    override fun help(context: Context) = "Inspect Compose previews running on a device or emulator"

    private val project by option("--project", help = "Gradle project root directory").file(
        mustExist = true,
        canBeFile = false,
    ).default(File("."))
    private val module by option("--module", help = "Gradle module path (e.g. :app or :feature:detail)")
    private val preview by option("--preview", help = "Preview FQN or glob pattern (e.g. 'com.example.*')")
    private val port by option("--port", help = "WebSocket port (default 7890)").int().default(7890)
    private val device by option("--device", help = "ADB device serial (optional)")
    private val format by option("--format", help = "Output format: inspector|json|png").default("inspector")
    private val output by option("--output", "-o", help = "Output directory — capture frame(s), write JSON + PNG, and exit")
    private val skipBuild by option("--skip-build", help = "Skip Gradle build step").flag()
    private val skipInstall by option("--skip-install", help = "Skip APK installation step").flag()
    private val list by option("--list", help = "List available previews and exit").flag()
    private val flavor by option("--flavor", help = "Product flavor name (required when the module declares flavors)")
    private val variant by option("--variant", help = "Explicit variant name (overrides --flavor; e.g. 'devBuddyDebug')")

    override fun run() {
        // When no --module is provided this command acts as a parent for subcommands.
        val mod = module ?: return

        val projectDir = project.absoluteFile

        // --list: print available previews and exit
        if (list) {
            listPreviews(projectDir, mod)
            return
        }

        // Resolve variant + APK directory. Precedence: --variant > --flavor > auto-detect.
        val moduleDirName = mod.trimStart(':').replace(':', '/')
        val moduleBuildDir = File(projectDir, "$moduleDirName/build")
        val apkBaseDir = File(moduleBuildDir, "outputs/apk")

        val (variantName, apkDir) = resolveVariantAndApkDir(apkBaseDir)
        val assembleTask = "assemble${variantName.replaceFirstChar { it.uppercase() }}"

        // Step 1 – Build
        if (!skipBuild) {
            echo("Building $mod:$assembleTask...")
            val buildResult = ProcessRunner.runGradle(
                projectDir, "$mod:$assembleTask",
                streamOutput = true,
            )
            if (buildResult.exitCode != 0) {
                echo("Build failed (exit ${buildResult.exitCode}).", err = true)
                throw RuntimeException("Gradle build failed")
            }
        }

        // Step 2 – Locate APK
        val apkFile = apkDir.listFiles { f -> f.extension == "apk" }?.firstOrNull()
            ?: run {
                echo("APK not found in ${apkDir.absolutePath}", err = true)
                throw RuntimeException("APK not found. Run the build first or check the module path.")
            }

        // Step 3 – Install
        if (!skipInstall) {
            echo("Installing APK...")
            val installResult = ProcessRunner.runAdb(device, "install", "-r", apkFile.absolutePath)
            if (installResult.exitCode != 0) {
                echo("Install failed: ${installResult.stderr.ifBlank { installResult.stdout }}", err = true)
                throw RuntimeException("adb install failed")
            }
        }

        // Step 4 – Determine preview FQN(s)
        // If --preview is a glob or omitted, resolve to the actual list of matches.
        val allPreviews: List<String>
        val launchFqn: String
        if (preview != null && !preview!!.contains('*') && !preview!!.contains('?')) {
            // Exact FQN — use as-is, no listing needed
            allPreviews = listOf(preview!!)
            launchFqn = preview!!
        } else {
            echo("Discovering previews...")
            val result = ProcessRunner.runGradle(projectDir, "$mod:buddyPreviewList", "--quiet")
            if (result.exitCode != 0) {
                echo("Preview list failed: ${result.stderr.ifBlank { result.stdout }}", err = true)
                throw RuntimeException("buddyPreviewList failed")
            }
            val discovered = parsePreviews(result.stdout)
            allPreviews = if (preview != null) {
                val filtered = discovered.filter { matchesGlob(it, preview!!) }
                if (filtered.isEmpty()) {
                    echo("No previews matched glob pattern '${preview}'. Available previews:", err = true)
                    discovered.forEach { echo("  $it", err = true) }
                    throw RuntimeException("No previews matched pattern '${preview}'")
                }
                echo("Matched ${filtered.size} preview(s) for pattern '${preview}':")
                filtered.forEach { echo("  $it") }
                filtered
            } else {
                if (discovered.isEmpty()) {
                    echo("buddyPreviewList output: ${result.stdout.ifBlank { "(empty)" }}", err = true)
                    echo(
                        "Hint: ensure the compose-buddy plugin is up-to-date and " +
                            "kspBuddyDebugKotlin has run successfully.",
                        err = true,
                    )
                    throw RuntimeException("No previews found in module $mod")
                }
                discovered
            }
            launchFqn = allPreviews.first()
        }
        echo("Launching preview: $launchFqn")

        // Step 5 – ADB port-forward
        val forwardResult = ProcessRunner.runAdb(device, "forward", "tcp:$port", "tcp:$port")
        if (forwardResult.exitCode != 0) {
            echo("Port forward failed: ${forwardResult.stderr.ifBlank { forwardResult.stdout }}", err = true)
            throw RuntimeException("adb forward failed")
        }

        // Step 6 – Derive namespace via aapt2
        val namespace = deriveAppId(apkFile)
        echo("Detected namespace: $namespace")

        // Step 7 – Launch activity with the first (or only) preview
        val activityComponent = "$namespace/dev.mikepenz.composebuddy.device.BuddyPreviewActivity"
        val launchResult = ProcessRunner.runAdb(
            device, "shell", "am", "start",
            "-n", activityComponent,
            "-e", "preview", launchFqn,
            "--ei", "port", port.toString(),
        )
        if (launchResult.exitCode != 0) {
            echo("Activity launch failed: ${launchResult.stderr.ifBlank { launchResult.stdout }}", err = true)
            throw RuntimeException("adb am start failed")
        }
        echo("Activity launched. Connecting to WebSocket on port $port...")

        // Step 8 – Connect
        val client = BuddyWebSocketClient(host = "localhost", port = port)
        val feed = InspectorFeed()
        val source = DevicePreviewSource(client = client, feed = feed)
        source.start()

        if (output != null) {
            captureToFiles(source, feed, File(output!!), allPreviews)
            return
        }

        when (format) {
            "inspector" -> launchInspectorMode(source, feed, launchFqn, allPreviews)
            "json" -> streamJson(source, feed)
            "png" -> streamPng(source, feed)
            else -> error("Unknown format: $format. Use inspector, json, or png.")
        }
    }

    // ---- Preview listing -------------------------------------------------------

    private fun resolveVariantAndApkDir(apkBaseDir: File): Pair<String, File> {
        // Explicit --variant wins.
        variant?.let { v ->
            val dir = locateApkDirForVariant(apkBaseDir, v)
                ?: error("APK directory not found for --variant '$v'. Expected under ${apkBaseDir.absolutePath}.")
            return v to dir
        }
        // --flavor: compute <flavor>BuddyDebug.
        flavor?.let { f ->
            val v = "${f}BuddyDebug"
            val dir = File(apkBaseDir, "$f/buddyDebug")
            return v to dir
        }
        // Auto-detect.
        val unflavored = File(apkBaseDir, "buddyDebug")
        if (unflavored.isDirectory) return "buddyDebug" to unflavored

        val flavorDirs = apkBaseDir.listFiles { f ->
            f.isDirectory && File(f, "buddyDebug").isDirectory
        }?.sortedBy { it.name } ?: emptyList()

        when (flavorDirs.size) {
            0 -> error(
                "No buddyDebug output found under ${apkBaseDir.absolutePath}. " +
                    "Run the build first or pass --flavor/--variant."
            )
            1 -> {
                val f = flavorDirs.single().name
                return "${f}BuddyDebug" to File(flavorDirs.single(), "buddyDebug")
            }
            else -> {
                val names = flavorDirs.joinToString(", ") { it.name }
                error("Multiple flavors detected ($names). Pass --flavor <name> or --variant <name>.")
            }
        }
    }

    private fun locateApkDirForVariant(apkBaseDir: File, variantName: String): File? {
        if (variantName == "buddyDebug") {
            return File(apkBaseDir, "buddyDebug").takeIf { it.isDirectory }
        }
        // Convention: <flavor>BuddyDebug — APK dir path is build/outputs/apk/<flavor>/buddyDebug
        val flavor = variantName.removeSuffix("BuddyDebug").ifEmpty { return null }
        val lowered = flavor.replaceFirstChar { it.lowercase() }
        return File(apkBaseDir, "$lowered/buddyDebug").takeIf { it.isDirectory }
    }

    private fun listPreviews(projectDir: File, mod: String) {
        val result = ProcessRunner.runGradle(projectDir, "$mod:buddyPreviewList", "--quiet")
        if (result.exitCode != 0) {
            echo("Preview list failed: ${result.stderr.ifBlank { result.stdout }}", err = true)
            throw RuntimeException("buddyPreviewList failed")
        }
        val previews = parsePreviews(result.stdout)
        if (previews.isEmpty()) {
            echo("No previews found in module $mod")
        } else {
            previews.forEach { echo(it) }
        }
    }

    private fun parsePreviews(output: String): List<String> {
        val trimmed = output.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val json = Json.parseToJsonElement(trimmed)
            json.jsonArray.map { element ->
                element.jsonObject["fqn"]?.jsonPrimitive?.content
                    ?: element.jsonObject["fullyQualifiedName"]?.jsonPrimitive?.content
                    ?: element.jsonPrimitive.content
            }
        } catch (_: Exception) {
            // Fallback: treat each non-blank line as a preview FQN
            trimmed.lines().filter { it.isNotBlank() }
        }
    }

    /** Match an FQN against a glob pattern supporting `*` (any segment chars) and `**` (any chars). */
    private fun matchesGlob(fqn: String, pattern: String): Boolean {
        val regex = buildString {
            var i = 0
            while (i < pattern.length) {
                when {
                    pattern[i] == '*' && i + 1 < pattern.length && pattern[i + 1] == '*' -> {
                        append(".*")
                        i += 2
                    }
                    pattern[i] == '*' -> {
                        append("[^.]*")
                        i++
                    }
                    pattern[i] == '?' -> {
                        append("[^.]")
                        i++
                    }
                    else -> {
                        append(Regex.escape(pattern[i].toString()))
                        i++
                    }
                }
            }
        }.toRegex()
        return regex.matches(fqn)
    }

    // ---- aapt2 namespace derivation -------------------------------------------

    private fun deriveAppId(apkFile: File): String {
        return try {
            val aapt2 = findAapt2()
            val result = ProcessRunner.execute(
                listOf(aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apkFile.absolutePath),
            )
            if (result.exitCode != 0) {
                error("aapt2 dump failed (exit ${result.exitCode}): ${result.stderr.ifBlank { result.stdout }}")
            }
            Regex("""A: package="([^"]+)"""").find(result.stdout)?.groupValues?.get(1)
                ?: error("Could not parse applicationId from APK manifest.\n${result.stdout}")
        } catch (e: Exception) {
            error("Could not determine applicationId. Ensure aapt2 is on PATH.\n${e.message}")
        }
    }

    private fun findAapt2(): String {
        val sdkDir = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: return "aapt2"
        val buildTools = File("$sdkDir/build-tools")
        val latest = buildTools.listFiles()?.sortedDescending()?.firstOrNull()
        return "${latest?.absolutePath}/aapt2"
    }

    // ---- Connection logic -------------------------------------------------------

    /**
     * Capture one frame per preview in [previews] by navigating between them and saving output.
     * If [previews] has a single entry, captures the already-displayed preview directly.
     */
    private fun captureToFiles(
        source: DevicePreviewSource,
        feed: InspectorFeed,
        outputDir: File,
        previews: List<String>,
    ) {
        outputDir.mkdirs()
        runBlocking {
            for ((index, fqn) in previews.withIndex()) {
                echo("Capturing preview ${index + 1}/${previews.size}: $fqn")
                val result = if (index == 0) {
                    // First preview is already displayed — just wait for the initial frame
                    withTimeout(15_000L) { feed.renders.first() }
                } else {
                    // Navigate to next preview (device auto-captures after switching)
                    source.navigate(fqn)
                }

                val safeName = fqn.replace('.', '_').ifEmpty { "device-preview-$index" }
                val pngFile = File(outputDir, "$safeName.png")
                val jsonFile = File(outputDir, "$safeName.json")

                result.imageBase64?.let { base64 ->
                    val bytes = Base64.getDecoder().decode(base64)
                    pngFile.writeBytes(bytes)
                    echo("  Saved image: ${pngFile.absolutePath} (${bytes.size} bytes)")
                } ?: echo("  No image data for $fqn")

                val jsonForFile = result.copy(imageBase64 = null, imagePath = pngFile.absolutePath)
                val json = Json { prettyPrint = true }
                jsonFile.writeText(json.encodeToString(RenderResult.serializer(), jsonForFile))
                echo("  Saved data:  ${jsonFile.absolutePath}")
            }
            source.stop()
        }
    }

    private fun launchInspectorMode(
        source: DevicePreviewSource,
        feed: InspectorFeed,
        initialPreview: String,
        knownPreviews: List<String>,
    ) {
        val session = InspectorSession(
            projectPath = "device:localhost:$port",
            modulePath = "device",
            renderTrigger = source,
        )

        // Seed with CLI-known previews immediately so the spotlight is populated
        session.setPreviewDefs(knownPreviews.map { Preview(fullyQualifiedName = it) })
        session.selectPreview(initialPreview, 0)

        val scope = CoroutineScope(Dispatchers.Default)

        // Collect incoming render frames into the session
        scope.launch {
            feed.renders.collect { result ->
                session.addFrame(result)
                session.cacheResult(result.previewName, 0, result)
            }
        }

        // After connecting, ask the device for its full registry (may include more previews
        // than were discovered via buddyPreviewList — e.g. generated multi-preview entries)
        scope.launch {
            try {
                val devicePreviews = source.fetchPreviewList()
                if (devicePreviews.isNotEmpty()) {
                    val merged = (knownPreviews + devicePreviews).distinct()
                    session.setPreviewDefs(merged.map { Preview(fullyQualifiedName = it) })
                }
            } catch (_: Exception) {
                // Non-fatal — the CLI-discovered list is already set
            }
        }

        launchInspector(session)
    }

    private fun streamJson(source: DevicePreviewSource, feed: InspectorFeed) {
        val json = Json { prettyPrint = true }
        runBlocking {
            feed.renders.collect { result ->
                println(json.encodeToString(RenderResult.serializer(), result))
            }
        }
    }

    private fun streamPng(source: DevicePreviewSource, feed: InspectorFeed) {
        var frameCount = 0
        runBlocking {
            feed.renders.collect { result ->
                val bytes = result.imageBase64?.let { Base64.getDecoder().decode(it) }
                val file = File("frame-${frameCount++}.png")
                bytes?.let { file.writeBytes(it) }
                println("Saved ${file.name}")
            }
        }
    }
}
