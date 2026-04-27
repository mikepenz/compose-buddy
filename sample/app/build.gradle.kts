plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("dev.mikepenz.composebuddy")
}

android {
    namespace = "com.example.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // buddyDebug is auto-created by the compose-buddy plugin; configure matchingFallbacks here
    // so that dependencies without a buddyDebug variant (like compose-buddy-device) fall back to debug.
    buildTypes.maybeCreate("buddyDebug").apply {
        matchingFallbacks += "debug"
        signingConfig = signingConfigs.getByName("debug")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
