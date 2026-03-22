package dev.mikepenz.composebuddy.inspector

/** Controls the inspector app's own theme. */
enum class ThemeMode { AUTO, LIGHT, DARK }

/** Controls the rendered preview's theme (light/dark mode). */
enum class PreviewTheme { LIGHT, DARK }

data class DevicePreset(
    val name: String,
    val widthDp: Int,
    val heightDp: Int,
    val densityDpi: Int,
) {
    val label: String get() = "$name (${widthDp}x${heightDp}dp, ${densityDpi}dpi)"
}

val DEVICE_PRESETS = listOf(
    DevicePreset("Default", -1, -1, -1),
    DevicePreset("Pixel 9", 411, 914, 420),
    DevicePreset("Pixel 9 Pro", 411, 914, 560),
    DevicePreset("Pixel 9 Pro XL", 411, 914, 560),
    DevicePreset("Pixel 10", 412, 915, 420),
    DevicePreset("Galaxy S24", 360, 780, 480),
    DevicePreset("Galaxy S24 Ultra", 411, 883, 560),
    DevicePreset("Desktop 1080p", 1920, 1080, 160),
    DevicePreset("Desktop 1440p", 2560, 1440, 160),
    DevicePreset("Tablet 10\"", 800, 1280, 240),
    DevicePreset("Custom", 0, 0, 0),
)

data class RenderConfig(
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val densityDpi: Int = -1,
    val uiMode: Int = -1, // -1 = use default, 0 = light, 0x21 = dark
)

enum class RendererType(val label: String) {
    AUTO("Auto"),
    DESKTOP("Desktop"),
    ANDROID("Android"),
}

data class InspectorSettings(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val previewTheme: PreviewTheme = PreviewTheme.LIGHT,
    val showRgbValues: Boolean = false,
    val renderConfig: RenderConfig = RenderConfig(),
    val selectedPresetIndex: Int = 0,
    val rendererType: RendererType = RendererType.AUTO,
)
