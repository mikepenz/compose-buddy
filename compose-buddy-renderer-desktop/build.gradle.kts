plugins {
    alias(baseLibs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(baseLibs.plugins.composeMultiplatform)
    alias(baseLibs.plugins.composeCompiler)
    alias(baseLibs.plugins.mavenPublish)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

dependencies {
    implementation(projects.composeBuddyCore)
    implementation(projects.composeBuddyRenderer)

    // Compose Desktop — provides ImageComposeScene for headless rendering
    implementation(compose.desktop.currentOs)
    // compose.uiTooling provides Inspectable + CompositionDataRecord + SlotTreeKt.asTree
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.uiTooling)

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
 * Worker JAR: thin JAR for the Desktop render worker subprocess.
 * Contains renderer-desktop + renderer + core + kermit + kotlinx-serialization.
 * EXCLUDES Compose Desktop deps (those come from the project classpath).
 */
val workerJar by tasks.registering(Jar::class) {
    archiveClassifier = "worker"
    isZip64 = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // The desktop worker JAR bundles the FULL Compose Desktop rendering stack.
    // For apps: the project's Compose version goes first on classpath and takes precedence.
    // For libraries: the project may only have Compose on compileClasspath (not runtime),
    // so the worker's bundled Compose fills in as fallback.
    val excludePrefixes = emptyList<String>()

    from(sourceSets.main.get().output)
    from(configurations.named("runtimeClasspath").map { config ->
        config.files.map { zipTree(it) }
    }) {
        exclude { element ->
            excludePrefixes.any { element.relativePath.pathString.startsWith(it) }
        }
        exclude("**/*.kotlin_metadata", "**/*.kotlin_module", "**/*.kotlin_builtins")
        exclude("**/module-info.class")
        exclude("META-INF/maven/**", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}
