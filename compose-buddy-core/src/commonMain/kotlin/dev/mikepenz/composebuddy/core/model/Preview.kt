package dev.mikepenz.composebuddy.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Preview(
    val fullyQualifiedName: String,
    val fileName: String = "",
    val lineNumber: Int = -1,
    val name: String = "",
    val group: String = "",
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val locale: String = "",
    val fontScale: Float = 1f,
    val uiMode: Int = 0,
    val device: String = "",
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0L,
    val showSystemUi: Boolean = false,
    val apiLevel: Int = -1,
    val parameterProviderClass: String? = null,
    val parameterProviderLimit: Int = Int.MAX_VALUE,
) {
    init {
        require(fullyQualifiedName.isNotBlank()) { "fullyQualifiedName must not be blank" }
        require(widthDp == -1 || widthDp > 0) { "widthDp must be -1 (wrap content) or > 0, was $widthDp" }
        require(heightDp == -1 || heightDp > 0) { "heightDp must be -1 (wrap content) or > 0, was $heightDp" }
        require(fontScale > 0f) { "fontScale must be > 0, was $fontScale" }
        require(parameterProviderLimit > 0) { "parameterProviderLimit must be > 0, was $parameterProviderLimit" }
    }
}
