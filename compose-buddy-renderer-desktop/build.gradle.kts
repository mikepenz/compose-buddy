plugins {
    alias(baseLibs.plugins.kotlinJvm)
    alias(baseLibs.plugins.kotlinSerialization)
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

    testImplementation(kotlin("test"))
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

    // Include Skiko native runtimes for all platforms so the worker JAR runs cross-platform
    // (e.g. built on macOS, deployed to Linux, and vice versa).
    // Resolve the Skiko version from the current runtimeClasspath to avoid hard-coding it.
    from(provider {
        val skikoVersion = configurations.getByName("runtimeClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .firstOrNull { it.moduleVersion.id.group == "org.jetbrains.skiko" && it.name.startsWith("skiko-awt-runtime") }
            ?.moduleVersion?.id?.version
            ?: return@provider emptyList<Any>()

        val allPlatforms = configurations.detachedConfiguration(
            dependencies.create("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:$skikoVersion"),
            dependencies.create("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skikoVersion"),
            dependencies.create("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$skikoVersion"),
            dependencies.create("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:$skikoVersion"),
            dependencies.create("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:$skikoVersion"),
        ).apply {
            isTransitive = false
        }
        allPlatforms.files.map { jar ->
            zipTree(jar).matching {
                include("libskiko-*.so", "libskiko-*.so.sha256", "libskiko-*.dylib", "libskiko-*.dylib.sha256", "skiko-*.dll", "skiko-*.dll.sha256")
            }
        }
    })
}
