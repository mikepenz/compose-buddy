package dev.mikepenz.composebuddy.renderer

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.Preview
import java.io.File

/**
 * Discovers @Preview-annotated composable functions by scanning compiled bytecode.
 * Supports:
 * - Direct @Preview annotations on methods
 * - Multi-preview annotations (e.g., @PreviewLightDark) that contain multiple @Preview
 * - Recursive multi-preview resolution
 */
class PreviewDiscovery {

    companion object {
        private const val PREVIEW_ANNOTATION_FQN = "androidx.compose.ui.tooling.preview.Preview"
        private val PREVIEW_DESCRIPTOR = "Landroidx/compose/ui/tooling/preview/Preview;".toByteArray()
        private const val PREVIEW_CONTAINER_DESC = "Landroidx/compose/ui/tooling/preview/Preview\$Container;"
    }

    /** Cache of annotation class → list of @Preview configs found on it */
    private val multiPreviewCache = mutableMapOf<String, List<Map<String, String>>>()

    fun discover(classpath: List<File>, packageFilter: String? = null): List<Preview> {
        val previews = mutableListOf<Preview>()

        // Build map of all class files and collect scannable files
        val classFileMap = mutableMapOf<String, File>()
        val jarClassIndex = mutableMapOf<String, Pair<File, String>>() // className → (jarFile, entryName)
        val toScan = mutableListOf<Pair<String, File>>() // className → file
        for (entry in classpath) {
            if (entry.isDirectory) {
                entry.walk().filter { it.extension == "class" }.forEach { f ->
                    val cn = f.relativeTo(entry).path.removeSuffix(".class").replace(File.separatorChar, '.')
                    classFileMap[cn] = f
                    if (packageFilter == null || cn.startsWith(packageFilter)) {
                        toScan.add(cn to f)
                    }
                }
            } else if (entry.extension == "jar" && entry.exists()) {
                // Index JAR entries for multi-preview annotation resolution
                try {
                    java.util.jar.JarFile(entry).use { jar ->
                        for (jarEntry in jar.entries()) {
                            if (jarEntry.name.endsWith(".class")) {
                                val cn = jarEntry.name.removeSuffix(".class").replace('/', '.')
                                if (!classFileMap.containsKey(cn)) {
                                    jarClassIndex[cn] = entry to jarEntry.name
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Create a unified class byte reader that checks directories first, then JARs
        val classReader = ClassBytesReader(classFileMap, jarClassIndex)

        for ((className, classFile) in toScan) {
            scanClassFile(className, classFile, previews, classReader)
        }

        Logger.i { "discovered ${previews.size} @Preview functions from ${classpath.size} classpath entries" }
        return previews
    }

    /** Reads class bytes from directories or JARs. */
    private class ClassBytesReader(
        private val fileMap: Map<String, File>,
        private val jarIndex: Map<String, Pair<File, String>>,
    ) {
        fun readBytes(className: String): ByteArray? {
            fileMap[className]?.let { return it.readBytes() }
            val (jarFile, entryName) = jarIndex[className] ?: return null
            return try {
                java.util.jar.JarFile(jarFile).use { jar ->
                    jar.getInputStream(jar.getJarEntry(entryName)).readBytes()
                }
            } catch (_: Exception) { null }
        }
    }

    private fun scanClassFile(className: String, file: File, previews: MutableList<Preview>, classReader: ClassBytesReader) {
        val bytes = file.readBytes()
        val utf8s = extractConstantPoolStrings(bytes)

        // Check for direct @Preview or any annotation that might be a multi-preview
        val hasDirectPreview = containsBytes(bytes, PREVIEW_DESCRIPTOR)
        val annotationDescs = utf8s.values.filter { it.startsWith("L") && it.endsWith(";") && it != "Landroidx/compose/ui/tooling/preview/Preview;" }

        if (!hasDirectPreview && annotationDescs.none { isMultiPreview(it, classReader) }) return

        val methods = extractMethodsWithAnnotations(bytes, utf8s)

        val sourceFileName = utf8s.values.firstOrNull { it.endsWith(".kt") || it.endsWith(".java") }
        val packageDir = className.substringBeforeLast('.', "").replace('.', '/')
        val fullSourcePath = if (sourceFileName != null && packageDir.isNotEmpty()) "$packageDir/$sourceFileName" else sourceFileName ?: ""

        for (method in methods) {
            // Direct @Preview
            if (method.hasDirectPreview) {
                previews.add(Preview(
                    fullyQualifiedName = "$className.${method.name}",
                    fileName = fullSourcePath,
                    lineNumber = method.lineNumber,
                ))
            }

            // Multi-preview annotations — collapse into a single Preview per composable.
            // Theme/config variants are controlled at render time via settings, not per-annotation.
            // Only create a Preview if there wasn't already a direct @Preview for this method.
            if (!method.hasDirectPreview) {
                for (annotDesc in method.otherAnnotations) {
                    val previewConfigs = resolveMultiPreview(annotDesc, classReader)
                    if (previewConfigs.isNotEmpty()) {
                        // Use the first config for defaults (size, background, etc.)
                        // but ignore uiMode — that's controlled by the user.
                        val config = previewConfigs.first()
                        previews.add(Preview(
                            fullyQualifiedName = "$className.${method.name}",
                            fileName = fullSourcePath,
                            lineNumber = method.lineNumber,
                            name = config["name"] ?: "",
                            widthDp = config["widthDp"]?.toIntOrNull() ?: -1,
                            heightDp = config["heightDp"]?.toIntOrNull() ?: -1,
                            locale = config["locale"] ?: "",
                            fontScale = config["fontScale"]?.toFloatOrNull() ?: 1f,
                            uiMode = 0, // Always default — theme controlled by settings
                            device = config["device"] ?: "",
                            showBackground = config["showBackground"] == "true" || config["showBackground"] == "1",
                            backgroundColor = config["backgroundColor"]?.toLongOrNull() ?: 0L,
                            showSystemUi = config["showSystemUi"] == "true" || config["showSystemUi"] == "1",
                            apiLevel = config["apiLevel"]?.toIntOrNull() ?: -1,
                        ))
                        break // Only one Preview per method from multi-preview annotations
                    }
                }
            }
        }
    }

    /** Check if an annotation descriptor refers to a multi-preview annotation. */
    private fun isMultiPreview(annotDesc: String, classReader: ClassBytesReader): Boolean {
        return resolveMultiPreview(annotDesc, classReader).isNotEmpty()
    }

    /** Resolve a multi-preview annotation → list of @Preview configs. Cached. */
    private fun resolveMultiPreview(annotDesc: String, classReader: ClassBytesReader): List<Map<String, String>> {
        multiPreviewCache[annotDesc]?.let { return it }

        // Mark as being resolved to prevent infinite recursion
        multiPreviewCache[annotDesc] = emptyList()

        val className = annotDesc.removePrefix("L").removeSuffix(";").replace('/', '.')
        val bytes = classReader.readBytes(className) ?: return emptyList()

        // Quick check: does this class even reference @Preview?
        if (!containsBytes(bytes, PREVIEW_DESCRIPTOR)) return emptyList()

        val utf8s = extractConstantPoolStrings(bytes)
        val previewDesc = "Landroidx/compose/ui/tooling/preview/Preview;"
        val containerDesc = PREVIEW_CONTAINER_DESC

        val hasPreview = previewDesc in utf8s.values
        val hasContainer = containerDesc in utf8s.values

        if (!hasPreview && !hasContainer) {
            // Recursively check class-level annotations only (not all descriptors)
            val result = mutableListOf<Map<String, String>>()
            // Only recurse into descriptors that are actual annotation references on this class
            for (desc in utf8s.values.filter {
                it.startsWith("L") && it.endsWith(";") && it != annotDesc &&
                    !it.startsWith("Lkotlin/") && !it.startsWith("Ljava/") &&
                    !it.startsWith("Landroidx/compose/runtime/")
            }) {
                result.addAll(resolveMultiPreview(desc, classReader))
            }
            multiPreviewCache[annotDesc] = result
            return result
        }

        val configs = extractPreviewConfigsFromAnnotationClass(bytes, utf8s)
        Logger.d { "Multi-preview $className: found ${configs.size} configs: $configs" }
        multiPreviewCache[annotDesc] = configs
        return configs
    }

    /** Extract @Preview parameter configs from an annotation class file. */
    private fun extractPreviewConfigsFromAnnotationClass(bytes: ByteArray, utf8s: Map<Int, String>): List<Map<String, String>> {
        // The annotation class has RuntimeVisibleAnnotations with either:
        // - @Preview (single) or @Preview$Container(value=[...]) (multiple)
        // We need to parse the class-level annotations (after methods section)
        val configs = mutableListOf<Map<String, String>>()

        try {
            var pos = 8 // skip magic + version
            val cpCount = readU2(bytes, pos); pos += 2

            // Skip constant pool
            var i = 1
            while (i < cpCount && pos < bytes.size) {
                val tag = bytes[pos].toInt() and 0xFF; pos++
                when (tag) {
                    1 -> { val len = readU2(bytes, pos); pos += 2 + len }
                    3, 4 -> pos += 4; 5, 6 -> { pos += 8; i++ }
                    7, 8, 16, 19, 20 -> pos += 2; 9, 10, 11, 12, 17, 18 -> pos += 4; 15 -> pos += 3
                    else -> return configs
                }
                i++
            }

            // Skip access flags, this class, super class, interfaces
            pos += 6
            val interfaceCount = readU2(bytes, pos); pos += 2; pos += interfaceCount * 2

            // Skip fields and methods
            val fieldCount = readU2(bytes, pos); pos += 2
            repeat(fieldCount) { pos = skipMemberAtPos(bytes, pos) }
            val methodCount = readU2(bytes, pos); pos += 2
            repeat(methodCount) { pos = skipMemberAtPos(bytes, pos) }

            // Class attributes — look for RuntimeVisibleAnnotations
            val rvaIdx = utf8s.entries.firstOrNull { it.value == "RuntimeVisibleAnnotations" }?.key
            val riaIdx = utf8s.entries.firstOrNull { it.value == "RuntimeInvisibleAnnotations" }?.key
            val classAttrCount = readU2(bytes, pos); pos += 2
            repeat(classAttrCount) {
                if (pos + 6 > bytes.size) return configs
                val attrNameIdx = readU2(bytes, pos); pos += 2
                val attrLen = readU4(bytes, pos); pos += 4

                if (attrNameIdx == rvaIdx || attrNameIdx == riaIdx) {
                    val numAnnotations = readU2(bytes, pos)
                    var scanPos = pos + 2
                    repeat(numAnnotations) {
                        if (scanPos + 4 > bytes.size) return@repeat
                        val typeIdx = readU2(bytes, scanPos); scanPos += 2
                        val numPairs = readU2(bytes, scanPos); scanPos += 2
                        val typeDesc = utf8s[typeIdx]

                        if (typeDesc == "Landroidx/compose/ui/tooling/preview/Preview;") {
                            // Single @Preview — extract params
                            val config = mutableMapOf<String, String>()
                            repeat(numPairs) {
                                if (scanPos + 3 > bytes.size) return@repeat
                                val nameIdx = readU2(bytes, scanPos); scanPos += 2
                                val paramName = utf8s[nameIdx] ?: ""
                                val (value, newPos) = readElementValue(bytes, scanPos, utf8s)
                                scanPos = newPos
                                if (value.isNotBlank()) config[paramName] = value
                            }
                            configs.add(config)
                        } else if (typeDesc == PREVIEW_CONTAINER_DESC) {
                            // @Preview$Container — has value=[@Preview(...), @Preview(...)]
                            repeat(numPairs) {
                                if (scanPos + 3 > bytes.size) return@repeat
                                val nameIdx = readU2(bytes, scanPos); scanPos += 2
                                val tag2 = bytes[scanPos].toInt() and 0xFF; scanPos++
                                if (tag2 == '['.code) {
                                    // Array of annotations
                                    val arrayLen = readU2(bytes, scanPos); scanPos += 2
                                    repeat(arrayLen) {
                                        if (scanPos + 4 > bytes.size) return@repeat
                                        val annTag = bytes[scanPos].toInt() and 0xFF; scanPos++
                                        if (annTag == '@'.code) {
                                            val annTypeIdx = readU2(bytes, scanPos); scanPos += 2
                                            val annPairs = readU2(bytes, scanPos); scanPos += 2
                                            val config = mutableMapOf<String, String>()
                                            repeat(annPairs) {
                                                if (scanPos + 3 > bytes.size) return@repeat
                                                val pNameIdx = readU2(bytes, scanPos); scanPos += 2
                                                val pName = utf8s[pNameIdx] ?: ""
                                                val (pVal, newPos) = readElementValue(bytes, scanPos, utf8s)
                                                scanPos = newPos
                                                if (pVal.isNotBlank()) config[pName] = pVal
                                            }
                                            configs.add(config)
                                        }
                                    }
                                } else {
                                    // Skip non-array element value
                                    val (_, newPos) = readElementValueFromTag(tag2, bytes, scanPos, utf8s)
                                    scanPos = newPos
                                }
                            }
                        } else {
                            // Skip other annotation's element-value pairs
                            repeat(numPairs) {
                                if (scanPos + 3 > bytes.size) return@repeat
                                scanPos += 2 // name index
                                val (_, newPos) = readElementValue(bytes, scanPos, utf8s)
                                scanPos = newPos
                            }
                        }
                    }
                }
                pos += attrLen
            }
        } catch (_: Exception) {}

        return configs
    }

    // --- Method scanning ---

    data class MethodWithAnnotations(
        val name: String,
        val lineNumber: Int,
        val hasDirectPreview: Boolean,
        val otherAnnotations: List<String>, // annotation descriptors
    )

    private fun extractMethodsWithAnnotations(bytes: ByteArray, utf8s: Map<Int, String>): List<MethodWithAnnotations> {
        val previewDesc = "Landroidx/compose/ui/tooling/preview/Preview;"
        val previewDescIdx = utf8s.entries.firstOrNull { it.value == previewDesc }?.key
        val rvaIdx = utf8s.entries.firstOrNull { it.value == "RuntimeVisibleAnnotations" }?.key
        val riaIdx = utf8s.entries.firstOrNull { it.value == "RuntimeInvisibleAnnotations" }?.key
        val codeIdx = utf8s.entries.firstOrNull { it.value == "Code" }?.key
        val lineNumTableIdx = utf8s.entries.firstOrNull { it.value == "LineNumberTable" }?.key

        var pos = 8
        val cpCount = readU2(bytes, pos); pos += 2
        var i = 1
        while (i < cpCount && pos < bytes.size) {
            val tag = bytes[pos].toInt() and 0xFF; pos++
            when (tag) {
                1 -> { val len = readU2(bytes, pos); pos += 2 + len }
                3, 4 -> pos += 4; 5, 6 -> { pos += 8; i++ }
                7, 8, 16, 19, 20 -> pos += 2; 9, 10, 11, 12, 17, 18 -> pos += 4; 15 -> pos += 3
                else -> return emptyList()
            }
            i++
        }
        pos += 6 // access, this, super
        val ifCount = readU2(bytes, pos); pos += 2; pos += ifCount * 2
        val fieldCount = readU2(bytes, pos); pos += 2
        repeat(fieldCount) { pos = skipMemberAtPos(bytes, pos) }

        val methodCount = readU2(bytes, pos); pos += 2
        val results = mutableListOf<MethodWithAnnotations>()

        repeat(methodCount) {
            if (pos + 6 > bytes.size) return results
            pos += 2 // access
            val nameIdx = readU2(bytes, pos); pos += 2
            pos += 2 // desc
            val attrCount = readU2(bytes, pos); pos += 2

            var hasPreview = false
            val otherAnnots = mutableListOf<String>()
            var lineNumber = -1

            repeat(attrCount) {
                if (pos + 6 > bytes.size) return results
                val attrNameIdx = readU2(bytes, pos); pos += 2
                val attrLen = readU4(bytes, pos); pos += 4

                if (attrNameIdx == rvaIdx || attrNameIdx == riaIdx) {
                    val endPos = pos + attrLen
                    if (pos + 2 <= bytes.size) {
                        val numAnnotations = readU2(bytes, pos)
                        var scanPos = pos + 2
                        repeat(numAnnotations) {
                            if (scanPos + 4 > bytes.size) return@repeat
                            val typeIdx = readU2(bytes, scanPos); scanPos += 2
                            val numPairs = readU2(bytes, scanPos); scanPos += 2
                            if (typeIdx == previewDescIdx) {
                                hasPreview = true
                            } else {
                                val desc = utf8s[typeIdx]
                                if (desc != null && desc.startsWith("L") && desc.endsWith(";")
                                    && !desc.startsWith("Lkotlin/") && !desc.startsWith("Ljava/")
                                    && !desc.contains("Composable")
                                ) {
                                    otherAnnots.add(desc)
                                }
                            }
                            // Skip element-value pairs
                            repeat(numPairs) {
                                if (scanPos + 3 > bytes.size) return@repeat
                                scanPos += 2
                                val (_, np) = readElementValue(bytes, scanPos, utf8s)
                                scanPos = np
                            }
                        }
                    }
                    pos = endPos
                } else if (attrNameIdx == codeIdx && lineNumber == -1) {
                    val codeEnd = pos + attrLen
                    if (pos + 8 <= bytes.size) {
                        pos += 4 // max_stack + max_locals
                        val codeLen = readU4(bytes, pos); pos += 4; pos += codeLen
                        if (pos + 2 <= bytes.size) { val etLen = readU2(bytes, pos); pos += 2; pos += etLen * 8 }
                        if (pos + 2 <= bytes.size) {
                            val caCount = readU2(bytes, pos); pos += 2
                            repeat(caCount) {
                                if (pos + 6 > bytes.size) return@repeat
                                val saIdx = readU2(bytes, pos); pos += 2
                                val saLen = readU4(bytes, pos); pos += 4
                                if (saIdx == lineNumTableIdx && lineNumber == -1 && pos + 4 <= bytes.size) {
                                    val lnCount = readU2(bytes, pos)
                                    if (lnCount > 0 && pos + 6 <= bytes.size) {
                                        lineNumber = readU2(bytes, pos + 4)
                                    }
                                }
                                pos += saLen
                            }
                        }
                    }
                    pos = codeEnd
                } else {
                    pos += attrLen
                }
            }

            val methodName = utf8s[nameIdx]
            if (methodName != null && !methodName.startsWith("<") && !methodName.contains("$")) {
                if (hasPreview || otherAnnots.isNotEmpty()) {
                    if (otherAnnots.isNotEmpty()) {
                        Logger.d { "Method $methodName has annotations: $otherAnnots" }
                    }
                    results.add(MethodWithAnnotations(methodName, lineNumber, hasPreview, otherAnnots))
                }
            }
        }
        return results
    }

    // --- Element value reading ---

    private fun readElementValue(bytes: ByteArray, pos: Int, utf8s: Map<Int, String>): Pair<String, Int> {
        if (pos >= bytes.size) return "" to pos
        val tag = bytes[pos].toInt() and 0xFF
        return readElementValueFromTag(tag, bytes, pos + 1, utf8s)
    }

    private fun readElementValueFromTag(tag: Int, bytes: ByteArray, pos: Int, utf8s: Map<Int, String>): Pair<String, Int> {
        return when (tag.toChar()) {
            'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> {
                val idx = readU2(bytes, pos)
                (utf8s[idx] ?: "") to (pos + 2)
            }
            's' -> {
                val idx = readU2(bytes, pos)
                (utf8s[idx] ?: "") to (pos + 2)
            }
            'e' -> { "" to (pos + 4) } // enum: 2 indexes
            'c' -> { "" to (pos + 2) } // class
            '@' -> { // nested annotation — skip
                var p = pos + 2 // type index
                val numPairs = readU2(bytes, p); p += 2
                repeat(numPairs) {
                    p += 2 // name
                    val (_, np) = readElementValue(bytes, p, utf8s); p = np
                }
                "" to p
            }
            '[' -> { // array
                val count = readU2(bytes, pos); var p = pos + 2
                repeat(count) { val (_, np) = readElementValue(bytes, p, utf8s); p = np }
                "" to p
            }
            else -> "" to pos
        }
    }

    // --- Utilities ---

    private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    private fun extractConstantPoolStrings(bytes: ByteArray): Map<Int, String> {
        if (bytes.size < 10) return emptyMap()
        if (bytes[0] != 0xCA.toByte() || bytes[1] != 0xFE.toByte() ||
            bytes[2] != 0xBA.toByte() || bytes[3] != 0xBE.toByte()) return emptyMap()
        val strings = mutableMapOf<Int, String>()
        var pos = 8; val cpCount = readU2(bytes, pos); pos += 2
        var i = 1
        while (i < cpCount && pos < bytes.size) {
            val tag = bytes[pos].toInt() and 0xFF; pos++
            when (tag) {
                1 -> { if (pos + 2 > bytes.size) break; val len = readU2(bytes, pos); pos += 2
                    if (pos + len > bytes.size) break; strings[i] = String(bytes, pos, len, Charsets.UTF_8); pos += len }
                3 -> { strings[i] = readU4(bytes, pos).toString(); pos += 4 } // Integer
                4 -> pos += 4 // Float (skip)
                5, 6 -> { pos += 8; i++ }
                7, 8, 16, 19, 20 -> pos += 2; 9, 10, 11, 12, 17, 18 -> pos += 4; 15 -> pos += 3
                else -> return strings
            }
            i++
        }
        return strings
    }

    private fun skipMemberAtPos(bytes: ByteArray, startPos: Int): Int {
        var pos = startPos + 6
        val attrCount = readU2(bytes, pos); pos += 2
        repeat(attrCount) { pos += 2; val len = readU4(bytes, pos); pos += 4 + len }
        return pos
    }

    private fun readU2(bytes: ByteArray, offset: Int) =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readU4(bytes: ByteArray, offset: Int) =
        ((bytes[offset].toInt() and 0xFF) shl 24) or ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
}
