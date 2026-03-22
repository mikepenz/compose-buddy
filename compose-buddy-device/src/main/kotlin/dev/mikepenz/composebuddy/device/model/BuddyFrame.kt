package dev.mikepenz.composebuddy.device.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirror of compose-buddy-device-client's BuddyFrame — must maintain matching JSON schema.
@Serializable
sealed class BuddyFrame {

    @Serializable
    @SerialName("frame")
    data class RenderFrame(
        val frameId: Int,
        val timestamp: Long,
        val preview: String,
        val image: String,
        val semantics: BuddySemanticNode,
        val slotTable: List<BuddySlotEntry>,
        val recompositions: Map<String, Int>,
        val frameTiming: BuddyFrameTiming,
        val densityDpi: Int = 420,
    ) : BuddyFrame()

    @Serializable
    @SerialName("previewList")
    data class PreviewListFrame(
        val previews: List<String>,
    ) : BuddyFrame()
}

@Serializable
data class BuddySemanticNode(
    val id: Int,
    val name: String,
    val role: String? = null,
    val bounds: BuddyBounds,
    val boundsInParent: BuddyBounds? = null,
    val offsetFromParent: BuddyBounds? = null,
    val contentDescription: String? = null,
    val testTag: String? = null,
    val mergedSemantics: Map<String, String> = emptyMap(),
    val actions: List<String> = emptyList(),
    val colors: BuddyNodeColors = BuddyNodeColors(),
    val sourceFile: String? = null,
    val sourceLine: Int? = null,
    val children: List<BuddySemanticNode>,
)

@Serializable
data class BuddyBounds(val left: Double, val top: Double, val right: Double, val bottom: Double)

@Serializable
data class BuddyNodeColors(val background: String? = null, val foreground: String? = null)

@Serializable
data class BuddySlotEntry(
    val key: Int,
    val name: String,
    val sourceInfo: String?,
    val depth: Int,
    val children: List<BuddySlotEntry>,
)

@Serializable
data class BuddyFrameTiming(
    val vsyncTimestamp: Long,
    val compositionMs: Double,
    val layoutMs: Double,
    val drawMs: Double,
) {
    val totalMs: Double get() = compositionMs + layoutMs + drawMs
}
