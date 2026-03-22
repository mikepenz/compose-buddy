package dev.mikepenz.composebuddy.cli

import java.io.File

object ProcessRunner {

    data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

    /**
     * Run a Gradle task via the project's wrapper script.
     *
     * @param projectDir  root of the Gradle project (where gradlew lives)
     * @param args        Gradle arguments (task names, flags, etc.)
     * @param streamOutput when `true` the child process stdout/stderr are
     *                     forwarded to the current process' console in real time;
     *                     they are still captured and returned in [ProcessResult].
     */
    fun runGradle(projectDir: File, vararg args: String, streamOutput: Boolean = false): ProcessResult {
        val wrapper = if (System.getProperty("os.name").lowercase().contains("win")) {
            "gradlew.bat"
        } else {
            "./gradlew"
        }
        val cmd = listOf(wrapper) + args.toList()
        return execute(cmd, workingDir = projectDir, streamOutput = streamOutput)
    }

    /**
     * Run an `adb` command, optionally targeting a specific device via [serial].
     */
    fun runAdb(serial: String? = null, vararg args: String): ProcessResult {
        val cmd = buildList {
            add("adb")
            if (!serial.isNullOrBlank()) {
                add("-s")
                add(serial)
            }
            addAll(args.toList())
        }
        return execute(cmd)
    }

    /**
     * Run an arbitrary command and return the result.
     */
    fun execute(
        command: List<String>,
        workingDir: File? = null,
        streamOutput: Boolean = false,
    ): ProcessResult {
        val pb = ProcessBuilder(command).apply {
            workingDir?.let { directory(it) }
        }

        if (streamOutput) {
            pb.redirectErrorStream(true)
            val process = pb.start()
            val output = StringBuilder()
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    println(line)
                    output.appendLine(line)
                }
            }
            val exit = process.waitFor()
            return ProcessResult(exitCode = exit, stdout = output.toString(), stderr = "")
        }

        val process = pb.start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        return ProcessResult(exitCode = exit, stdout = stdout, stderr = stderr)
    }
}
