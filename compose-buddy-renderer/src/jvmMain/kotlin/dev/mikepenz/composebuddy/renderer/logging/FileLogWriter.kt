package dev.mikepenz.composebuddy.renderer.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Kermit LogWriter that appends logs to a file for post-run analysis.
 * Log file location: ~/.compose-buddy/logs/compose-buddy-<timestamp>.log
 *
 * Use the primary constructor to create a new log file, or [forFile] to
 * append to an existing log file (e.g., from a parent process).
 */
class FileLogWriter private constructor(
    private val logFile: File,
    writeHeader: Boolean,
) : LogWriter() {

    private val writer: PrintWriter

    init {
        logFile.parentFile?.mkdirs()
        writer = PrintWriter(FileWriter(logFile, true), true)
        if (writeHeader) {
            writer.println("=== Compose Buddy Log — ${LocalDateTime.now()} ===")
            writer.println()
        }
    }

    /** Creates a new log file in [logDir] with a timestamped name. */
    constructor(
        logDir: File = File(System.getProperty("user.home"), ".compose-buddy/logs"),
    ) : this(
        logFile = newLogFile(logDir),
        writeHeader = true,
    )

    val logFilePath: String get() = logFile.absolutePath

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        writer.println("[$time] ${severity.name.padEnd(7)} [$tag] $message")
        if (throwable != null) {
            throwable.printStackTrace(writer)
        }
    }

    fun close() {
        writer.println()
        writer.println("=== Log End ===")
        writer.close()
    }

    companion object {
        /** Appends to an existing log file (e.g., one created by the parent process). */
        fun forFile(path: String): FileLogWriter = FileLogWriter(File(path), writeHeader = false)

        private fun newLogFile(logDir: File): File {
            logDir.mkdirs()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            return File(logDir, "compose-buddy-$timestamp.log")
        }
    }
}
