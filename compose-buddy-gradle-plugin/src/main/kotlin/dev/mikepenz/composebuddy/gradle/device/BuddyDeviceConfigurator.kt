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
                configureAndroidApp(project, extension, android)
            }
        }
    }

    private fun configureAndroidApp(
        project: Project,
        extension: ComposeBuddyExtension,
        android: ApplicationExtension,
    ) {
        // Apply per-dimension flavor fallbacks declared in the extension.
        val fallbacks = extension.deviceFlavorFallbacks.get()
        if (fallbacks.isNotEmpty()) {
            android.productFlavors.all { flavor ->
                val dim = flavor.dimension ?: return@all
                val fallback = fallbacks[dim] ?: return@all
                if (flavor.name != fallback && fallback !in flavor.matchingFallbacks) {
                    flavor.matchingFallbacks += fallback
                    project.logger.lifecycle(
                        "compose-buddy: flavor '${flavor.name}' (dim=$dim) matchingFallbacks += $fallback"
                    )
                }
            }
        }

        // Enumerate productFlavors to compute per-variant config names.
        // If no flavors are declared, the single variant is "buddyDebug".
        val flavorNames = android.productFlavors.map { it.name }
        val variantNames: List<String> = if (flavorNames.isEmpty()) {
            listOf("buddyDebug")
        } else {
            flavorNames.map { flavor -> "${flavor}BuddyDebug" }
        }

        for (variantName in variantNames) {
            val cap = variantName.replaceFirstChar { it.uppercase() }
            val implCfg = "${variantName}Implementation"
            val kspCfg = "ksp$cap"
            val kspProcCfg = "${kspCfg}KotlinProcessorClasspath"

            addWhenAvailable(project, implCfg, DEVICE_LIB)
            addWhenAvailable(project, kspCfg, KSP_PROCESSOR)
            addWhenAvailable(project, kspProcCfg, KSP_PROCESSOR)
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

        registerTasks(project, flavorNames)
    }

    private fun addWhenAvailable(project: Project, configName: String, dependencyNotation: String) {
        fun add() {
            project.logger.lifecycle("compose-buddy: adding $configName -> $dependencyNotation")
            project.dependencies.add(configName, dependencyNotation)
        }
        if (project.configurations.findByName(configName) != null) {
            add()
        } else {
            project.configurations.whenObjectAdded { config ->
                if (config.name == configName) add()
            }
        }
    }

    private fun registerTasks(project: Project, flavorNames: List<String>) {
        project.tasks.register("buddyPreviewDeploy", BuddyPreviewDeployTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "Build, install, and launch a @Preview on device via BuddyPreviewActivity"
            // When flavors are present, the APK emits under build/outputs/apk/<flavor>/buddyDebug.
            // This aggregate task points at the no-flavor path for backwards compatibility;
            // the CLI resolves the correct per-flavor path at runtime.
            task.apkDir.set(project.layout.buildDirectory.dir("outputs/apk/buddyDebug"))
            task.dependsOn("assembleBuddyDebug")
        }

        // buddyPreviewList is source-level (KSP) so a single aggregate task suffices.
        // The KSP output lives under generated/ksp/<variant>/... — pick the first flavor's variant
        // (or the unflavored one) as the source of truth.
        val primaryVariant = if (flavorNames.isEmpty()) "buddyDebug" else "${flavorNames.first()}BuddyDebug"
        val cap = primaryVariant.replaceFirstChar { it.uppercase() }
        project.tasks.register("buddyPreviewList", BuddyPreviewListTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "List all @Preview composables available in the buddyDebug variant as JSON"
            task.dependsOn("ksp${cap}Kotlin")
            task.registrySourceFile.set(
                project.layout.buildDirectory.file(
                    "generated/ksp/$primaryVariant/kotlin/dev/mikepenz/composebuddy/device/BuddyPreviewRegistryImpl.kt"
                )
            )
        }
    }
}
