pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Resolves the dev.mikepenz.composebuddy Gradle plugin from parent project
    includeBuild("..")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Resolves library dependencies (compose-buddy-device, -device-ksp) from parent project
includeBuild("..")

rootProject.name = "compose-buddy-sample"
include(":app")
include(":desktop")
