package dev.mikepenz.composebuddy.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratorTest {

    private fun previewInfo(
        packageName: String,
        functionName: String,
        containingFile: String,
        widthDp: Int = -1,
        heightDp: Int = -1,
        name: String = "",
        group: String = "",
        locale: String = "",
        fontScale: Float = 1f,
        uiMode: Int = 0,
        showBackground: Boolean = false,
        backgroundColor: Long = 0L,
        apiLevel: Int = -1,
    ) = PreviewInfo(
        packageName = packageName,
        functionName = functionName,
        containingFile = containingFile,
        configs = listOf(
            PreviewConfigInfo(
                name = name, group = group, widthDp = widthDp, heightDp = heightDp,
                locale = locale, fontScale = fontScale, uiMode = uiMode,
                showBackground = showBackground, backgroundColor = backgroundColor, apiLevel = apiLevel,
            )
        ),
    )

    @Test
    fun `RegistryGenerator produces registry with single preview`() {
        val previews = listOf(
            previewInfo(packageName = "com.example", functionName = "MyPreview", containingFile = "MyComposables.kt", widthDp = 360, heightDp = 640)
        )
        val output = captureOutput { codeGen ->
            RegistryGenerator.generate(codeGen, previews)
        }
        assertTrue(output.contains("com.example.MyComposablesKt.MyPreview"), "FQN missing: $output")
        assertTrue(output.contains("BuddyPreviewRegistryImpl"), "Registry object missing: $output")
        assertTrue(output.contains("mapOf("), "Should use mapOf: $output")
    }

    @Test
    fun `RegistryGenerator produces emptyMap for no previews`() {
        val output = captureOutput { codeGen ->
            RegistryGenerator.generate(codeGen, emptyList())
        }
        assertTrue(output.contains("emptyMap()"), "Should use emptyMap(): $output")
    }

    @Test
    fun `RegistryGenerator emits multiple configs for multi-preview`() {
        val previews = listOf(
            PreviewInfo(
                packageName = "com.example",
                functionName = "MyPreview",
                containingFile = "MyComposables.kt",
                configs = listOf(
                    PreviewConfigInfo(name = "Light", group = "", widthDp = -1, heightDp = -1, locale = "", fontScale = 1f, uiMode = 0, showBackground = false, backgroundColor = 0L, apiLevel = -1),
                    PreviewConfigInfo(name = "Dark", group = "", widthDp = -1, heightDp = -1, locale = "", fontScale = 1f, uiMode = 0x20, showBackground = false, backgroundColor = 0L, apiLevel = -1),
                ),
            )
        )
        val output = captureOutput { codeGen ->
            RegistryGenerator.generate(codeGen, previews)
        }
        assertTrue(output.contains("com.example.MyComposablesKt.MyPreview"), "FQN missing: $output")
        // Both configs should appear — check there are two PreviewConfig(...) blocks
        val configCount = Regex("PreviewConfig\\(").findAll(output).count()
        assertTrue(configCount == 2, "Expected 2 PreviewConfig entries, found $configCount: $output")
    }

    @Test
    fun `WrapperGenerator produces instrumented wrapper`() {
        val previews = listOf(
            previewInfo(packageName = "com.example", functionName = "GreetingPreview", containingFile = "Greetings.kt")
        )
        val output = captureOutput { codeGen ->
            WrapperGenerator.generate(codeGen, previews)
        }
        assertTrue(output.contains("GreetingPreviewInstrumented"), "Instrumented function missing: $output")
        assertTrue(output.contains("BuddyInstrumentationWrapper"), "Wrapper call missing: $output")
        assertTrue(output.contains("package com.example"), "Package missing: $output")
    }

    @Test
    fun `WrapperGenerator skips generation for empty previews`() {
        val output = captureOutput { codeGen ->
            WrapperGenerator.generate(codeGen, emptyList())
        }
        assertTrue(output.isEmpty(), "Should produce no output for empty previews: $output")
    }

    @Test
    fun `PreviewInfo fqn uses file name without extension`() {
        val info = previewInfo(packageName = "com.example", functionName = "Foo", containingFile = "Bar.kt")
        kotlin.test.assertEquals("com.example.BarKt.Foo", info.fqn)
    }

    @Test
    fun `PreviewInfo displayName falls back to function name`() {
        val info = previewInfo(packageName = "com.example", functionName = "Foo", containingFile = "Bar.kt", name = "")
        kotlin.test.assertEquals("Foo", info.displayName)
    }

    @Test
    fun `PreviewInfo displayName uses name when set`() {
        val info = previewInfo(packageName = "com.example", functionName = "Foo", containingFile = "Bar.kt", name = "Custom Name")
        kotlin.test.assertEquals("Custom Name", info.displayName)
    }

    private fun captureOutput(block: (CodeGenerator) -> Unit): String {
        val outputs = mutableMapOf<String, ByteArrayOutputStream>()
        val codeGen = FakeCodeGenerator(outputs)
        block(codeGen)
        return outputs.values.joinToString("\n") { it.toString(Charsets.UTF_8) }
    }

    private class FakeCodeGenerator(private val outputs: MutableMap<String, ByteArrayOutputStream>) : CodeGenerator {
        override val generatedFile: Collection<java.io.File> = emptyList()

        override fun associate(
            sources: List<com.google.devtools.ksp.symbol.KSFile>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) {
        }

        override fun createNewFile(
            dependencies: Dependencies,
            packageName: String,
            fileName: String,
            extensionName: String,
        ): OutputStream {
            val key = "$packageName.$fileName"
            val baos = ByteArrayOutputStream()
            outputs[key] = baos
            return baos
        }

        override fun createNewFileByPath(dependencies: Dependencies, path: String, extensionName: String): OutputStream {
            val baos = ByteArrayOutputStream()
            outputs[path] = baos
            return baos
        }

        override fun associateByPath(
            sources: List<com.google.devtools.ksp.symbol.KSFile>,
            path: String,
            extensionName: String,
        ) {
        }

        override fun associateWithClasses(
            classes: List<com.google.devtools.ksp.symbol.KSClassDeclaration>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) {
        }
    }
}
