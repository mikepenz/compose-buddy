plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.publishing")
    alias(baseLibs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.composeBuddyCore)
            implementation(projects.composeBuddyInspector)
            implementation(libs.kotlinx.serialization.json)
            implementation(baseLibs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kermit)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit5)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
