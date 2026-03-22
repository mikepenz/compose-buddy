package dev.mikepenz.composebuddy.renderer.android.paparazzi.worker

import dev.mikepenz.composebuddy.renderer.android.LayoutlibProvider
import dev.mikepenz.composebuddy.renderer.android.worker.AbstractForkedRenderer
import java.io.File

/**
 * Paparazzi-based forked renderer. Launches [PaparazziRenderWorker] in a child JVM
 * for proven, production-quality ComposeView lifecycle management.
 */
class PaparazziForkedRenderer(
    paths: LayoutlibProvider.LayoutlibPaths,
    projectClasspath: List<File>,
    outputDir: File,
    defaultDensityDpi: Int = 420,
    defaultWidthPx: Int = 1080,
    defaultHeightPx: Int = 1920,
) : AbstractForkedRenderer(paths, projectClasspath, outputDir, defaultDensityDpi, defaultWidthPx, defaultHeightPx) {
    override val workerClassName = "dev.mikepenz.composebuddy.renderer.android.paparazzi.worker.PaparazziRenderWorker"
    override val workerLabel = "Paparazzi render worker"
}
