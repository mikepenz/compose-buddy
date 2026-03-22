package dev.mikepenz.composebuddy.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RenderConfiguration(
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val locale: String = "",
    val fontScale: Float = 1f,
    val uiMode: Int = 0,
    val device: String = "",
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0L,
    val apiLevel: Int = -1,
    val densityDpi: Int = -1,
    val screenWidthPx: Int = 0,
    val screenHeightPx: Int = 0,
) {
    companion object {
        /**
         * Resolve final configuration by applying overrides on top of preview defaults.
         * Priority: overrides > preview annotation > defaults.
         */
        fun resolve(preview: Preview, overrides: RenderConfiguration? = null): RenderConfiguration {
            val base = RenderConfiguration(
                widthDp = preview.widthDp,
                heightDp = preview.heightDp,
                locale = preview.locale,
                fontScale = preview.fontScale,
                uiMode = preview.uiMode,
                device = preview.device,
                showBackground = preview.showBackground,
                backgroundColor = preview.backgroundColor,
                apiLevel = preview.apiLevel,
            )
            if (overrides == null) return base
            return base.copy(
                widthDp = if (overrides.widthDp != -1) overrides.widthDp else base.widthDp,
                heightDp = if (overrides.heightDp != -1) overrides.heightDp else base.heightDp,
                locale = overrides.locale.ifEmpty { base.locale },
                fontScale = if (overrides.fontScale != 1f) overrides.fontScale else base.fontScale,
                uiMode = if (overrides.uiMode != 0) overrides.uiMode else base.uiMode,
                device = overrides.device.ifEmpty { base.device },
                showBackground = overrides.showBackground || base.showBackground,
                backgroundColor = if (overrides.backgroundColor != 0L) overrides.backgroundColor else base.backgroundColor,
                apiLevel = if (overrides.apiLevel != -1) overrides.apiLevel else base.apiLevel,
                densityDpi = if (overrides.densityDpi > 0) overrides.densityDpi else base.densityDpi,
            )
        }
    }
}
