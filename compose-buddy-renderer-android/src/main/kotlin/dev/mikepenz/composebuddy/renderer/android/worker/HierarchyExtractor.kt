package dev.mikepenz.composebuddy.renderer.android.worker

import co.touchlab.kermit.Logger
import com.android.ide.common.rendering.api.ViewInfo
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import dev.mikepenz.composebuddy.core.semantics.SemanticsKeyMapper

/**
 * Extracts layout hierarchy from a rendered Compose view tree.
 * All dimensional output is in dp.
 */
object HierarchyExtractor {

    private class IdCounter { var value = 0; fun next() = value++ }

    /** Default semantic fields included in output. Minimal set for AI agents. */
    val DEFAULT_SEMANTICS = setOf(
        "text", "contentDescription", "testTag", "role",
        "onClick", "onLongClick", "editableText",
        "stateDescription", "toggleableState", "selected",
        "heading", "disabled", "focused",
    )

    /** All semantic field names seen during extraction. */
    val allSeenSemantics: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Which semantic fields to include. Null = all, empty set = none. */
    var includedSemantics: Set<String>? = DEFAULT_SEMANTICS

    fun extract(rootViewGroup: android.view.ViewGroup, densityDpi: Int): HierarchyNode? {
        val counter = IdCounter()
        try {
            val androidComposeView = findViewByClassName(rootViewGroup, "AndroidComposeView")
            if (androidComposeView != null) {
                val hierarchy = extractFromSemantics(androidComposeView, densityDpi, counter)
                if (hierarchy != null) {
                    Logger.i { "Hierarchy: ${countNodes(hierarchy)} nodes (semantics)" }
                    return hierarchy
                }
            }
            return null
        } catch (e: Exception) {
            Logger.w(e) { "Hierarchy failed: ${e::class.simpleName}: ${e.message}" }
            return null
        }
    }

    fun extractSemanticsMap(rootViewGroup: android.view.ViewGroup, dpi: Int): Map<String, Map<String, String>> {
        val androidComposeView = findViewByClassName(rootViewGroup, "AndroidComposeView") ?: return emptyMap()
        val map = mutableMapOf<String, Map<String, String>>()
        try {
            val semanticsOwner = androidComposeView::class.java.getDeclaredMethod("getSemanticsOwner").invoke(androidComposeView) ?: return map
            val rootNode = try {
                semanticsOwner::class.java.getDeclaredMethod("getUnmergedRootSemanticsNode").invoke(semanticsOwner)
            } catch (_: NoSuchMethodException) {
                semanticsOwner::class.java.getDeclaredMethod("getRootSemanticsNode").invoke(semanticsOwner)
            } ?: return map
            collectSemanticsFlat(rootNode, map)
        } catch (_: Exception) {}
        return map
    }

    private fun extractFromSemantics(androidComposeView: android.view.View, dpi: Int, counter: IdCounter): HierarchyNode? {
        try {
            val semanticsOwner = try {
                androidComposeView::class.java.getDeclaredMethod("getSemanticsOwner").invoke(androidComposeView)
            } catch (_: NoSuchMethodException) {
                androidComposeView::class.java.interfaces
                    .firstOrNull { it.simpleName.contains("ViewRootForTest") }
                    ?.getDeclaredMethod("getSemanticsOwner")?.invoke(androidComposeView)
            } ?: return null

            val rootNode = try {
                semanticsOwner::class.java.getDeclaredMethod("getRootSemanticsNode").invoke(semanticsOwner)
            } catch (_: NoSuchMethodException) {
                semanticsOwner::class.java.getDeclaredMethod("getUnmergedRootSemanticsNode").invoke(semanticsOwner)
            } ?: return null

            return buildFromSemanticsNode(rootNode, dpi, null, counter)
        } catch (e: Exception) {
            Logger.d { "Semantics: ${e::class.simpleName}: ${e.message}" }
            return null
        }
    }

