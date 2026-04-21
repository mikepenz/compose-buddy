package dev.mikepenz.composebuddy.renderer.hierarchy

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode

/**
 * Extracts the full Compose layout tree from the slot table.
 * All dimensional output is in dp. Pure reflection — no Android or Desktop dependencies.
 */
object SlotTreeWalker {

    private class IdCounter { var value = 0; fun next() = value++ }

    // Cached reflection lookups to avoid per-node Class.getMethod() overhead
    private val methodCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method?>()

    /**
     * Extract hierarchy starting from an AbstractComposeView (Android) by digging
     * into its `composition` field and reading the SlotTable.
     */
    fun extractFromComposeView(composeView: Any, densityDpi: Int): HierarchyNode? {
        val compositionData = getCompositionData(composeView) ?: return null
        return extractFromCompositionData(compositionData, densityDpi)
    }

    /**
     * Extract hierarchy from multiple CompositionData sources — the root composition
     * plus any sub-compositions collected via LocalInspectionTables (SubcomposeLayout,
     * Scaffold slots, LazyColumn items, etc.).
     *
     * All walked groups are flattened into a single synthetic Layout root so that the
     * caller sees every typography-carrying slot entry in one tree to match against.
     */
    fun extractFromCompositionDataList(list: List<Any>, densityDpi: Int): HierarchyNode? {
        if (list.isEmpty()) return null
        if (list.size == 1) return extractFromCompositionData(list[0], densityDpi)
        val trees = list.mapNotNull { extractFromCompositionData(it, densityDpi) }
        if (trees.isEmpty()) return null
        if (trees.size == 1) return trees[0]
        val root = trees[0]
        return root.copy(name = "Layout", semantics = null, children = trees)
    }

