plugins {
    alias(baseLibs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    implementation(projects.composeBuddyRenderer)
    implementation(projects.composeBuddyCore)
    implementation(projects.composeBuddyDeviceClient)

    testImplementation(projects.composeBuddyRendererAndroid)
    implementation(libs.mcp.kotlin.sdk)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(baseLibs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
