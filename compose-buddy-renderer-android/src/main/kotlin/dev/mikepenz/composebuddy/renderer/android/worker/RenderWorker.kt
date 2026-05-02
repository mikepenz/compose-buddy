package dev.mikepenz.composebuddy.renderer.android.worker

import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.android.ide.common.rendering.api.AssetRepository
import com.android.ide.common.rendering.api.HardwareConfig
import com.android.ide.common.rendering.api.ILayoutLog
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.SessionParams
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.android.RenderParamsFlags
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import dev.mikepenz.composebuddy.renderer.android.bridge.MinimalLayoutlibCallback
import dev.mikepenz.composebuddy.renderer.android.bridge.MinimalRenderResources
import dev.mikepenz.composebuddy.renderer.android.bridge.SimpleLayoutPullParser
import dev.mikepenz.composebuddy.renderer.hierarchy.HierarchyMerger
import dev.mikepenz.composebuddy.renderer.hierarchy.SlotTreeWalker
import dev.mikepenz.composebuddy.renderer.worker.ComposableInvoker
import dev.mikepenz.composebuddy.renderer.worker.WorkerProtocol
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties
import javax.imageio.ImageIO

/**
 * Standalone worker process for rendering Compose @Preview functions.
 *
 * Uses Bridge.init() + RenderSessionImpl directly — no Paparazzi dependency.
 * Manages ComposeView lifecycle (Recomposer, Choreographer, LifecycleOwner) directly.
 */
object RenderWorker {

    // Use shared protocol types — eliminates deserialization mismatches
    private typealias RenderRequest = WorkerProtocol.RenderRequest
    private typealias RenderResponse = WorkerProtocol.RenderResponse

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    // Direct render session management (no PaparazziSdk)
    private var renderSession: RenderSessionImpl? = null
    private var bridgeRenderSession: RenderSession? = null
    private var resourceResolver: MinimalRenderResources? = null

    private var rootViewGroup: android.view.ViewGroup? = null
    private var capturedImage: BufferedImage? = null
    private var capturedHierarchy: dev.mikepenz.composebuddy.core.model.HierarchyNode? = null
    private var currentComposeView: Any? = null
    private var renderDensityDpi: Int = 420

    private data class SessionConfig(val densityDpi: Int, val widthPx: Int, val heightPx: Int, val uiMode: Int, val fontScale: Float, val previewFqn: String = "")
    private var currentSessionConfig: SessionConfig? = null
    private var bridgeInitialized = false

    // LRU session cache: avoids expensive teardown/recreate when switching between configs
    private data class CachedSession(
        val config: SessionConfig,
        val session: RenderSessionImpl,
        val bridgeSession: RenderSession,
        val rootViewGroup: android.view.ViewGroup,
    )
    private val sessionCache = LinkedHashMap<SessionConfig, CachedSession>(4, 0.75f, true)
    private const val MAX_CACHED_SESSIONS = 3
    /** Target Android API level for layoutlib rendering. Update when upgrading layoutlib. */
    private const val TARGET_API_LEVEL = 35

