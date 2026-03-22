package dev.mikepenz.composebuddy.core.renderer

import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderResult

interface PreviewRenderer {
    fun setup()
    fun teardown()
    fun render(preview: Preview, configuration: RenderConfiguration): RenderResult
    fun extractHierarchy(preview: Preview, configuration: RenderConfiguration): HierarchyNode?
}
