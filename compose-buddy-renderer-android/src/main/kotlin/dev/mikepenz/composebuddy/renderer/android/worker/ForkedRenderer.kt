package dev.mikepenz.composebuddy.renderer.android.worker

import dev.mikepenz.composebuddy.renderer.android.LayoutlibProvider
import java.io.File

/**
 * Direct Bridge forked renderer. Launches [RenderWorker] in a child JVM
 * for layoutlib-based rendering without Paparazzi.
 */
class ForkedRenderer(
    paths: LayoutlibProvider.LayoutlibPaths,
    projectClasspath: List<File>,
    outputDir: File,
    defaultDensityDpi: Int = 420,
    defaultWidthPx: Int = 1080,
    defaultHeightPx: Int = 1920,
) : AbstractForkedRenderer(paths, projectClasspath, outputDir, defaultDensityDpi, defaultWidthPx, defaultHeightPx) {
    override val workerClassName = "dev.mikepenz.composebuddy.renderer.android.worker.RenderWorker"
    override val workerLabel = "render worker"
}
