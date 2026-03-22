package dev.mikepenz.composebuddy.renderer.android.worker

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.renderer.DeviceConfigMapper
import dev.mikepenz.composebuddy.renderer.android.LayoutlibProvider
import dev.mikepenz.composebuddy.renderer.worker.BaseForkedRenderer
import dev.mikepenz.composebuddy.renderer.worker.WorkerProtocol
import java.io.File

/**
 * Android-specific forked renderer base. Adds layoutlib paths, device config mapping,
 * Desktop JAR filtering, and android.jar discovery to [BaseForkedRenderer].
 *
 * Subclasses only need to specify [workerClassName] and [workerLabel].
 */
abstract class AbstractForkedRenderer(
    private val paths: LayoutlibProvider.LayoutlibPaths,
    projectClasspath: List<File>,
    outputDir: File,
    defaultDensityDpi: Int = 420,
    defaultWidthPx: Int = 1080,
    defaultHeightPx: Int = 1920,
) : BaseForkedRenderer(projectClasspath, outputDir, defaultDensityDpi, defaultWidthPx, defaultHeightPx) {

    override fun buildClasspath(): List<String> {
        val androidJar = findAndroidJar()

        // Try to use the pre-built worker JAR (embedded as a resource in the CLI fat JAR).
        // This eliminates fat JAR filtering — the worker JAR contains only the classes
        // needed by the Android renderer, without any desktop-specific dependencies.
        val workerJar = extractWorkerJar("worker-android.jar")
        if (workerJar.isNotEmpty()) {
            Logger.d { "Using embedded Android worker JAR" }
            return listOf(workerJar) +
                projectClasspath.map { it.absolutePath } +
                listOfNotNull(androidJar?.absolutePath)
        }

        // Fallback: running from exploded classpath (e.g., Gradle run task during development).
        // Use the host classpath directly, filtering out desktop-specific entries.
        Logger.d { "No embedded worker JAR, using host classpath (dev mode)" }
        val currentClasspath = System.getProperty("java.class.path")
        val classpathEntries = currentClasspath.split(File.pathSeparator)
        val filteredCliClasspath = classpathEntries.filter { entry ->
            val name = File(entry).name
            !name.contains("-desktop-") || name.contains("compose-buddy")
        }
        return filteredCliClasspath +
            projectClasspath.map { it.absolutePath } +
            listOfNotNull(androidJar?.absolutePath)
    }

    override fun buildJvmArgs(): List<String> {
        val nativeLibPath = File(paths.runtimeRoot, "data/${LayoutlibProvider.getNativeLibSubdir()}").absolutePath
        return listOf(
            "-XX:+EnableDynamicAgentLoading",
            "-Djava.library.path=$nativeLibPath",
        )
    }

    override fun createRenderRequest(preview: Preview, configuration: RenderConfiguration): WorkerProtocol.RenderRequest {
        val layoutlibConfig = DeviceConfigMapper.map(preview, configuration)
        return WorkerProtocol.RenderRequest(
            previewFqn = preview.fullyQualifiedName,
            previewName = preview.name,
            sourceFile = preview.fileName,
            sourceLine = preview.lineNumber,
            outputDir = outputDir.absolutePath,
            widthPx = layoutlibConfig.screenWidthPx,
            heightPx = layoutlibConfig.screenHeightPx,
            densityDpi = layoutlibConfig.densityDpi,
            uiMode = layoutlibConfig.uiMode,
            fontScale = layoutlibConfig.fontScale,
            showBackground = layoutlibConfig.showBackground,
            backgroundColor = layoutlibConfig.backgroundColor,
            layoutlibRuntimeRoot = paths.runtimeRoot.absolutePath,
            layoutlibResourcesRoot = paths.resourcesRoot.absolutePath,
        )
    }

    override fun createInitRequest(): WorkerProtocol.RenderRequest = WorkerProtocol.RenderRequest(
        previewFqn = "__init__",
        outputDir = outputDir.absolutePath,
        widthPx = defaultWidthPx,
        heightPx = defaultHeightPx,
        densityDpi = defaultDensityDpi,
        layoutlibRuntimeRoot = paths.runtimeRoot.absolutePath,
        layoutlibResourcesRoot = paths.resourcesRoot.absolutePath,
    )

    override fun mapResultConfig(
        configuration: RenderConfiguration,
        request: WorkerProtocol.RenderRequest,
    ): RenderConfiguration = configuration.copy(
        densityDpi = request.densityDpi,
        screenWidthPx = request.widthPx,
        screenHeightPx = request.heightPx,
    )
}
