plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.publishing")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.composeBuddyCore)
            implementation(baseLibs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit5)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
