plugins {
    alias(baseLibs.plugins.conventionPlugin)

    // Applied `apply false` so subprojects can use the shared classloader
    alias(baseLibs.plugins.kotlinMultiplatform) apply false
    alias(baseLibs.plugins.kotlinJvm) apply false
    alias(baseLibs.plugins.kotlinAndroid) apply false
    alias(baseLibs.plugins.androidLibrary) apply false
    alias(baseLibs.plugins.composeMultiplatform) apply false
    alias(baseLibs.plugins.composeCompiler) apply false
    alias(baseLibs.plugins.kotlinSerialization) apply false

    alias(baseLibs.plugins.mavenPublish) apply false
    alias(baseLibs.plugins.binaryCompatiblityValidator) apply false
    alias(baseLibs.plugins.dokka) apply false
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}
