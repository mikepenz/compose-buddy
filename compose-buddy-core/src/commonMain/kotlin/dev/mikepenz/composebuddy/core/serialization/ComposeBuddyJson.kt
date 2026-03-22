package dev.mikepenz.composebuddy.core.serialization

import kotlinx.serialization.json.Json

val ComposeBuddyJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Compact JSON for agent output — skips null/default fields for minimal size. */
val AgentJson = Json {
    prettyPrint = false
    encodeDefaults = false
    ignoreUnknownKeys = true
    explicitNulls = false
}
