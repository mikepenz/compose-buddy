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
import dev.mikepenz.composebuddy.core.analysis.AccessibilityAnalyzer
import dev.mikepenz.composebuddy.core.model.DesignToken
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import dev.mikepenz.composebuddy.renderer.PreviewRunner
import dev.mikepenz.composebuddy.renderer.ResourceCollector
import kotlinx.serialization.encodeToString
import java.io.File

class AnalyzeCommand : CliktCommand(name = "analyze") {
    override fun help(context: Context) = "Run accessibility and design spec analysis on @Preview composables"

    private val project by option("--project", help = "Gradle project root")
        .file(mustExist = true, canBeFile = false)
        .default(File("."))

    private val module by option("--module", help = "Gradle module (e.g., :app)")

    private val preview by option("--preview", help = "Preview FQN or glob filter")

    private val output by option("--output", help = "Output directory")
        .file()
        .default(File("build/compose-buddy"))

    private val checks by option("--checks", help = "Comma-separated: content-description,touch-target,contrast,all")
        .default("all")

    private val designTokens by option("--design-tokens", help = "Path to design tokens JSON file")
        .file()

    private val format by option("--format", help = "Output format: json or human")
        .default(if (System.console() != null) "human" else "json")

    private val widthDp by option("--widthDp", "--width", help = "Override width in dp").int()
    private val heightDp by option("--heightDp", "--height", help = "Override height in dp").int()
    private val density by option("--density", help = "Override density in dpi").int()
    private val fontScale by option("--fontScale", "--font-scale", help = "Override font scale").float()
    private val darkMode by option("--dark-mode", help = "Enable dark mode").flag()
    private val rendererType by option("--renderer", help = "Renderer: 'android', 'android-direct', 'desktop', or 'auto'")
        .default("auto")

    override fun run() {
        val overrides = RenderConfiguration(
            widthDp = widthDp ?: -1,
            heightDp = heightDp ?: -1,
            fontScale = fontScale ?: 1f,
            uiMode = if (darkMode) 0x21 else 0,
            densityDpi = density ?: -1,
        )

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
                overrides = if (overrides != RenderConfiguration()) overrides else null,
                projectPath = project.absolutePath,
                modulePath = module ?: "",
            ),
        )

        // Map CLI check names to analyzer check names
        val analyzerChecks = checks.split(",").map { it.trim().lowercase() }.flatMap {
            when (it) {
                "all" -> listOf("ALL")
                "content-description" -> listOf("CONTENT_DESCRIPTION")
                "touch-target" -> listOf("TOUCH_TARGET")
                "contrast" -> listOf("CONTRAST")
                else -> listOf(it.uppercase().replace("-", "_"))
            }
        }.toSet()

        // Load design tokens if provided
        val tokens = designTokens?.let {
            try {
                ComposeBuddyJson.decodeFromString<List<DesignToken>>(it.readText())
            } catch (e: Exception) {
                echo("Warning: Failed to load design tokens: ${e.message}", err = true)
                emptyList()
            }
        } ?: emptyList()

        val analyzer = AccessibilityAnalyzer()
        val allResults = mutableListOf<AnalysisOutput>()
        var totalFindings = 0

        for (result in manifest.results) {
            val hierarchy = result.hierarchy ?: continue
            val analysisResult = analyzer.analyze(
                hierarchy = hierarchy,
                checks = analyzerChecks,
                densityDpi = result.configuration.densityDpi.takeIf { it > 0 } ?: 420,
                designTokens = tokens,
            )
            totalFindings += analysisResult.totalFindings
            allResults.add(AnalysisOutput(
                previewName = result.previewName,
                status = analysisResult.status,
                findings = analysisResult.findings.map { f ->
                    FindingOutput(
                        type = f.type.name,
                        severity = f.severity.name,
                        nodeName = f.nodeName,
                        description = f.description,
                        actualValue = f.actualValue,
                        expectedValue = f.expectedValue,
                    )
                },
            ))
        }

        when (format) {
            "json" -> echo(ComposeBuddyJson.encodeToString(allResults))
            else -> {
                val analyzed = allResults.size
                val passed = allResults.count { it.status == "PASS" }
                echo("Analyzed $analyzed previews: $passed passed, ${analyzed - passed} with findings ($totalFindings total)")
                for (result in allResults) {
                    if (result.findings.isEmpty()) continue
                    echo("  ${result.previewName}:")
                    for (f in result.findings) {
                        echo("    ${f.severity} [${f.type}] ${f.nodeName}: ${f.description}")
                    }
                }
                if (totalFindings == 0) echo("No accessibility issues found.")
            }
        }

        if (totalFindings > 0) throw ProgramResult(1)
    }

    @kotlinx.serialization.Serializable
    private data class AnalysisOutput(
        val previewName: String,
        val status: String,
        val findings: List<FindingOutput>,
    )

    @kotlinx.serialization.Serializable
    private data class FindingOutput(
        val type: String,
        val severity: String,
        val nodeName: String,
        val description: String,
        val actualValue: String? = null,
        val expectedValue: String? = null,
    )
}
