package dev.mikepenz.composebuddy.renderer.worker

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderError
import dev.mikepenz.composebuddy.core.model.RenderErrorType
import dev.mikepenz.composebuddy.core.model.RenderResult
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Platform-agnostic base class for forked JVM renderers.
 * Manages the child process lifecycle, JSON-over-stdio protocol, and error handling.
 *
 * Subclasses customize rendering via [workerClassName], [workerLabel],
 * [buildClasspath], [buildJvmArgs], [createRenderRequest], and [mapResultConfig].
 */
abstract class BaseForkedRenderer(
    protected val projectClasspath: List<File>,
    protected val outputDir: File,
    protected val defaultDensityDpi: Int,
    protected val defaultWidthPx: Int,
    protected val defaultHeightPx: Int,
) {
    /** Fully qualified class name of the worker's main class. */
    protected abstract val workerClassName: String

    /** Human-readable label used in log messages and thread names. */
    protected abstract val workerLabel: String

    /** Build the classpath for the worker JVM. */
    protected abstract fun buildClasspath(): List<String>

    /** Additional JVM arguments (e.g., -Djava.library.path, -XX flags). */
    protected open fun buildJvmArgs(): List<String> = emptyList()

    /** Additional environment variables for the worker process. */
    protected open fun buildEnvironment(): Map<String, String> = emptyMap()

    /**
     * Extracts an embedded worker JAR from resources to the cache directory.
     * Uses SHA-256 content hash for cache invalidation.
     * Returns the absolute path to the cached JAR.
     */
    protected fun extractWorkerJar(resourceName: String): String {
        val cacheDir = File(System.getProperty("user.home"), ".compose-buddy/cache/workers")
        cacheDir.mkdirs()

        val resource = javaClass.classLoader.getResourceAsStream("workers/$resourceName")
        if (resource == null) {
            Logger.w { "Worker JAR resource not found: workers/$resourceName, falling back to classpath" }
            return "" // Caller should handle empty string as "not available"
        }

        val tempFile = File.createTempFile("worker-", ".jar", cacheDir)
        try {
            resource.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(tempFile.readBytes())
                .joinToString("") { "%02x".format(it) }
                .take(12)
            val cached = File(cacheDir, "$resourceName-$hash.jar")
            if (cached.exists()) {
                tempFile.delete()
            } else {
                // Clean old versions of this worker
                cacheDir.listFiles()?.filter { it.name.startsWith(resourceName) && it != cached }
                    ?.forEach { it.delete() }
                tempFile.renameTo(cached)
            }
            Logger.d { "Worker JAR: ${cached.absolutePath}" }
            return cached.absolutePath
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /** Create a render request from a preview and configuration. */
    protected abstract fun createRenderRequest(preview: Preview, configuration: RenderConfiguration): WorkerProtocol.RenderRequest

    /** Create the init request sent at worker startup. */
    protected open fun createInitRequest(): WorkerProtocol.RenderRequest = WorkerProtocol.RenderRequest(
        previewFqn = "__init__",
        outputDir = outputDir.absolutePath,
        widthPx = defaultWidthPx,
        heightPx = defaultHeightPx,
        densityDpi = defaultDensityDpi,
    )

    /** Map the render response config back to a RenderConfiguration. */
    protected abstract fun mapResultConfig(
        configuration: RenderConfiguration,
        request: WorkerProtocol.RenderRequest,
    ): RenderConfiguration

    private val json = Json { ignoreUnknownKeys = true }
    private var workerProcess: Process? = null
    private var workerWriter: BufferedWriter? = null
    private var workerReader: BufferedReader? = null
    @Volatile private var workerStarted = false

    fun start() {
        if (workerStarted) return
        startWorkerProcess()
        workerStarted = true
    }

    fun stop() {
        if (!workerStarted) return
        try {
            workerWriter?.write("\n")
            workerWriter?.flush()
            val exited = workerProcess?.waitFor(5, TimeUnit.SECONDS) ?: true
            if (!exited) {
                Logger.d { "Worker did not exit within 5s, forcing termination" }
            }
        } catch (e: Exception) {
            Logger.d { "Worker stop: ${e::class.simpleName}: ${e.message}" }
        }
        try { workerWriter?.close() } catch (_: Exception) {}
        try { workerReader?.close() } catch (_: Exception) {}
        workerProcess?.destroyForcibly()
        workerProcess = null
        workerWriter = null
        workerReader = null
        workerStarted = false
    }

    fun render(preview: Preview, configuration: RenderConfiguration): RenderResult {
        val startTime = System.currentTimeMillis()

        if (!workerStarted) start()

        val request = createRenderRequest(preview, configuration)

        return try {
            val response = sendRequest(request)
            if (response.success) {
                RenderResult(
                    previewName = preview.fullyQualifiedName,
                    configuration = mapResultConfig(configuration, request),
                    imagePath = response.imagePath,
                    imageWidth = response.imageWidth,
                    imageHeight = response.imageHeight,
                    renderDurationMs = response.durationMs,
                    hierarchy = response.hierarchy,
                )
            } else {
                RenderResult(
                    previewName = preview.fullyQualifiedName,
                    configuration = configuration,
                    renderDurationMs = response.durationMs,
                    error = RenderError(
                        type = RenderErrorType.COMPOSITION_ERROR,
                        message = response.error ?: "Unknown error",
                    ),
                )
            }
        } catch (e: Exception) {
            RenderResult(
                previewName = preview.fullyQualifiedName,
                configuration = configuration,
                renderDurationMs = System.currentTimeMillis() - startTime,
                error = RenderError(
                    type = RenderErrorType.UNKNOWN,
                    message = "Worker error: ${e.message}",
                    stackTrace = e.stackTraceToString(),
                ),
            )
        }
    }

    private fun startWorkerProcess() {
        val javaHome = System.getProperty("java.home")
        val javaBin = File(javaHome, "bin/java").absolutePath

        val classpathEntries = buildClasspath()
        val fullClasspath = classpathEntries.distinct().joinToString(File.pathSeparator)

        // Diagnostic: detect Compose runtime on classpath
        // JAR names vary: "compose-runtime-1.x.jar", "runtime-desktop-1.x.jar", "runtime-android-1.x.jar"
        val composeRuntimeJars = classpathEntries.filter { entry ->
            val name = File(entry).name
            name.contains("compose-runtime") || name.contains("compose_runtime") ||
                (name.startsWith("runtime-") && (name.contains("-desktop-") || name.contains("-android-") || name.contains("-jvm-")))
        }
        if (composeRuntimeJars.isNotEmpty()) {
            Logger.d { "Compose runtime on classpath: ${composeRuntimeJars.joinToString { File(it).name }}" }
        } else {
            Logger.d { "No compose-runtime detected on classpath (${classpathEntries.size} entries)" }
        }

        val command = mutableListOf(javaBin, "-cp", fullClasspath, "-Xmx2g")
        command.addAll(buildJvmArgs())
        command.add(workerClassName)

        Logger.i { "Starting $workerLabel (${classpathEntries.size} classpath entries)" }

        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(false)
        val env = buildEnvironment()
        if (env.isNotEmpty()) {
            processBuilder.environment().putAll(env)
        }
        val process = processBuilder.start()
        workerProcess = process
        workerWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
        workerReader = BufferedReader(InputStreamReader(process.inputStream))

        val recentStderr = java.util.Collections.synchronizedList(mutableListOf<String>())
        Thread {
            try {
                process.errorStream.bufferedReader().forEachLine { line ->
                    if (line.isNotBlank() && !line.startsWith("WARNING:") && !line.contains("BasicStyleResourceItem")) {
                        Logger.d { "worker: $line" }
                        synchronized(recentStderr) {
                            recentStderr.add(line)
                            if (recentStderr.size > 50) recentStderr.removeFirst()
                        }
                    }
                }
            } catch (_: Exception) { /* stream closed on process termination */ }
        }.apply { isDaemon = true; name = "ComposeBuddy-${workerLabel}-Stderr" }.start()

        val initRequest = createInitRequest()
        val writer = workerWriter ?: throw IllegalStateException("Worker process failed to start")
        val reader = workerReader ?: throw IllegalStateException("Worker process failed to start")
        writer.write(json.encodeToString(WorkerProtocol.RenderRequest.serializer(), initRequest))
        writer.newLine()
        writer.flush()

        val readyLine = reader.readLine()
        if (readyLine == "READY") {
            Logger.i { "$workerLabel ready" }
        } else {
            // Give stderr thread a moment to capture output
            Thread.sleep(200)
            val stderrTail = synchronized(recentStderr) { recentStderr.takeLast(20) }

            // Build diagnostic info for troubleshooting
            val diag = buildString {
                appendLine("$workerLabel init failed")
                appendLine("  Response: $readyLine")
                appendLine("  Classpath entries: ${classpathEntries.size}")
                classpathEntries.take(20).forEachIndexed { i, cp ->
                    appendLine("    [$i] $cp")
                }
                if (classpathEntries.size > 20) appendLine("    ... and ${classpathEntries.size - 20} more")
                if (stderrTail.isNotEmpty()) {
                    appendLine("  Worker stderr:")
                    stderrTail.forEach { appendLine("    $it") }
                }
            }
            Logger.e { diag }
            throw RuntimeException(diag)
        }
    }

    private fun sendRequest(request: WorkerProtocol.RenderRequest): WorkerProtocol.RenderResponse {
        val writer = workerWriter ?: throw IllegalStateException("Worker not started")
        val reader = workerReader ?: throw IllegalStateException("Worker not started")

        try {
            writer.write(json.encodeToString(WorkerProtocol.RenderRequest.serializer(), request))
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            // Worker process died — restart and retry once
            Logger.w { "Worker stream closed, restarting: ${e.message}" }
            restartWorker()
            val w = workerWriter ?: throw IllegalStateException("Worker restart failed")
            val r = workerReader ?: throw IllegalStateException("Worker restart failed")
            w.write(json.encodeToString(WorkerProtocol.RenderRequest.serializer(), request))
            w.newLine()
            w.flush()
            val line = r.readLine() ?: throw RuntimeException("Worker terminated after restart")
            return json.decodeFromString(WorkerProtocol.RenderResponse.serializer(), line)
        }

        val responseLine = reader.readLine()
            ?: throw RuntimeException("Worker terminated unexpectedly")

        return json.decodeFromString(WorkerProtocol.RenderResponse.serializer(), responseLine)
    }

    private fun restartWorker() {
        Logger.i { "Restarting $workerLabel..." }
        try { workerWriter?.close() } catch (_: Exception) {}
        try { workerReader?.close() } catch (_: Exception) {}
        workerProcess?.destroyForcibly()
        workerProcess = null
        workerWriter = null
        workerReader = null
        workerStarted = false
        startWorkerProcess()
        workerStarted = true
    }

    companion object {
        fun findAndroidJar(): File? {
            val sdkRoot = System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
                ?: listOf(
                    File(System.getProperty("user.home"), "Library/Android/sdk"),
                    File(System.getProperty("user.home"), "Android/Sdk"),
                    File(System.getProperty("user.home"), "Development/android/sdk"),
                ).firstOrNull { it.exists() }?.absolutePath

            if (sdkRoot == null) {
                Logger.w { "Android SDK not found" }
                return null
            }

            val platformsDir = File(sdkRoot, "platforms")
            if (!platformsDir.exists()) return null

            return platformsDir.listFiles()
                ?.filter { it.isDirectory && File(it, "android.jar").exists() }
                ?.sortedByDescending { it.name }
                ?.firstOrNull()
                ?.let { File(it, "android.jar") }
                ?.also { Logger.d { "Using android.jar: ${it.absolutePath}" } }
        }
    }
}
