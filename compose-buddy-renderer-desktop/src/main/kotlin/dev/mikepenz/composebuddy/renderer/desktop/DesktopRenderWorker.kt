package dev.mikepenz.composebuddy.renderer.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import dev.mikepenz.composebuddy.renderer.logging.FileLogWriter
import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import dev.mikepenz.composebuddy.core.semantics.SemanticsKeyMapper
import dev.mikepenz.composebuddy.core.model.boundsOf
import dev.mikepenz.composebuddy.core.model.pxToDp
import dev.mikepenz.composebuddy.core.model.sizeOf
import dev.mikepenz.composebuddy.renderer.hierarchy.HierarchyMerger
import dev.mikepenz.composebuddy.renderer.hierarchy.SlotTreeWalker
import dev.mikepenz.composebuddy.renderer.worker.ComposableInvoker
import dev.mikepenz.composebuddy.renderer.worker.WorkerProtocol
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import javax.imageio.ImageIO

/**
 * Standalone worker process for rendering Compose Desktop @Preview functions.
 * Uses ImageComposeScene for headless Skia-based rendering — no display needed.
 *
 * Protocol: JSON requests on stdin, JSON responses on stdout, logs on stderr.
 * Same protocol as the Android RenderWorker for seamless integration.
 */
object DesktopRenderWorker {

