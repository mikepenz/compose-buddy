plugins {
    alias(baseLibs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(baseLibs.plugins.composeMultiplatform)
    alias(baseLibs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.composeBuddyCore)

    // Compose Desktop + Material 3
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Nucleus — decorated window + dark mode
    implementation(libs.nucleus.decorated.window.core)
    implementation(libs.nucleus.decorated.window.jni)
    implementation(libs.nucleus.decorated.window.material3)
    implementation(libs.nucleus.darkmode.detector)

    // Image loading
    implementation(libs.coil.compose)

    implementation(baseLibs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kermit)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
