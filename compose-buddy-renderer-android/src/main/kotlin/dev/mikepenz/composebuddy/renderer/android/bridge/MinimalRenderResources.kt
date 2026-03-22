package dev.mikepenz.composebuddy.renderer.android.bridge

import co.touchlab.kermit.Logger
import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import java.io.File

/**
 * RenderResources that loads framework XML values (dimens, colors, integers, bools, strings)
 * from the layoutlib-resources res/values/ directory.
 *
 * This enables FrameLayout and other framework views to resolve their required resources
 * (e.g., config_scrollbarSize) without the full Paparazzi resource repository.
 */
class MinimalRenderResources(
    frameworkResDir: File? = null,
) : RenderResources() {

    // type -> (name -> value)
    private val frameworkValues = mutableMapOf<String, MutableMap<String, String>>()

    init {
        if (frameworkResDir != null) {
            loadFrameworkResources(frameworkResDir)
            Logger.i { "Loaded ${frameworkValues.values.sumOf { it.size }} framework resource values" }
        }
    }

    private fun loadFrameworkResources(resDir: File) {
        val valuesDir = File(resDir, "values")
        if (!valuesDir.exists()) return

        valuesDir.listFiles()?.filter { it.extension == "xml" }?.forEach { xmlFile ->
            try {
                parseValuesXml(xmlFile)
            } catch (e: Exception) {
                Logger.d { "Skipping ${xmlFile.name}: ${e.message}" }
            }
        }
    }

    private fun parseValuesXml(xmlFile: File) {
        xmlFile.inputStream().use { inputStream ->
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    val name = parser.getAttributeValue(null, "name")

                    if (name != null) {
                        when (tagName) {
                            "dimen", "color", "integer", "bool", "string", "fraction", "drawable" -> {
                                val value = parser.nextText()
                                frameworkValues.getOrPut(tagName) { mutableMapOf() }[name] = value
                            }
                            "item" -> {
                                val type = parser.getAttributeValue(null, "type")
                                if (type != null) {
                                    val value = parser.nextText()
                                    frameworkValues.getOrPut(type) { mutableMapOf() }[name] = value
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
    }

    override fun findResValue(reference: String?, forceFrameworkOnly: Boolean): ResourceValue? {
        if (reference == null) return null

        // Handle @type/name and @android:type/name references
        val url = ResourceUrl.parse(reference) ?: return null
        val ns = if (url.isFramework) ResourceNamespace.ANDROID else ResourceNamespace.RES_AUTO
        val typeName = url.type?.getName() ?: return null
        val resType = ResourceType.fromClassName(typeName) ?: return null

        val value = frameworkValues[typeName]?.get(url.name)
        if (value != null) {
            return ResourceValueImpl(ResourceReference(ns, resType, url.name), value)
        }
        return null
    }

    override fun resolveResValue(value: ResourceValue?): ResourceValue? {
        if (value == null) return null
        val stringValue = value.value ?: return value

        if (stringValue.startsWith("@")) {
            val resolved = findResValue(stringValue, false)
            if (resolved != null) return resolveResValue(resolved)
        }
        return value
    }

    override fun dereference(value: ResourceValue?): ResourceValue? = resolveResValue(value)

    /**
     * Resolve a resource by its ResourceReference (used by the Bridge after resolving ID → name).
     */
    fun resolveByReference(ref: ResourceReference): ResourceValue? {
        val typeName = ref.resourceType.getName()
        val value = frameworkValues[typeName]?.get(ref.name)
        if (value != null) {
            return ResourceValueImpl(ref, value)
        }
        return null
    }

    override fun getUnresolvedResource(reference: ResourceReference): ResourceValue? {
        val typeName = reference.resourceType.getName()
        val value = frameworkValues[typeName]?.get(reference.name)
        if (value != null) {
            return ResourceValueImpl(reference, value)
        }
        return null
    }

    override fun getResolvedResource(reference: ResourceReference): ResourceValue? {
        return resolveResValue(getUnresolvedResource(reference))
    }

    override fun findItemInTheme(reference: ResourceReference): ResourceValue? {
        // Try to find the resource by checking all loaded types
        val name = reference.name
        for ((typeName, values) in frameworkValues) {
            val value = values[name]
            if (value != null) {
                val resType = ResourceType.fromClassName(typeName) ?: continue
                val ref = ResourceReference(ResourceNamespace.ANDROID, resType, name)
                return ResourceValueImpl(ref, value)
            }
        }
        return null
    }
}
