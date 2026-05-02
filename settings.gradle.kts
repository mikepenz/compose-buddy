rootProject.name = "compose-buddy"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("baseLibs") {
            from("com.mikepenz:version-catalog:0.15.1")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":compose-buddy-core")
include(":compose-buddy-renderer")
include(":compose-buddy-renderer-android")
include(":compose-buddy-renderer-android-paparazzi")
include(":compose-buddy-renderer-desktop")
include(":compose-buddy-gradle-plugin")
include(":compose-buddy-inspector")
include(":compose-buddy-cli")
include(":compose-buddy-mcp")
include(":compose-buddy-device")
include(":compose-buddy-device-ksp")
include(":compose-buddy-device-client")

