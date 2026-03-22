package dev.mikepenz.composebuddy.renderer.android.worker

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size

/**
 * Extracts the full Compose layout tree from the slot table.
 * All dimensional output is in dp.
 */
object SlotTreeWalker {

    private class IdCounter { var value = 0; fun next() = value++ }

    // Cached reflection lookups to avoid per-node Class.getMethod() overhead
    private val methodCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method?>()

    fun extractFromComposeView(composeView: Any, densityDpi: Int): HierarchyNode? {
        val counter = IdCounter()
        return try {
            val compositionData = getCompositionData(composeView) ?: return null

            val slotTreeKtClass = Class.forName("androidx.compose.ui.tooling.data.SlotTreeKt")
            val asTreeMethod = slotTreeKtClass.declaredMethods.firstOrNull {
                it.name == "asTree" && it.parameterCount >= 1
            } ?: return null
            asTreeMethod.isAccessible = true

            val rootGroup = asTreeMethod.invoke(null, compositionData) ?: return null
            val rawTree = buildNode(rootGroup, densityDpi, null, counter) ?: return null
            pruneTree(rawTree)
        } catch (e: Exception) {
            Logger.d { "SlotTree: ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    private fun getCompositionData(composeView: Any): Any? {
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

        // Unwrap WrappedComposition → original CompositionImpl
        try {
            val originalField = composition::class.java.getDeclaredField("original")
            originalField.isAccessible = true
            composition = originalField.get(composition) ?: composition
        } catch (_: NoSuchFieldException) {}

        // Get SlotTable from CompositionImpl
        return composition::class.java.declaredMethods
            .filter { it.name.startsWith("getSlotTable") }
            .firstNotNullOfOrNull { m ->
                try { m.isAccessible = true; m.invoke(composition) } catch (_: Exception) { null }
            }
    }

    private fun buildNode(group: Any, dpi: Int, parentBounds: Bounds?, counter: IdCounter): HierarchyNode? {
        return try {
            buildNodeInternal(group, dpi, parentBounds, counter)
        } catch (e: Exception) {
            Logger.d { "buildNode: ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    /** Look up a no-arg method by name, caching per class+method key. */
    private fun cachedMethod(clazz: Class<*>, name: String): java.lang.reflect.Method? {
        val key = "${clazz.name}.$name"
        return methodCache.getOrPut(key) {
            try { clazz.getMethod(name) } catch (_: NoSuchMethodException) { null }
        }
    }

    private fun buildNodeInternal(group: Any, dpi: Int, parentBounds: Bounds?, counter: IdCounter): HierarchyNode? {
        val gc = group::class.java

        val name = try { cachedMethod(gc, "getName")?.invoke(group) as? String } catch (_: Exception) { null }

        // Bounds from IntRect box (px) → convert to dp with snap rounding
        val (lPx, tPx, rPx, bPx) = extractBoundsPx(group)
        val bounds = dev.mikepenz.composebuddy.core.model.pxBoundsToDp(lPx, tPx, rPx, bPx, dpi)
        val w = bounds.right - bounds.left
        val h = bounds.bottom - bounds.top

        if (w <= 0.0 && h <= 0.0 && name.isNullOrBlank()) return null

        val boundsInParent = parentBounds?.let {
            dev.mikepenz.composebuddy.core.model.boundsOf(bounds.left - it.left, bounds.top - it.top, bounds.right - it.left, bounds.bottom - it.top)
        }
        val size = dev.mikepenz.composebuddy.core.model.sizeOf(w, h)
        val offsetFromParent = parentBounds?.let {
            val l = bounds.left - it.left; val t = bounds.top - it.top
            val r = it.right - bounds.right; val b = it.bottom - bounds.bottom
            if (l > 0.5 || t > 0.5 || r > 0.5 || b > 0.5) dev.mikepenz.composebuddy.core.model.boundsOf(l, t, r, b) else null
        }

        val semantics = mutableMapOf<String, String>()
        extractGroupParameters(group, semantics)

        // Source location
        val (sourceFile, sourceLine) = extractSourceLocation(group)

        // Children
        val children = try {
            @Suppress("UNCHECKED_CAST")
            val childGroups = cachedMethod(gc, "getChildren")?.invoke(group) as? List<Any> ?: emptyList()
            childGroups.mapNotNull { buildNode(it, dpi, bounds, counter) }
        } catch (_: Exception) { emptyList() }

        val displayName = name ?: if (children.isNotEmpty()) "Layout" else if (w > 0.0) "Element" else null
            ?: return null

        return HierarchyNode(
            id = counter.next(), name = displayName,
            sourceFile = sourceFile, sourceLine = sourceLine,
            bounds = bounds, boundsInParent = boundsInParent,
            size = size, offsetFromParent = offsetFromParent,
            semantics = semantics.ifEmpty { null }, children = children,
        )
    }

    private fun extractBoundsPx(group: Any): List<Double> {
        return try {
            val box = cachedMethod(group::class.java, "getBox")?.invoke(group) ?: return listOf(0.0, 0.0, 0.0, 0.0)
            val bc = box::class.java
            val l = toDouble(cachedMethod(bc, "getLeft")?.invoke(box))
            val t = toDouble(cachedMethod(bc, "getTop")?.invoke(box))
            val r = toDouble(cachedMethod(bc, "getRight")?.invoke(box))
            val b = toDouble(cachedMethod(bc, "getBottom")?.invoke(box))
            listOf(l, t, r, b)
        } catch (_: Exception) { listOf(0.0, 0.0, 0.0, 0.0) }
    }

    private fun toDouble(value: Any?): Double = when (value) {
        is Int -> value.toDouble()
        is Float -> value.toDouble()
        is Double -> value
        is Number -> value.toDouble()
        else -> 0.0
    }

    private fun extractSourceLocation(group: Any): Pair<String?, Int?> {
        return try {
            val location = cachedMethod(group::class.java, "getLocation")?.invoke(group) ?: return Pair(null, null)
            val lc = location::class.java
            val file = try { cachedMethod(lc, "getSourceFile")?.invoke(location) as? String } catch (_: Exception) { null }
            val line = try { cachedMethod(lc, "getLineNumber")?.invoke(location) as? Int } catch (_: Exception) {
                try { cachedMethod(lc, "getOffset")?.invoke(location) as? Int } catch (_: Exception) { null }
            }
            // If file is null, try getting the sourceInformation string from the group
            val resolvedFile = file ?: try {
                val data = cachedMethod(group::class.java, "getData")?.invoke(group) as? List<*>
                data?.filterIsInstance<String>()?.firstOrNull { it.contains(".kt") }
            } catch (_: Exception) { null }
            Pair(resolvedFile, line)
        } catch (_: Exception) { Pair(null, null) }
    }

    private fun extractGroupParameters(group: Any, semantics: MutableMap<String, String>) {
        try {
            @Suppress("UNCHECKED_CAST")
            val params = cachedMethod(group::class.java, "getParameters")?.invoke(group) as? List<Any> ?: return
            for (param in params) {
                val pc = param::class.java
                val pName = try { cachedMethod(pc, "getName")?.invoke(param) as? String } catch (_: Exception) { null } ?: continue
                val pValue = try { cachedMethod(pc, "getValue")?.invoke(param) } catch (_: Exception) { null }

                when (pName.lowercase()) {
                    "text" -> extractTextValue(pValue)?.let { semantics["text"] = it }
                    "color" -> formatColor(pValue)?.let { semantics["color"] = it }
                    "backgroundcolor", "containercolor" -> formatColor(pValue)?.let { semantics["backgroundColor"] = it }
                    "contentcolor" -> formatColor(pValue)?.let { semantics["contentColor"] = it }
                    "contentdescription" -> pValue?.toString()?.let { semantics["contentDescription"] = it }
                    "onclick", "click" -> semantics["onClick"] = "true"
                    "enabled" -> semantics["enabled"] = pValue?.toString() ?: "true"
                    "style" -> extractTextStyle(pValue, semantics)
                    "fontsize" -> formatTextUnit(pValue)?.let { semantics["fontSize"] = it }
                    "fontweight" -> extractFontWeight(pValue)?.let { semantics["fontWeight"] = it }
                    "fontfamily" -> pValue?.toString()?.let { semantics["fontFamily"] = it }
                    "lineheight" -> formatTextUnit(pValue)?.let { semantics["lineHeight"] = it }
                    "letterspacing" -> formatTextUnit(pValue)?.let { semantics["letterSpacing"] = it }
                    "shape" -> extractShape(pValue)?.let { semantics["shape"] = it }
                    "elevation", "shadowelevation", "tonalelevation" -> formatDp(pValue)?.let { semantics["elevation"] = it }
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

    private fun formatColor(value: Any?): String? {
        if (value == null) return null
        return try {
            when (value) {
                is Long -> {
                    // Compose Color is packed as ULong: high 32 bits = ARGB, low 32 bits = color space
                    // If value looks like a small number (< 256), it's not a color — skip
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
                        // Check toString for hex color pattern
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
    private fun extractTextStyle(value: Any?, semantics: MutableMap<String, String>) {
        if (value == null) return
        try {
            val clazz = value::class.java
            // fontSize — TextUnit (value class backed by Long)
            tryCall(clazz, value, "getFontSize")?.let { formatTextUnit(it) }?.let { semantics["fontSize"] = it }
            // fontWeight
            tryCall(clazz, value, "getFontWeight")?.let { extractFontWeight(it) }?.let { semantics["fontWeight"] = it }
            // fontFamily
            tryCall(clazz, value, "getFontFamily")?.let { semantics["fontFamily"] = it.toString() }
            // lineHeight
            tryCall(clazz, value, "getLineHeight")?.let { formatTextUnit(it) }?.let { semantics["lineHeight"] = it }
            // letterSpacing
            tryCall(clazz, value, "getLetterSpacing")?.let { formatTextUnit(it) }?.let { semantics["letterSpacing"] = it }
            // textAlign
            tryCall(clazz, value, "getTextAlign")?.let {
                val s = it.toString()
                if (s != "Unspecified" && s.isNotBlank()) semantics["textAlign"] = s
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

    /**
     * Format a TextUnit value (value class backed by Long) to "X.sp" or "X.em".
     */
    private fun formatTextUnit(value: Any?): String? {
        if (value == null) return null
        return try {
            val s = value.toString()
            // TextUnit.toString() returns "16.0.sp" or "Unspecified"
            if (s == "Unspecified" || s.isBlank()) return null
            s
        } catch (_: Exception) { null }
    }

    private fun extractFontWeight(value: Any?): String? {
        if (value == null) return null
        return try {
            // FontWeight has a .weight field (int)
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

    private fun extractShape(value: Any?): String? {
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

    private fun formatDp(value: Any?): String? {
        if (value == null) return null
        return try {
            val s = value.toString()
            if (s == "0.0.dp" || s == "Unspecified" || s.isBlank()) null else s
        } catch (_: Exception) { null }
    }

    /** Collapse consecutive same-bounds unnamed wrapper nodes. */
    private fun pruneTree(node: HierarchyNode): HierarchyNode? {
        val prunedChildren = node.children.mapNotNull { pruneTree(it) }

        // Collapse single-child unnamed node with same bounds as child
        if (node.name in listOf("Layout", "Element") && prunedChildren.size == 1 && prunedChildren[0].bounds == node.bounds) {
            val child = prunedChildren[0]
            return child.copy(
                semantics = mergeMaps(node.semantics, child.semantics),
                boundsInParent = node.boundsInParent ?: child.boundsInParent,
                offsetFromParent = node.offsetFromParent ?: child.offsetFromParent,
            )
        }

        // Collapse unnamed node with no meaningful semantics and 1 child
        val meaningful = (node.semantics ?: emptyMap()).keys - setOf("backgroundColor", "foregroundColor", "dominantColor", "transparent")
        if (node.name in listOf("Layout", "Element") && meaningful.isEmpty() && prunedChildren.size == 1) {
            val child = prunedChildren[0]
            return child.copy(
                semantics = mergeMaps(node.semantics, child.semantics),
                boundsInParent = node.boundsInParent ?: child.boundsInParent,
                offsetFromParent = node.offsetFromParent ?: child.offsetFromParent,
            )
        }

        return node.copy(children = prunedChildren)
    }

    private fun mergeMaps(a: Map<String, String>?, b: Map<String, String>?): Map<String, String>? {
        val merged = (a ?: emptyMap()) + (b ?: emptyMap())
        return merged.ifEmpty { null }
    }

    private fun r1(v: Double): Double = kotlin.math.round(v * 10) / 10.0
}
