package dev.mikepenz.composebuddy.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Modifier

private const val PREVIEW_ANNOTATION = "androidx.compose.ui.tooling.preview.Preview"
private const val PREVIEW_PARAMETER_ANNOTATION = "androidx.compose.ui.tooling.preview.PreviewParameter"

data class PreviewParameterInfo(
    val paramName: String,
    val providerFqn: String,
)

/**
 * A single preview configuration extracted from one @Preview annotation instance.
 */
data class PreviewConfigInfo(
    val name: String,
    val group: String,
    val widthDp: Int,
    val heightDp: Int,
    val locale: String,
    val fontScale: Float,
    val uiMode: Int,
    val showBackground: Boolean,
    val backgroundColor: Long,
    val apiLevel: Int,
)

data class PreviewInfo(
    val packageName: String,
    val functionName: String,
    val containingFile: String,
    val configs: List<PreviewConfigInfo>,
    val previewParameter: PreviewParameterInfo? = null,
    val isPrivate: Boolean = false,
) {
    val fqn: String get() = "$packageName.${containingFile.removeSuffix(".kt")}Kt.$functionName"
    val displayName: String get() = configs.firstOrNull()?.name?.ifEmpty { functionName } ?: functionName
}

object PreviewAnnotationScanner {
    fun scan(resolver: Resolver): List<PreviewInfo> {
        val functionPreviews = mutableMapOf<KSFunctionDeclaration, MutableList<KSAnnotation>>()

        fun addPreviews(fn: KSFunctionDeclaration, annotations: List<KSAnnotation>) {
            functionPreviews.getOrPut(fn) { mutableListOf() }.addAll(annotations)
        }

        // Scan all source functions: collect direct @Preview and expand multi-preview annotations.
        // Iterating source files (rather than relying on getSymbolsWithAnnotation for annotation
        // class declarations) ensures we handle library-defined multi-previews like @PreviewLightDark,
        // whose annotation class lives in compiled bytecode and won't appear in source-only queries.
        resolver.getAllFiles().forEach { file ->
            file.declarations
                .filterIsInstance<KSFunctionDeclaration>()
                .forEach { fn ->
                    fn.annotations.forEach { annotation ->
                        val annoFqn = annotation.annotationType.resolve().declaration
                            .qualifiedName?.asString() ?: return@forEach

                        when (annoFqn) {
                            PREVIEW_ANNOTATION -> addPreviews(fn, listOf(annotation))
                            else -> {
                                // Check if this annotation class is itself annotated with @Preview
                                val annoClass = annotation.annotationType.resolve().declaration
                                if (annoClass is KSClassDeclaration &&
                                    annoClass.classKind == ClassKind.ANNOTATION_CLASS
                                ) {
                                    val expandedPreviews = annoClass.annotations.filter {
                                        it.annotationType.resolve().declaration
                                            .qualifiedName?.asString() == PREVIEW_ANNOTATION
                                    }.toList()
                                    if (expandedPreviews.isNotEmpty()) {
                                        addPreviews(fn, expandedPreviews)
                                    }
                                }
                            }
                        }
                    }
                }
        }

        // Step 3: convert to PreviewInfo, one per function with all its configs
        return functionPreviews.mapNotNull { (fn, annotations) ->
            toPreviewInfo(fn, annotations)
        }
    }

    private fun toPreviewInfo(
        fn: KSFunctionDeclaration,
        previewAnnotations: List<KSAnnotation>,
    ): PreviewInfo? {
        if (previewAnnotations.isEmpty()) return null

        val configs = previewAnnotations.map { annotation ->
            fun <T> arg(name: String, default: T): T {
                @Suppress("UNCHECKED_CAST")
                return annotation.arguments.find { it.name?.asString() == name }?.value as? T ?: default
            }
            PreviewConfigInfo(
                name = arg("name", ""),
                group = arg("group", ""),
                widthDp = arg("widthDp", -1),
                heightDp = arg("heightDp", -1),
                locale = arg("locale", ""),
                fontScale = arg("fontScale", 1f),
                uiMode = arg("uiMode", 0),
                showBackground = arg("showBackground", false),
                backgroundColor = arg("backgroundColor", 0L),
                apiLevel = arg("apiLevel", -1),
            )
        }

        val previewParam = fn.parameters.firstNotNullOfOrNull { param ->
            val ppAnnotation = param.annotations.find {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == PREVIEW_PARAMETER_ANNOTATION
            }
            if (ppAnnotation != null) {
                val providerFqn = ppAnnotation.arguments.firstOrNull()?.value
                    ?.toString()?.removeSuffix("::class") ?: return@firstNotNullOfOrNull null
                PreviewParameterInfo(
                    paramName = param.name?.asString() ?: "param",
                    providerFqn = providerFqn,
                )
            } else null
        }

        return PreviewInfo(
            packageName = fn.packageName.asString(),
            functionName = fn.simpleName.asString(),
            containingFile = fn.containingFile?.fileName ?: "",
            configs = configs,
            previewParameter = previewParam,
            isPrivate = fn.modifiers.contains(Modifier.PRIVATE),
        )
    }
}
