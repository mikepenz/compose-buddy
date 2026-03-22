package dev.mikepenz.composebuddy.inspector

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.inspector.analysis.AnalysisProvider
import dev.mikepenz.composebuddy.inspector.model.AiFinding
import dev.mikepenz.composebuddy.inspector.model.DesignOverlay
import dev.mikepenz.composebuddy.inspector.model.Frame
import java.util.UUID

/**
 * Root state object for an active inspector window.
 * Manages available previews, frame history for the selected preview,
 * component selection, overlays, and re-render state.
 */
class InspectorSession(
    val projectPath: String,
    val modulePath: String,
    val renderTrigger: RenderTrigger,
    val maxFrames: Int = 100,
    val analysisProvider: AnalysisProvider? = null,
) {
    val id: String = UUID.randomUUID().toString()

    /** All discovered preview definitions (from bytecode scanning, no rendering). */
    private val _previewDefs = mutableListOf<Preview>()
    val previewDefs: List<Preview> get() = _previewDefs

    /** Cached render results keyed by "$previewFQN#$variantIndex". */
    private val _renderCache = mutableMapOf<String, RenderResult>()

    /** All available preview results from the latest render (legacy, for toolbar compatibility). */
    private val _availablePreviews = mutableListOf<RenderResult>()
    val availablePreviews: List<RenderResult> get() = _availablePreviews

    /** Name of the currently selected preview for inspection. */
    var selectedPreviewName: String? = null
        private set

    private val _frames = mutableListOf<Frame>()
    val frames: List<Frame> get() = _frames

    var currentFrameIndex: Int = -1
        private set

    var selectedNodeId: Int? = null

    var comparedFrameIndex: Int? = null

    private val _overlays = mutableListOf<DesignOverlay>()
    val overlays: List<DesignOverlay> get() = _overlays

    var isRerendering: Boolean = false
        private set

    var lastRenderError: String? = null
        private set

    private val _findings = mutableListOf<AiFinding>()
    val findings: List<AiFinding> get() = _findings

    private var nextFrameId = 0

    /**
     * Set discovered preview definitions (from bytecode scanning).
     * No rendering happens — previews are rendered on demand when selected.
     */
    fun setPreviewDefs(previews: List<Preview>) {
        _previewDefs.clear()
        _previewDefs.addAll(previews)
    }

    /**
     * Set the available previews from a render manifest.
     * Does not clear the timeline — call selectPreview() to switch.
     */
    fun setAvailablePreviews(results: List<RenderResult>) {
        _availablePreviews.clear()
        _availablePreviews.addAll(results)
    }

    /** Get a cached render result, or null if not yet rendered. */
    fun getCachedResult(previewName: String, variantIndex: Int): RenderResult? =
        _renderCache[cacheKey(previewName, variantIndex)]

    /** Cache a render result. */
    fun cacheResult(previewName: String, variantIndex: Int, result: RenderResult) {
        _renderCache[cacheKey(previewName, variantIndex)] = result
    }

    /** Clear render cache (e.g. after config change). */
    fun clearRenderCache() {
        _renderCache.clear()
    }

    private fun cacheKey(previewName: String, variantIndex: Int) = "$previewName#$variantIndex"

    /** Index within the group of same-named previews (for variant selection). */
    var selectedVariantIndex: Int = 0
        private set

    /**
     * Select a preview by name and start a new timeline with its current result.
     * Clears the existing timeline.
     */
    fun selectPreview(previewName: String, variantIndex: Int = 0) {
        selectedPreviewName = previewName
        selectedVariantIndex = variantIndex
        _frames.clear()
        currentFrameIndex = -1
        nextFrameId = 0
        selectedNodeId = null

        // Try cache first, then fall back to availablePreviews
        val cached = getCachedResult(previewName, variantIndex)
        if (cached != null) {
            addFrame(cached)
            return
        }

        // Legacy path: find from already-rendered results
        val variants = _availablePreviews.filter { it.previewName == previewName }
        val result = variants.getOrElse(variantIndex) { variants.firstOrNull() }
        if (result != null) {
            addFrame(result)
        }
    }

    /**
     * Select a specific variant within the current preview group.
     */
    fun selectVariant(variantIndex: Int) {
        val name = selectedPreviewName ?: return
        selectPreview(name, variantIndex)
    }

    /**
     * Add a new frame from a render result. Evicts oldest frame if at capacity (FIFO).
     */
    fun addFrame(result: RenderResult, label: String? = null): Frame {
        val frame = Frame(
            id = nextFrameId++,
            timestamp = System.currentTimeMillis(),
            renderResult = result,
            label = label,
        )
        if (_frames.size >= maxFrames) {
            _frames.removeFirst()
        }
        _frames.add(frame)
        currentFrameIndex = _frames.lastIndex
        lastRenderError = null
        return frame
    }

    /**
     * Navigate to a specific frame by index.
     */
    fun navigateToFrame(index: Int) {
        require(index in _frames.indices) { "Frame index $index out of range [0, ${_frames.lastIndex}]" }
        currentFrameIndex = index
    }

    /**
     * Get the currently displayed frame, or null if no frames exist.
     */
    val currentFrame: Frame?
        get() = _frames.getOrNull(currentFrameIndex)

    /**
     * Trigger a re-render with optional config overrides.
     * On success, appends the result to the timeline.
     */
    suspend fun triggerRerender(config: RenderConfig? = null): Frame? {
        isRerendering = true
        lastRenderError = null
        return try {
            renderTrigger.rerender(config)
            // Frame is added by the feed collector (e.g. launchInspectorMode); don't add it here too.
            currentFrame
        } catch (e: RenderException) {
            lastRenderError = e.errorMessage
            null
        } finally {
            isRerendering = false
        }
    }

    /**
     * Clear all frames and re-render with new config (e.g. after device preset change).
     */
    suspend fun rerenderWithNewConfig(config: RenderConfig): Frame? {
        _frames.clear()
        currentFrameIndex = -1
        nextFrameId = 0
        selectedNodeId = null
        return triggerRerender(config)
    }

    fun addOverlay(overlay: DesignOverlay) {
        _overlays.add(overlay)
    }

    fun removeOverlay(overlayId: String) {
        _overlays.removeAll { it.id == overlayId }
    }

    fun updateOverlay(overlay: DesignOverlay) {
        val index = _overlays.indexOfFirst { it.id == overlay.id }
        if (index >= 0) {
            _overlays[index] = overlay
        }
    }

    fun setFindings(newFindings: List<AiFinding>) {
        _findings.clear()
        _findings.addAll(newFindings)
    }
}

