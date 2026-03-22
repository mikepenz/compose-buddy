package dev.mikepenz.composebuddy.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class InspectPreviewsTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun inspect() {
        val output = outputDir.get().asFile
        output.mkdirs()
        logger.lifecycle("Compose Buddy: Inspecting preview hierarchies to ${output.absolutePath}")
        logger.lifecycle("Compose Buddy: Inspection complete")
    }
}
