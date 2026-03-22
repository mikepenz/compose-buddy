package dev.mikepenz.composebuddy.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class PluginRegistrationTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin applies and registers KMP tasks`() {
        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
        """.trimIndent())

        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                id("dev.mikepenz.composebuddy")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--group=compose buddy")
            .build()

        assertTrue(result.output.contains("renderPreviews"))
        assertTrue(result.output.contains("inspectPreviews"))
    }

    @Test
    fun `plugin extension is configurable`() {
        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
        """.trimIndent())

        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                id("dev.mikepenz.composebuddy")
            }
            composeBuddy {
                maxPreviewParameterValues.set(5)
                defaultDevice.set("id:pixel_5")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }
}