/**
 * Creates an InspectorSession with all render results (eager mode).
 * Selects the first preview by default.
 */
fun createSession(
    projectPath: String,
    modulePath: String,
    allResults: List<RenderResult>,
    renderTrigger: RenderTrigger,
    maxFrames: Int = 100,
    analysisProvider: AnalysisProvider? = null,
): InspectorSession {
    val session = InspectorSession(
        projectPath = projectPath,
        modulePath = modulePath,
        renderTrigger = renderTrigger,
        maxFrames = maxFrames,
        analysisProvider = analysisProvider,
    )
    session.setAvailablePreviews(allResults)
    if (allResults.isNotEmpty()) {
        session.selectPreview(allResults.first().previewName)
    }
    return session
}

/**
 * Creates an InspectorSession with discovered preview definitions (lazy mode).
 * No rendering happens upfront — previews are rendered on demand when selected.
 */
fun createLazySession(
    projectPath: String,
    modulePath: String,
    previewDefs: List<Preview>,
    renderTrigger: RenderTrigger,
    maxFrames: Int = 100,
    analysisProvider: AnalysisProvider? = null,
): InspectorSession {
    val session = InspectorSession(
        projectPath = projectPath,
        modulePath = modulePath,
        renderTrigger = renderTrigger,
        maxFrames = maxFrames,
        analysisProvider = analysisProvider,
    )
    session.setPreviewDefs(previewDefs)
    return session
}
