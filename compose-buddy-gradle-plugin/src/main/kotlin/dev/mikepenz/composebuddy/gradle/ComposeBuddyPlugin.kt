package dev.mikepenz.composebuddy.gradle

import dev.mikepenz.composebuddy.gradle.device.BuddyDeviceConfigurator
import dev.mikepenz.composebuddy.gradle.tasks.ResolveClasspathTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class ComposeBuddyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("composeBuddy", ComposeBuddyExtension::class.java)

        // Detect project type: AGP vs KMP-only
        val hasAgp = project.plugins.hasPlugin("com.android.application") ||
            project.plugins.hasPlugin("com.android.library") ||
            project.plugins.hasPlugin("com.android.dynamic-feature")

        if (hasAgp) {
            configureAndroidProject(project, extension)
        }
        // Always configure KMP targets if multiplatform plugin is applied,
        // even for Android projects — they may also have jvm/desktop targets
        // whose classpath differs from the Android variant classpath.
        val hasKmp = project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        if (hasKmp || !hasAgp) {
            configureKmpProject(project, extension)
        }

        // On-device preview support (buddyDebug variant + KSP)
        BuddyDeviceConfigurator.configure(project, extension)
    }

    private fun configureAndroidProject(project: Project, extension: ComposeBuddyExtension) {
        // For Android projects, register variant-aware tasks
        project.afterEvaluate {
            // Register tasks for each variant
            val variants = try {
                // Try application variants first
                val appExtension = project.extensions.findByName("android")
                if (appExtension != null) {
                    val method = appExtension::class.java.getMethod("getApplicationVariants")
                    @Suppress("UNCHECKED_CAST")
                    val variants = method.invoke(appExtension) as? Iterable<Any>
                    variants?.map { variant ->
                        val nameMethod = variant::class.java.getMethod("getName")
                        nameMethod.invoke(variant) as String
                    }?.toList()
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            } ?: listOf("debug", "release")

            for (variant in variants) {
                val capitalizedVariant = variant.replaceFirstChar { it.uppercase() }
                registerTasksForVariant(project, extension, variant, capitalizedVariant)
            }
        }
    }

    private fun configureKmpProject(project: Project, extension: ComposeBuddyExtension) {
        // Register classpath resolution for JVM-compatible targets
        project.afterEvaluate {
            val targets = listOf("jvm", "desktop")
            for (target in targets) {
                val compileTask = "compileKotlin${target.replaceFirstChar { it.uppercase() }}"
                if (project.tasks.findByName(compileTask) != null) {
                    project.tasks.register(
                        "composeBuddyResolveClasspath${target.replaceFirstChar { it.uppercase() }}",
                        ResolveClasspathTask::class.java,
                    ) { task ->
                        task.group = "compose buddy"
                        task.description = "Resolve classpath for $target target"
                        task.variantName.set(target)
                        task.outputFile.set(project.layout.buildDirectory.file("compose-buddy/$target/classpath.json"))
                        task.dependsOn(compileTask)
                    }
                }
            }
        }

        // For KMP-only projects, register non-variant tasks
        project.tasks.register("renderPreviews", RenderPreviewsTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "Render all @Preview composables"
            task.outputDir.set(project.layout.buildDirectory.dir("compose-buddy/images"))
            task.maxPreviewParams.set(extension.maxPreviewParameterValues)
        }

        project.tasks.register("inspectPreviews", InspectPreviewsTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "Export layout hierarchy for all @Preview composables"
            task.outputDir.set(project.layout.buildDirectory.dir("compose-buddy/hierarchy"))
        }
    }

    private fun registerTasksForVariant(
        project: Project,
        extension: ComposeBuddyExtension,
        variant: String,
        capitalizedVariant: String,
    ) {
        project.tasks.register("composeBuddyResolveClasspath$capitalizedVariant", ResolveClasspathTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "Resolve classpath for $variant"
            task.variantName.set(variant)
            task.outputFile.set(project.layout.buildDirectory.file("compose-buddy/$variant/classpath.json"))
            val compileTask = "compile${capitalizedVariant}Kotlin"
            if (project.tasks.findByName(compileTask) != null) {
                task.dependsOn(compileTask)
            }
        }

        project.tasks.register("renderPreviews$capitalizedVariant", RenderPreviewsTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "Render all @Preview composables for $variant"
            task.outputDir.set(project.layout.buildDirectory.dir("compose-buddy/$variant/images"))
            task.maxPreviewParams.set(extension.maxPreviewParameterValues)
        }

        project.tasks.register("inspectPreviews$capitalizedVariant", InspectPreviewsTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "Export layout hierarchy for $variant"
            task.outputDir.set(project.layout.buildDirectory.dir("compose-buddy/$variant/hierarchy"))
        }

        project.tasks.register("analyzePreviews$capitalizedVariant", AnalyzePreviewsTask::class.java) { task ->
            task.group = "compose buddy"
            task.description = "Run accessibility analysis for $variant"
            task.outputDir.set(project.layout.buildDirectory.dir("compose-buddy/$variant/analysis"))
        }
    }
}
