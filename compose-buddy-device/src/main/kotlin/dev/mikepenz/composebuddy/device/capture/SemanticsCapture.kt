package dev.mikepenz.composebuddy.device.capture

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.isUnspecified
import dev.mikepenz.composebuddy.device.model.BuddyBounds
import dev.mikepenz.composebuddy.device.model.BuddyNodeColors
import dev.mikepenz.composebuddy.device.model.BuddySemanticNode

object SemanticsCapture {

    fun captureTree(owner: SemanticsOwner): BuddySemanticNode {
        val root = try {
            owner.unmergedRootSemanticsNode
        } catch (_: Exception) {
            owner.rootSemanticsNode
        }
        return mapNode(root, id = 0, parentBounds = null)
    }

    private fun mapNode(node: SemanticsNode, id: Int, parentBounds: BuddyBounds?): BuddySemanticNode {
        val config = node.config

        // Dynamic semantics iteration — iterate all config entries
        val semantics = mutableMapOf<String, String>()
        val actions = mutableListOf<String>()
        for (entry in config) {
            val key = entry.key
            val value = entry.value
            val normalizedKey = normalizeKeyName(key.name)
            val formattedValue = formatSemanticsValue(value)
            if (formattedValue != null) {
                semantics[normalizedKey] = formattedValue
            }
            // Track action keys separately for backward compatibility
            if (normalizedKey == "onClick" || normalizedKey == "onLongClick") {
                actions.add(normalizedKey)
            }
        }

        // Extract text layout properties via GetTextLayoutResult semantics action
        try {
            val getLayoutAction = config.getOrNull(SemanticsActions.GetTextLayoutResult)
            if (getLayoutAction != null) {
                val results = mutableListOf<TextLayoutResult>()
                if (getLayoutAction.action?.invoke(results) == true) {
                    results.firstOrNull()?.let { result ->
                        val style = result.layoutInput.style
                        if (!style.fontSize.isUnspecified) semantics.putIfAbsent("fontSize", style.fontSize.toString())
                        if (!style.lineHeight.isUnspecified) semantics.putIfAbsent("lineHeight", style.lineHeight.toString())
                        if (!style.letterSpacing.isUnspecified) semantics.putIfAbsent("letterSpacing", style.letterSpacing.toString())
                        style.fontWeight?.let { fw ->
                            val name = when (fw.weight) {
                                100 -> "Thin"; 200 -> "ExtraLight"; 300 -> "Light"; 400 -> "Normal"
                                500 -> "Medium"; 600 -> "SemiBold"; 700 -> "Bold"; 800 -> "ExtraBold"; 900 -> "Black"
                                else -> null
                            }
                            semantics.putIfAbsent("fontWeight", if (name != null) "$name (${fw.weight})" else "${fw.weight}")
                        }
                        style.textAlign.let { ta ->
                            val s = ta.toString()
                            if (s != "Unspecified" && s.isNotBlank()) semantics.putIfAbsent("textAlign", s)
                        }
                        style.fontFamily?.let { ff ->
                            semantics.putIfAbsent("fontFamily", ff.toString())
                        }
                        // Text layout metrics — useful for detecting truncation and layout behaviour
                        val lineCount = result.lineCount
                        semantics.putIfAbsent("textLineCount", lineCount.toString())
                        if (result.hasVisualOverflow) semantics.putIfAbsent("textOverflow", "true")
                        if (lineCount > 0 && result.isLineEllipsized(lineCount - 1)) semantics.putIfAbsent("textEllipsized", "true")
                        val maxLines = result.layoutInput.maxLines
                        if (maxLines < Int.MAX_VALUE) semantics.putIfAbsent("maxLines", maxLines.toString())
                        // TextOverflow is an inline class with a mangled method name — read via reflection
                        try {
                            val overflowMethod = result.layoutInput::class.java.methods
                                .firstOrNull { it.name.startsWith("getOverflow") }
                            val overflowVal = overflowMethod?.invoke(result.layoutInput)?.toString()
                            val overflowName = when (overflowVal) {
                                "0" -> null  // Clip — default, not worth noting
                                "1" -> "Ellipsis"
                                "2" -> "Visible"
                                else -> overflowVal?.takeIf { it.isNotBlank() && it != "null" }
                            }
                            if (overflowName != null) semantics.putIfAbsent("textOverflowMode", overflowName)
                        } catch (_: Exception) {}
                        if (!result.layoutInput.softWrap) semantics.putIfAbsent("softWrap", "false")
                    }
                }
            }
        } catch (_: Exception) {}

        // Extract well-known fields from the dynamic map
        val role = semantics.remove("role")
        val contentDesc = semantics.remove("contentDescription")
        val testTag = semantics.remove("testTag")

        val bounds = node.boundsInRoot.let {
            mapBounds(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
        }

        // Compute relative bounds
        val boundsInParent = parentBounds?.let {
            BuddyBounds(
                left = bounds.left - it.left,
                top = bounds.top - it.top,
                right = bounds.right - it.left,
                bottom = bounds.bottom - it.top,
            )
        }
        val offsetFromParent = parentBounds?.let {
            val l = bounds.left - it.left
            val t = bounds.top - it.top
            val r = it.right - bounds.right
            val b = it.bottom - bounds.bottom
            if (l > 0.5 || t > 0.5 || r > 0.5 || b > 0.5) BuddyBounds(l, t, r, b) else null
        }

        val childrenNodes = node.children.mapIndexed { i, child ->
            mapNode(child, id * 100 + i + 1, parentBounds = bounds)
        }

        // Improved node naming based on semantics
        val name = inferName(semantics, role, childrenNodes)

        return BuddySemanticNode(
            id = node.id,
            name = name,
            role = role,
            bounds = bounds,
            boundsInParent = boundsInParent,
            offsetFromParent = offsetFromParent,
            contentDescription = contentDesc,
            testTag = testTag,
            mergedSemantics = semantics.ifEmpty { emptyMap() },
            actions = actions,
            colors = BuddyNodeColors(),
            children = childrenNodes,
        )
    }

    /**
     * Infer a human-readable node name from semantics, matching the logic in HierarchyExtractor.
     */
    private fun inferName(
        sem: Map<String, String>,
        role: String?,
        children: List<BuddySemanticNode>,
    ): String = when {
        role?.contains("Button", true) == true -> "Button"
        role?.contains("Checkbox", true) == true -> "Checkbox"
        role?.contains("Switch", true) == true -> "Switch"
        role?.contains("Image", true) == true -> "Image"
        role != null -> role
        sem.containsKey("editableText") -> "TextField"
        sem.containsKey("text") && children.isEmpty() -> "Text"
        sem.containsKey("onClick") -> "Clickable"
        sem.containsKey("isTraversalGroup") && children.isNotEmpty() -> "Container"
        children.isNotEmpty() -> "Layout"
        else -> "Element"
    }

    // ---- Key normalization (ported from SemanticsKeyMapper) ----

    /**
     * Normalize a SemanticsPropertyKey name to a stable camelCase identifier.
     * Ported from compose-buddy-core SemanticsKeyMapper.mapKey().
     */
    internal fun normalizeKeyName(key: String): String {
        val c = key.removePrefix("SemanticsPropertyKey:").removePrefix("accessibilityKey: ")
            .removePrefix("accessibilityKey:").trim()
        if (c == "Text" || key.endsWith(": Text") || key.endsWith(":Text")) return "text"
        return when {
            c.contains("ContentDescription") -> "contentDescription"
            c.contains("TestTag") -> "testTag"
            c.equals("Role", true) -> "role"
            c.contains("EditableText") -> "editableText"
            c.contains("StateDescription") -> "stateDescription"
            c.contains("ToggleableState") -> "toggleableState"
            c.contains("Selected") -> "selected"
            c.contains("Heading") -> "heading"
            c.contains("OnClick") || c.contains("onClick") -> "onClick"
            c.contains("OnLongClick") -> "onLongClick"
            c.contains("IsTraversalGroup") -> "isTraversalGroup"
            c.contains("Focused") -> "focused"
            c.contains("Disabled") -> "disabled"
            c.contains("ProgressBarRangeInfo") -> "progressRange"
            c.contains("HorizontalScrollAxisRange") -> "horizontalScroll"
            c.contains("VerticalScrollAxisRange") -> "verticalScroll"
            c.contains("RequestFocus") -> "requestFocus"
            c.contains("CollectionInfo") && !c.contains("Item") -> "collectionInfo"
            c.contains("CollectionItemInfo") -> "collectionItemInfo"
            c.contains("TextSubstitution") -> "setTextSubstitution"
            c.contains("TextLayoutResult") -> "getTextLayoutResult"
            c.contains("SetSelection") -> "setSelection"
            c.contains("CopyText") -> "copyText"
            // Input field properties
            c.equals("Password", true) -> "password"
            c.contains("ImeAction") -> "imeAction"
            c.contains("Error") && !c.contains("EditableText") -> "error"
            // Accessibility structure
            c.contains("PaneTitle") -> "paneTitle"
            c.contains("LiveRegion") -> "liveRegion"
            c.contains("CustomActions") -> "customActions"
            // Expandable / dismissable state
            c.equals("Expand", true) || c.contains("Expanded") -> "expand"
            c.equals("Collapse", true) || c.contains("Collapsed") -> "collapse"
            c.equals("Dismiss", true) -> "dismiss"
            else -> c.replaceFirstChar { it.lowercase() }
        }
    }

    // ---- Value formatting (ported from SemanticsKeyMapper) ----

    /**
     * Format a semantics property value to a stable string representation.
     * Returns null for empty/null values.
     */
    internal fun formatSemanticsValue(v: Any?): String? {
        val result = when {
            v == null -> ""
            v is Unit -> "true"
            v is List<*> -> v.mapNotNull { formatSingle(it) }.joinToString(", ")
            v is Function<*> -> "true"
            else -> formatScrollAxisRange(v) ?: formatSingle(v) ?: ""
        }
        return result.ifBlank { null }
    }

    /**
     * Format a ScrollAxisRange by invoking its value/maxValue lambdas to capture actual scroll position.
     * Returns null if the value is not a ScrollAxisRange.
     */
    private fun formatScrollAxisRange(v: Any): String? {
        if (!v::class.java.simpleName.contains("ScrollAxisRange")) return null
        return try {
            val clazz = v::class.java
            @Suppress("UNCHECKED_CAST")
            val current = (clazz.getMethod("getValue").invoke(v) as? Function0<Float>)?.invoke() ?: return null
            @Suppress("UNCHECKED_CAST")
            val max = (clazz.getMethod("getMaxValue").invoke(v) as? Function0<Float>)?.invoke() ?: return null
            val reverse = try { clazz.getMethod("getReverseScrolling").invoke(v) as? Boolean } catch (_: Exception) { null }
            buildString {
                append("%.1f/%.1f".format(current, max))
                if (reverse == true) append(" (reversed)")
            }
        } catch (_: Exception) { null }
    }

    private fun formatSingle(v: Any?): String? {
        val s = v?.toString() ?: return null
        if (s.startsWith("AnnotatedString(")) {
            val inner = s.removePrefix("AnnotatedString(").removeSuffix(")")
            val textField = inner.substringAfter("text=", "").substringBefore(",").trim()
            return textField.ifEmpty { inner.substringBefore(",").trim() }
        }
        if (s.startsWith("AccessibilityAction(")) {
            // Extract the action label if present — more informative than just "true"
            val label = s.removePrefix("AccessibilityAction(").substringBefore(",").trim()
                .removePrefix("label=").removeSurrounding("'").removeSurrounding("\"")
            return if (label.isNotBlank() && label != "null") label else "true"
        }
        // Format CustomAccessibilityAction list items by their label
        if (s.startsWith("CustomAccessibilityAction(")) {
            return s.substringAfter("label=").substringBefore(",").trim()
                .removeSurrounding("'").removeSurrounding("\"").ifBlank { "action" }
        }
        return if (s == "null" || s == "[]") null else s
    }

    // Testable helpers — pure functions, no Android dependencies

    fun mapBounds(left: Int, top: Int, right: Int, bottom: Int): BuddyBounds =
        BuddyBounds(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())

    fun buildNode(
        id: Int, name: String, role: String?,
        left: Int, top: Int, right: Int, bottom: Int,
        contentDescription: String?, testTag: String?,
        mergedSemantics: Map<String, String>, actions: List<String>,
        bgColor: String?, fgColor: String?,
        children: List<BuddySemanticNode>,
    ): BuddySemanticNode = BuddySemanticNode(
        id = id, name = name, role = role,
        bounds = mapBounds(left, top, right, bottom),
        contentDescription = contentDescription, testTag = testTag,
        mergedSemantics = mergedSemantics, actions = actions,
        colors = BuddyNodeColors(background = bgColor, foreground = fgColor),
        children = children,
    )
}
