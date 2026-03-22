package dev.mikepenz.composebuddy.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import dev.mikepenz.composebuddy.core.VERSION
import dev.mikepenz.composebuddy.renderer.logging.LoggingSetup

class ComposeBuddyCli : CliktCommand(name = "compose-buddy") {
    override fun help(context: Context) = "Render and inspect Compose @Preview composables outside the IDE"

    init {
        versionOption(VERSION)
    }

    private val verbose by option("--verbose", "-v", help = "Enable verbose logging").flag()

    override fun run() {
        LoggingSetup.configure(verbose = verbose)
    }
}

fun main(args: Array<String>) {
    try {
        ComposeBuddyCli()
            .subcommands(
                RenderCommand(), InspectCommand(), AnalyzeCommand(), ServeCommand(), InstallCommand(),
                DeviceCommand().subcommands(DeviceConnectCommand()),
            )
            .main(args)
    } finally {
        LoggingSetup.shutdown()
    }
}
