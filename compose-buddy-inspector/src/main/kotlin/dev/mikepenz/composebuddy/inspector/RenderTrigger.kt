package dev.mikepenz.composebuddy.inspector

import dev.mikepenz.composebuddy.core.model.RenderResult

/**
 * Pluggable interface for triggering a new render from the inspector UI.
 * The CLI wires this to PreviewRunner; IDE plugin wires to its own build system.
 */
interface RenderTrigger {
    /**
     * Recompile and re-render the preview.
     * @param config optional render config overrides (resolution, density)
     * Returns the new RenderResult on success.
     * Throws [RenderException] with compilation error details on failure.
     */
    suspend fun rerender(config: RenderConfig? = null): RenderResult

    /**
     * Navigate to a specific preview FQN and render it.
     * Default implementation ignores [fqn] and delegates to [rerender] — this is correct for
     * static renderers that already know the selected preview from the session.
     * Device-based triggers override this to send a Navigate command before capturing.
     */
    suspend fun navigate(fqn: String, config: RenderConfig? = null): RenderResult = rerender(config)
}

class RenderException(
    val errorMessage: String,
    val errorStackTrace: String? = null,
) : Exception(errorMessage)
