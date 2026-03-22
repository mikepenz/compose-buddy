package dev.mikepenz.composebuddy.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import dev.mikepenz.composebuddy.mcp.ComposeBuddyMcpServer
import dev.mikepenz.composebuddy.renderer.ResourceCollector
import dev.mikepenz.composebuddy.renderer.android.paparazzi.PaparazziLayoutlibRenderer
import kotlinx.coroutines.runBlocking
import java.io.File

class ServeCommand : CliktCommand(name = "serve") {
    override fun help(context: Context) = "Start MCP server for AI agent access"

    private val project by option("--project", help = "Gradle project root")
        .file(mustExist = true, canBeFile = false)
        .default(File("."))

    private val module by option("--module", help = "Gradle module (e.g., :app)")

    private val transport by option("--transport", help = "MCP transport: stdio")
        .default("stdio")

    override fun run() {
        val outputDir = File(project, "build/compose-buddy/mcp")

        val resourceCollector = ResourceCollector(project, module)
        val classpath = resourceCollector.resolveClasspath()
        val resourceConfig = resourceCollector.collect()
        val projectResourceDirs = resourceConfig.projectResourceDirs.map { File(it) }

        val renderer = PaparazziLayoutlibRenderer(
            outputDir = outputDir,
            projectResourceDirs = projectResourceDirs,
            projectClasspath = classpath,
        )
        val server = ComposeBuddyMcpServer(
            renderer = renderer,
            classpath = classpath,
        )

        runBlocking {
            server.runStdio()
        }
    }
}