    @JvmStatic
    fun main(args: Array<String>) {
        Logger.setLogWriters(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                System.err.println("${severity.name.take(1)}: [$tag] $message")
                throwable?.printStackTrace(System.err)
            }
        })
        Logger.setTag("worker")

        val reader = BufferedReader(InputStreamReader(System.`in`))
        val firstLine = reader.readLine() ?: return
        val initReq = json.decodeFromString<RenderRequest>(firstLine)

        try {
            initBridge(initReq)
            createSession(initReq)
            println("READY")
            System.out.flush()
        } catch (e: Throwable) {
            Logger.e { "Init failed: ${e.message}" }
            e.printStackTrace(System.err)
            println(json.encodeToString(RenderResponse(previewFqn = "", success = false, error = "Init: ${e.message}")))
            System.out.flush()
            return
        }

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val req = json.decodeFromString<RenderRequest>(line)
            renderDensityDpi = req.densityDpi
            ensureSessionConfig(req)
            val resp = render(req)
            println(json.encodeToString(resp))
            System.out.flush()
        }

        teardownSession()
    }

    // --- Bridge initialization (once per process) ---

    private fun initBridge(req: RenderRequest) {
        val runtimeRoot = File(req.layoutlibRuntimeRoot)
        val resourcesRoot = File(req.layoutlibResourcesRoot)

        installEditModeInterceptor()
        dev.mikepenz.composebuddy.renderer.android.bridge.AndroidxRStubGenerator.ensureStubs()

        val buildProp = File(runtimeRoot, "build.prop")
        val fontsDir = File(runtimeRoot, "data/fonts")
        val nativeLibDir = File(runtimeRoot, "data/${detectNativeLibSubdir()}")
        // Auto-detect ICU data file (name includes version, e.g., icudt76l.dat, icudt77l.dat)
        val icuDir = File(runtimeRoot, "data/icu")
        val icuData = icuDir.listFiles()?.firstOrNull { it.name.startsWith("icudt") && it.name.endsWith(".dat") }
            ?: File(icuDir, "icudt76l.dat")
        val keyboardData = File(runtimeRoot, "data/keyboards/Generic.kcm")
        val hyphenData = File(runtimeRoot, "data/hyphen-data")
        val attrsFile = File(resourcesRoot, "res/values/attrs.xml")

        val sysProps = loadProps(buildProp) + mapOf("debug.choreographer.frametime" to "false")
        val enumMap = if (attrsFile.exists()) parseEnumMap(attrsFile) else emptyMap()

        val bridge = Bridge()
        check(bridge.init(sysProps, fontsDir, nativeLibDir.absolutePath,
            icuData.absolutePath, hyphenData.absolutePath,
            arrayOf(keyboardData.absolutePath), enumMap, quietLogger)) { "Bridge.init() failed" }
        Bridge.getLock().lock()
        try { Bridge.setLog(quietLogger) } finally { Bridge.getLock().unlock() }

        // Load framework resources for rendering
        val frameworkResDir = File(resourcesRoot, "res")
        resourceResolver = MinimalRenderResources(frameworkResDir)

        bridgeInitialized = true
        Logger.i { "Bridge initialized (no Paparazzi)" }
    }

    // --- Session management ---

    private fun createSession(req: RenderRequest) {
        val density = Density.create(req.densityDpi)
        val nightMode = if (req.uiMode and 0x30 == 0x20) NightMode.NIGHT else NightMode.NOTNIGHT

        val hardwareConfig = HardwareConfig(
            req.widthPx, req.heightPx, density,
            req.densityDpi.toFloat(), req.densityDpi.toFloat(),
            ScreenSize.NORMAL, ScreenOrientation.PORTRAIT,
            ScreenRound.NOTROUND, false,
        )

        val layoutXml = """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>"""
        val layoutParser = SimpleLayoutPullParser(layoutXml)
        val callback = MinimalLayoutlibCallback()

        val sessionParams = SessionParams(
            layoutParser, SessionParams.RenderingMode.NORMAL, null,
            hardwareConfig, resourceResolver, callback, 0, TARGET_API_LEVEL, quietLogger,
        )
        sessionParams.fontScale = req.fontScale
        sessionParams.setFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true)
        sessionParams.setForceNoDecor()
        sessionParams.setAssetRepository(ClasspathAssetRepository())

        val session = RenderSessionImpl(sessionParams)
        session.setElapsedFrameTimeNanos(0L)

        val initResult = session.init(sessionParams.timeout)
        Logger.i { "Session init: ${initResult.status}, error=${initResult.errorMessage}" }

        // Set default bitmap density
        try {
            val bitmapClass = Class.forName("android.graphics.Bitmap")
            val setDensityMethod = bitmapClass.getDeclaredMethod("setDefaultDensity", Int::class.javaPrimitiveType)
            setDensityMethod.invoke(null, req.densityDpi)
        } catch (_: Exception) {}

        // Create BridgeRenderSession via reflection
        val inflateResult = session.inflate()
        Logger.i { "Inflate result: ${inflateResult.status}, error=${inflateResult.errorMessage}" }
        val bridgeSessionClass = Class.forName("com.android.layoutlib.bridge.BridgeRenderSession")
        val constructor = bridgeSessionClass.getDeclaredConstructor(
            RenderSessionImpl::class.java,
            com.android.ide.common.rendering.api.Result::class.java
        )
        constructor.isAccessible = true
        val bridgeSession = constructor.newInstance(session, inflateResult) as RenderSession

        // Workaround for missing registerForRefreshRateChanges
        try {
            val viewGroup = bridgeSession.rootViews[0].viewObject as android.view.ViewGroup
            val displayField = android.view.Display::class.java.getDeclaredField("mRefreshRateChangesRegistered")
            displayField.isAccessible = true
            displayField.set(viewGroup.display, true)
        } catch (_: Exception) {}

        // Force initial layout to size the view hierarchy
        session.render(true)

        // Find the properly-sized system parent ViewGroup from the DecorView hierarchy.
        // The XML FrameLayout at rootViews[0] is 0x0 (match_parent not resolved by Bridge).
        // We need the system FrameLayout (its parent) which IS properly sized.
        var root = bridgeSession.rootViews[0].viewObject as android.view.ViewGroup
        while (root.width == 0 && root.height == 0 && root.parent is android.view.ViewGroup) {
            root = root.parent as android.view.ViewGroup
        }
        rootViewGroup = root

        renderSession = session
        bridgeRenderSession = bridgeSession
        renderDensityDpi = req.densityDpi
        currentSessionConfig = SessionConfig(req.densityDpi, req.widthPx, req.heightPx, req.uiMode, req.fontScale)

        Logger.i { "Session ready (density=${req.densityDpi}dpi, uiMode=${req.uiMode}, ${req.widthPx}x${req.heightPx}px)" }
    }

    private fun teardownSession() {
        // Release all cached sessions
        for (cached in sessionCache.values) {
            try { cached.session.release(); cached.bridgeSession.dispose() } catch (_: Exception) {}
        }
        sessionCache.clear()
        // Release active session
        try {
            renderSession?.release()
            bridgeRenderSession?.dispose()
        } catch (e: Exception) {
            Logger.d { "Session teardown: ${e::class.simpleName}: ${e.message}" }
        }
        renderSession = null
        bridgeRenderSession = null
    }

    private fun ensureSessionConfig(req: RenderRequest, previewFqn: String = "") {
        val needed = SessionConfig(req.densityDpi, req.widthPx, req.heightPx, req.uiMode, req.fontScale, previewFqn)
        if (needed == currentSessionConfig) return

        // Save current session to cache before switching
        val currentConfig = currentSessionConfig
        val currentSession = renderSession
        val currentBridge = bridgeRenderSession
        val currentRoot = rootViewGroup
        if (currentConfig != null && currentSession != null && currentBridge != null && currentRoot != null) {
            sessionCache[currentConfig] = CachedSession(currentConfig, currentSession, currentBridge, currentRoot)
            // Evict oldest if over limit
            while (sessionCache.size > MAX_CACHED_SESSIONS) {
                val oldest = sessionCache.entries.first()
                sessionCache.remove(oldest.key)
                try { oldest.value.session.release(); oldest.value.bridgeSession.dispose() } catch (_: Exception) {}
                Logger.d { "Evicted cached session: ${oldest.key}" }
            }
        }

        // Check cache for existing session with this config
        val cached = sessionCache.remove(needed)
        if (cached != null) {
            Logger.d { "Reusing cached session: $needed" }
            renderSession = cached.session
            bridgeRenderSession = cached.bridgeSession
            rootViewGroup = cached.rootViewGroup
            currentSessionConfig = needed
            return
        }

        Logger.d { "Creating new session: $currentSessionConfig → $needed" }
        renderSession = null
        bridgeRenderSession = null
        rootViewGroup = null
        if (previewFqn.isNotBlank()) {
            createAdapterSession(req, previewFqn, req.previewParameterProviderClass, req.previewParameterProviderIndex)
        } else {
            createSession(req)
        }
    }

    // --- Rendering ---

    private fun render(req: RenderRequest): RenderResponse {
        val start = System.currentTimeMillis()
        return try {
            val results = renderPreview(req)
            results.firstOrNull() ?: errorResp(req.previewFqn, "No image", start)
        } catch (e: Throwable) {
            var root: Throwable = e
            while (root is java.lang.reflect.InvocationTargetException || root is java.lang.reflect.UndeclaredThrowableException) {
                root = root.cause ?: break
            }
            errorResp(req.previewFqn, "${root::class.simpleName}: ${root.message}", start)
        }
    }

    private fun renderPreview(req: RenderRequest): List<RenderResponse> {
        val start = System.currentTimeMillis()
        val fqn = req.previewFqn

        // Try ComposeViewAdapter path first (simpler, handles lifecycle/recomposer/inspection internally)
        if (composeViewAdapterAvailable) {
            try {
                Logger.i { "Attempting ComposeViewAdapter rendering for $fqn" }
                val r = snapshotViaAdapter(req)
                return listOf(buildResp(req, r, start))
            } catch (e: Throwable) {
                var root: Throwable = e
                while (root is java.lang.reflect.InvocationTargetException || root is java.lang.reflect.UndeclaredThrowableException) {
                    root = root.cause ?: break
                }
                if (root is ClassNotFoundException || root is NoClassDefFoundError) {
                    composeViewAdapterAvailable = false
                    Logger.i { "ComposeViewAdapter not available, disabling adapter path: ${root.message}" }
                } else {
                    Logger.i { "ComposeViewAdapter failed for $fqn, falling back to manual path: ${root::class.simpleName}: ${root.message}" }
                }
            }
        }

        // Fallback: manual ComposeView-based rendering
        val resolved = ComposableInvoker.resolve(fqn)
        if (resolved is ComposableInvoker.ResolvedMethod.Error) {
            return listOf(errorResp(fqn, resolved.message, start))
        }

        return when (resolved) {
            is ComposableInvoker.ResolvedMethod.Regular -> {
                val method = resolved.method
                val r = snapshot(req.densityDpi) { c, ch ->
                    method.invoke(null, *ComposableInvoker.buildRegularArgs(c, ch, method))
                }
                listOf(buildResp(req, r, start))
            }
            is ComposableInvoker.ResolvedMethod.WithPreviewParameter -> {
                val method = resolved.method
                val className = fqn.substring(0, fqn.lastIndexOf('.'))
                val methodName = fqn.substring(fqn.lastIndexOf('.') + 1)
                val clazz = Class.forName(className)
                val values = ComposableInvoker.findPreviewParamValues(clazz, methodName)

                if (values.isEmpty()) {
                    val defaultValue = ComposableInvoker.defaultVal(method.parameterTypes[0])
                    val r = snapshot(req.densityDpi) { c, ch ->
                        method.invoke(null, *ComposableInvoker.buildPreviewParamArgs(
                            defaultValue, c, ch, method, resolved.composerIndex,
                        ))
                    }
                    listOf(buildResp(req, r, start))
                } else {
                    values.mapIndexed { i, v ->
                        val s = System.currentTimeMillis()
                        val r = snapshot(req.densityDpi) { c, ch ->
                            method.invoke(null, *ComposableInvoker.buildPreviewParamArgs(
                                v, c, ch, method, resolved.composerIndex,
                            ))
                        }
                        buildResp(req, r, s, i)
                    }
                }
            }
            else -> listOf(errorResp(fqn, "Unexpected resolution", start))
        }
    }

    // --- ComposeViewAdapter-based rendering ---

    /** Whether ComposeViewAdapter is available on the classpath.
     *  Disabled by default — the manual ComposeView path is more reliable.
     *  ComposeViewAdapter depends on compose-ui-tooling being on the classpath
     *  and tools: XML attributes being processed correctly, which is fragile. */
    private var composeViewAdapterAvailable = false

    /**
     * Generate XML layout for ComposeViewAdapter with the given preview FQN and optional parameter provider.
     */
    private fun buildComposeViewAdapterXml(
        previewFqn: String,
        parameterProviderClass: String = "",
        parameterProviderIndex: Int = -1,
    ): String {
        val paramProviderAttr = if (parameterProviderClass.isNotBlank()) {
            "\n    tools:parameterProviderClass=\"$parameterProviderClass\"\n    tools:parameterProviderIndex=\"$parameterProviderIndex\""
        } else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<androidx.compose.ui.tooling.ComposeViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:composableName="$previewFqn"$paramProviderAttr />"""
    }

    /**
     * Create a dedicated session with ComposeViewAdapter XML for a specific preview.
     * Returns the session config used, or null on failure.
     */
    private fun createAdapterSession(req: RenderRequest, previewFqn: String, paramProviderClass: String = "", paramProviderIndex: Int = -1) {
        val density = Density.create(req.densityDpi)
        val nightMode = if (req.uiMode and 0x30 == 0x20) NightMode.NIGHT else NightMode.NOTNIGHT

        val hardwareConfig = HardwareConfig(
            req.widthPx, req.heightPx, density,
            req.densityDpi.toFloat(), req.densityDpi.toFloat(),
            ScreenSize.NORMAL, ScreenOrientation.PORTRAIT,
            ScreenRound.NOTROUND, false,
        )

        val layoutXml = buildComposeViewAdapterXml(previewFqn, paramProviderClass, paramProviderIndex)
        Logger.d { "ComposeViewAdapter XML:\n$layoutXml" }
        val layoutParser = SimpleLayoutPullParser(layoutXml)
        val callback = MinimalLayoutlibCallback()

        val sessionParams = SessionParams(
            layoutParser, SessionParams.RenderingMode.NORMAL, null,
            hardwareConfig, resourceResolver, callback, 0, TARGET_API_LEVEL, quietLogger,
        )
        sessionParams.fontScale = req.fontScale
        sessionParams.setFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true)
        sessionParams.setForceNoDecor()
        sessionParams.setAssetRepository(ClasspathAssetRepository())

        val session = RenderSessionImpl(sessionParams)
        session.setElapsedFrameTimeNanos(0L)

        val initResult = session.init(sessionParams.timeout)
        Logger.i { "Adapter session init: ${initResult.status}, error=${initResult.errorMessage}" }

        // Set default bitmap density
        try {
            val bitmapClass = Class.forName("android.graphics.Bitmap")
            val setDensityMethod = bitmapClass.getDeclaredMethod("setDefaultDensity", Int::class.javaPrimitiveType)
            setDensityMethod.invoke(null, req.densityDpi)
        } catch (_: Exception) {}

        val inflateResult = session.inflate()
        Logger.i { "Adapter inflate: ${inflateResult.status}, error=${inflateResult.errorMessage}" }
        if (inflateResult.status != com.android.ide.common.rendering.api.Result.Status.SUCCESS) {
            throw RuntimeException("ComposeViewAdapter inflate failed: ${inflateResult.errorMessage}")
        }

        val bridgeSessionClass = Class.forName("com.android.layoutlib.bridge.BridgeRenderSession")
        val constructor = bridgeSessionClass.getDeclaredConstructor(
            RenderSessionImpl::class.java,
            com.android.ide.common.rendering.api.Result::class.java
        )
        constructor.isAccessible = true
        val bridgeSession = constructor.newInstance(session, inflateResult) as RenderSession

        // Workaround for missing registerForRefreshRateChanges
        try {
            val viewGroup = bridgeSession.rootViews[0].viewObject as android.view.ViewGroup
            val displayField = android.view.Display::class.java.getDeclaredField("mRefreshRateChangesRegistered")
            displayField.isAccessible = true
            displayField.set(viewGroup.display, true)
        } catch (_: Exception) {}

        session.render(true)

        var root = bridgeSession.rootViews[0].viewObject as android.view.ViewGroup
        while (root.width == 0 && root.height == 0 && root.parent is android.view.ViewGroup) {
            root = root.parent as android.view.ViewGroup
        }
        rootViewGroup = root

        renderSession = session
        bridgeRenderSession = bridgeSession
        renderDensityDpi = req.densityDpi
        currentSessionConfig = SessionConfig(req.densityDpi, req.widthPx, req.heightPx, req.uiMode, req.fontScale, previewFqn)

        Logger.i { "Adapter session ready for $previewFqn (density=${req.densityDpi}dpi, ${req.widthPx}x${req.heightPx}px)" }
    }

    /**
     * Render a preview using ComposeViewAdapter via XML inflation.
     * ComposeViewAdapter internally handles lifecycle owners, recomposer, inspection mode, and composable invocation.
     * Returns a SnapshotResult, or throws on failure.
     */
    private fun snapshotViaAdapter(req: RenderRequest): SnapshotResult {
        val previewFqn = req.previewFqn
        val paramProviderClass = req.previewParameterProviderClass
        val paramProviderIndex = req.previewParameterProviderIndex

        // Ensure we have an adapter session for this specific preview.
        // The previewFqn is part of the session cache key, so each preview gets its own session.
        ensureSessionConfig(req, previewFqn)

        val session = renderSession ?: throw IllegalStateException("No render session")
        val bridgeSession = bridgeRenderSession ?: throw IllegalStateException("No bridge session")

        // Replace the Bridge's internal image with a fresh transparent buffer
        try {
            val mImageField = session::class.java.getDeclaredField("mImage")
            mImageField.isAccessible = true
            val oldImage = mImageField.get(session) as? BufferedImage
            if (oldImage != null) {
                val freshImage = BufferedImage(oldImage.width, oldImage.height, BufferedImage.TYPE_INT_ARGB)
                mImageField.set(session, freshImage)
            }
        } catch (_: Exception) {}

        // Reset frame time counter
        session.setElapsedFrameTimeNanos(0L)

        // Reset time
        val systemDelegate = Class.forName("com.android.internal.lang.System_Delegate")
        val setNanosTime = systemDelegate.getDeclaredMethod("setNanosTime", Long::class.javaPrimitiveType)
        val setBootTime = systemDelegate.getDeclaredMethod("setBootTimeNanos", Long::class.javaPrimitiveType)
        setNanosTime.invoke(null, 0L)
        setBootTime.invoke(null, 0L)

        val choreographerDelegate = Class.forName("android.view.Choreographer_Delegate")
        val sChoreographerTimeField = choreographerDelegate.getDeclaredField("sChoreographerTime")
        sChoreographerTimeField.isAccessible = true
        val doFrameMethod = choreographerDelegate.getDeclaredMethod("doFrame", Long::class.javaPrimitiveType)

        val handlerDelegate = Class.forName("android.os.Handler_Delegate")
        val executeCallbacks = handlerDelegate.getDeclaredMethod("executeCallbacks", Long::class.javaPrimitiveType)

        val nanoTimeMethod = systemDelegate.getDeclaredMethod("nanoTime")
        val bootTimeMethod = systemDelegate.getDeclaredMethod("bootTime")

        // Multi-pass rendering (ComposeViewAdapter still needs choreographer ticking)
        for (pass in 1..3) {
            setNanosTime.invoke(null, 0L)
            sChoreographerTimeField.set(null, 0L)

            try {
                val uptimeNanos = (nanoTimeMethod.invoke(null) as Long) - (bootTimeMethod.invoke(null) as Long)
                executeCallbacks.invoke(null, uptimeNanos)
            } catch (_: Exception) {}

            try {
                val currentTimeNanos = (nanoTimeMethod.invoke(null) as Long) - (bootTimeMethod.invoke(null) as Long)
                doFrameMethod.invoke(null, currentTimeNanos)
            } catch (_: Exception) {}

            val result = session.render(true)
            if (pass == 1) {
                Logger.i { "Adapter render pass 1: status=${result.status}" }
                if (result.status == com.android.ide.common.rendering.api.Result.Status.ERROR_UNKNOWN) {
                    throw result.exception
                }
            }

            try {
                val uptimeNanos = (nanoTimeMethod.invoke(null) as Long) - (bootTimeMethod.invoke(null) as Long)
                executeCallbacks.invoke(null, uptimeNanos)
            } catch (_: Exception) {}
        }

        // Capture image
        val image = bridgeSession.image
        Logger.i { "Adapter captured image: ${image?.width}x${image?.height}" }

        // Extract hierarchy — find ComposeView/AndroidComposeView in the adapter's view tree
        var hierarchy: dev.mikepenz.composebuddy.core.model.HierarchyNode? = null

        // ComposeViewAdapter contains a ComposeView which creates AndroidComposeView
        // Find it for SlotTreeWalker extraction
        val rootVg = rootViewGroup
        if (rootVg != null) {
            val composeView = findViewByClassName(rootVg, "ComposeView")
                ?: findViewByClassName(rootVg, "ComposeViewAdapter")
            if (composeView != null) {
                hierarchy = SlotTreeWalker.extractFromComposeView(composeView, renderDensityDpi)
            }

            // Also try semantics-based extraction
            try {
                val rootViews = bridgeSession.rootViews
                if (rootViews != null && rootViews.isNotEmpty()) {
                    val rootViewGroup = rootViews[0].viewObject as? android.view.ViewGroup
                    if (rootViewGroup != null) {
                        val semanticsHierarchy = HierarchyExtractor.extract(rootViewGroup, renderDensityDpi)
                        if (hierarchy != null && semanticsHierarchy != null) {
                            hierarchy = HierarchyMerger.merge(hierarchy, semanticsHierarchy)
                        } else if (hierarchy == null) {
                            hierarchy = semanticsHierarchy
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.d { "Adapter semantics extraction: ${e::class.simpleName}: ${e.message}" }
            }
        }

        if (hierarchy != null && image != null) {
            hierarchy = ColorExtractor.enrich(hierarchy, image, renderDensityDpi)
        }

        return SnapshotResult(image, hierarchy)
    }

    /**
     * Find a view by simple class name in the view hierarchy (recursive).
     */
    private fun findViewByClassName(viewGroup: android.view.ViewGroup, simpleClassName: String): android.view.View? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child.javaClass.simpleName == simpleClassName) return child
            if (child is android.view.ViewGroup) {
                val found = findViewByClassName(child, simpleClassName)
                if (found != null) return found
            }
        }
        return null
    }

    // --- Snapshot (direct Bridge, no PaparazziSdk) ---

    data class SnapshotResult(val image: BufferedImage?, val hierarchy: dev.mikepenz.composebuddy.core.model.HierarchyNode?)

    private fun snapshot(densityDpi: Int, composable: (Any, Any) -> Unit): SnapshotResult {
        capturedImage = null
        capturedHierarchy = null
        currentComposeView = null

        val session = renderSession ?: throw IllegalStateException("No render session")
        val bridgeSession = bridgeRenderSession ?: throw IllegalStateException("No bridge session")

        // Replace the Bridge's internal image with a fresh transparent buffer.
        // This prevents accumulation from previous renders — render(true) paints
        // the entire view tree onto this buffer from scratch.
        try {
            val mImageField = session::class.java.getDeclaredField("mImage")
            mImageField.isAccessible = true
            val oldImage = mImageField.get(session) as? BufferedImage
            if (oldImage != null) {
                val freshImage = BufferedImage(oldImage.width, oldImage.height, BufferedImage.TYPE_INT_ARGB)
                mImageField.set(session, freshImage)
            }
        } catch (_: Exception) {}

        // Get context from layoutlib's RenderAction
        val renderActionClass = Class.forName("com.android.layoutlib.bridge.impl.RenderAction")
        val getContextMethod = renderActionClass.getDeclaredMethod("getCurrentContext")
        val context = getContextMethod.invoke(null) as android.content.Context

        val composeViewClass = Class.forName("androidx.compose.ui.platform.ComposeView")
        val composeView = composeViewClass
            .getConstructor(android.content.Context::class.java)
            .newInstance(context) as android.view.View
        composeView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        // Build @Composable content lambda with inspection wrapping
        val fn2 = Class.forName("kotlin.jvm.functions.Function2")
        val lambda = java.lang.reflect.Proxy.newProxyInstance(fn2.classLoader, arrayOf(fn2)) { _, m, args ->
            if (m.name == "invoke" && args != null && args.size >= 2) {
                wrapWithInspection(args[0], args[1]) { composable(args[0], args[1]) }
            }
            kotlin.Unit
        }
        composeViewClass.getDeclaredMethod("setContent", fn2).invoke(composeView, lambda)

        currentComposeView = composeView

        // Use the rootViews[0] FrameLayout as container. Even though it's 0x0,
        // render(true) draws the entire DecorView tree onto the image.
        // We use rootViewGroup (the sized system parent) for adding our content.
        val parentViewGroup = rootViewGroup ?: throw IllegalStateException("No root view group")

        // Remove all children from the parent to get a clean canvas
        parentViewGroup.removeAllViews()

        // Use a simple wrapper that's our only content
        val viewGroup = android.widget.FrameLayout(context)
        viewGroup.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        parentViewGroup.addView(viewGroup)

        // Reset frame time counter for this snapshot
        session.setElapsedFrameTimeNanos(0L)

        // Reset time
        val systemDelegate = Class.forName("com.android.internal.lang.System_Delegate")
        val setNanosTime = systemDelegate.getDeclaredMethod("setNanosTime", Long::class.javaPrimitiveType)
        val setBootTime = systemDelegate.getDeclaredMethod("setBootTimeNanos", Long::class.javaPrimitiveType)
        setNanosTime.invoke(null, 0L)
        setBootTime.invoke(null, 0L)

        val choreographerDelegate = Class.forName("android.view.Choreographer_Delegate")
        val sChoreographerTimeField = choreographerDelegate.getDeclaredField("sChoreographerTime")
        sChoreographerTimeField.isAccessible = true
        val doFrameMethod = choreographerDelegate.getDeclaredMethod("doFrame", Long::class.javaPrimitiveType)

        val handlerDelegate = Class.forName("android.os.Handler_Delegate")
        val executeCallbacks = handlerDelegate.getDeclaredMethod("executeCallbacks", Long::class.javaPrimitiveType)

        val nanoTimeMethod = systemDelegate.getDeclaredMethod("nanoTime")
        val bootTimeMethod = systemDelegate.getDeclaredMethod("bootTime")

        // Initialize choreographer at time=0
        initTime(0L, sChoreographerTimeField, doFrameMethod, executeCallbacks, nanoTimeMethod, bootTimeMethod)

        // Set up Compose lifecycle
        var recomposer: Any? = null
        try {
            viewGroup.id = android.R.id.content

            // Create Handler-based dispatcher for deterministic recomposition
            val handler = try {
                android.os.Handler.getMain()
            } catch (_: Exception) {
                val looper = android.os.Looper.getMainLooper()
                    ?: run { android.os.Looper.prepareMainLooper(); android.os.Looper.getMainLooper() }
                android.os.Handler(looper)
            }
            val dispatcherKt = Class.forName("kotlinx.coroutines.android.HandlerDispatcherKt")
            val mainDispatcher = try {
                dispatcherKt.getDeclaredMethod("from", android.os.Handler::class.java, String::class.java)
                    .invoke(null, handler, "ComposeBuddy-Main")
            } catch (_: NoSuchMethodException) {
                try {
                    dispatcherKt.getDeclaredMethod("from", android.os.Handler::class.java)
                        .invoke(null, handler)
                } catch (_: NoSuchMethodException) {
                    dispatcherKt.getDeclaredMethod("asCoroutineDispatcher", android.os.Handler::class.java, String::class.java)
                        .invoke(null, handler, "ComposeBuddy-Main")
                }
            }
            Logger.i { "Main dispatcher: ${mainDispatcher::class.java.name}" }

            // WindowRecomposerPolicy.setFactory — it's a Kotlin object (singleton)
            val policyClass = Class.forName("androidx.compose.ui.platform.WindowRecomposerPolicy")
            val factoryIface = Class.forName("androidx.compose.ui.platform.WindowRecomposerFactory")
            val setFactory = policyClass.getDeclaredMethod("setFactory", factoryIface)
            val policyInstance = policyClass.getDeclaredField("INSTANCE").get(null)
            val factory = java.lang.reflect.Proxy.newProxyInstance(
                factoryIface.classLoader, arrayOf(factoryIface)
            ) { _, _, factoryArgs ->
                val rootView = factoryArgs!![0]
                // Find createLifecycleAwareWindowRecomposer — signature varies
                val windowRecomposerKt = Class.forName("androidx.compose.ui.platform.WindowRecomposer_androidKt")
                val createMethod = windowRecomposerKt.declaredMethods.firstOrNull {
                    it.name == "createLifecycleAwareWindowRecomposer" ||
                        it.name.startsWith("createLifecycleAwareWindowRecomposer")
                }
                if (createMethod != null) {
                    createMethod.isAccessible = true
                    Logger.i { "createLifecycleAwareWindowRecomposer: ${createMethod.parameterTypes.map { it.name }}" }
                    // Get lifecycle from the view tree (set by ViewOwnerHelper)
                    val lifecycle = try {
                        val lcTagId = Class.forName("androidx.lifecycle.runtime.R\$id")
                            .getDeclaredField("view_tree_lifecycle_owner").getInt(null)
                        val owner = (rootView as android.view.View).getTag(lcTagId)
                        if (owner != null) {
                            owner::class.java.getMethod("getLifecycle").invoke(owner)
                        } else null
                    } catch (_: Exception) { null }

                    // Handle various overloads:
                    // (View, CoroutineContext) — 2 params
                    // (View, CoroutineContext, Lifecycle) — 3 params
                    // (View, CoroutineContext, Lifecycle, int, Object) — 5 params ($default variant)
                    val windowRecomposer = when (createMethod.parameterCount) {
                        2 -> createMethod.invoke(null, rootView, mainDispatcher)
                        3 -> createMethod.invoke(null, rootView, mainDispatcher, lifecycle)
                        5 -> {
                            // Kotlin $default: (View, CoroutineContext, Lifecycle?, $default_mask, $marker)
                            // If lifecycle is null, set bit 2 (mask=4) to use default
                            val defaultMask = if (lifecycle == null) 4 else 0
                            createMethod.invoke(null, rootView, mainDispatcher, lifecycle, defaultMask, null)
                        }
                        else -> {
                            val args = arrayOfNulls<Any>(createMethod.parameterCount)
                            args[0] = rootView
                            args[1] = mainDispatcher
                            if (createMethod.parameterCount > 2) args[2] = lifecycle
                            // Fill remaining with 0/null for $default params
                            for (i in 3 until createMethod.parameterCount) {
                                if (createMethod.parameterTypes[i] == Int::class.javaPrimitiveType) args[i] = 0
                            }
                            createMethod.invoke(null, *args)
                        }
                    }
                    recomposer = windowRecomposer
                    Logger.i { "Recomposer created: ${windowRecomposer?.javaClass?.name}" }
                    windowRecomposer
                } else {
                    Logger.e { "createLifecycleAwareWindowRecomposer not found" }
                    null
                }
            }
            setFactory.invoke(policyInstance, factory)
            Logger.i { "WindowRecomposerPolicy factory set" }
        } catch (e: Exception) {
            Logger.e { "Recomposer setup failed: ${e::class.simpleName}: ${e.message}" }
            e.printStackTrace(System.err)
        }

        // Perform initial render
        session.render(false)
        // Check the session's own view infos
        val viewInfos = session.viewInfos
        Logger.i { "Session viewInfos: ${viewInfos?.size}, systemViewInfos: ${session.systemViewInfos?.size}" }
        viewInfos?.forEachIndexed { i, vi ->
            val v = vi.viewObject
            Logger.i { "  viewInfo[$i]: ${v?.javaClass?.simpleName} ${(v as? android.view.View)?.width}x${(v as? android.view.View)?.height} bounds=${vi.left},${vi.top}-${vi.right},${vi.bottom}" }
        }
        Logger.i { "Root ViewGroup after render: ${viewGroup.width}x${viewGroup.height}" }
        // Walk up the parent chain
        var parent: android.view.View? = viewGroup
        var depth = 0
        while (parent != null && depth < 5) {
            Logger.i { "  parent[$depth]: ${parent!!.javaClass.simpleName} ${parent!!.width}x${parent!!.height}" }
            parent = parent!!.parent as? android.view.View
            depth++
        }

        // Set up LifecycleOwner + SavedStateRegistryOwner using ByteBuddy-generated classes
        ViewOwnerHelper.setupViewTreeOwners(composeView, viewGroup)

        // Add view to root for rendering
        viewGroup.addView(composeView)

        try {
            // Multi-pass rendering with Handler dispatch between passes.
            // Pass 1: addView triggers onAttachedToWindow → Recomposer setup
            // Handler dispatch: processes pending Compose coroutine work
            // Pass 2: render with composed content
            // Handler dispatch: any deferred recomposition
            // Pass 3: final render to capture all drawn content
            for (pass in 1..3) {
                // Set time context
                val setNanosTime = Class.forName("com.android.internal.lang.System_Delegate")
                    .getDeclaredMethod("setNanosTime", Long::class.javaPrimitiveType)
                setNanosTime.invoke(null, 0L)
                sChoreographerTimeField.set(null, 0L)

                // Dispatch pending Handler callbacks (Compose work runs here)
                try {
                    val uptimeNanos = (nanoTimeMethod.invoke(null) as Long) - (bootTimeMethod.invoke(null) as Long)
                    executeCallbacks.invoke(null, uptimeNanos)
                } catch (_: Exception) {}

                // Tick Choreographer
                try {
                    val currentTimeNanos = (nanoTimeMethod.invoke(null) as Long) - (bootTimeMethod.invoke(null) as Long)
                    doFrameMethod.invoke(null, currentTimeNanos)
                } catch (_: Exception) {}

                // Render
                val result = session.render(true)
                if (pass == 1) {
                    Logger.i { "Render pass 1: status=${result.status}" }
                    if (result.status == com.android.ide.common.rendering.api.Result.Status.ERROR_UNKNOWN) {
                        throw result.exception
                    }
                }

                // After render, dispatch Handler callbacks again (composition may post work)
                try {
                    val uptimeNanos = (nanoTimeMethod.invoke(null) as Long) - (bootTimeMethod.invoke(null) as Long)
                    executeCallbacks.invoke(null, uptimeNanos)
                } catch (_: Exception) {}
            }

            // Check ComposeView dimensions after rendering
            Logger.i { "ComposeView after render: ${composeView.width}x${composeView.height}, visibility=${composeView.visibility}" }
            Logger.i { "ViewGroup children: ${viewGroup.childCount}, viewGroup size: ${viewGroup.width}x${viewGroup.height}" }
            // Check all root views
            val allRoots = bridgeSession.rootViews
            Logger.i { "Root views count: ${allRoots?.size}" }
            allRoots?.forEachIndexed { i, vi ->
                val v = vi.viewObject
                Logger.i { "  root[$i]: ${v?.javaClass?.simpleName} ${(v as? android.view.View)?.width}x${(v as? android.view.View)?.height}" }
            }

            // Capture image
            val image = bridgeSession.image
            if (image != null) {
                // Check if the image has any non-transparent pixels
                var nonTransparent = 0
                for (y in 0 until minOf(100, image.height)) {
                    for (x in 0 until minOf(100, image.width)) {
                        val argb = image.getRGB(x, y)
                        val alpha = (argb shr 24) and 0xFF
                        if (alpha > 0) nonTransparent++
                    }
                }
                val samplePixel = if (image.width > 50 && image.height > 50) "%08X".format(image.getRGB(50, 50)) else "N/A"
                Logger.i { "Captured image: ${image.width}x${image.height}, type=${image.type}, nonTransparent=$nonTransparent/10000, sample@50,50=0x$samplePixel" }
            } else {
                Logger.i { "No image captured" }
            }
            capturedImage = image

            // Extract hierarchy
            var hierarchy: dev.mikepenz.composebuddy.core.model.HierarchyNode? = null
            if (currentComposeView != null) {
                hierarchy = SlotTreeWalker.extractFromComposeView(currentComposeView!!, renderDensityDpi)
            }
            try {
                val rootViews = bridgeSession.rootViews
                if (rootViews != null && rootViews.isNotEmpty()) {
                    val rootVg = rootViews[0].viewObject as? android.view.ViewGroup
                    if (rootVg != null) {
                        val semanticsHierarchy = HierarchyExtractor.extract(rootVg, renderDensityDpi)
                        if (hierarchy != null && semanticsHierarchy != null) {
                            hierarchy = HierarchyMerger.merge(hierarchy, semanticsHierarchy)
                        } else if (hierarchy == null) {
                            hierarchy = semanticsHierarchy
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.d { "Semantics extraction: ${e::class.simpleName}: ${e.message}" }
            }

            if (hierarchy != null && image != null) {
                hierarchy = ColorExtractor.enrich(hierarchy, image, renderDensityDpi)
            }
            capturedHierarchy = hierarchy

        } finally {
            viewGroup.removeAllViews()
            parentViewGroup.removeAllViews()

            // Clean up animation handler
            try {
                val animHandlerClass = Class.forName("android.animation.AnimationHandler")
                val sAnimatorHandler = animHandlerClass.getDeclaredField("sAnimatorHandler")
                sAnimatorHandler.isAccessible = true
                (sAnimatorHandler.get(null) as? ThreadLocal<*>)?.set(null)
            } catch (_: Exception) {}

            // Force release Compose reference leaks
            try {
                val handler = android.os.Handler.getMain()
                handler::class.java.getDeclaredMethod("hasCallbacks").let { /* just check availability */ }
            } catch (_: Exception) {}

            // Reset choreographer
            try {
                val choreographer = android.view.Choreographer.getInstance()
                val lastFrameField = choreographer::class.java.getDeclaredField("mLastFrameTimeNanos")
                lastFrameField.isAccessible = true
                lastFrameField.set(choreographer, 0L)
            } catch (_: Exception) {}
        }

        currentComposeView = null
        return SnapshotResult(capturedImage, capturedHierarchy)
    }

    /** Initialize choreographer at a given time without rendering. */
    private fun initTime(
        timeNanos: Long,
        sChoreographerTimeField: java.lang.reflect.Field,
        doFrameMethod: java.lang.reflect.Method,
        executeCallbacks: java.lang.reflect.Method,
        nanoTimeMethod: java.lang.reflect.Method,
        bootTimeMethod: java.lang.reflect.Method,
    ) {
        val systemDelegate = Class.forName("com.android.internal.lang.System_Delegate")
        val setNanosTime = systemDelegate.getDeclaredMethod("setNanosTime", Long::class.javaPrimitiveType)
        setNanosTime.invoke(null, 0L)
        sChoreographerTimeField.set(null, timeNanos)
        try {
            val uptimeNanos = (nanoTimeMethod.invoke(null) as Long) - (bootTimeMethod.invoke(null) as Long)
            executeCallbacks.invoke(null, uptimeNanos)
            val currentTimeNanos = (nanoTimeMethod.invoke(null) as Long) - (bootTimeMethod.invoke(null) as Long)
            doFrameMethod.invoke(null, currentTimeNanos)
        } catch (_: Throwable) {}
    }

    // --- ByteBuddy isInEditMode interception ---

    private fun installEditModeInterceptor() {
        try {
            net.bytebuddy.agent.ByteBuddyAgent.install()
            net.bytebuddy.ByteBuddy()
                .redefine(android.view.View::class.java)
                .method(net.bytebuddy.matcher.ElementMatchers.named("isInEditMode"))
                .intercept(net.bytebuddy.implementation.FixedValue.value(false))
                .make()
                .load(android.view.View::class.java.classLoader, net.bytebuddy.dynamic.loading.ClassReloadingStrategy.fromInstalledAgent())
            Logger.i { "ByteBuddy: View.isInEditMode() intercepted → false" }
        } catch (e: Exception) {
            Logger.d { "ByteBuddy interception unavailable: ${e::class.simpleName}: ${e.message}" }
        }
    }

    // --- Inspection wrapping ---

    private fun wrapWithInspection(composer: Any, changed: Any, content: () -> Unit) {
        try {
            val compositionLocalClass = Class.forName("androidx.compose.runtime.ProvidableCompositionLocal")
            val providesMethod = compositionLocalClass.getMethod("provides", Any::class.java)
            val providedValueClass = Class.forName("androidx.compose.runtime.ProvidedValue")

            // Collect all composition locals to provide
            val providedValues = mutableListOf<Any>()

            // LocalInspectionMode = true
            try {
                val inspectionModeClass = Class.forName("androidx.compose.ui.platform.InspectionModeKt")
                val getLocal = inspectionModeClass.getDeclaredMethod("getLocalInspectionMode")
                getLocal.isAccessible = true
                val localInspectionMode = getLocal.invoke(null)
                providedValues.add(providesMethod.invoke(localInspectionMode, true))
            } catch (e: Exception) {
                Logger.d { "LocalInspectionMode unavailable: ${e::class.simpleName}" }
            }

            // LocalFontFamilyResolver — enables proper font rendering in preview context
            try {
                val context = Class.forName("com.android.layoutlib.bridge.impl.RenderAction")
                    .getDeclaredMethod("getCurrentContext").invoke(null) as android.content.Context
                val fontResolverKt = Class.forName("androidx.compose.ui.text.font.FontFamilyResolverKt")
                val createResolver = fontResolverKt.declaredMethods.firstOrNull {
                    it.name == "createFontFamilyResolver" && it.parameterCount == 1 &&
                        it.parameterTypes[0] == android.content.Context::class.java
                }
                if (createResolver != null) {
                    createResolver.isAccessible = true
                    val resolver = createResolver.invoke(null, context)
                    val fontLocalsKt = Class.forName("androidx.compose.ui.text.font.FontFamilyResolverKt")
                    val getLocalFontFamilyResolver = fontLocalsKt.declaredMethods.firstOrNull {
                        it.name == "getLocalFontFamilyResolver"
                    }
                    if (getLocalFontFamilyResolver != null) {
                        getLocalFontFamilyResolver.isAccessible = true
                        val localFontResolver = getLocalFontFamilyResolver.invoke(null)
                        providedValues.add(providesMethod.invoke(localFontResolver, resolver))
                        Logger.d { "LocalFontFamilyResolver configured" }
                    }
                }
            } catch (e: Exception) {
                Logger.d { "Font resolver setup skipped: ${e::class.simpleName}: ${e.message}" }
            }

            if (providedValues.isEmpty()) {
                content()
                return
            }

            // Use the array overload: CompositionLocalProvider(Array<ProvidedValue>, content, composer, changed)
            val providerClass = Class.forName("androidx.compose.runtime.CompositionLocalKt")
            val contentFn2 = Class.forName("kotlin.jvm.functions.Function2")
            val contentProxy = java.lang.reflect.Proxy.newProxyInstance(contentFn2.classLoader, arrayOf(contentFn2)) { _, m, _ ->
                if (m.name == "invoke") content()
                kotlin.Unit
            }

            if (providedValues.size == 1) {
                // Single-value overload: CompositionLocalProvider(ProvidedValue, content, Composer, Int)
                val providerMethod = providerClass.declaredMethods.firstOrNull { m ->
                    m.name.startsWith("CompositionLocalProvider") && m.parameterCount >= 3 &&
                        m.parameterTypes[0].isAssignableFrom(providedValueClass)
                }
                if (providerMethod != null) {
                    providerMethod.isAccessible = true
                    providerMethod.invoke(null, providedValues[0], contentProxy, composer, changed)
                    return
                }
            } else {
                // Array overload: CompositionLocalProvider(ProvidedValue[], content, Composer, Int)
                val arrayType = java.lang.reflect.Array.newInstance(providedValueClass, 0)::class.java
                val providerMethod = providerClass.declaredMethods.firstOrNull { m ->
                    m.name.startsWith("CompositionLocalProvider") && m.parameterCount >= 3 &&
                        m.parameterTypes[0] == arrayType
                }
                if (providerMethod != null) {
                    providerMethod.isAccessible = true
                    val arr = java.lang.reflect.Array.newInstance(providedValueClass, providedValues.size)
                    for (i in providedValues.indices) java.lang.reflect.Array.set(arr, i, providedValues[i])
                    providerMethod.invoke(null, arr, contentProxy, composer, changed)
                    return
                }
            }
        } catch (e: Exception) {
            Logger.d { "Inspection wrapping unavailable: ${e::class.simpleName}: ${e.message}" }
        }
        content()
    }

    // --- Helpers ---

    private fun buildResp(req: RenderRequest, result: SnapshotResult, start: Long, paramIdx: Int? = null): RenderResponse {
        val img = result.image ?: return errorResp(req.previewFqn, "No image captured", start)
        val dir = File(req.outputDir).apply { mkdirs() }
        val nameSuffix = if (req.previewName.isNotBlank()) "_${req.previewName.replace(' ', '_')}" else ""
        val paramSuffix = if (paramIdx != null) "_$paramIdx" else ""
        val file = File(dir, req.previewFqn.replace('.', '_') + "$nameSuffix$paramSuffix.png")
        ImageIO.write(img, "png", file)
        val hierarchy = if (result.hierarchy != null) {
            result.hierarchy.copy(
                sourceFile = req.sourceFile.ifBlank { null },
                sourceLine = if (req.sourceLine > 0) req.sourceLine else null,
            )
        } else result.hierarchy

        return RenderResponse(req.previewFqn, true, file.absolutePath, img.width, img.height,
            System.currentTimeMillis() - start, renderDensityDpi, hierarchy = hierarchy)
    }

    private fun errorResp(fqn: String, err: String, start: Long) =
        RenderResponse(fqn, false, error = err, durationMs = System.currentTimeMillis() - start, densityDpi = renderDensityDpi)

    private fun detectNativeLibSubdir(): String {
        val os = System.getProperty("os.name").lowercase()
        val label = when {
            os.startsWith("mac") -> if (System.getProperty("os.arch").lowercase().startsWith("x86")) "mac" else "mac-arm"
            os.startsWith("windows") -> "win"; else -> "linux"
        }
        return "$label/lib64"
    }

    private fun loadProps(file: File): Map<String, String> {
        val p = Properties(); FileInputStream(file).use { p.load(it) }
        return p.stringPropertyNames().associateWith { p.getProperty(it) }
    }

    private fun parseEnumMap(file: File): Map<String, Map<String, Int>> {
        val map = mutableMapOf<String, MutableMap<String, Int>>()
        try {
            val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(FileInputStream(file), null)
            var attr: String? = null; var ev = parser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        "attr" -> attr = parser.getAttributeValue(null, "name")
                        "enum", "flag" -> if (attr != null) {
                            map.getOrPut(attr) { mutableMapOf() }[parser.getAttributeValue(null, "name")] =
                                java.lang.Long.decode(parser.getAttributeValue(null, "value")).toInt()
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "attr") attr = null
                }
                ev = parser.next()
            }
        } catch (e: Exception) {
            Logger.d { "Enum map parsing: ${e::class.simpleName}: ${e.message}" }
        }
        return map
    }

    private val quietLogger = object : ILayoutLog {
        override fun warning(tag: String?, message: String?, viewCookie: Any?, data: Any?) {}
        override fun fidelityWarning(tag: String?, message: String?, throwable: Throwable?, viewCookie: Any?, data: Any?) {}
        override fun error(tag: String?, message: String?, viewCookie: Any?, data: Any?) {
            Logger.e { "layoutlib [$tag]: $message" }
        }
        override fun error(tag: String?, message: String?, throwable: Throwable?, viewCookie: Any?, data: Any?) {
            Logger.e { "layoutlib [$tag]: $message" }
        }
        override fun logAndroidFramework(priority: Int, tag: String?, message: String?) {}
    }
}

/**
 * Asset repository that resolves assets from the classpath.
 * Layoutlib calls openAsset/openNonAsset for fonts, drawables, etc.
 * Returning null causes NPE inside layoutlib — return empty stream as fallback.
 */
private class ClasspathAssetRepository : AssetRepository() {
    override fun openAsset(path: String, mode: Int): java.io.InputStream? {
        return resolveFromClasspath(path)
    }

    override fun openNonAsset(cookie: Int, path: String, mode: Int): java.io.InputStream? {
        return resolveFromClasspath(path)
    }

    private fun resolveFromClasspath(path: String): java.io.InputStream? {
        // Try loading from project classpath (fonts, drawables, etc.)
        val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        return classLoader.getResourceAsStream(path)
            ?: classLoader.getResourceAsStream("assets/$path")
            ?: java.io.ByteArrayInputStream(ByteArray(0)) // empty fallback to prevent NPE
    }

    override fun isSupported() = true
}
