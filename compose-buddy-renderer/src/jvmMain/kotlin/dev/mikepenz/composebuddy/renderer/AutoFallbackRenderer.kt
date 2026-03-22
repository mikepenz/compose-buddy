package dev.mikepenz.composebuddy.renderer

import co.touchlab.kermit.Logger
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import dev.mikepenz.composebuddy.core.model.RenderError
import dev.mikepenz.composebuddy.core.model.RenderResult
import dev.mikepenz.composebuddy.core.renderer.PreviewRenderer

/**
 * Desktop-first renderer with lazy Android fallback.
 *
 * In auto mode, tries Desktop (ImageComposeScene) for every preview.
 * If a preview fails with an Android-specific error (UnsatisfiedLinkError,
 * NoClassDefFoundError for android.* classes), lazily starts the Android
 * renderer and retries just that preview.
 *
 * The Android renderer is never started unless actually needed.
 */
class AutoFallbackRenderer(
    private val primary: PreviewRenderer,
    private val fallbackFactory: () -> PreviewRenderer,
) : PreviewRenderer {

    private var fallback: PreviewRenderer? = null

    override fun setup() {
        primary.setup()
    }

    override fun teardown() {
        primary.teardown()
        fallback?.teardown()
        fallback = null
    }

    override fun render(preview: Preview, configuration: RenderConfiguration): RenderResult {
        val result = primary.render(preview, configuration)

        if (result.error != null && needsAndroidFallback(result.error!!)) {
            val fb = getOrCreateFallback()
            if (fb != null) {
                Logger.i { "Retrying ${preview.fullyQualifiedName} with Android renderer" }
                val fallbackResult = fb.render(preview, configuration)
                return fallbackResult.copy(rendererUsed = "android-fallback")
            }
        }

        return result.copy(rendererUsed = if (result.error == null) "desktop" else result.rendererUsed)
    }

    override fun extractHierarchy(preview: Preview, configuration: RenderConfiguration): HierarchyNode? {
        return render(preview, configuration).hierarchy
    }

    private fun getOrCreateFallback(): PreviewRenderer? {
        if (fallback != null) return fallback
        return try {
            Logger.i { "Starting Android renderer for fallback..." }
            val fb = fallbackFactory()
            fb.setup()
            fallback = fb
            fb
        } catch (e: Exception) {
            Logger.e { "Android fallback renderer failed to start: ${e.message}" }
            null
        }
    }

    companion object {
        private val ANDROID_ERROR_PATTERNS = listOf(
            // Android-specific classes
            "UnsatisfiedLinkError",
            "NoClassDefFoundError: android",
            "NoClassDefFoundError: Landroid/",
            "NoClassDefFoundError: Could not initialize class android",
            "NoClassDefFoundError: Could not initialize class androidx.compose",
            "NoClassDefFoundError: Could not initialize class androidx.lifecycle",
            "ClassNotFoundException: android",
            // Compose version mismatch (Desktop CLI jars vs project's Android Compose)
            "NoSuchFieldError:",
            "NoSuchMethodError: 'androidx.compose",
            "NoSuchMethodError: 'void androidx.compose",
            "NoSuchMethodError: 'androidx.lifecycle",
            "NoSuchMethodError: 'void androidx.lifecycle",
            "NoClassDefFoundError: androidx/compose",
            "NoClassDefFoundError: androidx/lifecycle",
            // Android resource errors
            "android.content.res.Resources",
            // Android-only composable locals
            "LocalView",
            "ComposeView",
            "Composable uses Android-only APIs",
        )

        fun needsAndroidFallback(error: RenderError): Boolean {
            val msg = error.message + (error.stackTrace ?: "")
            return ANDROID_ERROR_PATTERNS.any { msg.contains(it) }
        }
    }
}
