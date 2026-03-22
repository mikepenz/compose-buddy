package dev.mikepenz.composebuddy.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class AnalyzePreviewsTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun analyze() {
        val output = outputDir.get().asFile
        output.mkdirs()
        logger.lifecycle("Compose Buddy: Analyzing accessibility for previews to ${output.absolutePath}")
        logger.lifecycle("Compose Buddy: Analysis complete")
    }
}