    // Use shared protocol types — eliminates deserialization mismatches
    private typealias RenderRequest = WorkerProtocol.RenderRequest
    private typealias RenderResponse = WorkerProtocol.RenderResponse

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    @JvmStatic
    fun main(args: Array<String>) {
        initLogging("")
        Logger.setTag("desktop-worker")

        val reader = BufferedReader(InputStreamReader(System.`in`))

        // First line is init — parse to get logFile, then ACK ready
        val initLine = reader.readLine() ?: return
        val initReq = json.decodeFromString<RenderRequest>(initLine)
        initLogging("")
        Logger.i { "Desktop render worker ready" }
        println("READY")
        System.out.flush()

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val req = json.decodeFromString<RenderRequest>(line)
            val resp = render(req)
            println(json.encodeToString(resp))
            System.out.flush()
        }
    }

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

    private fun render(req: RenderRequest): RenderResponse {
        val start = System.currentTimeMillis()
        return try {
            val fqn = req.previewFqn

            val resolved = ComposableInvoker.resolve(fqn)
            if (resolved is ComposableInvoker.ResolvedMethod.Error) {
                return errorResp(fqn, resolved.message, start, req.densityDpi)
            }

            // Render via ImageComposeScene
            val densityValue = req.densityDpi / 160f
            val density = Density(densityValue, req.fontScale)
            val fn2Class = Class.forName("kotlin.jvm.functions.Function2")

            // Set dark theme based on uiMode by overriding Skiko's currentSystemTheme
            val isDarkTheme = (req.uiMode and 0x30) == 0x20
            setSystemTheme(isDarkTheme)

            val innerLambda = when (resolved) {
                is ComposableInvoker.ResolvedMethod.WithPreviewParameter -> {
                    val method = resolved.method
                    val className = fqn.substring(0, fqn.lastIndexOf('.'))
                    val methodName = fqn.substring(fqn.lastIndexOf('.') + 1)
                    val clazz = Class.forName(className)
                    val values = ComposableInvoker.findPreviewParamValues(clazz, methodName)
                    val value = values.firstOrNull() ?: ComposableInvoker.defaultVal(method.parameterTypes[0])

                    java.lang.reflect.Proxy.newProxyInstance(
                        fn2Class.classLoader, arrayOf(fn2Class),
                    ) { _, m, args ->
                        if (m.name == "invoke" && args != null && args.size >= 2) {
                            val invokeArgs = ComposableInvoker.buildPreviewParamArgs(
                                value, args[0], args[1], method, resolved.composerIndex,
                            )
                            method.invoke(null, *invokeArgs)
                        }
                        kotlin.Unit
                    }
                }
                is ComposableInvoker.ResolvedMethod.Regular -> {
                    val method = resolved.method
                    java.lang.reflect.Proxy.newProxyInstance(
                        fn2Class.classLoader, arrayOf(fn2Class),
                    ) { _, m, args ->
                        if (m.name == "invoke" && args != null && args.size >= 2) {
                            val invokeArgs = ComposableInvoker.buildRegularArgs(args[0], args[1], method)
                            method.invoke(null, *invokeArgs)
                        }
                        kotlin.Unit
                    }
                }
                else -> return errorResp(fqn, "Unexpected resolution", start, req.densityDpi)
            }

            // Capture sub-composition CompositionData (e.g. from SubcomposeLayout inside
            // Scaffold / LazyColumn) via LocalInspectionTables. Compose's runtime adds each
            // child composition to this set when it starts composing.
            val inspectionTables: MutableSet<Any> = java.util.Collections.synchronizedSet(java.util.LinkedHashSet())
            val inspectionWrapped = wrapWithInspectionMode(innerLambda, fn2Class, inspectionTables)
            // Tell the composer to collect parameter information, so SlotTreeKt.mapTree
            // exposes names/params in SourceContext. Without this, sourceInformation()
            // calls are no-ops and slot tree groups come through unnamed/paramless.
            val composableLambda = wrapWithCollectParameterInformation(inspectionWrapped, fn2Class)

            val scene = ImageComposeScene(req.widthPx, req.heightPx, density)
            val setContentMethod = scene::class.java.methods.firstOrNull { it.name == "setContent" }
            if (setContentMethod != null) setContentMethod.invoke(scene, composableLambda)
            renderScene(scene, req, fqn, start, densityValue, inspectionTables)
        } catch (e: Throwable) {
            var cause: Throwable = e
            while (cause is java.lang.reflect.InvocationTargetException ||
                cause is java.lang.reflect.UndeclaredThrowableException) {
                cause = cause.cause ?: break
            }
            Logger.e(cause) { "Render error for ${req.previewFqn}: ${cause::class.simpleName}: ${cause.message}" }
            errorResp(req.previewFqn, "${cause::class.simpleName}: ${cause.message}", start, req.densityDpi)
        }
    }

    // --- Scene rendering ---

    private fun renderScene(scene: ImageComposeScene, req: RenderRequest, fqn: String, start: Long, densityValue: Float, inspectionTables: MutableSet<Any>): RenderResponse {
        val skiaImage = scene.render()

        // Slot tree extraction — gives typography/color params not in semantics.
        // The root composition is reached via ImageComposeScene → BaseComposeScene →
        // composition.slotTable. Sub-compositions (SubcomposeLayout inside Scaffold /
        // LazyColumn / BoxWithConstraints) register themselves with LocalInspectionTables.
        val compositionDataList = buildList {
            extractCompositionDataFromScene(scene)?.let { add(it) }
            addAll(inspectionTables)
        }
        val slotHierarchy: HierarchyNode? = try {
            SlotTreeWalker.extractFromCompositionDataList(compositionDataList, req.densityDpi)
        } catch (e: Exception) {
            Logger.d { "Slot tree: ${e::class.simpleName}: ${e.message}" }
            null
        }

        var semanticsHierarchy: HierarchyNode? = null
        try {
            val owners = scene::class.java.getMethod("getSemanticsOwners").invoke(scene)
            @Suppress("UNCHECKED_CAST")
            val ownerCollection = owners as? Collection<*>
            val firstOwner = ownerCollection?.firstOrNull()
            if (firstOwner != null) {
                semanticsHierarchy = extractSemanticsHierarchy(firstOwner, req.densityDpi)
            }
        } catch (e: Exception) {
            Logger.d { "Semantics: ${e::class.simpleName}: ${e.message}" }
        }

        var hierarchy: HierarchyNode? = when {
            slotHierarchy != null && semanticsHierarchy != null -> HierarchyMerger.merge(slotHierarchy, semanticsHierarchy)
            slotHierarchy != null -> slotHierarchy
            else -> semanticsHierarchy
        }

        if (hierarchy != null && req.sourceFile.isNotBlank()) {
            hierarchy = hierarchy.copy(
                sourceFile = req.sourceFile,
                sourceLine = if (req.sourceLine > 0) req.sourceLine else null,
            )
        }

        val pngData = skiaImage.encodeToData() ?: run {
            scene.close()
            return errorResp(fqn, "Failed to encode image", start, req.densityDpi)
        }
        val pngBytes = pngData.bytes
        val bufferedImage = ImageIO.read(ByteArrayInputStream(pngBytes))

        // Enrich hierarchy with colors sampled from the rendered image
        if (hierarchy != null) {
            hierarchy = ColorExtractor.enrich(hierarchy, bufferedImage, req.densityDpi)
        }

        val dir = File(req.outputDir).apply { mkdirs() }
        val nameSuffix = if (req.previewName.isNotBlank()) "_${req.previewName.replace(' ', '_').replace(Regex("[^\\x20-\\x7E]"), "_")}" else ""
        val file = File(dir, req.previewFqn.replace('.', '_') + "$nameSuffix.png")
        ImageIO.write(bufferedImage, "png", file)

        scene.close()

        return RenderResponse(
            previewFqn = fqn, success = true,
            imagePath = file.absolutePath,
            imageWidth = req.widthPx, imageHeight = req.heightPx,
            durationMs = System.currentTimeMillis() - start,
            densityDpi = req.densityDpi,
            hierarchy = hierarchy,
        )
    }

    // --- Source-info capture wrapping ---

    /**
     * Wraps a composable lambda so that, on entry, it invokes
     * `ComposerImpl.collectParameterInformation()` on the active composer. This enables
     * recording of `sourceInformation` in the slot table (names + parameters), which
     * SlotTreeKt.mapTree then exposes via SourceContext.
     */
    private fun wrapWithCollectParameterInformation(composableLambda: Any, fn2Class: Class<*>): Any {
        return try {
            val composerImplClass = try { Class.forName("androidx.compose.runtime.ComposerImpl") } catch (_: Exception) { null }
            val collectMethod = composerImplClass?.methods?.firstOrNull { it.name == "collectParameterInformation" && it.parameterCount == 0 }
            if (collectMethod == null) composableLambda
            else java.lang.reflect.Proxy.newProxyInstance(
                fn2Class.classLoader, arrayOf(fn2Class),
            ) { _, m, args ->
                if (m.name == "invoke" && args != null && args.size >= 2) {
                    val composer = args[0]
                    if (composer != null && composerImplClass.isInstance(composer)) {
                        try { collectMethod.invoke(composer) } catch (_: Exception) {}
                    }
                    @Suppress("UNCHECKED_CAST")
                    (composableLambda as kotlin.jvm.functions.Function2<Any?, Any?, Any?>)
                        .invoke(args[0], args[1])
                }
                kotlin.Unit
            }
        } catch (_: Exception) { composableLambda }
    }

    // --- Inspection mode wrapping ---

    /**
     * Wraps a composable lambda with CompositionLocalProvider(
     *   LocalInspectionMode provides true,
     *   LocalInspectionTables provides inspectionTables,
     * ).
     *
     * LocalInspectionMode makes composables skip Android-only codepaths (BackHandler,
     * LocalView access, etc.). LocalInspectionTables gives the Compose runtime a
     * Set<CompositionData> to register every sub-composition into — critical for
     * SubcomposeLayout-based components (Scaffold, LazyColumn, BoxWithConstraints),
     * whose contents live in child compositions outside the root slot table.
     */
    private fun wrapWithInspectionMode(composableLambda: Any, fn2Class: Class<*>, inspectionTables: MutableSet<Any>): Any {
        try {
            val compositionLocalClass = Class.forName("androidx.compose.runtime.ProvidableCompositionLocal")
            val providesMethod = compositionLocalClass.getMethod("provides", Any::class.java)
            val providedValueClass = Class.forName("androidx.compose.runtime.ProvidedValue")
            val composerClass = Class.forName("androidx.compose.runtime.Composer")

            val inspectionModeClass = Class.forName("androidx.compose.ui.platform.InspectionModeKt")
            val getInspectionMode = inspectionModeClass.getDeclaredMethod("getLocalInspectionMode")
            getInspectionMode.isAccessible = true
            val providedInspectionMode = providesMethod.invoke(getInspectionMode.invoke(null), true)

            val providedTables = try {
                val cls = Class.forName("androidx.compose.runtime.tooling.InspectionTablesKt")
                val get = cls.getDeclaredMethod("getLocalInspectionTables").apply { isAccessible = true }
                providesMethod.invoke(get.invoke(null), inspectionTables)
            } catch (_: Exception) { null }

            val providerClass = Class.forName("androidx.compose.runtime.CompositionLocalKt")
            // Prefer the vararg overload so we can provide both locals in one go.
            val varargProvider = providerClass.declaredMethods.firstOrNull { m ->
                m.name.startsWith("CompositionLocalProvider") && m.parameterTypes.size >= 4 &&
                    m.parameterTypes[0].isArray && m.parameterTypes[0].componentType == providedValueClass &&
                    m.parameterTypes[1] == fn2Class && m.parameterTypes[2] == composerClass
            }?.apply { isAccessible = true }
            val singleProvider = providerClass.declaredMethods.firstOrNull { m ->
                m.name.startsWith("CompositionLocalProvider") && m.parameterTypes.size >= 4 &&
                    m.parameterTypes[0] == providedValueClass &&
                    m.parameterTypes[1] == fn2Class && m.parameterTypes[2] == composerClass
            }?.apply { isAccessible = true }

            fun proxyFor(inner: Any, invokeProvider: (Any?, Any?, Any) -> Any?): Any =
                java.lang.reflect.Proxy.newProxyInstance(fn2Class.classLoader, arrayOf(fn2Class)) { _, m, args ->
                    if (m.name == "invoke" && args != null && args.size >= 2) {
                        val composer = args[0]; val changed = args[1]
                        val contentProxy = java.lang.reflect.Proxy.newProxyInstance(
                            fn2Class.classLoader, arrayOf(fn2Class),
                        ) { _, _, ia ->
                            if (ia != null && ia.size >= 2) {
                                @Suppress("UNCHECKED_CAST")
                                (inner as kotlin.jvm.functions.Function2<Any?, Any?, Any?>).invoke(ia[0], ia[1])
                            }
                            kotlin.Unit
                        }
                        invokeProvider(composer, changed, contentProxy)
                    }
                    kotlin.Unit
                }

            if (varargProvider != null && providedTables != null) {
                val arr = java.lang.reflect.Array.newInstance(providedValueClass, 2).also {
                    java.lang.reflect.Array.set(it, 0, providedInspectionMode)
                    java.lang.reflect.Array.set(it, 1, providedTables)
                }
                return proxyFor(composableLambda) { composer, changed, contentProxy ->
                    val args = mutableListOf<Any?>(arr, contentProxy, composer, changed)
                    repeat(varargProvider.parameterCount - 4) { args.add(0) }
                    varargProvider.invoke(null, *args.toTypedArray())
                }
            }

            if (singleProvider == null) return composableLambda
            // Fallback: chain single-value providers (outer=tables, inner=inspectionMode → content).
            val innerWrapped = proxyFor(composableLambda) { composer, changed, contentProxy ->
                val args = mutableListOf<Any?>(providedInspectionMode, contentProxy, composer, changed)
                repeat(singleProvider.parameterCount - 4) { args.add(0) }
                singleProvider.invoke(null, *args.toTypedArray())
            }
            return if (providedTables != null) proxyFor(innerWrapped) { composer, changed, contentProxy ->
                val args = mutableListOf<Any?>(providedTables, contentProxy, composer, changed)
                repeat(singleProvider.parameterCount - 4) { args.add(0) }
                singleProvider.invoke(null, *args.toTypedArray())
            } else innerWrapped
        } catch (e: Exception) {
            Logger.d { "InspectionMode wrapping unavailable: ${e::class.simpleName}: ${e.message}" }
            return composableLambda
        }
    }

    // --- CompositionData extraction ---

    /**
     * Reach into [ImageComposeScene] → BaseComposeScene.composition → CompositionImpl.slotTable
     * to obtain a CompositionData suitable for SlotTreeKt.asTree().
     */
    private fun extractCompositionDataFromScene(scene: ImageComposeScene): Any? {
        return try {
            val sceneField = scene::class.java.getDeclaredField("scene")
            sceneField.isAccessible = true
            val innerScene = sceneField.get(scene) ?: return null

            var clazz: Class<*>? = innerScene::class.java
            var composition: Any? = null
            while (clazz != null && composition == null) {
                try {
                    val f = clazz.getDeclaredField("composition")
                    f.isAccessible = true
                    composition = f.get(innerScene)
                } catch (_: NoSuchFieldException) {}
                clazz = clazz.superclass
            }
            if (composition == null) return null

            composition::class.java.declaredMethods
                .filter { it.name.startsWith("getSlotTable") }
                .firstNotNullOfOrNull { m ->
                    try { m.isAccessible = true; m.invoke(composition) } catch (_: Exception) { null }
                }
        } catch (e: Exception) {
            Logger.d { "extractCompositionDataFromScene: ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    // --- Theme control ---

    /**
     * Override the default value of LocalSystemTheme by replacing the
     * defaultValueHolder inside the StaticProvidableCompositionLocal.
     * This makes isSystemInDarkTheme() return the correct value for the
     * current render, overriding the OS-level detection.
     */
    private fun setSystemTheme(isDark: Boolean) {
        try {
            val systemThemeClass = Class.forName("androidx.compose.ui.SystemTheme")
            val targetTheme = if (isDark)
                systemThemeClass.getField("Dark").get(null)
            else
                systemThemeClass.getField("Light").get(null)

            // Get the LocalSystemTheme field
            val ktClass = Class.forName("androidx.compose.ui.SystemThemeKt")
            val field = ktClass.getDeclaredField("LocalSystemTheme")
            field.isAccessible = true
            val local = field.get(null)

            // CompositionLocal stores its default in a 'defaultValueHolder' (private final).
            // Replace the entire ValueHolder with a new StaticValueHolder containing our theme.
            val compositionLocalClass = Class.forName("androidx.compose.runtime.CompositionLocal")
            val holderField = compositionLocalClass.getDeclaredField("defaultValueHolder")
            holderField.isAccessible = true

            // Create new StaticValueHolder with the target theme
            val staticValueHolderClass = Class.forName("androidx.compose.runtime.StaticValueHolder")
            val newHolder = staticValueHolderClass.getConstructor(Any::class.java).newInstance(targetTheme)

            // Try VarHandle first (stable API, JDK 9+), fall back to sun.misc.Unsafe
            val replaced = try {
                val lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                    compositionLocalClass, java.lang.invoke.MethodHandles.lookup()
                )
                val varHandle = lookup.findVarHandle(compositionLocalClass, "defaultValueHolder", holderField.type)
                varHandle.set(local, newHolder)
                true
            } catch (_: Exception) { false }

            if (!replaced) {
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
                unsafeField.isAccessible = true
                val unsafe = unsafeField.get(null)
                val objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
                val putObject = unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)
                val offset = objectFieldOffset.invoke(unsafe, holderField) as Long
                putObject.invoke(unsafe, local, offset, newHolder)
            }
        } catch (_: Exception) {}
    }


    // --- Semantics extraction ---

    private class IdCounter { var value = 0; fun next() = value++; val current get() = value }

    private fun extractSemanticsHierarchy(owner: Any, densityDpi: Int): HierarchyNode? {
        val counter = IdCounter()
        return try {
            val rootNode = try {
                owner::class.java.getDeclaredMethod("getRootSemanticsNode").invoke(owner)
            } catch (_: NoSuchMethodException) {
                owner::class.java.getDeclaredMethod("getUnmergedRootSemanticsNode").invoke(owner)
            } ?: return null
            buildNode(rootNode, densityDpi, null, counter)
        } catch (e: Exception) {
            Logger.d { "Hierarchy: ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    private fun buildNode(node: Any, dpi: Int, parentBounds: Bounds?, counter: IdCounter): HierarchyNode {
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

        val bounds = dev.mikepenz.composebuddy.core.model.pxBoundsToDp(lPx, tPx, rPx, bPx, dpi)
        val boundsInParent = parentBounds?.let {
            boundsOf(bounds.left - it.left, bounds.top - it.top, bounds.right - it.left, bounds.bottom - it.top)
        }
        val size = sizeOf(bounds.right - bounds.left, bounds.bottom - bounds.top)
        val offsetFromParent = parentBounds?.let {
            val l = bounds.left - it.left; val t = bounds.top - it.top
            val r = it.right - bounds.right; val b = it.bottom - bounds.bottom
            if (l > 0.5 || t > 0.5 || r > 0.5 || b > 0.5) boundsOf(l, t, r, b) else null
        }

        val semantics = mutableMapOf<String, String>()
        try {
            val config = node::class.java.getDeclaredMethod("getConfig").invoke(node)
            if (config is Iterable<*>) {
                for (entry in config) {
                    val me = (entry as? Map.Entry<*, *>) ?: continue
                    val rawKey = me.key ?: continue
                    val key = (try { rawKey::class.java.getMethod("getName").invoke(rawKey)?.toString() } catch (_: Exception) { null }) ?: rawKey.toString()
                    val nk = SemanticsKeyMapper.mapKey(key)
                    val value = SemanticsKeyMapper.formatValue(me.value)
                    if (value.isNotBlank()) semantics[nk] = value
                }
            }
        } catch (_: Exception) {}

        val children = try {
            @Suppress("UNCHECKED_CAST")
            val childList = (node::class.java.getDeclaredMethod("getChildren").invoke(node) as? List<Any>) ?: emptyList()
            childList.map { buildNode(it, dpi, bounds, counter) }
        } catch (_: Exception) { emptyList() }

        val name = when {
            semantics["role"]?.contains("Button", true) == true -> "Button"
            semantics.containsKey("text") && children.isEmpty() -> "Text"
            semantics.containsKey("onClick") -> "Clickable"
            children.isNotEmpty() -> "Layout"
            else -> "Element"
        }

        return HierarchyNode(
            id = id, name = name,
            bounds = bounds, boundsInParent = boundsInParent,
            size = size, offsetFromParent = offsetFromParent,
            semantics = semantics.ifEmpty { null },
            children = children,
        )
    }

    private fun errorResp(fqn: String, err: String, start: Long, dpi: Int) =
        RenderResponse(fqn, false, error = err, durationMs = System.currentTimeMillis() - start, densityDpi = dpi)
}
