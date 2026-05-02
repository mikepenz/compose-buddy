plugins {
    id("com.mikepenz.convention.android-library")
    id("com.mikepenz.convention.compose")
    id("com.mikepenz.convention.publishing")
    alias(baseLibs.plugins.kotlinSerialization)
}

android {
    namespace = "dev.mikepenz.composebuddy.device"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(baseLibs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(baseLibs.androidx.compose.runtime)
    implementation(baseLibs.androidx.compose.ui)
    implementation(baseLibs.androidx.compose.ui.tooling)
    implementation(libs.activity.compose)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
