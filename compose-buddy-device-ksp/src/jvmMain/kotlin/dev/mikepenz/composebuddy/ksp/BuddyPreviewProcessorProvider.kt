package dev.mikepenz.composebuddy.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class BuddyPreviewProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        environment.logger.warn("BuddyPreviewProcessorProvider: creating processor")
        return BuddyPreviewProcessor(environment.codeGenerator, environment.logger)
    }
}
