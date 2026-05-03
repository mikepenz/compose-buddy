plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("dev.mikepenz.composebuddy")
}

android {
    namespace = "com.example.flavors"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.flavors"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes.maybeCreate("buddyDebug").apply {
        matchingFallbacks += "debug"
        signingConfig = signingConfigs.getByName("debug")
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
        }
        create("prod") {
            dimension = "env"
            applicationIdSuffix = ".prod"
        }
    }

    // Each flavor ships its own Greeting implementation under src/<flavor>/kotlin.
    sourceSets {
        getByName("dev") { kotlin.srcDir("src/dev/kotlin") }
        getByName("prod") { kotlin.srcDir("src/prod/kotlin") }
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

composeBuddy {
    // Both flavors fall back to dev for dependencies that don't declare a matching flavor.
    deviceFlavorFallbacks.put("env", "dev")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Flavor-scoped dependency — exercises per-flavor classpath wiring.
    "prodImplementation"("androidx.annotation:annotation:1.10.0")
}
