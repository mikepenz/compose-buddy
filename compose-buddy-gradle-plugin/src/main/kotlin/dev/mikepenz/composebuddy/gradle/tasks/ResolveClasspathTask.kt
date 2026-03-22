package dev.mikepenz.composebuddy.gradle.tasks

import dev.mikepenz.composebuddy.core.model.ClasspathManifest
import dev.mikepenz.composebuddy.core.serialization.ComposeBuddyJson
import kotlinx.serialization.encodeToString
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.Instant

abstract class ResolveClasspathTask : DefaultTask() {

    @get:Input
    abstract val variantName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun resolve() {
        val variant = variantName.get()
        logger.lifecycle("Compose Buddy: Resolving classpath for variant '$variant'")

        val compiledClassDirs = resolveCompiledClassDirs()
        val dependencyJars = resolveDependencyJars()
        val androidJar = detectAndroidJar()
        val resourceDirs = resolveResourceDirs()
        val assetDirs = resolveAssetDirs()
        val composeVersion = detectArtifactVersion("androidx.compose.runtime", "runtime")
            ?: detectArtifactVersion("org.jetbrains.compose.runtime", "runtime")
        val kotlinVersion = detectArtifactVersion("org.jetbrains.kotlin", "kotlin-stdlib")
        val platformType = detectPlatformType()
        val namespace = detectNamespace()
        val compileSdk = detectCompileSdk()

        val manifest = ClasspathManifest(
            compiledClassDirs = compiledClassDirs.map { it.absolutePath },
            dependencyJars = dependencyJars.map { it.absolutePath },
            androidJar = androidJar?.absolutePath,
            resourceDirs = resourceDirs.map { it.absolutePath },
            assetDirs = assetDirs.map { it.absolutePath },
            composeVersion = composeVersion,
            kotlinVersion = kotlinVersion,
            platformType = platformType,
            namespace = namespace,
            compileSdk = compileSdk,
            generatedAt = Instant.now().toString(),
        )

        val output = outputFile.get().asFile
        output.parentFile?.mkdirs()
        output.writeText(ComposeBuddyJson.encodeToString(manifest))
        logger.lifecycle("Compose Buddy: Wrote classpath manifest to ${output.absolutePath} (${compiledClassDirs.size} class dirs, ${dependencyJars.size} dependency JARs)")
    }

    private fun resolveCompiledClassDirs(): List<File> {
        val dirs = mutableSetOf<File>()

        // Collect from Kotlin compilation outputs
        project.rootProject.allprojects.forEach { p ->
            val candidates = listOf(
                "build/classes/kotlin/android/main",
                "build/classes/kotlin/main",
                "build/classes/kotlin/test",
                "build/classes/kotlin/jvm/main",
                "build/classes/kotlin/jvm/test",
                "build/classes/kotlin/desktop/main",
                "build/classes/kotlin/desktop/test",
                "build/tmp/kotlin-classes/${variantName.get()}",
                "build/intermediates/built_in_kotlinc/${variantName.get()}/compile${variantName.get().replaceFirstChar { it.uppercase() }}Kotlin/classes",
                "build/intermediates/javac/${variantName.get()}/classes",
                "build/processedResources/jvm/main",
                "build/processedResources/desktop/main",
                "build/processedResources/android/${variantName.get()}",
            )
            for (candidate in candidates) {
                val f = p.file(candidate)
                if (f.exists()) dirs.add(f)
            }

            // R.jar candidates
            val rJarCandidates = listOf(
                "build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/${variantName.get()}/processDebugResources/R.jar",
                "build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/${variantName.get()}/R.jar",
                "build/intermediates/compile_and_runtime_r_class_jar/${variantName.get()}/processDebugResources/R.jar",
                "build/intermediates/compile_r_class_jar/${variantName.get()}/generateDebugRFile/R.jar",
            )
            for (candidate in rJarCandidates) {
                val f = p.file(candidate)
                if (f.exists()) dirs.add(f)
            }
        }

        return dirs.toList()
    }

