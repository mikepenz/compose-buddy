package dev.mikepenz.composebuddy.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.float
import com.github.ajalt.clikt.parameters.types.int
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import dev.mikepenz.composebuddy.inspector.RenderTrigger
import dev.mikepenz.composebuddy.inspector.createLazySession
import dev.mikepenz.composebuddy.inspector.createSession
import dev.mikepenz.composebuddy.inspector.launchInspector
import dev.mikepenz.composebuddy.renderer.PreviewDiscovery
import dev.mikepenz.composebuddy.renderer.PreviewRunner
import dev.mikepenz.composebuddy.renderer.ResourceCollector
import kotlinx.serialization.encodeToString
import java.io.File

class InspectCommand : CliktCommand(name = "inspect") {
    override fun help(context: Context) = "Inspect layout hierarchy of @Preview composables"

    private val project by option("--project", help = "Gradle project root")
        .file(mustExist = true, canBeFile = false)
        .default(File("."))

    private val module by option("--module", help = "Gradle module (e.g., :app)")

    private val preview by option("--preview", help = "Preview FQN or glob filter")

    private val ui by option("--ui", help = "Launch interactive inspector window")
        .flag()

    private val semantics by option("--semantics", help = "Include semantics")
        .flag(default = true)

    private val modifiers by option("--modifiers", help = "Include modifier chain")
        .flag()

    private val sourceLocations by option("--source-locations", help = "Include source file/line")
        .flag()

    private val format by option("--format", help = "Output format: json or human")
        .default("json")

    private val width by option("--width", help = "Override width in dp").int()
    private val height by option("--height", help = "Override height in dp").int()
    private val localeOpt by option("--locale", help = "Override locale").default("")
    private val fontScale by option("--font-scale", help = "Override font scale").float()
    private val darkMode by option("--dark-mode", help = "Enable dark mode").flag()
    private val rendererType by option("--renderer", help = "Renderer: 'android', 'android-direct', 'desktop', or 'auto'")
        .default("auto")
    private val maxFrames by option("--max-frames", help = "Frame history limit for inspector UI")
        .int()
        .default(100)

