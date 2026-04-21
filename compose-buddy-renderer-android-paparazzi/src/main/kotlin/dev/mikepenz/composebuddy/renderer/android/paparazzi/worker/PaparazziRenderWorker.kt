package dev.mikepenz.composebuddy.renderer.android.paparazzi.worker

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Environment
import app.cash.paparazzi.PaparazziSdk
import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import dev.mikepenz.composebuddy.renderer.logging.FileLogWriter
import com.android.ide.common.rendering.api.ILayoutLog
import com.android.ide.common.rendering.api.SessionParams
import com.android.layoutlib.bridge.Bridge
import com.android.resources.Density
import dev.mikepenz.composebuddy.renderer.android.worker.ColorExtractor
import dev.mikepenz.composebuddy.renderer.android.worker.HierarchyExtractor
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
 * Uses Bridge.init() directly (proven working for native lib/ICU loading),
 * then PaparazziSdk for ComposeView lifecycle management (Recomposer, etc.).
 *
 * The density for dp↔px conversion comes from the render request,
 * which is derived from @Preview annotation parameters.
 */
object PaparazziRenderWorker {

    // Use shared protocol types — eliminates deserialization mismatches
    private typealias RenderRequest = WorkerProtocol.RenderRequest
    private typealias RenderResponse = WorkerProtocol.RenderResponse

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private var sdk: PaparazziSdk? = null
    private var capturedImage: BufferedImage? = null
    private var capturedHierarchy: dev.mikepenz.composebuddy.core.model.HierarchyNode? = null
    private var currentComposeView: Any? = null
    private var renderDensityDpi: Int = 420

    /** Current session config — when these change, SDK must be re-prepared */
    private data class SessionConfig(val densityDpi: Int, val widthPx: Int, val heightPx: Int, val uiMode: Int, val fontScale: Float)
    private var currentSessionConfig: SessionConfig? = null
    private var initRequest: RenderRequest? = null

    private var fileLogWriter: FileLogWriter? = null

