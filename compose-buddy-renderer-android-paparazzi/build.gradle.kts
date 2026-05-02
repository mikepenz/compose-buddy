plugins {
    alias(baseLibs.plugins.kotlinJvm)
    alias(baseLibs.plugins.kotlinSerialization)
    alias(baseLibs.plugins.mavenPublish)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

dependencies {
    implementation(projects.composeBuddyCore)
    implementation(projects.composeBuddyRenderer)
    implementation(projects.composeBuddyRendererAndroid)

    // Paparazzi — ComposeView lifecycle management (Recomposer, Choreographer)
    implementation(libs.paparazzi)

    implementation(libs.layoutlib)
    implementation(libs.layoutlib.api)

    // ByteBuddy for View.isInEditMode() interception
    implementation(libs.bytebuddy.agent)
    implementation(libs.bytebuddy.core)

    // XML parsing (for enum map)
    implementation(libs.kxml2)

    implementation(baseLibs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kermit)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
