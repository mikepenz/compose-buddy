package dev.mikepenz.composebuddy.renderer.android.paparazzi

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import dev.mikepenz.composebuddy.renderer.android.LayoutlibProvider
import dev.mikepenz.composebuddy.renderer.android.paparazzi.worker.PaparazziForkedRenderer
import java.io.File

/**
 * Renders Compose @Preview composables using Paparazzi + layoutlib.
 *
 * Paparazzi provides proven ComposeView lifecycle management including
 * Recomposer, Choreographer frame advancement, and WindowRecomposerPolicy.
 */
class PaparazziLayoutlibRenderer(
    private val outputDir: File,
    private val layoutlibPaths: LayoutlibProvider.LayoutlibPaths? = null,
    private val projectResourceDirs: List<File> = emptyList(),
    private val projectClasspath: List<File> = emptyList(),
    private val defaultDensityDpi: Int = 420,
) : PreviewRenderer {

    private var initialized = false
    private var resolvedPaths: LayoutlibProvider.LayoutlibPaths? = null
    private var forkedRenderer: PaparazziForkedRenderer? = null

    override fun setup() {
        outputDir.mkdirs()

        resolvedPaths = layoutlibPaths ?: run {
            val provider = LayoutlibProvider()
            provider.provide()
        }

        val paths = resolvedPaths ?: error("Failed to resolve layoutlib paths")
        val renderer = PaparazziForkedRenderer(
            paths = paths,
            projectClasspath = projectClasspath,
            outputDir = outputDir,
            defaultDensityDpi = defaultDensityDpi,
        )
        renderer.start()
        forkedRenderer = renderer
        Logger.i { "Paparazzi compose renderer ready" }

        initialized = true
    }

    override fun teardown() {
        forkedRenderer?.stop()
        forkedRenderer = null
        initialized = false
    }

    override fun render(preview: Preview, configuration: RenderConfiguration): RenderResult {
        check(initialized) { "Renderer not initialized. Call setup() first." }
        return forkedRenderer!!.render(preview, configuration)
    }

    override fun extractHierarchy(preview: Preview, configuration: RenderConfiguration): HierarchyNode? =
        render(preview, configuration).hierarchy
}
