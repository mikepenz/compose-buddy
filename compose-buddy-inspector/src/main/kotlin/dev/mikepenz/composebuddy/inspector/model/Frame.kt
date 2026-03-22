package dev.mikepenz.composebuddy.inspector.model

import dev.mikepenz.composebuddy.core.model.RenderResult
import kotlinx.serialization.Serializable

/**
 * A timestamped snapshot of a single render in the inspector timeline.
 * Each frame retains the full RenderResult (image + hierarchy + semantics)
 * enabling full interactive inspection on historical frames.
 */
@Serializable
data class Frame(
    val id: Int,
    val timestamp: Long,
    val renderResult: RenderResult,
    val label: String? = null,
)
