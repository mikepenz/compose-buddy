package dev.mikepenz.composebuddy.renderer.desktop

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.renderer.worker.BaseForkedRenderer
import dev.mikepenz.composebuddy.renderer.worker.WorkerProtocol
import java.io.File

/**
 * Desktop Compose forked renderer. Launches [DesktopRenderWorker] in a child JVM
 * for headless Skia-based rendering via ImageComposeScene.
 */
class DesktopForkedRenderer(
    projectClasspath: List<File>,
    outputDir: File,
    defaultDensityDpi: Int = 160,
    defaultWidthPx: Int = 1280,
    defaultHeightPx: Int = 800,
) : BaseForkedRenderer(projectClasspath, outputDir, defaultDensityDpi, defaultWidthPx, defaultHeightPx) {

    override val workerClassName = "dev.mikepenz.composebuddy.renderer.desktop.DesktopRenderWorker"
    override val workerLabel = "Desktop render worker"

    override fun buildClasspath(): List<String> {
        // Try to use the pre-built worker JAR (embedded as a resource in the CLI fat JAR).
        val workerJar = extractWorkerJar("worker-desktop.jar")
        if (workerJar.isNotEmpty()) {
            Logger.d { "Using embedded Desktop worker JAR" }
            // Worker JAR goes first to provide the Compose Desktop rendering stack.
            // For libraries, Compose is only on compileClasspath (not runtime), so the
            // worker's bundled Compose fills in. For apps, the project's classpath has
            // Compose too — classpath order gives the worker's version precedence, which
            // ensures consistent rendering behavior.
            return listOf(workerJar) +
                projectClasspath.map { it.absolutePath }
        }

        // Fallback: running from exploded classpath (e.g., Gradle run task during development).
        val currentClasspath = System.getProperty("java.class.path")
        return currentClasspath.split(File.pathSeparator) +
            projectClasspath.map { it.absolutePath }
    }

    override fun buildEnvironment(): Map<String, String> = mapOf(
        // Force software rendering for headless/CI environments
        "SKIKO_RENDER_API" to "SOFTWARE",
    )

    override fun createRenderRequest(preview: Preview, configuration: RenderConfiguration): WorkerProtocol.RenderRequest {
        val dpi = if (configuration.densityDpi > 0) configuration.densityDpi else defaultDensityDpi
        val widthPx = if (configuration.widthDp > 0) (configuration.widthDp * dpi / 160f).toInt() else defaultWidthPx
        val heightPx = if (configuration.heightDp > 0) (configuration.heightDp * dpi / 160f).toInt() else defaultHeightPx

        return WorkerProtocol.RenderRequest(
            previewFqn = preview.fullyQualifiedName,
            previewName = preview.name,
            sourceFile = preview.fileName,
            sourceLine = preview.lineNumber,
            outputDir = outputDir.absolutePath,
            widthPx = widthPx,
            heightPx = heightPx,
            densityDpi = dpi,
            uiMode = configuration.uiMode,
            fontScale = configuration.fontScale,
        )
    }

    override fun mapResultConfig(
        configuration: RenderConfiguration,
        request: WorkerProtocol.RenderRequest,
    ): RenderConfiguration = configuration.copy(
        densityDpi = request.densityDpi,
        screenWidthPx = request.widthPx,
        screenHeightPx = request.heightPx,
    )
}