    private fun resolveDependencyJars(): List<File> {
        val variant = variantName.get()
        // Compile classpath BEFORE runtime — KMP libraries only have Compose on compileClasspath.
        // Include both KMP target names (jvm/desktop/android) and AGP variant names (debug/release).
        val configNames = listOf(
            "${variant}CompileClasspath",
            "${variant}RuntimeClasspath",
            "jvmCompileClasspath",
            "jvmRuntimeClasspath",
            "desktopCompileClasspath",
            "desktopRuntimeClasspath",
            "androidCompileClasspath",
            "androidRuntimeClasspath",
            "compileClasspath",
            "runtimeClasspath",
            "jvmTestCompileClasspath",
            "jvmTestRuntimeClasspath",
            "testRuntimeClasspath",
        )

        val seen = mutableSetOf<String>()
        val jars = mutableListOf<File>()

        for (name in configNames) {
            val cfg = try {
                project.configurations.getByName(name)
            } catch (_: Exception) {
                continue
            }
            try {
                cfg.incoming.artifactView { it.lenient(true) }.files.forEach { file ->
                    if (seen.add(file.absolutePath)) {
                        jars.add(file)
                    }
                }
            } catch (_: Exception) {
                continue
            }
        }

        return jars
    }

    private fun detectAndroidJar(): File? {
        val androidExt = project.extensions.findByName("android") ?: return null
        return try {
            val sdkDirMethod = androidExt::class.java.getMethod("getSdkDirectory")
            val sdkDir = sdkDirMethod.invoke(androidExt) as? File ?: return null
            val compileSdk = detectCompileSdk() ?: return null
            val jar = File(sdkDir, "platforms/android-$compileSdk/android.jar")
            if (jar.exists()) jar else null
        } catch (_: Exception) {
            null
        }
    }

    private fun detectCompileSdk(): Int? {
        val androidExt = project.extensions.findByName("android") ?: return null
        return try {
            val compileSdkMethod = androidExt::class.java.getMethod("getCompileSdkVersion")
            val version = compileSdkMethod.invoke(androidExt) as? String
            version?.removePrefix("android-")?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun detectNamespace(): String? {
        val androidExt = project.extensions.findByName("android") ?: return null
        return try {
            val method = androidExt::class.java.getMethod("getNamespace")
            method.invoke(androidExt) as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun detectPlatformType(): String {
        val hasAgp = project.plugins.hasPlugin("com.android.application") ||
            project.plugins.hasPlugin("com.android.library")
        val hasKmp = project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")

        return when {
            hasKmp && hasAgp -> "kmp"
            hasKmp -> "desktop"
            hasAgp -> "android"
            else -> "unknown"
        }
    }

    private fun detectArtifactVersion(group: String, module: String): String? {
        val variant = variantName.get()
        val configNames = listOf(
            "${variant}RuntimeClasspath",
            "jvmRuntimeClasspath",
            "desktopRuntimeClasspath",
            "runtimeClasspath",
        )
        for (name in configNames) {
            val cfg = try {
                project.configurations.getByName(name)
            } catch (_: Exception) {
                continue
            }
            try {
                val resolved = cfg.resolvedConfiguration.lenientConfiguration
                    .allModuleDependencies
                    .firstOrNull { it.moduleGroup == group && it.moduleName == module }
                if (resolved != null) return resolved.moduleVersion
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun resolveResourceDirs(): List<File> {
        val candidates = listOf(
            project.file("src/main/res"),
            project.file("src/commonMain/composeResources"),
            project.file("src/androidMain/res"),
        )
        return candidates.filter { it.exists() && it.isDirectory }
    }

    private fun resolveAssetDirs(): List<File> {
        val candidates = listOf(
            project.file("src/main/assets"),
            project.file("src/androidMain/assets"),
        )
        return candidates.filter { it.exists() && it.isDirectory }
    }
}