    override fun run() {
        val overrides = RenderConfiguration(
            widthDp = width ?: -1,
            heightDp = height ?: -1,
            locale = localeOpt,
            fontScale = fontScale ?: 1f,
            uiMode = if (darkMode) 0x21 else 0,
        )

        val outputDir = File(project, "build/compose-buddy/hierarchy")

        val resourceCollector = ResourceCollector(project, module)
        val discoveryClasspath = resourceCollector.resolveClasspath("all")
        val desktopClasspath = resourceCollector.resolveClasspath("jvm")
        val androidClasspath = resourceCollector.resolveClasspath("android")
        val resourceConfig = resourceCollector.collect()
        val projectResourceDirs = resourceConfig.projectResourceDirs.map { File(it) }

        val renderer = RendererFactory.create(
            rendererType = rendererType,
            desktopClasspath = desktopClasspath,
            androidClasspath = androidClasspath,
            outputDir = outputDir,
            density = null,
            projectResourceDirs = projectResourceDirs,
        )
        val runner = PreviewRunner(renderer = renderer)

        val runConfig = PreviewRunner.RunConfig(
            classpath = discoveryClasspath,
            outputDir = outputDir,
            previewFilter = preview,
            overrides = if (overrides != RenderConfiguration()) overrides else null,
            projectPath = project.absolutePath,
            modulePath = module ?: "",
        )

        if (ui) {
            // Lazy mode: discover previews without rendering, render on demand
            val discovery = PreviewDiscovery()
            val allPreviews = discovery.discover(discoveryClasspath)
            val filteredPreviews = if (preview != null) {
                allPreviews.filter { p ->
                    if (preview!!.contains('*')) {
                        val regex = preview!!.replace(".", "\\.").replace("*", ".*").toRegex()
                        regex.matches(p.fullyQualifiedName)
                    } else {
                        p.fullyQualifiedName == preview || p.fullyQualifiedName.startsWith("$preview.")
                    }
                }
            } else allPreviews

            if (filteredPreviews.isEmpty()) {
                echo("No previews found")
                return
            }

            // Set up renderer (kept alive for the session, swappable)
            var currentRenderer = renderer
            currentRenderer.setup()

            var sessionRef: dev.mikepenz.composebuddy.inspector.InspectorSession? = null

            fun createRendererForType(type: dev.mikepenz.composebuddy.inspector.RendererType): dev.mikepenz.composebuddy.core.renderer.PreviewRenderer {
                val typeName = when (type) {
                    dev.mikepenz.composebuddy.inspector.RendererType.AUTO -> "auto"
                    dev.mikepenz.composebuddy.inspector.RendererType.DESKTOP -> "desktop"
                    dev.mikepenz.composebuddy.inspector.RendererType.ANDROID -> "android-direct"
                }
                return RendererFactory.create(
                    rendererType = typeName,
                    desktopClasspath = desktopClasspath,
                    androidClasspath = androidClasspath,
                    outputDir = outputDir,
                    density = null,
                    projectResourceDirs = projectResourceDirs,
                )
            }

            val renderTrigger = object : RenderTrigger {
                override suspend fun rerender(config: dev.mikepenz.composebuddy.inspector.RenderConfig?): dev.mikepenz.composebuddy.core.model.RenderResult {
                    val session = sessionRef ?: throw dev.mikepenz.composebuddy.inspector.RenderException("No session")
                    val previewName = session.selectedPreviewName ?: throw dev.mikepenz.composebuddy.inspector.RenderException("No preview selected")
                    val variantIndex = session.selectedVariantIndex

                    val variants = filteredPreviews.filter { it.fullyQualifiedName == previewName }
                    val previewDef = variants.getOrElse(variantIndex) { variants.firstOrNull() }
                        ?: throw dev.mikepenz.composebuddy.inspector.RenderException("Preview not found: $previewName")

                    val hasConfigOverrides = config != null && (config.widthDp > 0 || config.heightDp > 0 || config.densityDpi > 0 || config.uiMode >= 0)
                    val renderOverrides = if (hasConfigOverrides) {
                        RenderConfiguration(
                            widthDp = if (config!!.widthDp > 0) config.widthDp else overrides.widthDp,
                            heightDp = if (config.heightDp > 0) config.heightDp else overrides.heightDp,
                            locale = overrides.locale,
                            fontScale = overrides.fontScale,
                            uiMode = if (config.uiMode >= 0) config.uiMode else overrides.uiMode,
                            densityDpi = if (config.densityDpi > 0) config.densityDpi else -1,
                        )
                    } else if (overrides != RenderConfiguration()) overrides else null

                    val renderConfig = RenderConfiguration.resolve(previewDef, renderOverrides)
                    val result = currentRenderer.render(previewDef, renderConfig)
                    // No InspectorFeed in static CLI mode — publish the frame directly so the UI shows it.
                    session.addFrame(result)
                    return result
                }
            }

            val session = createLazySession(
                projectPath = project.absolutePath,
                modulePath = module ?: "",
                previewDefs = filteredPreviews,
                renderTrigger = renderTrigger,
                maxFrames = maxFrames,
            )
            sessionRef = session

            try {
                launchInspector(
                    session = session,
                    onRendererChanged = { type ->
                        // Swap renderer: teardown old, create and setup new
                        currentRenderer.teardown()
                        currentRenderer = createRendererForType(type)
                        currentRenderer.setup()
                    },
                )
            } finally {
                currentRenderer.teardown()
            }
        } else {
            // Non-UI mode: render all upfront
            val manifest = runner.run(runConfig)
            // Original CLI output
            for (result in manifest.results) {
                val hierarchy = result.hierarchy
                if (hierarchy != null) {
                    when (format) {
                        "json" -> echo(ComposeBuddyJson.encodeToString(hierarchy))
                        else -> printHierarchyHuman(hierarchy, 0)
                    }
                } else {
                    echo("No hierarchy data available for ${result.previewName}")
                }
            }
        }
    }

    private fun printHierarchyHuman(node: dev.mikepenz.composebuddy.core.model.HierarchyNode, indent: Int) {
        val prefix = "  ".repeat(indent)
        echo("$prefix${node.name} [${node.size.width}x${node.size.height}] @ (${node.bounds.left},${node.bounds.top})-(${node.bounds.right},${node.bounds.bottom})")
        if (semantics && node.semantics != null) {
            for ((key, value) in node.semantics) {
                echo("$prefix  $key=$value")
            }
        }
        for (child in node.children) {
            printHierarchyHuman(child, indent + 1)
        }
    }
}
