package dev.mikepenz.composebuddy.renderer

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration

/**
 * Maps @Preview annotation parameters and overrides to layoutlib rendering parameters.
 */
object DeviceConfigMapper {

    data class LayoutlibConfig(
        val screenWidthPx: Int,
        val screenHeightPx: Int,
        val densityDpi: Int,
        val xDpi: Float,
        val yDpi: Float,
        val fontScale: Float,
        val locale: String,
        val uiMode: Int,
        val showBackground: Boolean,
        val backgroundColor: Long,
    )

    private data class DeviceSpec(
        val widthPx: Int,
        val heightPx: Int,
        val dpi: Int,
    )

    private val knownDevices = mapOf(
        "id:pixel_5" to DeviceSpec(1080, 2340, 440),
        "id:pixel_6" to DeviceSpec(1080, 2400, 411),
        "id:pixel_7" to DeviceSpec(1080, 2400, 420),
        "id:pixel_8" to DeviceSpec(1080, 2400, 420),
        "id:pixel_9" to DeviceSpec(1080, 2424, 420),
        "id:nexus_5" to DeviceSpec(1080, 1920, 480),
        "id:nexus_7" to DeviceSpec(1200, 1920, 323),
        "id:nexus_10" to DeviceSpec(2560, 1600, 299),
    )

    private val defaultDevice = DeviceSpec(1080, 1920, 420)

    fun map(preview: Preview, config: RenderConfiguration): LayoutlibConfig {
        val device = resolveDevice(config.device)
        val dpi = if (config.densityDpi > 0) config.densityDpi else device.dpi
        val widthPx = if (config.widthDp > 0) dpToPx(config.widthDp, dpi) else device.widthPx
        val heightPx = if (config.heightDp > 0) dpToPx(config.heightDp, dpi) else device.heightPx

        return LayoutlibConfig(
            screenWidthPx = widthPx,
            screenHeightPx = heightPx,
            densityDpi = dpi,
            xDpi = dpi.toFloat(),
            yDpi = dpi.toFloat(),
            fontScale = config.fontScale,
            locale = config.locale,
            uiMode = config.uiMode,
            showBackground = config.showBackground,
            backgroundColor = config.backgroundColor,
        )
    }

    private fun resolveDevice(device: String): DeviceSpec {
        if (device.isBlank()) return defaultDevice
        knownDevices[device]?.let { return it }
        return parseDeviceSpec(device) ?: defaultDevice
    }

    private fun parseDeviceSpec(spec: String): DeviceSpec? {
        if (!spec.startsWith("spec:")) return null
        val params = spec.removePrefix("spec:").split(",").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
        val width = params["width"]?.removeSuffix("px")?.toIntOrNull() ?: return null
        val height = params["height"]?.removeSuffix("px")?.toIntOrNull() ?: return null
        val dpi = params["dpi"]?.toIntOrNull() ?: 420
        return DeviceSpec(width, height, dpi)
    }

    private fun dpToPx(dp: Int, dpi: Int): Int = (dp * dpi / 160f).toInt()
}
