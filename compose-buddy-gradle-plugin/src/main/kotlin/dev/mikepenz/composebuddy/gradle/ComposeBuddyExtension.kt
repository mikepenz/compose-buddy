package dev.mikepenz.composebuddy.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ComposeBuddyExtension @Inject constructor(objects: ObjectFactory) {
    val maxPreviewParameterValues: Property<Int> = objects.property(Int::class.java).convention(10)
    val defaultDevice: Property<String> = objects.property(String::class.java).convention("")
    val designTokensFile: RegularFileProperty = objects.fileProperty()

    /** Set to false to disable on-device preview support (buddyDebug variant). */
    val devicePreviewEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /** Port used by the on-device preview server. */
    val devicePort: Property<Int> = objects.property(Int::class.java).convention(7890)
}
