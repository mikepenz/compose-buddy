package dev.mikepenz.composebuddy.renderer.android

import java.io.File
import java.net.URI
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Downloads and caches layoutlib artifacts for standalone CLI use.
 * Resolves platform-specific native libraries from Google Maven.
 */
class LayoutlibProvider(
    private val cacheDir: File = File(System.getProperty("user.home"), ".compose-buddy/cache"),
    private val layoutlibVersion: String = DEFAULT_LAYOUTLIB_VERSION,
) {

    companion object {
        const val DEFAULT_LAYOUTLIB_VERSION = "16.2.1"
        private const val GOOGLE_MAVEN_URL = "https://dl.google.com/dl/android/maven2"

        fun detectPlatformClassifier(): String {
            val osName = System.getProperty("os.name").lowercase(Locale.US)
            return when {
                osName.startsWith("mac") -> {
                    val osArch = System.getProperty("os.arch").lowercase(Locale.US)
                    if (osArch.startsWith("x86")) "mac" else "mac-arm"
                }
                osName.startsWith("windows") -> "win"
                else -> "linux"
            }
        }

        fun getNativeLibSubdir(): String {
            return "${detectPlatformClassifier()}/lib64"
        }
    }

    data class LayoutlibPaths(
        val runtimeRoot: File,
        val resourcesRoot: File,
        val platformClassifier: String,
    ) {
        val buildProp: File get() = File(runtimeRoot, "build.prop")
        val fontsDir: File get() = File(runtimeRoot, "data/fonts")
        val icuData: File get() {
            val icuDir = File(runtimeRoot, "data/icu")
            // Auto-detect ICU data file (name changes across layoutlib versions, e.g., icudt76l.dat → icudt77l.dat)
            return icuDir.listFiles()?.firstOrNull { it.name.startsWith("icudt") && it.name.endsWith(".dat") }
                ?: File(icuDir, "icudt76l.dat") // fallback
        }
        val keyboardData: File get() = File(runtimeRoot, "data/keyboards/Generic.kcm")
        val hyphenData: File get() = File(runtimeRoot, "data/hyphen-data")
        val nativeLibDir: File get() = File(runtimeRoot, "data/${getNativeLibSubdir()}")
        val frameworkResDir: File get() = File(resourcesRoot, "res")
    }

    fun provide(): LayoutlibPaths {
        val classifier = detectPlatformClassifier()
        val runtimeRoot = provideRuntime(classifier)
        val resourcesRoot = provideResources()

        return LayoutlibPaths(
            runtimeRoot = runtimeRoot,
            resourcesRoot = resourcesRoot,
            platformClassifier = classifier,
        )
    }

    private fun provideRuntime(classifier: String): File {
        val targetDir = File(cacheDir, "layoutlib-runtime-$layoutlibVersion-$classifier")
        if (targetDir.exists() && File(targetDir, "build.prop").exists()) {
            return targetDir
        }

        val groupPath = "com/android/tools/layoutlib"
        val artifactId = "layoutlib-runtime"
        val url = "$GOOGLE_MAVEN_URL/$groupPath/$artifactId/$layoutlibVersion/$artifactId-$layoutlibVersion-$classifier.jar"

        println("Downloading layoutlib-runtime ($classifier)...")
        downloadAndExtract(url, targetDir)
        return targetDir
    }

    private fun provideResources(): File {
        val targetDir = File(cacheDir, "layoutlib-resources-$layoutlibVersion")
        if (targetDir.exists() && File(targetDir, "res").exists()) {
            return targetDir
        }

        val groupPath = "com/android/tools/layoutlib"
        val artifactId = "layoutlib-resources"
        val url = "$GOOGLE_MAVEN_URL/$groupPath/$artifactId/$layoutlibVersion/$artifactId-$layoutlibVersion.jar"

        println("Downloading layoutlib-resources...")
        downloadAndExtract(url, targetDir)
        return targetDir
    }

    private fun downloadAndExtract(url: String, targetDir: File) {
        targetDir.mkdirs()

        val connection = URI(url).toURL().openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000

        val inputStream = connection.getInputStream()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { os ->
                        zis.copyTo(os)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Make native libraries executable
        val nativeDir = File(targetDir, "data/${getNativeLibSubdir()}")
        if (nativeDir.exists()) {
            nativeDir.walk().filter { it.isFile }.forEach { it.setExecutable(true) }
        }
    }
}
