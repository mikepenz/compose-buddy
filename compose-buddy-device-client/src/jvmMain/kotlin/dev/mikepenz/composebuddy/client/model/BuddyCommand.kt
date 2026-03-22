package dev.mikepenz.composebuddy.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class BuddyCommand {
    @Serializable
    @SerialName("rerender")
    data object Rerender : BuddyCommand()

    @Serializable
    @SerialName("tap")
    data class Tap(val x: Int, val y: Int) : BuddyCommand()

    @Serializable
    @SerialName("setConfig")
    data class SetConfig(
        val darkMode: Boolean? = null,
        val fontScale: Float? = null,
        val locale: String? = null,
    ) : BuddyCommand()

    @Serializable
    @SerialName("navigate")
    data class Navigate(val preview: String) : BuddyCommand()

    @Serializable
    @SerialName("requestPreviewList")
    data object RequestPreviewList : BuddyCommand()
}
