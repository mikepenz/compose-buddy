package dev.mikepenz.composebuddy.renderer.worker

import co.touchlab.kermit.Logger
import java.lang.reflect.Method

/**
 * Shared reflection utilities for discovering and invoking @Composable @Preview functions.
 * Used by both Desktop and Android render workers to reduce duplication and
 * ensure consistent, robust composable method resolution across backends.
 */
object ComposableInvoker {

    /**
     * Result of resolving a composable method from a fully-qualified name.
     */
    sealed class ResolvedMethod {
        /** Regular composable: only synthetic params (Composer, changed, default). */
        data class Regular(
            val method: Method,
            val composerIndex: Int,
        ) : ResolvedMethod()

        /** Composable with @PreviewParameter: value params before Composer. */
        data class WithPreviewParameter(
            val method: Method,
            val composerIndex: Int,
            val valueParamCount: Int,
        ) : ResolvedMethod()

        data class Error(val message: String) : ResolvedMethod()
    }

    /**
     * Resolves a composable method from a fully-qualified name (e.g., "com.example.MyPreviewKt.MyPreview").
     * Uses flexible Composer detection that works across Compose compiler versions.
     */
    fun resolve(fqn: String): ResolvedMethod {
        val lastDot = fqn.lastIndexOf('.')
        if (lastDot < 0) return ResolvedMethod.Error("Invalid FQN: $fqn")

        val className = fqn.substring(0, lastDot)
        val methodName = fqn.substring(lastDot + 1)

        val clazz = try {
            Class.forName(className)
        } catch (_: ClassNotFoundException) {
            return ResolvedMethod.Error("Class not found: $className")
        }

        try {
            Class.forName("androidx.compose.runtime.Composer")
        } catch (_: ClassNotFoundException) {
            return ResolvedMethod.Error("Composer not on classpath")
        }

        val candidates = clazz.declaredMethods.filter { it.name == methodName }

        // Find Composer parameter by class name (robust across compiler versions)
        fun findComposerIndex(m: Method): Int =
            m.parameterTypes.indexOfFirst { it.name.endsWith("Composer") }

        // Try regular composable: Composer is first param, rest are Int (changed/default)
        val regular = candidates
            .filter { m ->
                val ci = findComposerIndex(m)
                ci >= 0 && ci == 0 &&
                    m.parameterCount >= 2 &&
                    m.parameterTypes.drop(1).all { it == Int::class.javaPrimitiveType }
            }
            .maxByOrNull { it.parameterCount }

        if (regular != null) {
            regular.isAccessible = true
            return ResolvedMethod.Regular(regular, composerIndex = 0)
        }

        // Try @PreviewParameter composable: value params before Composer
        val paramMethod = candidates
            .filter { m ->
                val ci = findComposerIndex(m)
                ci > 0 && m.parameterCount >= 3
            }
            .maxByOrNull { it.parameterCount }

        if (paramMethod != null) {
            paramMethod.isAccessible = true
            val composerIdx = findComposerIndex(paramMethod)
            return ResolvedMethod.WithPreviewParameter(
                paramMethod, composerIndex = composerIdx, valueParamCount = composerIdx,
            )
        }

        return ResolvedMethod.Error("No matching composable method: $fqn")
    }

    /**
     * Builds argument array for invoking a regular composable method.
     * @param composer The Composer instance
     * @param changed The changed flags value (typically from the second invoke arg)
     * @param method The resolved method
     */
    fun buildRegularArgs(composer: Any?, changed: Any?, method: Method): Array<Any?> {
        val args = mutableListOf<Any?>(composer, changed)
        repeat(method.parameterCount - 2) { args.add(0) }
        return args.toTypedArray()
    }

    /**
     * Builds argument array for invoking a @PreviewParameter composable.
     * @param value The preview parameter value
     * @param composer The Composer instance
     * @param changed The changed flags value
     * @param method The resolved method
     * @param composerIndex Index of the Composer parameter
     */
    fun buildPreviewParamArgs(
        value: Any?,
        composer: Any?,
        changed: Any?,
        method: Method,
        composerIndex: Int,
    ): Array<Any?> {
        val args = mutableListOf<Any?>()
        args.add(value)
        for (j in 1 until composerIndex) {
            args.add(defaultVal(method.parameterTypes[j]))
        }
        args.add(composer)
        args.add(changed)
        val syntheticCount = method.parameterCount - composerIndex
        repeat(syntheticCount - 2) { args.add(0) }
        return args.toTypedArray()
    }

    /**
     * Finds @PreviewParameter provider values for a composable method.
     * Returns up to 10 values from the provider, or empty list if not found.
     */
    fun findPreviewParamValues(clazz: Class<*>, methodName: String): List<Any?> {
        return try {
            val method = clazz.declaredMethods.firstOrNull {
                it.name == methodName && it.parameterCount >= 3 &&
                    !it.parameterTypes[0].name.endsWith("Composer")
            } ?: return emptyList()
            val annots = method.parameterAnnotations[0]
            val pp = annots.firstOrNull {
                it.annotationClass.java.name == "androidx.compose.ui.tooling.preview.PreviewParameter"
            } ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            val providerClass = pp.annotationClass.java.getMethod("provider").invoke(pp) as Class<*>
            val provider = providerClass.getDeclaredConstructor().newInstance()
            val seq = providerClass.getMethod("getValues").invoke(provider) as kotlin.sequences.Sequence<*>
            seq.take(10).toList()
        } catch (e: Exception) {
            Logger.d { "PreviewParameter resolution: ${e::class.simpleName}: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Returns a sensible default value for a given primitive/common type.
     */
    fun defaultVal(type: Class<*>): Any? = when (type) {
        String::class.java -> ""
        Int::class.javaPrimitiveType -> 0
        Boolean::class.javaPrimitiveType -> false
        Float::class.javaPrimitiveType -> 0f
        Long::class.javaPrimitiveType -> 0L
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> ' '
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        else -> null
    }
}