    /**
     * Extract hierarchy from a raw CompositionData/SlotTable. Used on Desktop where
     * we capture the composition via `Inspectable(CompositionDataRecord, content)`.
     */
    fun extractFromCompositionData(compositionData: Any, densityDpi: Int): HierarchyNode? {
        // Prefer mapTree: it decodes sourceInformation (names, parameters, bounds) via
        // SourceContext, which plain asTree does not populate on newer Compose versions.
        val mapped = mapTreeExtract(compositionData, densityDpi)
        if (mapped != null) return mapped

        // Fallback: legacy asTree walk — names/params likely null, but bounds still usable.
        val counter = IdCounter()
        return try {
            val slotTreeKtClass = Class.forName("androidx.compose.ui.tooling.data.SlotTreeKt")
            val asTreeMethod = slotTreeKtClass.declaredMethods.firstOrNull {
                it.name == "asTree" && it.parameterCount >= 1
            } ?: return null
            asTreeMethod.isAccessible = true
            val rootGroup = asTreeMethod.invoke(null, compositionData) ?: return null
            val rawTree = buildNode(rootGroup, densityDpi, null, counter) ?: return null
            val pruned = pruneToList(rawTree)
            when {
                pruned.isEmpty() -> null
                pruned.size == 1 -> pruned[0]
                else -> rawTree.copy(name = "Layout", semantics = null, children = pruned)
            }
        } catch (e: Exception) {
            Logger.d { "SlotTree: ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    /**
     * Walk the composition via SlotTreeKt.mapTree — a visitor-style API that decodes
     * sourceInformation and exposes name/parameters/bounds via [SourceContext].
     * This is the same API the JetBrains Compose Desktop inspector uses.
     */
    private fun mapTreeExtract(compositionData: Any, densityDpi: Int): HierarchyNode? {
        return try {
            val slotTreeKtClass = Class.forName("androidx.compose.ui.tooling.data.SlotTreeKt")
            val contextCacheClass = Class.forName("androidx.compose.ui.tooling.data.ContextCache")
            val cache = contextCacheClass.getDeclaredConstructor().newInstance()

            // Overload: mapTree(CompositionData, Function3<CompositionGroup, SourceContext, List<T>, T>, ContextCache)
            val mapTreeMethod = slotTreeKtClass.declaredMethods.firstOrNull { m ->
                m.name == "mapTree" && m.parameterCount == 3
            } ?: return null
            mapTreeMethod.isAccessible = true

            val counter = IdCounter()
            val findParamsMethod = slotTreeKtClass.declaredMethods.firstOrNull {
                it.name == "findParameters" && it.parameterCount >= 2
            }?.also { it.isAccessible = true }

            // Build a Function3 proxy that receives (group, sourceContext, children) and returns HierarchyNode.
            val function3Class = Class.forName("kotlin.jvm.functions.Function3")
            val visitor = java.lang.reflect.Proxy.newProxyInstance(
                function3Class.classLoader, arrayOf(function3Class),
            ) { _, m, args ->
                if (m.name != "invoke" || args == null || args.size < 3) return@newProxyInstance null
                val compositionGroup = args[0]
                val sourceContext = args[1] ?: return@newProxyInstance null
                @Suppress("UNCHECKED_CAST")
                val children = (args[2] as? List<HierarchyNode?>)?.filterNotNull() ?: emptyList()
                buildNodeFromSourceContext(sourceContext, children, densityDpi, counter, compositionGroup, findParamsMethod, cache)
            }

            val root = mapTreeMethod.invoke(null, compositionData, visitor, cache) as? HierarchyNode ?: return null
            val pruned = pruneToList(root)
            when {
                pruned.isEmpty() -> null
                pruned.size == 1 -> pruned[0]
                else -> root.copy(name = "Layout", semantics = null, children = pruned)
            }
        } catch (e: Exception) {
            Logger.d { "mapTree: ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    private val sourceContextInterface: Class<*>? by lazy {
        try { Class.forName("androidx.compose.ui.tooling.data.SourceContext") } catch (_: Exception) { null }
    }

    private fun scInvoke(sourceContext: Any, method: String): Any? {
        return try {
            val iface = sourceContextInterface ?: return null
            val m = iface.getMethod(method)
            m.invoke(sourceContext)
        } catch (_: Exception) { null }
    }

    private fun buildNodeFromSourceContext(
        sourceContext: Any,
        children: List<HierarchyNode>,
        densityDpi: Int,
        counter: IdCounter,
        compositionGroup: Any? = null,
        findParamsMethod: java.lang.reflect.Method? = null,
        contextCache: Any? = null,
    ): HierarchyNode? {
        val name = scInvoke(sourceContext, "getName") as? String
        val bounds = try {
            val rect = scInvoke(sourceContext, "getBounds") ?: error("no bounds")
            val rc = rect::class.java
            val l = toDouble(rc.getMethod("getLeft").invoke(rect))
            val t = toDouble(rc.getMethod("getTop").invoke(rect))
            val r = toDouble(rc.getMethod("getRight").invoke(rect))
            val b = toDouble(rc.getMethod("getBottom").invoke(rect))
            dev.mikepenz.composebuddy.core.model.pxBoundsToDp(l, t, r, b, densityDpi)
        } catch (_: Exception) {
            dev.mikepenz.composebuddy.core.model.pxBoundsToDp(0.0, 0.0, 0.0, 0.0, densityDpi)
        }
        val w = bounds.right - bounds.left
        val h = bounds.bottom - bounds.top
        val size = dev.mikepenz.composebuddy.core.model.sizeOf(w, h)
        val (sourceFile, sourceLine) = try {
            val loc = scInvoke(sourceContext, "getLocation")
            if (loc == null) null to null else {
                val lc = loc::class.java
                val f = try { lc.getMethod("getSourceFile").invoke(loc) as? String } catch (_: Exception) { null }
                val l = try { lc.getMethod("getLineNumber").invoke(loc) as? Int } catch (_: Exception) { null }
                f to l
            }
        } catch (_: Exception) { null to null }

        val semantics = mutableMapOf<String, String>()
        @Suppress("UNCHECKED_CAST")
        var params = (scInvoke(sourceContext, "getParameters") as? List<Any>) ?: emptyList()
        if (params.isEmpty() && compositionGroup != null && findParamsMethod != null && contextCache != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                params = (findParamsMethod.invoke(null, compositionGroup, contextCache) as? List<Any>) ?: emptyList()
            } catch (_: Exception) {}
        }
        extractParameterInfos(params, semantics)
        // Fallback: on desktop Compose, ParameterInformation is often empty because the
        // Jetbrains ui-tooling-data build doesn't parse P(...) metadata the same way as
        // AndroidX. The raw group data (iterable of slot contents) still contains the
        // actual parameter values — type-scan it for TextStyle, Color, Shape, etc.
        if (compositionGroup != null) extractFromGroupData(compositionGroup, name, semantics)

        val displayName = name ?: if (children.isNotEmpty()) "Layout" else "Element"

        return HierarchyNode(
            id = counter.next(), name = displayName,
            sourceFile = sourceFile, sourceLine = sourceLine,
            bounds = bounds, boundsInParent = null,
            size = size, offsetFromParent = null,
            semantics = semantics.ifEmpty { null }, children = children,
        )
    }

    private fun extractParameterInfos(params: List<Any>, semantics: MutableMap<String, String>) {
        for (param in params) {
            val pc = param::class.java
            val pName = try { pc.getMethod("getName").invoke(param) as? String } catch (_: Exception) { null } ?: continue
            val pValue = try { pc.getMethod("getValue").invoke(param) } catch (_: Exception) { null }
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

    /**
     * Scan a CompositionGroup's raw data for typed values we recognize as composable
     * parameters (TextStyle, Color, Shape, String text, etc.). Used on Desktop where
     * ParameterInformation extraction returns nothing.
     */
    private fun extractFromGroupData(compositionGroup: Any, groupName: String?, semantics: MutableMap<String, String>) {
        try {
            val iface = Class.forName("androidx.compose.runtime.tooling.CompositionGroup")
            val getData = iface.getMethod("getData")
            val data = getData.invoke(compositionGroup) as? Iterable<*> ?: return
            var textCandidate: String? = null
            var sawTextStyle = false
            for (entry in data) {
                if (entry == null) continue
                val cls = entry::class.java.name
                when {
                    cls.endsWith(".TextStyle") -> {
                        extractTextStyle(entry, semantics); sawTextStyle = true
                    }
                    cls.endsWith(".AnnotatedString") -> {
                        if (textCandidate == null) textCandidate = extractTextValue(entry) ?: entry.toString()
                    }
                    cls.endsWith(".FontWeight") -> extractFontWeight(entry)?.let { semantics.putIfAbsent("fontWeight", it) }
                    cls.endsWith(".RoundedCornerShape") || cls.endsWith(".CutCornerShape")
                        || cls.endsWith(".RectangleShape") || cls.endsWith(".CircleShape")
                        || (entry.toString().contains("Shape") && cls.contains("shape")) ->
                        extractShape(entry)?.let { semantics.putIfAbsent("shape", it) }
                    entry is String && textCandidate == null -> textCandidate = entry
                }
            }
            // Only treat the String as "text" if this group is clearly a text composable.
            // Presence of a TextStyle (or a Text/BasicText name) is the signal.
            if (textCandidate != null && (sawTextStyle || groupName == "Text" || groupName == "BasicText")) {
                semantics.putIfAbsent("text", textCandidate)
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
                is Long -> packedColorToHex(value)
                is Int -> if (value in 0..0xFF) null else "#%08X".format(value)
                else -> {
                    // Boxed Color value class → pull the packed ULong via getValue-xxx.
                    val m = value::class.java.declaredMethods.firstOrNull {
                        it.name.startsWith("getValue") && it.returnType == Long::class.javaPrimitiveType
                    }
                    if (m != null) {
                        m.isAccessible = true
                        packedColorToHex(m.invoke(value) as Long)
                    } else {
                        val str = value.toString()
                        if (str.startsWith("Color(") || str.startsWith("#")) str else null
                    }
                }
            }
        } catch (_: Exception) { null }
    }

    /**
     * Interpret a Compose Color packed as ULong-in-Long and format as #AARRGGBB.
     * The upper 32 bits hold ARGB for SRGB; lower 32 are a color-space tag.
     * Returns null for Color.Unspecified (packed == 0x10L) and fully-transparent results.
     */
    private fun packedColorToHex(packed: Long): String? {
        // Color.Unspecified is packed as 0x10L; Color.Transparent is 0 in the argb bits.
        if (packed == 0x10L) return null
        val argb = (packed ushr 32).toInt()
        return if (argb != 0) "#%08X".format(argb) else null
    }

    /**
     * Extract typography properties from a TextStyle object via reflection.
     */
    private fun extractTextStyle(value: Any?, semantics: MutableMap<String, String>) {
        if (value == null) return
        try {
            val clazz = value::class.java
            // color on TextStyle
            tryCall(clazz, value, "getColor")?.let { formatColor(it) }?.let { semantics.putIfAbsent("color", it) }
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
                if (s != "Unspecified" && s.isNotBlank() && s != "0") semantics["textAlign"] = s
            }
        } catch (_: Exception) {}
    }

    private fun tryCall(clazz: Class<*>, obj: Any, methodName: String): Any? {
        return try {
            // Kotlin value-class returns are mangled to "$methodName-XXXXX".
            val m = clazz.methods.firstOrNull {
                it.parameterCount == 0 && (it.name == methodName || it.name.startsWith("$methodName-"))
            }
            m?.isAccessible = true
            m?.invoke(obj)
        } catch (_: Exception) { null }
    }

    /**
     * Format a TextUnit to "X.sp" / "X.em". Accepts either a boxed TextUnit or the
     * raw Long — getters on Kotlin value classes return the raw Long via reflection.
     */
    private fun formatTextUnit(value: Any?): String? {
        if (value == null) return null
        return try {
            val s = when (value) {
                is Long -> textUnitToString(value)
                else -> {
                    val direct = value.toString()
                    if (direct.isNotBlank() && direct != "kotlin.Unit" && !direct.matches(Regex("\\d+"))) direct
                    else null
                }
            } ?: return null
            if (s == "Unspecified" || s.isBlank()) null else s
        } catch (_: Exception) { null }
    }

    private val textUnitToStringMethod: java.lang.reflect.Method? by lazy {
        try {
            val cls = Class.forName("androidx.compose.ui.unit.TextUnit")
            cls.methods.firstOrNull { it.name == "toString-impl" && it.parameterCount == 1 }
        } catch (_: Exception) { null }
    }

    private fun textUnitToString(raw: Long): String? {
        return try {
            textUnitToStringMethod?.invoke(null, raw) as? String
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

    /**
     * Names we always want to elide from the hierarchy. Their children are lifted
     * into the parent. These are Compose internals that don't correspond to UI the
     * user authored (property getters, memoization, composition-local plumbing,
     * measure policies, Material internal helpers, etc.).
     */
    private val transparentExactNames = setOf(
        "CompositionLocalProvider", "ReusableComposeNode", "remember",
        "rememberComposableLambda", "rememberTextSelectionColors",
        "contentColorFor", "cardColors", "cardElevation", "applyTonalElevation",
        "surfaceColorAtElevation", "shadowElevation", "BackgroundTextMeasurement",
        "columnMeasurePolicy", "rowMeasurePolicy", "MaterialTheme",
        "ProvideTextStyle",
    )

    private fun isTransparentName(name: String?): Boolean {
        if (name == null) return false
        // Property accessors like <get-current>, <get-shape>.
        if (name.startsWith("<") && name.endsWith(">")) return true
        return name in transparentExactNames
    }

    /**
     * Prune the slot-tree-derived hierarchy:
     *  - Elide transparent helper nodes, lifting their children up.
     *  - Collapse same-bounds unnamed wrappers (Layout/Element) into their sole child.
     *  - Drop empty-bounds leaves.
     */
    private fun pruneToList(node: HierarchyNode): List<HierarchyNode> {
        val prunedChildren = node.children.flatMap { pruneToList(it) }

        if (isTransparentName(node.name)) {
            // Lift children up; drop any non-structural semantics on the helper itself.
            return prunedChildren
        }

        // Drop leaves with no size, no semantics, no children, and no useful name.
        if (prunedChildren.isEmpty() && node.size.width <= 0.0 && node.size.height <= 0.0
            && node.semantics.isNullOrEmpty() && (node.name == "Layout" || node.name == "Element")) {
            return emptyList()
        }

        // Collapse unnamed wrapper with 1 child sharing the same bounds.
        if (node.name in listOf("Layout", "Element") && prunedChildren.size == 1 && prunedChildren[0].bounds == node.bounds) {
            val child = prunedChildren[0]
            return listOf(child.copy(
                semantics = mergeMaps(node.semantics, child.semantics),
                boundsInParent = node.boundsInParent ?: child.boundsInParent,
                offsetFromParent = node.offsetFromParent ?: child.offsetFromParent,
            ))
        }

        // Collapse unnamed node with no meaningful semantics and 1 child.
        val meaningful = (node.semantics ?: emptyMap()).keys - setOf("backgroundColor", "foregroundColor", "dominantColor", "transparent")
        if (node.name in listOf("Layout", "Element") && meaningful.isEmpty() && prunedChildren.size == 1) {
            val child = prunedChildren[0]
            return listOf(child.copy(
                semantics = mergeMaps(node.semantics, child.semantics),
                boundsInParent = node.boundsInParent ?: child.boundsInParent,
                offsetFromParent = node.offsetFromParent ?: child.offsetFromParent,
            ))
        }

        return listOf(node.copy(children = prunedChildren))
    }

    private fun mergeMaps(a: Map<String, String>?, b: Map<String, String>?): Map<String, String>? {
        val merged = (a ?: emptyMap()) + (b ?: emptyMap())
        return merged.ifEmpty { null }
    }
}
