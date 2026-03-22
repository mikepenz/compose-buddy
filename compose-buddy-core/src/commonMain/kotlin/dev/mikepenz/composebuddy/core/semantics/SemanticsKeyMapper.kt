package dev.mikepenz.composebuddy.core.semantics

/**
 * Normalizes Compose SemanticsPropertyKey names to stable, camelCase identifiers.
 * Handles different key formats across Compose versions (1.5, 1.6+).
 */
object SemanticsKeyMapper {

    /**
     * Map a raw semantics key to a normalized property name.
     * Returns the normalized name, or a lowercase-first version of the cleaned key.
     */
    fun mapKey(key: String): String {
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
            else -> c.replaceFirstChar { it.lowercase() }
        }
    }

    /**
     * Format a semantics property value to a stable string representation.
     * Handles AnnotatedString, AccessibilityAction, lists, and function types.
     */
    fun formatValue(v: Any?): String = when {
        v == null -> ""
        v is List<*> -> v.mapNotNull { formatSingle(it) }.joinToString(", ")
        v is Function<*> -> "true"
        else -> formatSingle(v) ?: ""
    }

    private fun formatSingle(v: Any?): String? {
        val s = v?.toString() ?: return null
        if (s.startsWith("AnnotatedString(")) {
            val inner = s.removePrefix("AnnotatedString(").removeSuffix(")")
            val textField = inner.substringAfter("text=", "").substringBefore(",").trim()
            return textField.ifEmpty { inner.substringBefore(",").trim() }
        }
        if (s.startsWith("AccessibilityAction(")) return "true"
        return if (s == "null" || s == "[]") null else s
    }
}
