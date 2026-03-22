package dev.mikepenz.composebuddy.inspector

import dev.mikepenz.composebuddy.core.model.RenderResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Flow-based interface for pushing new renders to the inspector.
 * The CLI's render pipeline emits to this feed; the inspector UI collects reactively.
 */
class InspectorFeed {
    private val _renders = MutableSharedFlow<RenderResult>(replay = 1)
    val renders: SharedFlow<RenderResult> = _renders.asSharedFlow()

    suspend fun emit(result: RenderResult) {
        _renders.emit(result)
    }
}
