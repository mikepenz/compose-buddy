package dev.mikepenz.composebuddy.device.capture

import android.util.Log
import dev.mikepenz.composebuddy.device.model.BuddySemanticNode
import dev.mikepenz.composebuddy.device.model.BuddySlotEntry
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SlotTableCapture"

object SlotTableCapture {

    // Cached reflection lookups to avoid per-node Class.getMethod() overhead
    private val methodCache = ConcurrentHashMap<String, java.lang.reflect.Method?>()

    /**
     * Extract the slot table as a flat list (legacy API kept for BuddyFrame.slotTable).
     */
    fun extract(compositionData: Any): List<BuddySlotEntry> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val groups = compositionData::class.java.getMethod("getCompositionGroups")
                .invoke(compositionData) as? Iterable<Any> ?: return emptyList()
            groups.map { group -> mapGroup(group, depth = 0) }
        } catch (e: Exception) {
            Log.d(TAG, "extract failed: ${e.message}")
            emptyList()
        }
    }

    private fun mapGroup(group: Any, depth: Int): BuddySlotEntry {
        val name = try {
            group::class.java.getMethod("getSourceInfo").invoke(group)?.toString()
                ?.substringBefore("(")?.trim() ?: "<unknown>"
        } catch (_: Exception) { "<unknown>" }
        val sourceInfo = try {
            group::class.java.getMethod("getSourceInfo").invoke(group)?.toString()
        } catch (_: Exception) { null }
        val key = try {
            group::class.java.getMethod("getKey").invoke(group)?.hashCode() ?: 0
        } catch (_: Exception) { 0 }
        val children = try {
            @Suppress("UNCHECKED_CAST")
            val childGroups = group::class.java.getMethod("getCompositionGroups")
                .invoke(group) as? Iterable<Any> ?: emptyList()
            childGroups.map { child -> mapGroup(child, depth + 1) }
        } catch (_: Exception) { emptyList() }

        return BuddySlotEntry(
            key = key,
            name = name,
            sourceInfo = sourceInfo,
            depth = depth,
            children = children,
        )
    }

    // ---- Composition data extraction via reflection ----

    /**
     * Gets CompositionData from an AndroidComposeView via reflection on the composition field.
     */
    fun getCompositionData(composeView: Any): Any? {
        return try {
            // Get composition field from AbstractComposeView
            var composition: Any? = null
            var clazz: Class<*>? = composeView::class.java
            while (clazz != null && composition == null) {
                try {
                    val field = clazz.getDeclaredField("composition")
                    field.isAccessible = true
                    composition = field.get(composeView)
                } catch (_: NoSuchFieldException) {}
                clazz = clazz.superclass
            }
            if (composition == null) return null

            // Unwrap WrappedComposition -> original CompositionImpl
            try {
                val originalField = composition::class.java.getDeclaredField("original")
                originalField.isAccessible = true
                composition = originalField.get(composition) ?: composition
            } catch (_: NoSuchFieldException) {}

            // Get SlotTable from CompositionImpl
            composition::class.java.declaredMethods
                .filter { it.name.startsWith("getSlotTable") }
                .firstNotNullOfOrNull { m ->
                    try { m.isAccessible = true; m.invoke(composition) } catch (_: Exception) { null }
                }
        } catch (e: Exception) {
            Log.d(TAG, "getCompositionData: ${e.message}")
            null
        }
    }

    /**
     * Use SlotTreeKt.asTree() to get the Group tree, walk it, extract parameters per node.
     * Returns a map of boundsKey ("left,top,right,bottom" in pixels) -> properties.
     */
    fun extractSlotTreeProperties(compositionData: Any, densityDpi: Int): Map<String, Map<String, String>> {
        return try {
            val slotTreeKtClass = Class.forName("androidx.compose.ui.tooling.data.SlotTreeKt")
            val asTreeMethod = slotTreeKtClass.declaredMethods.firstOrNull {
                it.name == "asTree" && it.parameterCount >= 1
            } ?: return emptyMap()
            asTreeMethod.isAccessible = true

            val rootGroup = asTreeMethod.invoke(null, compositionData) ?: return emptyMap()
            val result = mutableMapOf<String, MutableMap<String, String>>()
            walkGroup(rootGroup, result)
            result
        } catch (e: Exception) {
            Log.d(TAG, "extractSlotTreeProperties: ${e.message}")
            emptyMap()
        }
    }

    private fun walkGroup(group: Any, result: MutableMap<String, MutableMap<String, String>>) {
        try {
            val gc = group::class.java

            // Extract bounds in pixels
            val boundsKey = extractBoundsKey(group)

            // Extract parameters for this group
            val props = mutableMapOf<String, String>()
            extractGroupParameters(group, props)

            // Extract source location
            val (sourceFile, sourceLine) = extractSourceLocation(group)
            if (sourceFile != null) props["sourceFile"] = sourceFile
            if (sourceLine != null) props["sourceLine"] = sourceLine.toString()

            // Merge into result map by bounds key
            if (boundsKey != null && props.isNotEmpty()) {
                val existing = result.getOrPut(boundsKey) { mutableMapOf() }
                // Don't overwrite existing values (first/outer group wins for most properties)
                for ((k, v) in props) {
                    existing.putIfAbsent(k, v)
                }
            }

            // Recurse into children
            @Suppress("UNCHECKED_CAST")
            val children = cachedMethod(gc, "getChildren")?.invoke(group) as? List<Any> ?: emptyList()
            for (child in children) {
                walkGroup(child, result)
            }
        } catch (e: Exception) {
            Log.d(TAG, "walkGroup: ${e.message}")
        }
    }

    private fun extractBoundsKey(group: Any): String? {
        return try {
            val box = cachedMethod(group::class.java, "getBox")?.invoke(group) ?: return null
            val bc = box::class.java
            val l = toInt(cachedMethod(bc, "getLeft")?.invoke(box))
            val t = toInt(cachedMethod(bc, "getTop")?.invoke(box))
            val r = toInt(cachedMethod(bc, "getRight")?.invoke(box))
            val b = toInt(cachedMethod(bc, "getBottom")?.invoke(box))
            if (r <= 0 && b <= 0) null else "$l,$t,$r,$b"
        } catch (_: Exception) { null }
    }

    private fun toInt(value: Any?): Int = when (value) {
        is Int -> value
        is Float -> value.toInt()
        is Double -> value.toInt()
        is Number -> value.toInt()
        else -> 0
    }

    /** Look up a no-arg method by name, caching per class+method key. */
    private fun cachedMethod(clazz: Class<*>, name: String): java.lang.reflect.Method? {
        val key = "${clazz.name}.$name"
        return methodCache.getOrPut(key) {
            try { clazz.getMethod(name) } catch (_: NoSuchMethodException) { null }
        }
    }

    // ---- Parameter extraction (ported from SlotTreeWalker) ----

    private fun extractGroupParameters(group: Any, result: MutableMap<String, String>) {
        try {
            @Suppress("UNCHECKED_CAST")
            val params = cachedMethod(group::class.java, "getParameters")?.invoke(group) as? List<Any> ?: return
            for (param in params) {
                val pc = param::class.java
                val pName = try { cachedMethod(pc, "getName")?.invoke(param) as? String } catch (_: Exception) { null } ?: continue
                val pValue = try { cachedMethod(pc, "getValue")?.invoke(param) } catch (_: Exception) { null }

                when (pName.lowercase()) {
                    "text" -> extractTextValue(pValue)?.let { result["text"] = it }
                    "color" -> formatColor(pValue)?.let { result["color"] = it }
                    "backgroundcolor", "containercolor" -> formatColor(pValue)?.let { result["backgroundColor"] = it }
                    "contentcolor" -> formatColor(pValue)?.let { result["contentColor"] = it }
                    "contentdescription" -> pValue?.toString()?.let { result["contentDescription"] = it }
                    "onclick", "click" -> result["onClick"] = "true"
                    "enabled" -> result["enabled"] = pValue?.toString() ?: "true"
                    "style" -> extractTextStyle(pValue, result)
                    "fontsize" -> formatTextUnit(pValue)?.let { result["fontSize"] = it }
                    "fontweight" -> extractFontWeight(pValue)?.let { result["fontWeight"] = it }
                    "fontfamily" -> pValue?.toString()?.let { result["fontFamily"] = it }
                    "lineheight" -> formatTextUnit(pValue)?.let { result["lineHeight"] = it }
                    "letterspacing" -> formatTextUnit(pValue)?.let { result["letterSpacing"] = it }
                    "shape" -> extractShape(pValue)?.let { result["shape"] = it }
                    "elevation", "shadowelevation", "tonalelevation" -> formatDp(pValue)?.let { result["elevation"] = it }
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractTextValue(value: Any?): String? {
        val s = value?.toString() ?: return null
        return when {
            s.startsWith("AnnotatedString(") -> s.removePrefix("AnnotatedString(").substringBefore(",").removeSuffix(")")
            s.isBlank() -> null
            else -> s
        }
    }

    internal fun formatColor(value: Any?): String? {
        if (value == null) return null
        return try {
            when (value) {
                is Long -> {
                    if (value < 256) return null
                    val argb = (value shr 32).toInt()
                    if (argb != 0) "#%08X".format(argb) else null
                }
                is Int -> {
                    if (value < 256) return null
                    "#%08X".format(value)
                }
                else -> {
                    // Try to extract via getValue-xxx method (Color value class)
                    val m = value::class.java.declaredMethods.firstOrNull {
                        it.name.startsWith("getValue") && it.returnType == Long::class.javaPrimitiveType
                    }
                    if (m != null) {
                        m.isAccessible = true
                        val packed = m.invoke(value) as Long
                        val argb = (packed shr 32).toInt()
                        if (argb != 0) "#%08X".format(argb) else null
                    } else {
                        val str = value.toString()
                        if (str.startsWith("Color(") || str.startsWith("#")) str else null
                    }
                }
            }
        } catch (_: Exception) { null }
    }

    /**
     * Extract typography properties from a TextStyle object via reflection.
     */
    private fun extractTextStyle(value: Any?, result: MutableMap<String, String>) {
        if (value == null) return
        try {
            val clazz = value::class.java
            tryCall(clazz, value, "getFontSize")?.let { formatTextUnit(it) }?.let { result["fontSize"] = it }
            tryCall(clazz, value, "getFontWeight")?.let { extractFontWeight(it) }?.let { result["fontWeight"] = it }
            tryCall(clazz, value, "getFontFamily")?.let { result["fontFamily"] = it.toString() }
            tryCall(clazz, value, "getLineHeight")?.let { formatTextUnit(it) }?.let { result["lineHeight"] = it }
            tryCall(clazz, value, "getLetterSpacing")?.let { formatTextUnit(it) }?.let { result["letterSpacing"] = it }
            tryCall(clazz, value, "getTextAlign")?.let {
                val s = it.toString()
                if (s != "Unspecified" && s.isNotBlank()) result["textAlign"] = s
            }
        } catch (_: Exception) {}
    }

    private fun tryCall(clazz: Class<*>, obj: Any, methodName: String): Any? {
        return try {
            val m = clazz.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
            m?.isAccessible = true
            m?.invoke(obj)
        } catch (_: Exception) { null }
    }

    internal fun formatTextUnit(value: Any?): String? {
        if (value == null) return null
        return try {
            val s = value.toString()
            if (s == "Unspecified" || s.isBlank()) null else s
        } catch (_: Exception) { null }
    }

    internal fun extractFontWeight(value: Any?): String? {
        if (value == null) return null
        return try {
            val weightField = value::class.java.methods.firstOrNull { it.name == "getWeight" && it.parameterCount == 0 }
            val weight = weightField?.invoke(value) as? Int
            if (weight != null) {
                val name = when (weight) {
                    100 -> "Thin"; 200 -> "ExtraLight"; 300 -> "Light"; 400 -> "Normal"
                    500 -> "Medium"; 600 -> "SemiBold"; 700 -> "Bold"; 800 -> "ExtraBold"; 900 -> "Black"
                    else -> null
                }
                if (name != null) "$name ($weight)" else "$weight"
            } else {
                value.toString().takeIf { it.isNotBlank() && it != "null" }
            }
        } catch (_: Exception) { value.toString() }
    }

    internal fun extractShape(value: Any?): String? {
        if (value == null) return null
        return try {
            val s = value.toString()
            when {
                s.contains("RoundedCornerShape") -> s
                s.contains("CircleShape") -> "CircleShape"
                s.contains("RectangleShape") -> "RectangleShape"
                s.contains("CutCornerShape") -> s
                s.isBlank() || s == "null" -> null
                else -> s
            }
        } catch (_: Exception) { null }
    }

    internal fun formatDp(value: Any?): String? {
        if (value == null) return null
        return try {
            val s = value.toString()
            if (s == "0.0.dp" || s == "Unspecified" || s.isBlank()) null else s
        } catch (_: Exception) { null }
    }

    private fun extractSourceLocation(group: Any): Pair<String?, Int?> {
        return try {
            val location = cachedMethod(group::class.java, "getLocation")?.invoke(group) ?: return Pair(null, null)
            val lc = location::class.java
            val file = try { cachedMethod(lc, "getSourceFile")?.invoke(location) as? String } catch (_: Exception) { null }
            val line = try { cachedMethod(lc, "getLineNumber")?.invoke(location) as? Int } catch (_: Exception) {
                try { cachedMethod(lc, "getOffset")?.invoke(location) as? Int } catch (_: Exception) { null }
            }
            val resolvedFile = file ?: try {
                val data = cachedMethod(group::class.java, "getData")?.invoke(group) as? List<*>
                data?.filterIsInstance<String>()?.firstOrNull { it.contains(".kt") }
            } catch (_: Exception) { null }
            Pair(resolvedFile, line)
        } catch (_: Exception) { Pair(null, null) }
    }

    // ---- Semantics enrichment ----

    /**
     * Enrich a semantics tree with slot table properties (typography, colors, shapes, source locations).
     * Matches slot table groups to semantic nodes by pixel bounds overlap.
     */
    fun enrichSemantics(composeView: Any, root: BuddySemanticNode, densityDpi: Int): BuddySemanticNode {
        return try {
            val compositionData = getCompositionData(composeView) ?: return root
            val slotProps = extractSlotTreeProperties(compositionData, densityDpi)
            if (slotProps.isEmpty()) return root
            mergeSlotProperties(root, slotProps)
        } catch (e: Exception) {
            Log.d(TAG, "enrichSemantics: ${e.message}")
            root
        }
    }

    private fun mergeSlotProperties(
        node: BuddySemanticNode,
        slotProps: Map<String, Map<String, String>>,
    ): BuddySemanticNode {
        // Build bounds key from the node's pixel bounds
        val boundsKey = "${node.bounds.left.toInt()},${node.bounds.top.toInt()},${node.bounds.right.toInt()},${node.bounds.bottom.toInt()}"

        // Find matching slot properties by exact bounds key
        val matched = slotProps[boundsKey]

        // Merge properties into the node
        var updatedNode = node
        if (matched != null) {
            // Merge into mergedSemantics (don't overwrite existing semantics values)
            val extraSemantics = mutableMapOf<String, String>()
            for ((k, v) in matched) {
                if (k == "sourceFile" || k == "sourceLine") continue
                if (!node.mergedSemantics.containsKey(k)) {
                    extraSemantics[k] = v
                }
            }
            val newSemantics = if (extraSemantics.isNotEmpty()) {
                node.mergedSemantics + extraSemantics
            } else {
                node.mergedSemantics
            }

            // Extract source location
            val sourceFile = matched["sourceFile"] ?: node.sourceFile
            val sourceLine = matched["sourceLine"]?.toIntOrNull() ?: node.sourceLine

            updatedNode = node.copy(
                mergedSemantics = newSemantics,
                sourceFile = sourceFile,
                sourceLine = sourceLine,
            )
        }

        return updatedNode.copy(
            children = updatedNode.children.map { child -> mergeSlotProperties(child, slotProps) },
        )
    }
}