    private fun buildFromSemanticsNode(node: Any, dpi: Int, parentBounds: Bounds?, counter: IdCounter): HierarchyNode {
        val id = counter.next()
        val (lPx, tPx, rPx, bPx) = try {
            val b = node::class.java.getDeclaredMethod("getBoundsInRoot").invoke(node)!!
            listOf(
                (b::class.java.getDeclaredMethod("getLeft").invoke(b) as Float).toDouble(),
                (b::class.java.getDeclaredMethod("getTop").invoke(b) as Float).toDouble(),
                (b::class.java.getDeclaredMethod("getRight").invoke(b) as Float).toDouble(),
                (b::class.java.getDeclaredMethod("getBottom").invoke(b) as Float).toDouble(),
            )
        } catch (_: Exception) { listOf(0.0, 0.0, 0.0, 0.0) }

        val bounds = dev.mikepenz.composebuddy.core.model.pxBoundsToDp(lPx.toDouble(), tPx.toDouble(), rPx.toDouble(), bPx.toDouble(), dpi)
        val boundsInParent = parentBounds?.let {
            dev.mikepenz.composebuddy.core.model.boundsOf(bounds.left - it.left, bounds.top - it.top, bounds.right - it.left, bounds.bottom - it.top)
        }
        val size = dev.mikepenz.composebuddy.core.model.sizeOf(bounds.right - bounds.left, bounds.bottom - bounds.top)
        val offsetFromParent = parentBounds?.let {
            val l = bounds.left - it.left; val t = bounds.top - it.top
            val r = it.right - bounds.right; val b = it.bottom - bounds.bottom
            if (l > 0.5 || t > 0.5 || r > 0.5 || b > 0.5) dev.mikepenz.composebuddy.core.model.boundsOf(l, t, r, b) else null
        }

        val semantics = mutableMapOf<String, String>()
        // Try known config methods + dynamically discovered getUnmergedConfig variants
        val configMethods = mutableListOf("getConfig", "mergeConfig")
        try {
            node::class.java.declaredMethods.filter { it.name.startsWith("getUnmergedConfig") }
                .forEach { configMethods.add(1, it.name) } // insert after getConfig
        } catch (_: Exception) {}
        for (method in configMethods) {
            extractConfig(node, method, semantics)
        }

        val children = try {
            val obj = try { node::class.java.getDeclaredMethod("getChildren").invoke(node) }
            catch (_: NoSuchMethodException) { node::class.java.getDeclaredMethod("getReplacedChildren").invoke(node) }
            @Suppress("UNCHECKED_CAST")
            (obj as? List<Any>)?.map { buildFromSemanticsNode(it, dpi, bounds, counter) } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val name = inferName(semantics, children)
        return HierarchyNode(id = id, name = name, bounds = bounds, boundsInParent = boundsInParent,
            size = size, offsetFromParent = offsetFromParent, semantics = semantics.ifEmpty { null }, children = children)
    }

    private fun inferName(sem: Map<String, String>, children: List<HierarchyNode>) = when {
        sem["role"]?.contains("Button", true) == true -> "Button"
        sem["role"]?.contains("Checkbox", true) == true -> "Checkbox"
        sem["role"]?.contains("Switch", true) == true -> "Switch"
        sem["role"]?.contains("Image", true) == true -> "Image"
        sem["role"] != null -> sem["role"]!!
        sem["testTag"] != null -> sem["testTag"]!!
        sem.containsKey("editableText") -> "TextField"
        sem.containsKey("text") && children.isEmpty() -> "Text"
        sem.containsKey("onClick") -> "Clickable"
        sem.containsKey("isTraversalGroup") && children.isNotEmpty() -> "Container"
        children.isNotEmpty() -> "Layout"
        else -> "Element"
    }

    private fun extractConfig(node: Any, methodName: String, sem: MutableMap<String, String>) {
        try {
            val m = node::class.java.getDeclaredMethod(methodName); m.isAccessible = true
            val config = m.invoke(node) ?: return
            if (config is Iterable<*>) {
                for (entry in config) {
                    val me = (entry as? Map.Entry<*, *>) ?: continue
                    // Use SemanticsPropertyKey.getName() for stable key names across Compose versions
                    val rawKey = me.key ?: continue
                    val key = try {
                        rawKey::class.java.getMethod("getName").invoke(rawKey)?.toString()
                    } catch (_: Exception) { null } ?: rawKey.toString()
                    val propName = SemanticsKeyMapper.mapKey(key)
                    allSeenSemantics.add(propName)
                    // Filter to included set (null = include all)
                    val included = includedSemantics
                    if (included != null && propName !in included) continue
                    if (!sem.containsKey(propName) || methodName == "getConfig") {
                        val v = SemanticsKeyMapper.formatValue(me.value)
                        if (v.isNotBlank()) sem[propName] = v
                    }
                }
            }
        } catch (_: NoSuchMethodException) {} catch (_: Exception) {}
    }

    private fun collectSemanticsFlat(node: Any, map: MutableMap<String, Map<String, String>>) {
        try {
            val b = node::class.java.getDeclaredMethod("getBoundsInRoot").invoke(node) ?: return
            val l = (b::class.java.getDeclaredMethod("getLeft").invoke(b) as Float).toInt()
            val t = (b::class.java.getDeclaredMethod("getTop").invoke(b) as Float).toInt()
            val r = (b::class.java.getDeclaredMethod("getRight").invoke(b) as Float).toInt()
            val bo = (b::class.java.getDeclaredMethod("getBottom").invoke(b) as Float).toInt()
            val props = mutableMapOf<String, String>()
            val config = node::class.java.getDeclaredMethod("getConfig").invoke(node)
            if (config is Iterable<*>) for (e in config) {
                val me = (e as? Map.Entry<*, *>) ?: continue
                val pn = SemanticsKeyMapper.mapKey(me.key?.toString() ?: continue)
                val pv = SemanticsKeyMapper.formatValue(me.value); if (pv.isNotBlank()) props[pn] = pv
            }
            if (props.isNotEmpty()) map["$l,$t,$r,$bo"] = props
            @Suppress("UNCHECKED_CAST")
            (try { node::class.java.getDeclaredMethod("getChildren").invoke(node) as? List<Any> } catch (_: Exception) { null })
                ?.forEach { collectSemanticsFlat(it, map) }
        } catch (_: Exception) {}
    }

    private fun findViewByClassName(vg: android.view.ViewGroup, name: String): android.view.View? {
        for (i in 0 until vg.childCount) {
            val c = vg.getChildAt(i)
            if (c::class.java.simpleName.contains(name)) return c
            if (c is android.view.ViewGroup) findViewByClassName(c, name)?.let { return it }
        }
        return null
    }

    private fun countNodes(n: HierarchyNode): Int = 1 + n.children.sumOf { countNodes(it) }
}
