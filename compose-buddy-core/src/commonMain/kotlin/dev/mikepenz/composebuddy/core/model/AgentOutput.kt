package dev.mikepenz.composebuddy.core.model

import kotlinx.serialization.Serializable

/**
 * Condensed output format optimized for AI agent consumption.
 *
 * Design principles:
 * - Minimal: only fields an agent needs to validate UI correctness
 * - Flat where possible: bounds as [x, y, w, h] array instead of nested object
 * - Actionable: accessibility issues flagged inline
 * - Referential: image path included so agent can load the visual
 * - No noise: internal IDs, durations, redundant bounds removed
 */
@Serializable
data class AgentOutput(
    val project: String,
    val module: String = "",
    val densityDpi: Int,
    val previews: List<AgentPreview>,
)

@Serializable
data class AgentPreview(
    val name: String,
    val source: String? = null,
    val image: String,
    val imageSize: List<Int>,
    val tree: AgentNode,
    val a11yIssues: List<String> = emptyList(),
)

/**
 * Condensed hierarchy node for agent consumption.
 *
 * bounds = [x, y, width, height] in dp (integers, rounded)
 * Only semantically meaningful properties are included.
 */
@Serializable
data class AgentNode(
    val type: String,
    val bounds: List<Int>,
    val text: String? = null,
    val contentDescription: String? = null,
    val role: String? = null,
    val testTag: String? = null,
    val clickable: Boolean? = null,
    val disabled: Boolean? = null,
    val selected: String? = null,
    val toggleState: String? = null,
    val focused: Boolean? = null,
    val heading: Boolean? = null,
    val bgColor: String? = null,
    val fgColor: String? = null,
    val dominantColor: String? = null,
    val children: List<AgentNode>? = null,
)

object AgentOutputBuilder {

    fun build(manifest: RenderManifest): AgentOutput {
        return AgentOutput(
            project = manifest.projectPath,
            module = manifest.modulePath,
            densityDpi = manifest.densityDpi,
            previews = manifest.results
                .filter { it.error == null && it.hierarchy != null }
                .map { buildPreview(it) },
        )
    }

    private fun buildPreview(result: RenderResult): AgentPreview {
        val hierarchy = result.hierarchy!!
        val tree = buildNode(hierarchy)
        val a11yIssues = mutableListOf<String>()
        collectA11yIssues(hierarchy, a11yIssues)

        return AgentPreview(
            name = result.previewName.substringAfterLast('.'),
            source = hierarchy.sourceFile?.let { file ->
                val line = hierarchy.sourceLine
                if (line != null) "$file:$line" else file
            },
            image = result.imagePath,
            imageSize = listOf(result.imageWidth, result.imageHeight),
            tree = tree,
            a11yIssues = a11yIssues.ifEmpty { emptyList() },
        )
    }

    private fun buildNode(node: HierarchyNode): AgentNode {
        val sem = node.semantics ?: emptyMap()
        val children = node.children.map { buildNode(it) }

        return AgentNode(
            type = node.name,
            bounds = listOf(
                node.bounds.left.toInt(),
                node.bounds.top.toInt(),
                node.size.width.toInt(),
                node.size.height.toInt(),
            ),
            text = sem["text"],
            contentDescription = sem["contentDescription"],
            role = sem["role"],
            testTag = sem["testTag"],
            clickable = if (sem.containsKey("onClick")) true else null,
            disabled = if (sem.containsKey("disabled")) true else null,
            selected = sem["selected"],
            toggleState = sem["toggleableState"],
            focused = if (sem["focused"] == "true") true else null,
            heading = if (sem.containsKey("heading")) true else null,
            bgColor = sem["backgroundColor"],
            fgColor = sem["foregroundColor"],
            dominantColor = sem["dominantColor"],
            children = children.ifEmpty { null },
        )
    }

    private fun collectA11yIssues(node: HierarchyNode, issues: MutableList<String>) {
        val sem = node.semantics ?: emptyMap()
        val hasLabel = sem.containsKey("text") || sem.containsKey("contentDescription")
        val w = node.size.width
        val h = node.size.height

        // Skip invisible/internal nodes (0x0 or at origin with no size)
        if (w <= 0 && h <= 0) {
            node.children.forEach { collectA11yIssues(it, issues) }
            return
        }

        val pos = "${node.bounds.left.toInt()},${node.bounds.top.toInt()}"

        // Clickable without accessible label
        if (sem.containsKey("onClick") && !hasLabel) {
            issues.add("Clickable at ($pos) ${w.toInt()}x${h.toInt()}dp has no text or contentDescription")
        }

        // Button without label
        if (node.name == "Button" && !hasLabel) {
            issues.add("Button at ($pos) has no accessible label")
        }

        // Image without contentDescription
        if (node.children.isEmpty() && !hasLabel && w > 0 && h > 0 &&
            node.name in listOf("Image", "Icon") && !sem.containsKey("role")) {
            issues.add("${node.name} at ($pos) may need contentDescription")
        }

        // Touch target too small (48dp minimum per Material/WCAG)
        if (sem.containsKey("onClick") && (w < 48 || h < 48)) {
            issues.add("Touch target at ($pos) is ${w.toInt()}x${h.toInt()}dp — below 48dp minimum")
        }

        for (child in node.children) {
            collectA11yIssues(child, issues)
        }
    }
}
