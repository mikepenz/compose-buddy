package dev.mikepenz.composebuddy.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.float
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import dev.mikepenz.composebuddy.core.model.AgentOutputBuilder
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderManifest
import dev.mikepenz.composebuddy.core.serialization.AgentJson
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import dev.mikepenz.composebuddy.renderer.PreviewRunner
import dev.mikepenz.composebuddy.renderer.ResourceCollector
import kotlinx.serialization.encodeToString
import java.io.File

class RenderCommand : CliktCommand(name = "render") {
    override fun help(context: Context) = "Render @Preview composables as PNG images"

    // --- Project options ---
    private val project by option("--project", help = "Gradle project root")
        .file(mustExist = true, canBeFile = false)
        .default(File("."))
    private val module by option("--module", help = "Gradle module (e.g., :app)")
    private val preview by option("--preview", help = "Preview FQN or glob filter")
    private val output by option("--output", help = "Output directory")
        .file()
        .default(File("build/compose-buddy"))

    // --- @Preview parameter overrides (matching annotation exactly) ---
    private val widthDp by option("--widthDp", "--width", help = "Override width in dp (matches @Preview widthDp)")
        .int()
    private val heightDp by option("--heightDp", "--height", help = "Override height in dp (matches @Preview heightDp)")
        .int()
    private val density by option("--density", help = "Override density in dpi (e.g., 420, 480, 560)")
        .int()
    private val locale by option("--locale", help = "Override locale (BCP 47, e.g., 'en', 'ja')")
    private val fontScale by option("--fontScale", "--font-scale", help = "Override font scale (e.g., 1.0, 1.5, 2.0)")
        .float()
    private val darkMode by option("--dark-mode", help = "Enable dark mode (sets uiMode to UI_MODE_NIGHT_YES)")
        .flag()
    private val uiMode by option("--uiMode", help = "Override uiMode bitmask (e.g., 0x21 for night mode)")
        .int()
    private val device by option("--device", help = "Device ID or spec (e.g., 'id:pixel_5', 'spec:width=1080px,height=1920px,dpi=420')")
    private val showBackground by option("--showBackground", "--show-background", help = "Show background behind composable")
        .flag()
    private val backgroundColor by option("--backgroundColor", "--background-color", help = "Background ARGB color (e.g., 0xFFFFFFFF)")
        .long()
    private val showSystemUi by option("--showSystemUi", "--show-system-ui", help = "Show system UI decorations")
        .flag()
    private val apiLevel by option("--apiLevel", "--api-level", help = "Target SDK API level for rendering")
        .int()

    // --- Output options ---
    private val format by option("--format", help = "Output format: json, human, or agent")
        .default(if (System.console() != null) "human" else "json")
    private val agent by option("--agent", help = "AI agent output: condensed JSON with only what's needed for UI validation (text, colors, layout, accessibility issues, image paths)")
        .flag()
    private val hierarchy by option("--hierarchy", help = "Include hierarchy JSON in output")
        .flag()
    private val semanticsOpt by option("--semantics",
        help = "Semantic fields to include: 'all' for everything, 'default' for minimal, or comma-separated names (e.g., 'text,role,onClick')")
    private val build by option("--build", help = "Build the project before rendering (auto-detects compile task)")
        .flag()
    private val rendererType by option("--renderer", help = "Renderer backend: 'android' (Paparazzi+layoutlib), 'android-direct' (layoutlib only), 'desktop' (ImageComposeScene), or 'auto' (detect from classpath)")
        .default("auto")
    private val maxParams by option("--max-params", help = "Max @PreviewParameter values to render")
        .int()
        .default(10)
    private val diagnose by option("--diagnose", help = "Print diagnostic info (classpath, versions, architecture) and exit")
        .flag()

    override fun run() {
        if (diagnose) {
            runDiagnose()
            return
        }

        val overrides = RenderConfiguration(
            widthDp = widthDp ?: -1,
            heightDp = heightDp ?: -1,
            locale = locale ?: "",
            fontScale = fontScale ?: 1f,
            uiMode = uiMode ?: if (darkMode) 0x21 else 0,
            device = device ?: "",
            showBackground = showBackground,
            backgroundColor = backgroundColor ?: 0L,
            apiLevel = apiLevel ?: -1,
            densityDpi = density ?: -1,
        )
        val effectiveOverrides = if (overrides != RenderConfiguration()) overrides else null

        // Discover which modules to scan
        val modules = if (module != null) {
            listOf(module!!)
        } else {
            val collector = ResourceCollector(project, null)
            val allModules = collector.discoverModules()
            val withPreviews = collector.discoverModulesWithPreviews()
            if (withPreviews.isEmpty()) {
                echo("No modules with @Preview annotations found in ${project.absolutePath}", err = true)
                echo("  Scanned ${allModules.size} modules. Try --build first, or specify --module explicitly.", err = true)
                throw ProgramResult(2)
            }
            echo("Auto-discovered ${withPreviews.size} module(s) with previews: ${withPreviews.joinToString(", ")}", err = true)
            withPreviews
        }

        // Build if requested
        if (build) {
            for (mod in modules) {
                val rc = ResourceCollector(project, mod)
                if (!rc.build()) {
                    echo("Build failed for $mod", err = true)
                    throw ProgramResult(3)
                }
            }
        }

        // Render each module and collect results
        val allManifests = mutableListOf<RenderManifest>()
        for (mod in modules) {
            val resourceCollector = ResourceCollector(project, mod)
            // Resolve platform-isolated classpaths. Each renderer gets ONLY its platform's
            // dependencies, preventing Desktop/Android variant mixing.
            val discoveryClasspath = resourceCollector.resolveClasspath("all")
            val desktopClasspath = resourceCollector.resolveClasspath("jvm")
            val androidClasspath = resourceCollector.resolveClasspath("android")
            val resourceConfig = resourceCollector.collect()
            val projectResourceDirs = resourceConfig.projectResourceDirs.map { File(it) }

            val renderer = RendererFactory.create(
                rendererType = rendererType,
                desktopClasspath = desktopClasspath,
                androidClasspath = androidClasspath,
                outputDir = output,
                density = density,
                projectResourceDirs = projectResourceDirs,
            )
            val runner = PreviewRunner(renderer = renderer)

            val manifest = runner.run(
                PreviewRunner.RunConfig(
                    classpath = discoveryClasspath,
                    outputDir = output,
                    previewFilter = preview,
                    overrides = effectiveOverrides,
                    maxPreviewParams = maxParams,
                    projectPath = project.absolutePath,
                    modulePath = mod,
                ),
            )
            allManifests.add(manifest)
        }

        // Merge manifests from all modules
        val merged = mergeManifests(allManifests)
        outputResults(merged)

        if (merged.totalErrors > 0 && merged.totalRendered == 0) throw ProgramResult(2)
        else if (merged.totalErrors > 0) throw ProgramResult(1)
    }

