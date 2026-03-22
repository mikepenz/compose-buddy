package dev.mikepenz.composebuddy.gradle.device.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

abstract class BuddyPreviewDeployTask : DefaultTask() {

    @get:InputDirectory
    abstract val apkDir: DirectoryProperty

    @get:Input
    @get:Optional
    @get:Option(option = "preview", description = "Fully-qualified preview key (e.g. com.example.FooKt.MyPreview)")
    abstract val previewFqn: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "device", description = "ADB device serial (optional, uses first connected device if omitted)")
    abstract val deviceSerial: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "port", description = "WebSocket port (default 7890)")
    abstract val wsPort: Property<String>

    @TaskAction
    fun deploy() {
        val dir = apkDir.get().asFile
        val apk = dir.listFiles { f -> f.extension == "apk" }?.firstOrNull()
            ?: error("APK not found in ${dir.absolutePath}. Run assembleBuddyDebug first.")

        val serial = deviceSerial.orNull
        val port = wsPort.orNull?.toIntOrNull() ?: 7890
        val fqn = previewFqn.orNull
            ?: error("--preview is required. Run buddyPreviewList to see available previews.")

        val appId = deriveAppId(apk)

        adb(serial, "install", "-r", apk.absolutePath)
        adb(serial, "forward", "tcp:$port", "tcp:$port")
        adb(
            serial, "shell", "am", "start",
            "-n", "$appId.buddy/dev.mikepenz.composebuddy.device.BuddyPreviewActivity",
            "-e", "preview", fqn,
            "--ei", "port", port.toString(),
        )

        println("compose-buddy: device preview launched.")
        println("compose-buddy: WebSocket ready at ws://localhost:$port")
        println("compose-buddy: run 'compose-buddy device connect --port=$port' to inspect.")
    }

    private fun adb(serial: String?, vararg args: String) {
        val cmd = buildList {
            add("adb")
            if (!serial.isNullOrBlank()) { add("-s"); add(serial) }
            addAll(args.toList())
        }
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            error("adb command failed (exit $exit): ${cmd.joinToString(" ")}\n$output")
        }
        if (output.isNotBlank()) logger.lifecycle(output)
    }

    private fun deriveAppId(apk: java.io.File): String {
        return try {
            val aapt2 = findAapt2()
            val process = ProcessBuilder(
                aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml",
                apk.absolutePath,
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Regex("""A: package="([^"]+)"""").find(output)?.groupValues?.get(1)
                ?: error("Could not parse applicationId from APK manifest.\n$output")
        } catch (e: Exception) {
            error("Could not determine applicationId. Ensure aapt2 is on PATH.\n${e.message}")
        }
    }

    private fun findAapt2(): String {
        val sdkDir = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: return "aapt2"
        val buildTools = java.io.File("$sdkDir/build-tools")
        val latest = buildTools.listFiles()?.sortedDescending()?.firstOrNull()
        return "${latest?.absolutePath}/aapt2"
    }
}
