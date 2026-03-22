package dev.mikepenz.composebuddy.renderer.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Configures Kermit logging for Compose Buddy.
 * All logging goes to stderr + file. stdout is never used (reserved for JSON output).
 */
object LoggingSetup {

    private var fileLogWriter: FileLogWriter? = null

    /** Path to the current log file, or empty if logging is not yet configured. */
    val logFilePath: String get() = fileLogWriter?.logFilePath ?: ""

    fun configure(verbose: Boolean = false): FileLogWriter {
        val fileWriter = FileLogWriter()
        fileLogWriter = fileWriter

        val minSeverity = if (verbose) Severity.Debug else Severity.Info

        // Stderr writer — never writes to stdout (which is for JSON protocol)
        val stderrWriter = object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                System.err.println("${severity.name}: ($tag) $message")
                throwable?.printStackTrace(System.err)
            }
        }

        Logger.setLogWriters(stderrWriter, fileWriter)
        Logger.setMinSeverity(minSeverity)
        Logger.setTag("compose-buddy")

        Logger.i { "Logging initialized — file: ${fileWriter.logFilePath}" }
        return fileWriter
    }

    fun shutdown() {
        fileLogWriter?.close()
        fileLogWriter = null
    }
}