    private fun initLogging(logFile: String) {
        val stderrWriter = object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                System.err.println("${severity.name.take(1)}: [$tag] $message")
                throwable?.printStackTrace(System.err)
            }
        }
        if (logFile.isNotBlank()) {
            val fw = FileLogWriter.forFile(logFile)
            fileLogWriter = fw
            Logger.setLogWriters(stderrWriter, fw)
        } else {
            Logger.setLogWriters(stderrWriter)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        initLogging("")
        Logger.setTag("worker")

        val reader = BufferedReader(InputStreamReader(System.`in`))
        val firstLine = reader.readLine() ?: return
        val initReq = json.decodeFromString<RenderRequest>(firstLine)
        initLogging("")

        try {
            initBridgeThenSdk(initReq)
            println("READY")
            System.out.flush()
        } catch (e: Throwable) {
            Logger.e(e) { "Init failed: ${e::class.simpleName}: ${e.message}" }
            val errorDetail = buildString {
                append("Init: ${e::class.simpleName}: ${e.message}")
                // Include root cause if different
                var root = e.cause
                while (root != null) {
                    append(" <- ${root::class.simpleName}: ${root.message}")
                    root = root.cause
                }
            }
            println(json.encodeToString(RenderResponse(previewFqn = "", success = false, error = errorDetail)))
            System.out.flush()
            return
        }

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val req = json.decodeFromString<RenderRequest>(line)
            renderDensityDpi = req.densityDpi
            // Re-prepare SDK if rendering config changed (uiMode, density, etc.)
            ensureSessionConfig(req)
            val resp = render(req)
            println(json.encodeToString(resp))
            System.out.flush()
        }

        sdk?.teardown()
    }

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

    // --- Snapshot ---

    data class SnapshotResult(val image: BufferedImage?, val hierarchy: dev.mikepenz.composebuddy.core.model.HierarchyNode?)

    private fun snapshot(densityDpi: Int, composable: (Any, Any) -> Unit): SnapshotResult {
        capturedImage = null
        capturedHierarchy = null
        currentComposeView = null

        val composeViewClass = Class.forName("androidx.compose.ui.platform.ComposeView")
        val composeView = composeViewClass
            .getConstructor(android.content.Context::class.java)
            .newInstance(sdk!!.context) as android.view.View
        composeView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        // Build the content lambda that wraps the composable with inspection support:
        // 1. LocalInspectionMode provides true — lets composables detect preview context
        // 2. Inspectable wrapping — calls collectParameterInformation() for slot table params
        val fn2 = Class.forName("kotlin.jvm.functions.Function2")
        val lambda = java.lang.reflect.Proxy.newProxyInstance(fn2.classLoader, arrayOf(fn2)) { _, m, args ->
            if (m.name == "invoke" && args != null && args.size >= 2) {
                val composer = args[0]
                val changed = args[1]
                wrapWithInspection(composer, changed) {
                    composable(composer, changed)
                }
            }
            kotlin.Unit
        }
        composeViewClass.getDeclaredMethod("setContent", fn2).invoke(composeView, lambda)

        currentComposeView = composeView
        sdk!!.snapshot(composeView)
        currentComposeView = null

        return SnapshotResult(capturedImage, capturedHierarchy)
    }

    // --- Init ---

    private fun initBridgeThenSdk(req: RenderRequest) {
        initRequest = req
        val runtimeRoot = File(req.layoutlibRuntimeRoot)
        val resourcesRoot = File(req.layoutlibResourcesRoot)

        // Step 0: Install ByteBuddy interception so View.isInEditMode() returns true
        installEditModeInterceptor()
        dev.mikepenz.composebuddy.renderer.android.bridge.AndroidxRStubGenerator.ensureStubs()

        // Step 1: Pre-init Bridge ONCE (fixes ICU data mapping issue)
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
        Logger.i { "Bridge pre-initialized" }

        // Step 2: Set system properties ONCE
        System.setProperty("paparazzi.layoutlib.runtime.root", runtimeRoot.absolutePath)
        System.setProperty("paparazzi.layoutlib.resources.root", resourcesRoot.absolutePath)
        System.setProperty("paparazzi.project.dir", System.getProperty("user.dir"))
        System.setProperty("paparazzi.build.dir", File(System.getProperty("user.dir"), "build").absolutePath)
        System.setProperty("paparazzi.artifacts.cache.dir", File(System.getProperty("user.home"), ".gradle").absolutePath)
        val resJson = File.createTempFile("compose-buddy-res", ".json").apply {
            deleteOnExit()
            writeText("""{"mainPackage":"","targetSdkVersion":"35","resourcePackageNames":[],"projectResourceDirs":[],"moduleResourceDirs":[],"aarExplodedDirs":[],"projectAssetDirs":[],"aarAssetDirs":[]}""")
        }
        System.setProperty("paparazzi.test.resources", resJson.absolutePath)

        // Step 3: Create initial SDK session
        createSdkSession(req)
    }

    /** Check if config changed and re-prepare SDK if needed. */
    private fun ensureSessionConfig(req: RenderRequest) {
        val needed = SessionConfig(req.densityDpi, req.widthPx, req.heightPx, req.uiMode, req.fontScale)
        if (needed == currentSessionConfig) return

        Logger.d { "Session config changed: $currentSessionConfig → $needed" }
        sdk?.teardown()
        sdk = null
        createSdkSession(req)
    }

    /** Create (or recreate) PaparazziSdk with the given config. Bridge must already be initialized. */
    private fun createSdkSession(req: RenderRequest) {
        // Reset PaparazziSdk's companion isInitialized flag so prepare() runs fresh
        try {
            val companionClass = Class.forName("app.cash.paparazzi.PaparazziSdk\$Companion")
            val sdkClass = PaparazziSdk::class.java
            // Reset static renderer and sessionParamsBuilder
            sdkClass.getDeclaredField("renderer").apply { isAccessible = true; set(null, null) }
            sdkClass.getDeclaredField("sessionParamsBuilder").apply { isAccessible = true; set(null, null) }
        } catch (e: Exception) {
            Logger.d { "SDK companion reset: ${e.message}" }
        }
        val density = Density.create(req.densityDpi)
        val nightMode = if (req.uiMode and 0x30 == 0x20) com.android.resources.NightMode.NIGHT else com.android.resources.NightMode.NOTNIGHT
        val deviceConfig = DeviceConfig(
            screenWidth = req.widthPx,
            screenHeight = req.heightPx,
            density = density,
            xdpi = req.densityDpi,
            ydpi = req.densityDpi,
            nightMode = nightMode,
        )
        renderDensityDpi = req.densityDpi
        currentSessionConfig = SessionConfig(req.densityDpi, req.widthPx, req.heightPx, req.uiMode, req.fontScale)

        val env = Environment(
            System.getProperty("user.dir"), "compose.buddy", 35,
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        )
        sdk = PaparazziSdk(
            environment = env,
            deviceConfig = deviceConfig,
            onNewFrame = { image ->
                capturedImage = image
                var hierarchy: dev.mikepenz.composebuddy.core.model.HierarchyNode? = null

                // Strategy 1: Slot tree (composable names + structure)
                val cv = currentComposeView
                if (cv != null) {
                    hierarchy = SlotTreeWalker.extractFromComposeView(cv, renderDensityDpi)
                }

                // Strategy 2: Semantics tree (text, roles, click handlers)
                // Always try — merge into slot tree if available, or use standalone
                try {
                    val sessionField = sdk!!::class.java.getDeclaredField("bridgeRenderSession")
                    sessionField.isAccessible = true
                    val session = sessionField.get(sdk!!) as? com.android.ide.common.rendering.api.RenderSession
                    val rootViews = session?.rootViews
                    if (rootViews != null && rootViews.isNotEmpty()) {
                        val rootVg = rootViews[0].viewObject as? android.view.ViewGroup
                        if (rootVg != null) {
                            val semanticsHierarchy = HierarchyExtractor.extract(rootVg, renderDensityDpi)
                            if (hierarchy != null && semanticsHierarchy != null) {
                                // Merge semantics into slot tree by matching bounds
                                hierarchy = mergeSemantics(hierarchy, semanticsHierarchy)
                            } else if (hierarchy == null) {
                                hierarchy = semanticsHierarchy
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.d { "Semantics extraction: ${e::class.simpleName}: ${e.message}" }
                }

                // Enrich with pixel-sampled colors
                if (hierarchy != null) {
                    hierarchy = ColorExtractor.enrich(hierarchy, image, renderDensityDpi)
                }
                capturedHierarchy = hierarchy
            },
        )
        sdk!!.setup()
        sdk!!.prepare()

        Logger.i { "Session ready (density=${req.densityDpi}dpi, uiMode=${req.uiMode}, ${req.widthPx}x${req.heightPx}px)" }
    }

    // --- ByteBuddy isInEditMode interception ---

    /**
     * Uses ByteBuddy to redefine View.isInEditMode() to return true.
     * This ensures custom Views that check isInEditMode() render their preview paths,
     * matching Android Studio's behavior.
     */
    private fun installEditModeInterceptor() {
        try {
            val instrumentation = net.bytebuddy.agent.ByteBuddyAgent.install()

            // Intercept AppCompatDrawableManager.preload() before the class loads.
            // Paparazzi calls initializeAppCompatIfPresent → preload() which crashes when
            // appcompat R$drawable is incomplete (missing resource IDs outside a full Android build).
            instrumentation.addTransformer(object : java.lang.instrument.ClassFileTransformer {
                override fun transform(
                    loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                    protectionDomain: java.security.ProtectionDomain?, classfileBuffer: ByteArray,
                ): ByteArray? {
                    if (className != "androidx/appcompat/widget/AppCompatDrawableManager") return null
                    return try {
                        val reader = net.bytebuddy.jar.asm.ClassReader(classfileBuffer)
                        val writer = net.bytebuddy.jar.asm.ClassWriter(reader, net.bytebuddy.jar.asm.ClassWriter.COMPUTE_MAXS)
                        reader.accept(object : net.bytebuddy.jar.asm.ClassVisitor(net.bytebuddy.jar.asm.Opcodes.ASM9, writer) {
                            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): net.bytebuddy.jar.asm.MethodVisitor? {
                                if (name == "preload") {
                                    // Replace preload() with empty body
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    mv?.visitCode()
                                    mv?.visitInsn(net.bytebuddy.jar.asm.Opcodes.RETURN)
                                    mv?.visitMaxs(0, 0)
                                    mv?.visitEnd()
                                    return null // skip original method body
                                }
                                return super.visitMethod(access, name, descriptor, signature, exceptions)
                            }
                        }, 0)
                        Logger.i { "ASM: AppCompatDrawableManager.preload() stubbed out" }
                        writer.toByteArray()
                    } catch (e: Throwable) {
                        Logger.d { "AppCompat transform failed: ${e::class.simpleName}: ${e.message}" }
                        null
                    }
                }
            })

            net.bytebuddy.ByteBuddy()
                .redefine(android.view.View::class.java)
                .method(net.bytebuddy.matcher.ElementMatchers.named("isInEditMode"))
                .intercept(net.bytebuddy.implementation.FixedValue.value(true))
                .make()
                .load(android.view.View::class.java.classLoader, net.bytebuddy.dynamic.loading.ClassReloadingStrategy.fromInstalledAgent())
            Logger.i { "ByteBuddy: View.isInEditMode() intercepted → true" }
        } catch (e: Exception) {
            // ByteBuddy interception is best-effort — rendering still works without it
            Logger.d { "ByteBuddy interception unavailable: ${e::class.simpleName}: ${e.message}" }
        }
    }

    // --- Inspection wrapping ---

    /**
     * Wraps composable invocation with LocalInspectionMode and Inspectable.
     *
     * This mirrors what Android Studio does internally via ComposeViewAdapter:
     * - Sets LocalInspectionMode to true so composables can detect preview context
     * - Wraps with Inspectable which calls collectParameterInformation() on the Composer,
     *   enabling the slot table to capture composable parameter values
     */
    private fun wrapWithInspection(composer: Any, changed: Any, content: () -> Unit) {
        try {
            // Provide LocalInspectionMode = true
            val inspectionModeClass = Class.forName("androidx.compose.ui.platform.InspectionModeKt")
            val getLocal = inspectionModeClass.getDeclaredMethod("getLocalInspectionMode")
            getLocal.isAccessible = true
            val localInspectionMode = getLocal.invoke(null) // ProvidableCompositionLocal<Boolean>

            val compositionLocalClass = Class.forName("androidx.compose.runtime.ProvidableCompositionLocal")
            val providesMethod = compositionLocalClass.getMethod("provides", Any::class.java)
            val provided = providesMethod.invoke(localInspectionMode, true) // ProvidedValue<Boolean>

            // CompositionLocalProvider(LocalInspectionMode provides true) { content() }
            val providerClass = Class.forName("androidx.compose.runtime.CompositionLocalKt")
            val providerMethods = providerClass.declaredMethods.filter {
                it.name.startsWith("CompositionLocalProvider") && it.parameterCount >= 3
            }

            // Try to find the single-value overload: CompositionLocalProvider(ProvidedValue, content, Composer, Int)
            val providedValueClass = Class.forName("androidx.compose.runtime.ProvidedValue")
            val providerMethod = providerMethods.firstOrNull { m ->
                m.parameterTypes.size >= 3 &&
                    m.parameterTypes[0].isAssignableFrom(providedValueClass)
            }

            if (providerMethod != null) {
                providerMethod.isAccessible = true
                val contentFn2 = Class.forName("kotlin.jvm.functions.Function2")
                val contentProxy = java.lang.reflect.Proxy.newProxyInstance(contentFn2.classLoader, arrayOf(contentFn2)) { _, m, _ ->
                    if (m.name == "invoke") content()
                    kotlin.Unit
                }
                providerMethod.invoke(null, provided, contentProxy, composer, changed)
                return
            }
        } catch (e: Exception) {
            Logger.d { "Inspection wrapping unavailable: ${e::class.simpleName}: ${e.message}" }
        }

        // Fallback: invoke without inspection wrapping
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
        // Set source file on root hierarchy node from preview discovery data
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

    /** Merge semantic properties from semanticsTree into slotTree by matching bounds. */
    private fun mergeSemantics(
        slotNode: dev.mikepenz.composebuddy.core.model.HierarchyNode,
        semanticsNode: dev.mikepenz.composebuddy.core.model.HierarchyNode,
    ): dev.mikepenz.composebuddy.core.model.HierarchyNode {
        // Collect all semantics by bounds key
        val semanticsMap = mutableMapOf<String, Map<String, String>>()
        fun collect(n: dev.mikepenz.composebuddy.core.model.HierarchyNode) {
            val s = n.semantics
            if (s != null && s.isNotEmpty()) {
                val key = "${n.bounds.left},${n.bounds.top},${n.bounds.right},${n.bounds.bottom}"
                semanticsMap[key] = (semanticsMap[key] ?: emptyMap()) + s
            }
            n.children.forEach { collect(it) }
        }
        collect(semanticsNode)

        // Apply to slot tree
        fun enrich(n: dev.mikepenz.composebuddy.core.model.HierarchyNode): dev.mikepenz.composebuddy.core.model.HierarchyNode {
            val key = "${n.bounds.left},${n.bounds.top},${n.bounds.right},${n.bounds.bottom}"
            val extra = semanticsMap[key]
            val merged = if (extra != null) (n.semantics ?: emptyMap()) + extra else n.semantics
            // Update name from semantics if slot tree name is generic
            val name = if (n.name in listOf("Layout", "Element") && extra != null) {
                when {
                    extra["role"]?.contains("Button", true) == true -> "Button"
                    extra.containsKey("text") && n.children.isEmpty() -> "Text"
                    extra.containsKey("onClick") -> "Clickable"
                    else -> n.name
                }
            } else n.name
            return n.copy(name = name, semantics = merged, children = n.children.map { enrich(it) })
        }
        return enrich(slotNode)
    }

    private fun countNodes(n: dev.mikepenz.composebuddy.core.model.HierarchyNode): Int = 1 + n.children.sumOf { countNodes(it) }

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