    private fun runDiagnose() {
        echo("=== compose-buddy diagnostics ===")
        echo("JVM: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        echo("OS: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        echo("Renderer: $rendererType")

        // Check classpath for worker JARs
        val workerResources = listOf("workers/worker-android.jar", "workers/worker-desktop.jar")
        for (res in workerResources) {
            val found = javaClass.classLoader.getResourceAsStream(res) != null
            echo("Worker JAR [$res]: ${if (found) "embedded" else "NOT FOUND"}")
        }

        // Check Android SDK
        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        echo("ANDROID_HOME: ${sdkRoot ?: "not set"}")

        // Check project classpath
        val collector = ResourceCollector(project, module)
        val classpath = try { collector.resolveClasspath() } catch (e: Exception) {
            echo("Classpath resolution failed: ${e.message}", err = true)
            emptyList()
        }
        echo("Project classpath: ${classpath.size} entries")

        // Check for Compose runtime on classpath
        val composeRuntime = classpath.any { name ->
            val n = name.name
            n.contains("compose-runtime") || n.contains("compose_runtime") ||
                (n.startsWith("runtime-") && (n.contains("-desktop-") || n.contains("-android-") || n.contains("-jvm-")))
        }
        echo("Compose runtime on classpath: $composeRuntime")

        // Check for Android classes on classpath
        val hasAndroidClasses = classpath.any { it.name.contains("android") && !it.name.contains("compose-buddy") }
        echo("Android classes on classpath: $hasAndroidClasses")

        // Cache directory
        val cacheDir = File(System.getProperty("user.home"), ".compose-buddy/cache")
        echo("Cache dir: ${cacheDir.absolutePath} (exists=${cacheDir.exists()})")
        if (cacheDir.exists()) {
            cacheDir.walkTopDown().filter { it.isFile }.forEach {
                echo("  ${it.relativeTo(cacheDir)} (${it.length() / 1024}KB)")
            }
        }
    }

    private fun mergeManifests(manifests: List<RenderManifest>): RenderManifest {
        if (manifests.size == 1) return manifests.first()
        val allResults = manifests.flatMap { it.results }
        val allSemantics = manifests.flatMap { it.availableSemantics }.distinct().sorted()
        return RenderManifest(
            timestamp = manifests.first().timestamp,
            projectPath = manifests.first().projectPath,
            modulePath = manifests.joinToString(", ") { it.modulePath },
            totalPreviews = allResults.size,
            totalRendered = allResults.count { it.error == null },
            totalErrors = allResults.count { it.error != null },
            densityDpi = manifests.firstOrNull { it.totalRendered > 0 }?.densityDpi ?: 420,
            availableSemantics = allSemantics,
            results = allResults,
        )
    }

    private fun outputResults(manifest: RenderManifest) {
        val effectiveFormat = if (agent) "agent" else format
        when (effectiveFormat) {
            "agent" -> {
                val agentOutput = AgentOutputBuilder.build(manifest)
                echo(AgentJson.encodeToString(agentOutput))
            }
            "json" -> echo(ComposeBuddyJson.encodeToString(manifest))
            else -> {
                val fallbackCount = manifest.results.count { it.rendererUsed == "android-fallback" }
                val rendererInfo = if (fallbackCount > 0) ", $fallbackCount via android fallback" else ""
                echo("Rendered ${manifest.totalRendered}/${manifest.totalPreviews} previews (density=${manifest.densityDpi}dpi$rendererInfo)")
                if (manifest.totalErrors > 0) echo("Errors: ${manifest.totalErrors}")
                for (result in manifest.results) {
                    val err = result.error
                    val renderer = if (result.rendererUsed == "android-fallback") " [android]" else ""
                    if (err != null) {
                        echo("  FAIL: ${result.previewName} - ${err.message}")
                    } else {
                        echo("  OK: ${result.previewName} -> ${result.imagePath}$renderer (${result.renderDurationMs}ms)")
                    }
                }
                echo("Output: ${output.absolutePath}")
            }
        }
    }

}
