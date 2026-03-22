package dev.mikepenz.composebuddy.gradle.device.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Prints available @Preview composables to stdout as JSON by parsing
 * the KSP-generated BuddyPreviewRegistryImpl source file.
 */
abstract class BuddyPreviewListTask : DefaultTask() {

    @get:Internal
    abstract val registrySourceFile: RegularFileProperty

    @TaskAction
    fun listPreviews() {
        val file = registrySourceFile.orNull?.asFile
        if (file == null || !file.exists()) {
            println("[]")
            logger.warn("BuddyPreviewRegistryImpl.kt not found. Run kspBuddyDebugKotlin first.")
            return
        }

        val content = file.readText()

        val pattern = Regex("""fqn\s*=\s*"([^"]+)"[^)]*?displayName\s*=\s*"([^"]+)"""")
        val entries = pattern.findAll(content).map { match ->
            val fqn = match.groupValues[1]
            val displayName = match.groupValues[2]
            """{"fqn":"$fqn","displayName":"$displayName"}"""
        }.toList()

        if (entries.isEmpty()) {
            println("[]")
        } else {
            println(entries.joinToString(",\n  ", prefix = "[\n  ", postfix = "\n]"))
        }
    }
}
