plugins {
    alias(baseLibs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(baseLibs.plugins.mavenPublish)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

dependencies {
    implementation(projects.composeBuddyCore)
    implementation(projects.composeBuddyRenderer)

    // Layoutlib Bridge — the rendering engine (same as Android Studio)
    implementation(libs.layoutlib)
    implementation(libs.layoutlib.api)

    // Android tools for resource resolution
    implementation(libs.android.tools.common)
    implementation(libs.android.tools.sdk.common)
    implementation(libs.android.tools.ninepatch)

    // ByteBuddy for View.isInEditMode() interception
    implementation(libs.bytebuddy.agent)
    implementation(libs.bytebuddy.core)

    // XML parsing
    implementation(libs.kxml2)
    implementation(libs.trove4j)

    implementation(baseLibs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kermit)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

/**
 * Worker JAR: self-contained fat JAR for the Android render worker subprocess.
 * Contains renderer-android + renderer-android-paparazzi + their deps (layoutlib, bytebuddy, etc.)
 * but EXCLUDES Compose/lifecycle/savedstate (those come from the project classpath).
 */
val workerJar by tasks.registering(Jar::class) {
    archiveClassifier = "worker"
    isZip64 = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Packages to exclude — these come from the PROJECT classpath at runtime.
    // The worker subprocess classpath is: worker.jar + project-classpath + android.jar
    val excludePrefixes = listOf(
        "androidx/compose/", "androidx/lifecycle/", "androidx/savedstate/",
        "androidx/activity/", "androidx/annotation/", "androidx/arch/",
        "androidx/collection/", "androidx/core/", "androidx/customview/",
        "androidx/profileinstaller/", "androidx/startup/", "androidx/tracing/",
        "androidx/window/",
    )

    from(sourceSets.main.get().output)
    // Include the paparazzi module's classes too
    dependsOn(project(":compose-buddy-renderer-android-paparazzi").tasks.named("classes"))
    from(project(":compose-buddy-renderer-android-paparazzi").sourceSets.main.get().output)

    // Include paparazzi module's runtime classpath
    val paparazziClasspath = project(":compose-buddy-renderer-android-paparazzi")
        .configurations.named("runtimeClasspath")

    // Merge both classpaths, excluding Compose/lifecycle (from project classpath)
    from(configurations.named("runtimeClasspath").zip(paparazziClasspath) { a, b ->
        (a.files + b.files).distinct().map { zipTree(it) }
    }) {
        exclude { element ->
            excludePrefixes.any { element.relativePath.pathString.startsWith(it) }
        }
        exclude("**/*.kotlin_metadata", "**/*.kotlin_module", "**/*.kotlin_builtins")
        exclude("**/module-info.class")
        exclude("META-INF/maven/**", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}
