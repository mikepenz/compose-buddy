package dev.mikepenz.composebuddy.renderer.android

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import dev.mikepenz.composebuddy.renderer.DeviceConfigMapper
import dev.mikepenz.composebuddy.renderer.android.worker.ForkedRenderer
import java.io.File

/**
 * Renders Compose @Preview composables using Android's layoutlib engine.
 *
 * Self-sustained: automatically downloads and caches layoutlib binaries
 * from Google Maven on first use. No Gradle plugin required.
 *
 * Rendering happens in a forked JVM process that has both layoutlib and
 * the project's Compose dependencies on its classpath, enabling it to
 * load and render the actual @Composable functions.
 */
class LayoutlibRenderer(
    private val outputDir: File,
    private val layoutlibPaths: LayoutlibProvider.LayoutlibPaths? = null,
    private val projectResourceDirs: List<File> = emptyList(),
    private val projectClasspath: List<File> = emptyList(),
    private val defaultDensityDpi: Int = 420,
    private val skipBridgeInit: Boolean = false,
) : PreviewRenderer {

    private var initialized = false
    private var resolvedPaths: LayoutlibProvider.LayoutlibPaths? = null
    private var forkedRenderer: ForkedRenderer? = null

    override fun setup() {
        outputDir.mkdirs()

        resolvedPaths = layoutlibPaths ?: run {
            val provider = LayoutlibProvider()
            provider.provide()
        }

        val paths = resolvedPaths ?: error("Failed to resolve layoutlib paths")
        if (!skipBridgeInit) {
            val renderer = ForkedRenderer(
                paths = paths,
                projectClasspath = projectClasspath,
                outputDir = outputDir,
                defaultDensityDpi = defaultDensityDpi,
            )
            renderer.start()
            forkedRenderer = renderer
            Logger.i { "Compose renderer ready (long-running worker)" }
        }

        initialized = true
    }

    override fun teardown() {
        forkedRenderer?.stop()
        forkedRenderer = null
        initialized = false
    }

    override fun render(preview: Preview, configuration: RenderConfiguration): RenderResult {
        check(initialized) { "Renderer not initialized. Call setup() first." }

        val renderer = forkedRenderer
        return if (renderer != null) {
            renderer.render(preview, configuration)
        } else {
            // Placeholder mode for tests
            placeholderRender(preview, configuration)
        }
    }

    override fun extractHierarchy(preview: Preview, configuration: RenderConfiguration): HierarchyNode? {
        return render(preview, configuration).hierarchy
    }

    private fun placeholderRender(preview: Preview, configuration: RenderConfiguration): RenderResult {
        val layoutlibConfig = DeviceConfigMapper.map(preview, configuration)
        val image = java.awt.image.BufferedImage(
            layoutlibConfig.screenWidthPx,
            layoutlibConfig.screenHeightPx,
            java.awt.image.BufferedImage.TYPE_INT_ARGB,
        )
        val g = image.createGraphics()
        g.color = java.awt.Color(245, 245, 245)
        g.fillRect(0, 0, image.width, image.height)
        g.dispose()

        val fileName = preview.fullyQualifiedName.replace('.', '_') + ".png"
        val outputFile = File(outputDir, fileName)
        javax.imageio.ImageIO.write(image, "png", outputFile)

        return RenderResult(
            previewName = preview.fullyQualifiedName,
            configuration = configuration.copy(
                densityDpi = layoutlibConfig.densityDpi,
                screenWidthPx = layoutlibConfig.screenWidthPx,
                screenHeightPx = layoutlibConfig.screenHeightPx,
            ),
            imagePath = outputFile.absolutePath,
            imageWidth = image.width,
            imageHeight = image.height,
            renderDurationMs = 0,
        )
    }
}
