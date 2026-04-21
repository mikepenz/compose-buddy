package dev.mikepenz.composebuddy.gradle.device

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import dev.mikepenz.composebuddy.core.VERSION
import dev.mikepenz.composebuddy.gradle.ComposeBuddyExtension
import dev.mikepenz.composebuddy.gradle.device.tasks.BuddyPreviewDeployTask
import dev.mikepenz.composebuddy.gradle.device.tasks.BuddyPreviewListTask
import org.gradle.api.Project

object BuddyDeviceConfigurator {

    private const val DEVICE_LIB = "dev.mikepenz.composebuddy:compose-buddy-device:$VERSION"
    private const val KSP_PROCESSOR = "dev.mikepenz.composebuddy:compose-buddy-device-ksp:$VERSION"

    fun configure(project: Project, extension: ComposeBuddyExtension) {
        project.pluginManager.withPlugin("com.android.application") {
            val android = project.extensions.findByType(ApplicationExtension::class.java) ?: return@withPlugin

            // Create buddyDebug build type eagerly during configuration (before afterEvaluate)
            // so AGP property locks (e.g. enableUnitTestCoverage) are not yet in effect.
            android.buildTypes.maybeCreate("buddyDebug").apply {
                initWith(android.buildTypes.getByName("debug"))
                applicationIdSuffix = ".buddy"
                matchingFallbacks += "debug"
                signingConfig = android.signingConfigs.findByName("debug")
            }

            // Merge src/debug/ into the buddyDebug variant via the AGP variant API so that
            // both KSP and the Kotlin compiler see the debug sources.
            // java.srcDir() on the legacy source set feeds KSP but not the compiler in AGP 8+.
            val androidComponents = project.extensions.findByType(ApplicationAndroidComponentsExtension::class.java)
            androidComponents?.onVariants { variant ->
                if (variant.buildType == "buddyDebug") {
                    variant.sources.java?.addStaticSourceDirectory("src/debug/java")
                    variant.sources.kotlin?.addStaticSourceDirectory("src/debug/kotlin")
                    variant.sources.res?.addStaticSourceDirectory("src/debug/res")
                    variant.sources.assets?.addStaticSourceDirectory("src/debug/assets")
                    variant.sources.resources?.addStaticSourceDirectory("src/debug/resources")
                }
            }

            // Check at afterEvaluate so users can set devicePreviewEnabled in their build script
            project.afterEvaluate {
                if (!extension.devicePreviewEnabled.get()) return@afterEvaluate
                configureAndroidApp(project)
            }
        }
    }

    private fun configureAndroidApp(project: Project) {
        // Add runtime dependency for buddyDebug variant.
        // buddyDebugImplementation is created by AGP during variant finalization and may not
        // exist yet at this point; use whenObjectAdded as a fallback.
        fun addDeviceLib() {
            project.logger.lifecycle("compose-buddy: adding buddyDebugImplementation -> $DEVICE_LIB")
            project.dependencies.add("buddyDebugImplementation", DEVICE_LIB)
        }
        if (project.configurations.findByName("buddyDebugImplementation") != null) {
            addDeviceLib()
        } else {
            project.logger.lifecycle("compose-buddy: buddyDebugImplementation not found yet, registering whenObjectAdded")
            project.configurations.whenObjectAdded { config ->
                if (config.name == "buddyDebugImplementation") addDeviceLib()
            }
        }

        // Auto-apply KSP if on classpath, otherwise warn
        try {
            project.pluginManager.apply("com.google.devtools.ksp")
        } catch (e: Exception) {
            project.logger.warn(
                "compose-buddy: KSP plugin not on classpath. " +
                    "Add id(\"com.google.devtools.ksp\") to your plugins block."
            )
        }

        // KSP creates kspBuddyDebug lazily via the variant API — it may not exist yet at
        // afterEvaluate time. Use whenObjectAdded to register the processor whenever
        // the configuration appears, and also try immediately in case it already exists.
        // Also add directly to kspBuddyDebugKotlinProcessorClasspath (KSP's internal classpath
        // that the task actually resolves) in case it doesn't inherit from kspBuddyDebug.
        val kspConfigs = project.configurations.map { it.name }.filter { it.startsWith("ksp") }
        project.logger.lifecycle("compose-buddy: ksp configs at afterEvaluate: $kspConfigs")

        fun addKspProcessor(configName: String) {
            project.logger.lifecycle("compose-buddy: adding $configName -> $KSP_PROCESSOR")
            project.dependencies.add(configName, KSP_PROCESSOR)
        }

        for (configName in listOf("kspBuddyDebug", "kspBuddyDebugKotlinProcessorClasspath")) {
            if (project.configurations.findByName(configName) != null) {
                addKspProcessor(configName)
            } else {
                project.configurations.whenObjectAdded { config ->
                    if (config.name == configName) addKspProcessor(configName)
                }
            }
        }

        registerTasks(project)
    }

    private fun registerTasks(project: Project) {
        project.tasks.register("buddyPreviewDeploy", BuddyPreviewDeployTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "Build, install, and launch a @Preview on device via BuddyPreviewActivity"
            task.apkDir.set(project.layout.buildDirectory.dir("outputs/apk/buddyDebug"))
            task.dependsOn("assembleBuddyDebug")
        }

        project.tasks.register("buddyPreviewList", BuddyPreviewListTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "List all @Preview composables available in the buddyDebug variant as JSON"
            task.dependsOn("kspBuddyDebugKotlin")
            task.registrySourceFile.set(
                project.layout.buildDirectory.file(
                    "generated/ksp/buddyDebug/kotlin/dev/mikepenz/composebuddy/device/BuddyPreviewRegistryImpl.kt"
                )
            )
        }
    }
}
