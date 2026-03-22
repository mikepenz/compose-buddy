package dev.mikepenz.composebuddy.renderer.worker

import dev.mikepenz.composebuddy.core.model.HierarchyNode
import kotlinx.serialization.Serializable

/**
 * Shared protocol types for the forked JVM worker process communication.
 * Used by all rendering backends (Android direct, Paparazzi, Desktop).
 *
 * Workers receive [RenderRequest] as JSON lines on stdin,
 * and send [RenderResponse] as JSON lines on stdout.
 * Workers that don't use certain fields simply ignore them (ignoreUnknownKeys = true).
 */
object WorkerProtocol {

    @Serializable
    data class RenderRequest(
        val previewFqn: String,
        val previewName: String = "",
        val sourceFile: String = "",
        val sourceLine: Int = -1,
        val outputDir: String = "",
        val widthPx: Int = 1080,
        val heightPx: Int = 1920,
        val densityDpi: Int = 420,
        val uiMode: Int = 0,
        val fontScale: Float = 1f,
        val showBackground: Boolean = false,
        val backgroundColor: Long = 0L,
        val layoutlibRuntimeRoot: String = "",
        val layoutlibResourcesRoot: String = "",
        val previewParameterProviderClass: String = "",
        val previewParameterProviderIndex: Int = -1,
    )

    @Serializable
    data class RenderResponse(
        val previewFqn: String,
        val success: Boolean,
        val imagePath: String = "",
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val durationMs: Long = 0,
        val densityDpi: Int = 420,
        val error: String? = null,
        val hierarchy: HierarchyNode? = null,
    )
}
