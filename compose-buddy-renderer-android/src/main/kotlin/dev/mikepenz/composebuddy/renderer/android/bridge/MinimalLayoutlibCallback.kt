package dev.mikepenz.composebuddy.renderer.android.bridge

import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Minimal LayoutlibCallback for standalone rendering.
 * Loads framework resource ID mappings from the android.R class in the layoutlib JAR.
 */
class MinimalLayoutlibCallback(
    private val classLoader: ClassLoader = MinimalLayoutlibCallback::class.java.classLoader,
) : LayoutlibCallback() {

    private val resourceMap = mutableMapOf<Int, ResourceReference>()
    private val reverseMap = mutableMapOf<ResourceReference, Int>()
    private var nextId = 0x7f000000

    init {
        loadFrameworkResourceIds()
    }

    /**
     * Load framework resource IDs from android.R inner classes.
     * The layoutlib JAR contains android.R with all framework resource IDs.
     */
    private fun loadFrameworkResourceIds() {
        // Load public framework resources from android.R
        loadRClass("android.R")
        // Load internal framework resources from com.android.internal.R
        loadRClass("com.android.internal.R")
        co.touchlab.kermit.Logger.i { "Loaded ${resourceMap.size} framework resource IDs" }
    }

    private fun loadRClass(className: String) {
        try {
            val rClass = classLoader.loadClass(className)
            for (innerClass in rClass.declaredClasses) {
                val typeName = innerClass.simpleName
                val resourceType = ResourceType.fromClassName(typeName) ?: continue

                for (field in innerClass.declaredFields) {
                    if (field.type == Int::class.javaPrimitiveType) {
                        try {
                            field.isAccessible = true
                            val id = field.getInt(null)
                            val ref = ResourceReference(ResourceNamespace.ANDROID, resourceType, field.name)
                            resourceMap[id] = ref
                            reverseMap[ref] = id
                        } catch (_: Exception) {
                            // Skip inaccessible fields
                        }
                    }
                }
            }
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.d { "Could not load $className: ${e.message}" }
        }
    }

    override fun loadView(name: String, constructorSignature: Array<out Class<*>>?, constructorArgs: Array<out Any>?): Any? {
        return try {
            val clazz = classLoader.loadClass(name)
            if (constructorSignature != null && constructorArgs != null) {
                val constructor = clazz.getConstructor(*constructorSignature)
                constructor.newInstance(*constructorArgs)
            } else {
                clazz.getDeclaredConstructor().newInstance()
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun resolveResourceId(id: Int): ResourceReference? = resourceMap[id]

    override fun getOrGenerateResourceId(resource: ResourceReference): Int {
        return reverseMap.getOrPut(resource) {
            val id = nextId++
            resourceMap[id] = resource
            id
        }
    }

    override fun getParser(layoutResource: ResourceValue): ILayoutPullParser? {
        // Try to provide a parser for framework XML resources (color state lists, drawables, etc.)
        val value = layoutResource.value ?: return null
        val file = java.io.File(value)
        if (file.exists() && file.extension == "xml") {
            return SimpleLayoutPullParser(file.readText())
        }
        return null
    }

    override fun getAdapterBinding(viewObject: Any?, attributes: MutableMap<String, String>?): AdapterBinding? = null

    override fun getActionBarCallback(): ActionBarCallback = ActionBarCallback()

    override fun findClass(name: String): Class<*>? {
        return try {
            classLoader.loadClass(name)
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    // XmlParserFactory methods
    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser? = createXmlParserForFile(fileName)

    override fun createXmlParserForFile(fileName: String): XmlPullParser? {
        val file = java.io.File(fileName)
        if (file.exists() && file.extension == "xml") {
            return XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(file.inputStream(), "UTF-8")
            }
        }
        return createXmlParser()
    }

    override fun createXmlParser(): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
}
