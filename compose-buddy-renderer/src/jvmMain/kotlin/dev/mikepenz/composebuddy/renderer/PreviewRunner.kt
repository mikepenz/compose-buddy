package dev.mikepenz.composebuddy.renderer

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderError
import dev.mikepenz.composebuddy.core.model.RenderErrorType
import dev.mikepenz.composebuddy.core.model.RenderManifest
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import co.touchlab.kermit.Logger
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.Instant

/**
 * Orchestrates the preview rendering pipeline:
 * discover → configure → render → write manifest.
 */
class PreviewRunner(
    private val renderer: PreviewRenderer,
    private val discovery: PreviewDiscovery = PreviewDiscovery(),
) {

    data class RunConfig(
        val classpath: List<File> = emptyList(),
        val outputDir: File,
        val previewFilter: String? = null,
        val packageFilter: String? = null,
        val overrides: RenderConfiguration? = null,
        val maxPreviewParams: Int = 10,
        val projectPath: String = "",
        val modulePath: String = "",
    )

    fun run(config: RunConfig): RenderManifest {
        val previews = discovery.discover(config.classpath, config.packageFilter)
            .let { allPreviews ->
                if (config.previewFilter != null) {
                    allPreviews.filter { matchesFilter(it, config.previewFilter) }
                } else {
                    allPreviews
                }
            }

        renderer.setup()

        val results = mutableListOf<RenderResult>()
        try {
            // Pre-resolve configs once, then sort to minimize expensive session reconfigurations.
            // Previews with the same density/uiMode/dimensions will be rendered consecutively.
            val previewsWithConfig = previews.map { it to RenderConfiguration.resolve(it, config.overrides) }
            val sortedPreviews = previewsWithConfig.sortedWith(compareBy(
                { it.second.densityDpi },
                { it.second.uiMode },
                { it.second.widthDp },
                { it.second.heightDp },
            ))
            val total = sortedPreviews.size
            for ((index, entry) in sortedPreviews.withIndex()) {
                val (preview, renderConfig) = entry
                val shortName = preview.fullyQualifiedName.substringAfterLast('.')
                Logger.i { "Rendering [${index + 1}/$total] $shortName" }
                val result = try {
                    renderer.render(preview, renderConfig)
                } catch (e: Exception) {
                    RenderResult(
                        previewName = preview.fullyQualifiedName,
                        configuration = renderConfig,
                        error = RenderError(
                            type = RenderErrorType.UNKNOWN,
                            message = e.message ?: "Unknown error",
                            stackTrace = e.stackTraceToString(),
                        ),
                    )
                }
                if (result.error != null) {
                    Logger.i { "  FAIL: ${result.error!!.message}" }
                }
                results.add(result)
            }
        } finally {
            renderer.teardown()
        }

        val actualDensity = results.firstOrNull { it.error == null }?.configuration?.densityDpi ?: 420

        // Collect all semantic field names from hierarchy nodes
        val allSemanticKeys = mutableSetOf<String>()
        fun collectKeys(node: dev.mikepenz.composebuddy.core.model.HierarchyNode) {
            node.semantics?.keys?.let { allSemanticKeys.addAll(it) }
            node.children.forEach { collectKeys(it) }
        }
        results.forEach { it.hierarchy?.let { h -> collectKeys(h) } }

        val manifest = RenderManifest(
            timestamp = Instant.now().toString(),
            projectPath = config.projectPath,
            modulePath = config.modulePath,
            totalPreviews = previews.size,
            totalRendered = results.count { it.error == null },
            totalErrors = results.count { it.error != null },
            densityDpi = actualDensity,
            availableSemantics = allSemanticKeys.sorted(),
            results = results,
        )

        val manifestFile = File(config.outputDir, "manifest.json")
        manifestFile.parentFile?.mkdirs()
        manifestFile.writeText(ComposeBuddyJson.encodeToString(manifest))

        return manifest
    }

    private fun matchesFilter(preview: Preview, filter: String): Boolean {
        if (filter.contains('*')) {
            val regex = filter
                .replace(".", "\\.")
                .replace("*", ".*")
                .toRegex()
            return regex.matches(preview.fullyQualifiedName)
        }
        return preview.fullyQualifiedName == filter ||
            preview.fullyQualifiedName.startsWith("$filter.")
    }
}
