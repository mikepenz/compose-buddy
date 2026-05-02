plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.publishing")
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.ksp.api)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit5)
            // kotlin-compile-testing-ksp 1.6.0 is incompatible with Kotlin 2.3.x
            // KSP processor is tested via unit tests on generator output
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xopt-in=kotlin.RequiresOptIn", "-Xallow-experimental-api")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
