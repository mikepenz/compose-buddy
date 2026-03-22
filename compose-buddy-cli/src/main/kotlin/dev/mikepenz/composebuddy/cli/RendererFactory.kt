package dev.mikepenz.composebuddy.cli

import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer
import dev.mikepenz.composebuddy.renderer.AutoFallbackRenderer
import dev.mikepenz.composebuddy.renderer.android.LayoutlibRenderer
import dev.mikepenz.composebuddy.renderer.android.paparazzi.PaparazziLayoutlibRenderer
import dev.mikepenz.composebuddy.renderer.desktop.DesktopRenderer
import java.io.File

/**
 * Shared renderer creation logic for CLI commands.
 * Each renderer receives only the classpath for its target platform (JVM/Desktop or Android)
 * to prevent mixing artifacts from different Compose target platforms.
 */
object RendererFactory {

    fun create(
        rendererType: String,
        desktopClasspath: List<File>,
        androidClasspath: List<File>,
        outputDir: File,
        density: Int?,
        projectResourceDirs: List<File> = emptyList(),
    ): PreviewRenderer {
        return when (rendererType) {
            "desktop" -> createDesktop(desktopClasspath, outputDir, density)
            "android-direct" -> createAndroidDirect(androidClasspath, outputDir, density, projectResourceDirs)
            "android" -> createAndroid(androidClasspath, outputDir, density, projectResourceDirs)
            else -> AutoFallbackRenderer(
                primary = createDesktop(desktopClasspath, outputDir, density),
                fallbackFactory = { createAndroidDirect(androidClasspath, outputDir, density, projectResourceDirs) },
            )
        }
    }

    private fun createDesktop(classpath: List<File>, outputDir: File, density: Int?) =
        DesktopRenderer(
            outputDir = outputDir,
            defaultDensityDpi = density ?: 160,
            projectClasspath = classpath,
        )

    private fun createAndroid(classpath: List<File>, outputDir: File, density: Int?, resourceDirs: List<File>) =
        PaparazziLayoutlibRenderer(
            outputDir = outputDir,
            defaultDensityDpi = density ?: 420,
            projectResourceDirs = resourceDirs,
            projectClasspath = classpath,
        )

    private fun createAndroidDirect(classpath: List<File>, outputDir: File, density: Int?, resourceDirs: List<File>) =
        LayoutlibRenderer(
            outputDir = outputDir,
            defaultDensityDpi = density ?: 420,
            projectResourceDirs = resourceDirs,
            projectClasspath = classpath,
        )
}
