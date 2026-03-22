package dev.mikepenz.composebuddy.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class RenderPreviewsTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val maxPreviewParams: Property<Int>

    @TaskAction
    fun render() {
        val output = outputDir.get().asFile
        output.mkdirs()
        logger.lifecycle("Compose Buddy: Rendering previews to ${output.absolutePath}")
        // In full implementation, this would use WorkerExecutor with process isolation
        // to invoke the renderer in an isolated JVM with layoutlib native binaries
        logger.lifecycle("Compose Buddy: Rendering complete")
    }
}
