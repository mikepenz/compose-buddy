package dev.mikepenz.composebuddy.renderer.android

import co.touchlab.kermit.Logger
import com.android.ide.common.rendering.api.HardwareConfig
import com.android.ide.common.rendering.api.ILayoutLog
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.SessionParams
import com.android.layoutlib.bridge.Bridge
import com.android.resources.ResourceType
import dev.mikepenz.composebuddy.renderer.DeviceConfigMapper
import dev.mikepenz.composebuddy.renderer.android.bridge.MinimalLayoutlibCallback
import dev.mikepenz.composebuddy.renderer.android.bridge.MinimalRenderResources
import dev.mikepenz.composebuddy.renderer.android.bridge.SimpleLayoutPullParser
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * Renders views using layoutlib Bridge with framework resource resolution.
 * No Paparazzi dependency — uses Bridge API directly with MinimalRenderResources.
 */
class BridgeRenderer(
    private val paths: LayoutlibProvider.LayoutlibPaths,
) {
    private var bridge: Bridge? = null
    private var initialized = false
    private var resourceResolver: MinimalRenderResources? = null

    private val logger = object : ILayoutLog {
        override fun warning(tag: String?, message: String?, viewCookie: Any?, data: Any?) {
            Logger.w { "layoutlib [$tag]: $message" }
        }
        override fun fidelityWarning(tag: String?, message: String?, throwable: Throwable?, viewCookie: Any?, data: Any?) {}
        override fun error(tag: String?, message: String?, viewCookie: Any?, data: Any?) {
            Logger.e { "layoutlib [$tag]: $message" }
        }
        override fun error(tag: String?, message: String?, throwable: Throwable?, viewCookie: Any?, data: Any?) {
            Logger.e { "layoutlib [$tag]: $message" }
        }
        override fun logAndroidFramework(priority: Int, tag: String?, message: String?) {
            Logger.d { "android [$tag]: $message" }
        }
    }

    fun init(): Boolean {
        if (initialized) return true

        val attrsFile = File(paths.frameworkResDir, "values/attrs.xml")
        if (!paths.buildProp.exists() || !paths.nativeLibDir.exists()) {
            Logger.e { "Missing required layoutlib files" }
            return false
        }

        val systemProperties = loadBuildProperties(paths.buildProp) +
            mapOf("debug.choreographer.frametime" to "false")
        val enumMap = if (attrsFile.exists()) parseEnumMap(attrsFile) else emptyMap()

        bridge = Bridge().apply {
            val success = init(systemProperties, paths.fontsDir, paths.nativeLibDir.absolutePath,
                paths.icuData.absolutePath, paths.hyphenData.absolutePath,
                arrayOf(paths.keyboardData.absolutePath), enumMap, logger)
            if (!success) { Logger.e { "Bridge.init() returned false" }; return false }
        }
        Bridge.getLock().lock()
        try { Bridge.setLog(logger) } finally { Bridge.getLock().unlock() }

        // Load framework resources from res/values/*.xml
        resourceResolver = MinimalRenderResources(paths.frameworkResDir)

        initialized = true
        Logger.i { "Bridge initialized with framework resources" }
        return true
    }

    fun renderSession(config: DeviceConfigMapper.LayoutlibConfig): BufferedImage? {
        if (!initialized || bridge == null) return null

        var session: com.android.ide.common.rendering.api.RenderSession? = null
        try {
            Bridge.prepareThread()

            val hardwareConfig = HardwareConfig(
                config.screenWidthPx, config.screenHeightPx,
                com.android.resources.Density.create(config.densityDpi),
                config.xDpi, config.yDpi,
                com.android.resources.ScreenSize.NORMAL,
                com.android.resources.ScreenOrientation.PORTRAIT,
                com.android.resources.ScreenRound.NOTROUND, false,
            )

            val bgColor = if (config.showBackground) "%08X".format(config.backgroundColor) else "00000000"
            val layoutParser = SimpleLayoutPullParser("""
                <?xml version="1.0" encoding="utf-8"?>
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#$bgColor"/>
            """.trimIndent())

            val callback = MinimalLayoutlibCallback()
            val renderResources = resourceResolver ?: MinimalRenderResources(paths.frameworkResDir)

            val sessionParams = SessionParams(
                layoutParser, SessionParams.RenderingMode.NORMAL, null,
                hardwareConfig, renderResources, callback, 0, 35, logger,
            )
            sessionParams.fontScale = config.fontScale

            session = bridge!!.createSession(sessionParams)
            if (session.result.status != com.android.ide.common.rendering.api.Result.Status.SUCCESS) {
                Logger.w { "Render: ${session.result.status} — ${session.result.errorMessage}" }
            }

            val image = session.image
            if (image != null) Logger.i { "Rendered: ${image.width}x${image.height}" }
            return image
        } catch (e: Exception) {
            Logger.e(e) { "Render failed: ${e.message}" }
            return null
        } finally {
            session?.dispose()
            Bridge.cleanupThread()
        }
    }

    fun dispose() { initialized = false; bridge = null; resourceResolver = null }

    /**
     * Load FrameworkResourceRepository via reflection (it's internal in Paparazzi).
     */

    private fun loadBuildProperties(buildProp: File): Map<String, String> {
        val props = Properties()
        FileInputStream(buildProp).use { props.load(it) }
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }

    private fun parseEnumMap(attrsFile: File): Map<String, Map<String, Int>> {
        val map = mutableMapOf<String, MutableMap<String, Int>>()
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(FileInputStream(attrsFile), null)
            var currentAttr: String? = null
            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        "attr" -> currentAttr = parser.getAttributeValue(null, "name")
                        "enum", "flag" -> if (currentAttr != null) {
                            val name = parser.getAttributeValue(null, "name")
                            val value = parser.getAttributeValue(null, "value")
                            map.getOrPut(currentAttr) { mutableMapOf() }[name] = java.lang.Long.decode(value).toInt()
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "attr") currentAttr = null
                }
                eventType = parser.next()
            }
        } catch (e: Exception) { Logger.w(e) { "Failed to parse attrs.xml: ${e.message}" } }
        return map
    }
}
