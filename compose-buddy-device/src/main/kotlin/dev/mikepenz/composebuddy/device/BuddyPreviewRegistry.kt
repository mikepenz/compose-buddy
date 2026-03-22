package dev.mikepenz.composebuddy.device

import dev.mikepenz.composebuddy.device.model.BuddyPreviewEntry

/**
 * Interface for the preview registry. KSP generates the implementation
 * (BuddyPreviewRegistryImpl) which is loaded at runtime.
 */
interface BuddyPreviewRegistry {
    val previews: Map<String, BuddyPreviewEntry>

    companion object {
        /**
         * Load the KSP-generated registry implementation via reflection.
         * Returns an empty registry if KSP was not applied.
         */
        fun load(): BuddyPreviewRegistry {
            return try {
                val clazz = Class.forName("dev.mikepenz.composebuddy.device.BuddyPreviewRegistryImpl")
                clazz.getDeclaredField("INSTANCE").get(null) as BuddyPreviewRegistry
            } catch (_: Exception) {
                object : BuddyPreviewRegistry {
                    override val previews: Map<String, BuddyPreviewEntry> = emptyMap()
                }
            }
        }
    }
}
