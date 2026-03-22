package dev.mikepenz.composebuddy.renderer

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.ClasspathManifest
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Collects project resources for rendering.
 * Generates a resources.json config that the renderer uses to resolve
 * Android resources (drawables, strings, themes, etc.) from the project.
 */
class ResourceCollector(
    private val projectDir: File,
    private val modulePath: String? = null,
) {

    @Serializable
    data class ResourceConfig(
        val mainPackage: String = "",
        val targetSdkVersion: String = "34",
        val resourcePackageNames: List<String> = emptyList(),
        val projectResourceDirs: List<String> = emptyList(),
        val moduleResourceDirs: List<String> = emptyList(),
        val aarExplodedDirs: List<String> = emptyList(),
        val projectAssetDirs: List<String> = emptyList(),
        val aarAssetDirs: List<String> = emptyList(),
    )

    /**
     * Build the project module so compiled classes are available for preview discovery.
     * Auto-detects the correct compile task based on the module structure.
     * @return true if build succeeded
     */
    fun build(): Boolean {
        val gradlew = findGradleWrapper() ?: run {
            Logger.e { "No Gradle wrapper found in ${projectDir.absolutePath}" }
            return false
        }
        val moduleDir = resolveModuleDir()
        val tasks = detectCompileTasks(moduleDir)
        val fullTasks = tasks.map { if (modulePath.isNullOrBlank()) it else "$modulePath:$it" }

        Logger.i { "Building ${fullTasks.joinToString(", ")}..." }
        return try {
            val cmd = mutableListOf(gradlew.absolutePath)
            cmd.addAll(fullTasks)
            cmd.add("-q")
            val process = ProcessBuilder(cmd)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Logger.e { "Build failed (exit $exitCode): ${output.take(500)}" }
                false
            } else {
                Logger.i { "Build succeeded" }
                true
            }
        } catch (e: Exception) {
            Logger.e { "Build error: ${e.message}" }
            false
        }
    }

    /**
     * Auto-detect the correct Gradle compile tasks for the module.
     * Returns both main and test compile tasks so previews in test source sets are found.
     */
    private fun detectCompileTasks(moduleDir: File): List<String> {
        val buildFile = File(moduleDir, "build.gradle.kts")
        val buildContent = if (buildFile.exists()) buildFile.readText() else ""
        val isKmp = buildContent.contains("kotlin(\"multiplatform\")") ||
            buildContent.contains("kotlinMultiplatform") ||
            buildContent.contains("kotlin-multiplatform") ||
            File(moduleDir, "src/commonMain").exists()
        val hasTestPreviews = File(moduleDir, "src/jvmTest").exists() ||
            File(moduleDir, "src/desktopTest").exists() ||
            File(moduleDir, "src/test").exists()

        // Standalone Android project (not KMP)
        val isAndroid = buildContent.contains("com.android.application") ||
            buildContent.contains("com.android.library") ||
            buildContent.contains("android-application") ||
            buildContent.contains("android-library") ||
            File(moduleDir, "src/main/AndroidManifest.xml").exists() ||
            File(moduleDir, "build/intermediates").exists()
        if (!isKmp && isAndroid) {
            return listOf("compileDebugKotlin")
        }

        if (isKmp) {
            val main = when {
                File(moduleDir, "src/desktopMain").exists() -> "compileKotlinDesktop"
                File(moduleDir, "src/jvmMain").exists() ||
                    buildContent.contains("jvm()") || buildContent.contains("jvm {") ||
                    buildContent.contains("jvm(") -> "compileKotlinJvm"
                File(moduleDir, "src/androidMain").exists() -> "compileDebugKotlin"
                else -> detectCompileTaskViaGradle(moduleDir) ?: "compileKotlinJvm"
            }
            val tasks = mutableListOf(main)
            // Add test compile task if test sources exist
            if (hasTestPreviews) {
                val test = when (main) {
                    "compileKotlinDesktop" -> "compileTestKotlinDesktop"
                    "compileKotlinJvm" -> "compileTestKotlinJvm"
                    else -> null
                }
                if (test != null) tasks.add(test)
            }
            // Also compile Compose resources
            tasks.add("jvmProcessResources")
            return tasks
        }

        // Plain JVM / Kotlin JVM
        val tasks = mutableListOf("compileKotlin")
        if (hasTestPreviews) tasks.add("compileTestKotlin")
        return tasks
    }

    /**
     * Query Gradle for available compile tasks when auto-detection fails.
     */
    private fun detectCompileTaskViaGradle(moduleDir: File): String? {
        val gradlew = findGradleWrapper() ?: return null
        val taskPrefix = if (modulePath.isNullOrBlank()) "" else "$modulePath:"
        return try {
            val process = ProcessBuilder(gradlew.absolutePath, "${taskPrefix}tasks", "--all", "-q")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)

            // Find compileKotlin* tasks, prefer jvm/desktop over ios/js/wasm
            val compileTasks = output.lines()
                .map { it.trim() }
                .filter { it.startsWith("compileKotlin") && !it.contains(" ") }
                .map { it.substringBefore(" -") }
            val preferred = listOf("compileKotlinJvm", "compileKotlinDesktop", "compileDebugKotlin")
            preferred.firstOrNull { it in compileTasks } ?: compileTasks.firstOrNull()
        } catch (_: Exception) { null }
    }

    fun collect(): ResourceConfig {
        val moduleDir = resolveModuleDir()

        val resourceDirs = findResourceDirs(moduleDir)
        val assetDirs = findAssetDirs(moduleDir)
        val mainPackage = detectPackage(moduleDir)

        return ResourceConfig(
            mainPackage = mainPackage,
            projectResourceDirs = resourceDirs.map { it.absolutePath },
            projectAssetDirs = assetDirs.map { it.absolutePath },
        )
    }

    fun writeConfig(outputFile: File): File {
        val config = collect()
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(Json { prettyPrint = true }.encodeToString(config))
        return outputFile
    }

    /**
     * Discover all modules in the project via `./gradlew projects -q`.
     * Returns module paths like ":app", ":lib:core", etc.
     */
    fun discoverModules(): List<String> {
        val gradlew = findGradleWrapper() ?: return emptyList()
        return try {
            val process = ProcessBuilder(gradlew.absolutePath, "projects", "-q")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)

            // Parse lines like "+--- Project ':aboutlibraries-compose-m3'"
            val projectRegex = Regex("""Project '([^']+)'""")
            val modules = projectRegex.findAll(output)
                .map { it.groupValues[1] }
                .filter { it.startsWith(":") } // exclude root project name
                .toList()
            Logger.i { "Discovered ${modules.size} modules via Gradle" }
            modules.ifEmpty { listOf("") }
        } catch (e: Exception) {
            Logger.d { "Module discovery failed: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Discover modules that have compiled classes with @Preview annotations.
     * Checks for direct @Preview and common multi-preview annotations.
     * Returns module paths that are worth scanning.
     */
    fun discoverModulesWithPreviews(): List<String> {
        val previewPatterns = listOf(
            "Landroidx/compose/ui/tooling/preview/Preview;",
            "Landroidx/compose/ui/tooling/preview/PreviewLightDark;",
            "Landroidx/compose/ui/tooling/preview/PreviewScreenSizes;",
            "Landroidx/compose/ui/tooling/preview/PreviewFontScale;",
            "Landroidx/compose/ui/tooling/preview/PreviewDynamicColors;",
        ).map { it.toByteArray() }
        return discoverModules().filter { mod ->
            val dir = if (mod.isBlank()) projectDir
            else File(projectDir, mod.removePrefix(":").replace(':', File.separatorChar))
            hasCompiledClasses(dir) && previewPatterns.any { hasPreviewAnnotation(dir, it) }
        }
    }

    private fun hasCompiledClasses(moduleDir: File): Boolean {
        val classDirs = listOf(
            "build/classes/kotlin/main", "build/classes/kotlin/jvm/main",
            "build/classes/kotlin/desktop/main", "build/classes/kotlin/jvm/test",
            "build/classes/kotlin/debug", "build/tmp/kotlin-classes/debug",
            "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
        )
        return classDirs.any { File(moduleDir, it).exists() }
    }

    private fun hasPreviewAnnotation(moduleDir: File, previewBytes: ByteArray): Boolean {
        val classDirs = listOf(
            "build/classes/kotlin/main", "build/classes/kotlin/jvm/main",
            "build/classes/kotlin/desktop/main", "build/classes/kotlin/jvm/test",
            "build/classes/kotlin/debug", "build/tmp/kotlin-classes/debug",
            "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
        )
        for (dirPath in classDirs) {
            val dir = File(moduleDir, dirPath)
            if (!dir.exists()) continue
            // Quick scan: check first 200 class files for @Preview bytes
            var checked = 0
            for (f in dir.walk()) {
                if (f.extension != "class") continue
                if (++checked > 200) break
                val bytes = f.readBytes()
                if (containsBytes(bytes, previewBytes)) return true
            }
        }
        return false
    }

    private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun resolveModuleDir(): File {
        if (modulePath.isNullOrBlank()) return projectDir
        val relativePath = modulePath.removePrefix(":").replace(':', File.separatorChar)
        val moduleDir = File(projectDir, relativePath)
        return if (moduleDir.exists()) moduleDir else projectDir
    }

    private fun findResourceDirs(moduleDir: File): List<File> {
        val candidates = listOf(
            File(moduleDir, "src/main/res"),
            File(moduleDir, "src/commonMain/composeResources"),
            File(moduleDir, "src/androidMain/res"),
        )
        return candidates.filter { it.exists() && it.isDirectory }
    }

    private fun findAssetDirs(moduleDir: File): List<File> {
        val candidates = listOf(
            File(moduleDir, "src/main/assets"),
            File(moduleDir, "src/androidMain/assets"),
        )
        return candidates.filter { it.exists() && it.isDirectory }
    }

    private fun detectPackage(moduleDir: File): String {
        // Try AndroidManifest.xml
        val manifest = File(moduleDir, "src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            val content = manifest.readText()
            val match = Regex("""package\s*=\s*"([^"]+)"""").find(content)
            if (match != null) return match.groupValues[1]
        }

        // Try build.gradle.kts for namespace
        val buildFile = File(moduleDir, "build.gradle.kts")
        if (buildFile.exists()) {
            val content = buildFile.readText()
            val match = Regex("""namespace\s*=\s*"([^"]+)"""").find(content)
            if (match != null) return match.groupValues[1]
        }

        return ""
    }

    /**
     * Resolves compiled class directories and dependency JARs for a specific platform.
     *
     * @param platform Which Gradle classpath variant to resolve:
     *   - `"jvm"` — JVM/Desktop Compose dependencies (for desktop renderer)
     *   - `"android"` — Android Compose dependencies (for android renderer)
     *   - `"all"` — merge all available (for preview discovery)
     *
     * Each platform resolves ONLY its own Gradle configurations to prevent mixing
     * Desktop-variant and Android-variant JARs (which have incompatible APIs).
     */
    fun resolveClasspath(platform: String = "all"): List<File> {
        val moduleDir = resolveModuleDir()
        val entries = mutableListOf<File>()

        // Compiled class directories — shared across all platforms
        val classDirs = listOf(
            "build/classes/kotlin/main", "build/classes/kotlin/test", "build/classes/kotlin/debug",
            "build/classes/kotlin/jvm/main", "build/classes/kotlin/jvm/test",
            "build/classes/kotlin/desktop/main", "build/classes/kotlin/desktop/test",
            "build/classes/kotlin/android/debug",
            "build/tmp/kotlin-classes/debug",
            "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
            "build/classes/java/main", "build/classes/java/test", "build/classes/java/debug",
            "build/intermediates/javac/debug/classes",
            "build/processedResources/jvm/main", "build/processedResources/desktop/main",
            "build/processedResources/android/debug",
        )
        entries.addAll(classDirs.map { File(moduleDir, it) }.filter { it.exists() })

        // R.jar — merged R classes (needed for Android ComposeView)
        if (platform == "android" || platform == "all") {
            val rJarCandidates = listOf(
                "build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar",
                "build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar",
                "build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar",
                "build/intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar",
            )
            entries.addAll(rJarCandidates.map { File(moduleDir, it) }.filter { it.exists() })
        }

        // Dependency JARs — platform-isolated
        entries.addAll(resolveDependencyJars(moduleDir, platform))

        return entries.distinct()
    }

    /**
     * Resolves dependency JARs by invoking Gradle on the target project.
     * Tries: manifest cache -> plugin task -> init-script fallback -> scan fallback.
     */
    private fun resolveDependencyJars(moduleDir: File, platform: String = "all"): List<File> {
        // Try reading a previously generated ClasspathManifest
        val manifest = resolveClasspathViaManifest(moduleDir)
        if (manifest != null) {
            Logger.d { "Using classpath manifest (${manifest.dependencyJars.size} JARs)" }
            return manifest.dependencyJars.map { File(it) }.filter { it.exists() } +
                manifest.compiledClassDirs.map { File(it) }.filter { it.exists() }
        }

        // Try plugin-based resolution
        val pluginClasspath = resolveClasspathViaPlugin(moduleDir, platform = platform)
        if (pluginClasspath.isNotEmpty()) return pluginClasspath

        // Try cached classpath first (only for "all" — platform-specific should not use stale caches)
        if (platform == "all") {
            val cached = loadCachedClasspath(moduleDir)
            if (cached != null) return cached
        }

        // Resolve via Gradle init script (existing fallback)
        val gradleClasspath = resolveClasspathViaGradle(platform)
        if (gradleClasspath.isNotEmpty()) {
            saveCachedClasspath(moduleDir, gradleClasspath)
            return gradleClasspath
        }

        // Fallback: scan known locations
        return resolveDependencyJarsFallback(moduleDir)
    }

    /**
     * Reads a cached ClasspathManifest from `build/compose-buddy/{variant}/classpath.json`.
     */
    private fun resolveClasspathViaManifest(moduleDir: File, variant: String = "debug"): ClasspathManifest? {
        val manifestFile = File(moduleDir, "build/compose-buddy/$variant/classpath.json")
        if (!manifestFile.exists()) return null
        return try {
            val fingerprint = buildFingerprint(moduleDir)
            val manifest = ComposeBuddyJson.decodeFromString<ClasspathManifest>(manifestFile.readText())
            // Check if the manifest is stale by comparing build fingerprint
            val fingerprintFile = File(manifestFile.parentFile, ".fingerprint")
            if (fingerprintFile.exists() && fingerprintFile.readText() != fingerprint) {
                Logger.d { "Classpath manifest is stale, ignoring" }
                return null
            }
            manifest
        } catch (e: Exception) {
            Logger.d { "Failed to read classpath manifest: ${e.message}" }
            null
        }
    }

    /**
     * Runs the Gradle plugin classpath resolution task via an init script.
     * Generates an init script that applies the compose-buddy plugin and runs the
     * `composeBuddyResolveClasspath` task.
     */
    private fun resolveClasspathViaPlugin(moduleDir: File, variant: String = "debug", platform: String = "all"): List<File> {
        val gradlew = findGradleWrapper() ?: return emptyList()
        val pluginJar = findPluginJar() ?: run {
            Logger.d { "Plugin fat JAR not found, skipping plugin-based classpath resolution" }
            return emptyList()
        }

        // Try platform-appropriate variants only to keep classpaths isolated
        val variantsToTry = when (platform) {
            "jvm" -> listOf("jvm", "desktop")
            "android" -> listOf("android", variant, "debug")
            else -> listOf("jvm", "desktop", "android", variant)
        }.distinct()
        for (v in variantsToTry) {
            val result = runPluginResolveTask(moduleDir, gradlew, pluginJar, v)
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun runPluginResolveTask(moduleDir: File, gradlew: File, pluginJar: File, variant: String): List<File> {
        val capitalizedVariant = variant.replaceFirstChar { it.uppercase() }
        val taskName = if (modulePath.isNullOrBlank()) {
            "composeBuddyResolveClasspath$capitalizedVariant"
        } else {
            "$modulePath:composeBuddyResolveClasspath$capitalizedVariant"
        }

        val initScript = File.createTempFile("compose-buddy-plugin", ".init.gradle.kts")
        initScript.writeText(
            """
            initscript {
                dependencies {
                    classpath(files("${pluginJar.absolutePath.replace("\\", "\\\\")}"))
                }
            }
            allprojects {
                afterEvaluate {
                    if (!plugins.hasPlugin("dev.mikepenz.composebuddy")) {
                        apply(plugin = "dev.mikepenz.composebuddy")
                    }
                }
            }
            """.trimIndent(),
        )
        initScript.deleteOnExit()

        return try {
            Logger.i { "Resolving classpath via plugin task: $taskName" }
            val process = ProcessBuilder(
                gradlew.absolutePath, "--init-script", initScript.absolutePath, taskName, "-q",
            )
                .directory(projectDir)
                .redirectErrorStream(false)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exited = process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)
            val exitCode = if (exited) process.exitValue() else -1

            if (exitCode != 0) {
                Logger.d { "Plugin classpath task failed (exit $exitCode): ${error.take(300)}" }
                return emptyList()
            }

            // Read the generated manifest
            val capitalizedV = variant.replaceFirstChar { it.uppercase() }
            val manifest = resolveClasspathViaManifest(resolveModuleDir(), variant)
            if (manifest != null) {
                // Save fingerprint for staleness detection
                val fingerprintFile = File(resolveModuleDir(), "build/compose-buddy/$variant/.fingerprint")
                fingerprintFile.parentFile?.mkdirs()
                fingerprintFile.writeText(buildFingerprint(resolveModuleDir()))

                val entries = manifest.dependencyJars.map { File(it) }.filter { it.exists() } +
                    manifest.compiledClassDirs.map { File(it) }.filter { it.exists() }
                Logger.i { "Plugin resolved ${entries.size} classpath entries (variant=$variant)" }
                entries
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Logger.d { "Plugin classpath task '$variant' failed: ${e.message}" }
            emptyList()
        } finally {
            initScript.delete()
        }
    }

    /**
     * Locates the plugin fat JAR. Checks:
     * 1. Classpath resources (embedded in CLI fat JAR)
     * 2. Sibling directories (development layout)
     * 3. User cache directory
     */
    private fun findPluginJar(): File? {
        // Check embedded resources
        val resourceStream = javaClass.getResourceAsStream("/plugin/compose-buddy-gradle-plugin-fat.jar")
        if (resourceStream != null) {
            val cacheDir = File(System.getProperty("user.home"), ".compose-buddy/cache/plugin")
            cacheDir.mkdirs()
            val targetJar = File(cacheDir, "compose-buddy-gradle-plugin-fat.jar")
            if (!targetJar.exists()) {
                resourceStream.use { input ->
                    targetJar.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return targetJar
        }

        // Check sibling directories (development layout)
        val devJar = File(projectDir, "../compose-buddy-gradle-plugin/build/libs")
        if (devJar.exists()) {
            val fatJar = devJar.listFiles()?.firstOrNull { it.name.endsWith("-fat.jar") }
            if (fatJar != null) return fatJar
        }

        // Check user cache
        val cachedJar = File(System.getProperty("user.home"), ".compose-buddy/cache/plugin/compose-buddy-gradle-plugin-fat.jar")
        if (cachedJar.exists()) return cachedJar

        return null
    }

    /**
     * Loads cached classpath if the cache file exists and the build fingerprint matches.
     * Cache is invalidated when build.gradle.kts or settings.gradle.kts change.
     */
    private fun loadCachedClasspath(moduleDir: File): List<File>? {
        val cacheFile = File(moduleDir, "build/.compose-buddy-classpath-cache")
        if (!cacheFile.exists()) return null
        try {
            val lines = cacheFile.readLines()
            if (lines.size < 2) return null
            val savedFingerprint = lines[0]
            val currentFingerprint = buildFingerprint(moduleDir)
            if (savedFingerprint != currentFingerprint) return null
            val files = lines.drop(1).map { File(it) }.filter { it.exists() }
            if (files.isEmpty()) return null
            Logger.d { "Using cached classpath (${files.size} entries)" }
            return files
        } catch (_: Exception) {
            return null
        }
    }

    private fun saveCachedClasspath(moduleDir: File, classpath: List<File>) {
        try {
            val cacheFile = File(moduleDir, "build/.compose-buddy-classpath-cache")
            cacheFile.parentFile?.mkdirs()
            val fingerprint = buildFingerprint(moduleDir)
            cacheFile.writeText(fingerprint + "\n" + classpath.joinToString("\n") { it.absolutePath })
        } catch (_: Exception) { /* best effort */ }
    }

    private fun buildFingerprint(moduleDir: File): String {
        val buildFiles = listOf(
            File(moduleDir, "build.gradle.kts"),
            File(moduleDir, "build.gradle"),
            File(projectDir, "settings.gradle.kts"),
            File(projectDir, "settings.gradle"),
            File(projectDir, "gradle/libs.versions.toml"),
        )
        return buildFiles.filter { it.exists() }
            .joinToString(",") { "${it.name}:${it.lastModified()}" }
    }

    /**
     * Invokes Gradle with a one-shot init script to print the compile classpath.
     */
    private fun resolveClasspathViaGradle(platform: String = "all"): List<File> {
        val gradlew = findGradleWrapper() ?: return emptyList()
        // Run on ALL projects to capture cross-module transitive dependencies.
        // Each project resolves its own classpath (cross-project resolution is blocked in Gradle 9+).
        val taskName = "printCompileClasspath"

        // Create a temporary init script that adds a classpath-printing task
        val initScript = File.createTempFile("compose-buddy-classpath", ".gradle.kts")
        initScript.writeText(
            """
            allprojects {
                tasks.register("printCompileClasspath") {
                    doLast {
                        val variant = "debug"
                        // 1. Include ALL project modules' compiled class dirs
                        val moduleDirs = listOf(
                            "build/classes/kotlin/android/main",
                            "build/classes/kotlin/main",
                            "build/classes/kotlin/test",
                            "build/classes/kotlin/jvm/main",
                            "build/classes/kotlin/jvm/test",
                            "build/classes/kotlin/desktop/main",
                            "build/classes/kotlin/desktop/test",
                            "build/tmp/kotlin-classes/${'$'}variant",
                            "build/intermediates/built_in_kotlinc/${'$'}variant/compile${'$'}{variant.replaceFirstChar { it.uppercase() }}Kotlin/classes",
                            "build/intermediates/javac/${'$'}variant/classes",
                            "build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/${'$'}variant/processDebugResources/R.jar",
                            "build/processedResources/jvm/main",
                            "build/processedResources/desktop/main",
                            "build/processedResources/android/${'$'}variant"
                        )
                        rootProject.allprojects.forEach { p ->
                            for (d in moduleDirs) {
                                val f = p.file(d)
                                if (f.exists()) println("CLASSPATH_ENTRY:" + f.absolutePath)
                            }
                        }
                        // 2. Resolve external dependencies (JARs/AARs)
                        // Platform-isolated: only resolve configs for the requested platform
                        // to prevent mixing Desktop-variant and Android-variant JARs.
                        val platform = "${platform}"
                        val configs = when (platform) {
                            "jvm" -> listOf(
                                "jvmCompileClasspath", "jvmRuntimeClasspath",
                                "desktopCompileClasspath", "desktopRuntimeClasspath",
                                "compileClasspath", "runtimeClasspath"
                            )
                            "android" -> listOf(
                                "androidCompileClasspath", "androidRuntimeClasspath",
                                "${'$'}{variant}CompileClasspath", "${'$'}{variant}RuntimeClasspath",
                                "compileClasspath", "runtimeClasspath"
                            )
                            else -> listOf(
                                "${'$'}{variant}CompileClasspath", "${'$'}{variant}RuntimeClasspath",
                                "jvmCompileClasspath", "jvmRuntimeClasspath",
                                "desktopCompileClasspath", "desktopRuntimeClasspath",
                                "androidCompileClasspath", "androidRuntimeClasspath",
                                "compileClasspath", "runtimeClasspath"
                            )
                        }
                        val seen = mutableSetOf<String>()
                        // Resolve target module's configurations
                        for (name in configs) {
                            val cfg = try { configurations.getByName(name) } catch (_: Exception) { null } ?: continue
                            try {
                                cfg.incoming.artifactView { lenient(true) }.files.forEach {
                                    if (seen.add(it.absolutePath)) {
                                        println("CLASSPATH_ENTRY:" + it.absolutePath)
                                    }
                                }
                            } catch (_: Exception) { continue }
                        }
                        // Note: cross-project configuration resolution is not supported in Gradle 9+.
                        // Sibling module deps are resolved by running printCompileClasspath on all projects.
                    }
                }
            }
            """.trimIndent(),
        )
        initScript.deleteOnExit()

        return try {
            val process = ProcessBuilder(
                gradlew.absolutePath, "--init-script", initScript.absolutePath, taskName, "-q",
            )
                .directory(projectDir)
                .redirectErrorStream(false)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)

            output.lines()
                .filter { it.startsWith("CLASSPATH_ENTRY:") }
                .map { File(it.removePrefix("CLASSPATH_ENTRY:")) }
                .filter { it.exists() }
                .flatMap { file ->
                    when (file.extension) {
                        "jar" -> listOf(file)
                        "aar" -> {
                            val extracted = extractClassesJarFromAar(file)
                            if (extracted != null) listOf(extracted) else emptyList()
                        }
                        else -> listOf(file)
                    }
                }
        } catch (e: Exception) {
            System.err.println("compose-buddy: Failed to resolve classpath via Gradle: ${e.message}")
            emptyList()
        } finally {
            initScript.delete()
        }
    }

    private fun findGradleWrapper(): File? {
        val wrapper = File(projectDir, "gradlew")
        return if (wrapper.exists() && wrapper.canExecute()) wrapper else null
    }

    private fun resolveDependencyJarsFallback(moduleDir: File): List<File> {
        val jars = mutableListOf<File>()

        // Android project: JARs in intermediates
        val intermediates = File(moduleDir, "build/intermediates")
        if (intermediates.exists()) {
            intermediates.walk()
                .filter { it.extension == "jar" && it.name != "R.jar" }
                .filter { it.absolutePath.contains("debug") || !it.absolutePath.contains("release") }
                .forEach { jars.add(it) }
        }

        // Gradle modules cache: look for compose tooling-preview AAR and extract classes.jar
        val modulesCache = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")
        if (modulesCache.exists()) {
            // Find the AAR and extract classes.jar from it
            val aarFile = modulesCache.walk()
                .filter { it.extension == "aar" && it.name.contains("ui-tooling-preview") }
                .firstOrNull()
            if (aarFile != null) {
                val extractedJar = extractClassesJarFromAar(aarFile)
                if (extractedJar != null) jars.add(extractedJar)
            }
        }

        return jars
    }

    private fun extractClassesJarFromAar(aarFile: File): File? {
        val cacheDir = File(System.getProperty("user.home"), ".compose-buddy/cache/aar-classes")
        val targetJar = File(cacheDir, "${aarFile.nameWithoutExtension}-classes.jar")
        if (targetJar.exists()) return targetJar

        return try {
            cacheDir.mkdirs()
            java.util.zip.ZipFile(aarFile).use { zip ->
                val entry = zip.getEntry("classes.jar") ?: return null
                zip.getInputStream(entry).use { input ->
                    targetJar.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            targetJar
        } catch (_: Exception) {
            null
        }
    }
}
