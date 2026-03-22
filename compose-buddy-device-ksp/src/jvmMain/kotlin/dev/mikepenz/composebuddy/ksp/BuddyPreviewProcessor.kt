package dev.mikepenz.composebuddy.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

class BuddyPreviewProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()
        processed = true

        val previews = PreviewAnnotationScanner.scan(resolver)
        logger.warn("BuddyPreviewProcessor: found ${previews.size} @Preview functions")

        RegistryGenerator.generate(codeGenerator, previews)
        WrapperGenerator.generate(codeGenerator, previews)

        return emptyList()
    }
}
