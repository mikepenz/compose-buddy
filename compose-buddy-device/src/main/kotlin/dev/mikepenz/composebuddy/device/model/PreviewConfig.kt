package dev.mikepenz.composebuddy.device.model

data class PreviewConfig(
    val widthDp: Int = 360,
    val heightDp: Int = 640,
    val densityDpi: Int = 420,
    val locale: String = "",
    val fontScale: Float = 1f,
    val darkMode: Boolean = false,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0xFFFFFFFFL,
    val apiLevel: Int = 34,
)
