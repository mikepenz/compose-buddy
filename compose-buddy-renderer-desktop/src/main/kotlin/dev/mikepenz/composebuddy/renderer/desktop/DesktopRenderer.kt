package dev.mikepenz.composebuddy.renderer.desktop

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import java.io.File

/**
 * Renders Compose Desktop @Preview composables using ImageComposeScene.
 *
 * Self-sustained: no Android SDK or layoutlib needed. Uses Skia-backed
 * headless rendering via Compose Multiplatform Desktop.
 */
class DesktopRenderer(
    private val outputDir: File,
    private val projectClasspath: List<File> = emptyList(),
    private val defaultDensityDpi: Int = 160,
) : PreviewRenderer {

    private var initialized = false
    private var forkedRenderer: DesktopForkedRenderer? = null

    override fun setup() {
        outputDir.mkdirs()

        val renderer = DesktopForkedRenderer(
            projectClasspath = projectClasspath,
            outputDir = outputDir,
            defaultDensityDpi = defaultDensityDpi,
        )
        renderer.start()
        forkedRenderer = renderer
        Logger.i { "Desktop compose renderer ready" }

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

    override fun extractHierarchy(preview: Preview, configuration: RenderConfiguration): HierarchyNode? {
        return render(preview, configuration).hierarchy
    }
}
