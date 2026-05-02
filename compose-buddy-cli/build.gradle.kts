plugins {
    alias(baseLibs.plugins.kotlinJvm)
    application
    alias(baseLibs.plugins.mavenPublish)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

application {
    mainClass.set("dev.mikepenz.composebuddy.cli.MainKt")
}

dependencies {
    implementation(projects.composeBuddyRenderer)
    implementation(projects.composeBuddyRendererAndroid)
    implementation(projects.composeBuddyRendererAndroidPaparazzi)
    implementation(projects.composeBuddyRendererDesktop)
    implementation(projects.composeBuddyCore)
    implementation(projects.composeBuddyInspector)
    implementation(projects.composeBuddyMcp)
    implementation(projects.composeBuddyDeviceClient)
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(baseLibs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Run from the repository root so that relative --project paths (e.g. ./sample) resolve correctly.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.withType<AbstractCopyTask> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Embed worker JARs as resources so they can be extracted at runtime.
val copyWorkerJars by tasks.registering(Copy::class) {
    // Reference cross-project tasks by path to avoid configuration-order issues
    dependsOn(":compose-buddy-renderer-android:workerJar", ":compose-buddy-renderer-desktop:workerJar")

    // Use the version from the project to pick only the current version's worker JAR,
    // avoiding stale JARs from previous versions that may linger in the build/libs directory.
    val projectVersion = version.toString()
    from(layout.projectDirectory.dir("..").file("compose-buddy-renderer-android/build/libs")) {
        include("*$projectVersion-worker.jar")
        rename { "worker-android.jar" }
    }
    from(layout.projectDirectory.dir("..").file("compose-buddy-renderer-desktop/build/libs")) {
        include("*$projectVersion-worker.jar")
        rename { "worker-desktop.jar" }
    }
    into(layout.buildDirectory.dir("resources/main/workers"))
}

// Embed plugin fat JAR as a resource for init-script injection.
val copyPluginJar by tasks.registering(Copy::class) {
    dependsOn(":compose-buddy-gradle-plugin:pluginFatJar")

    from(layout.projectDirectory.dir("..").file("compose-buddy-gradle-plugin/build/libs")) {
        include("*-fat.jar")
        rename { "compose-buddy-gradle-plugin-fat.jar" }
    }
    into(layout.buildDirectory.dir("resources/main/plugin"))
}

tasks.named("processResources") { dependsOn(copyWorkerJars, copyPluginJar) }
tasks.named("classes") { dependsOn(copyWorkerJars, copyPluginJar) }

val mainJar = tasks.named<Jar>("jar")

val fatJarProvider = tasks.register<Jar>("fatJar") {
    dependsOn(configurations.named("runtimeClasspath"))
    dependsOn(mainJar)

    archiveClassifier = "fat"
    isZip64 = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "dev.mikepenz.composebuddy.cli.MainKt"
    }

    val sourceClasses = sourceSets.main.get().output
    inputs.files(sourceClasses)

    doFirst {
        from(sourceClasses)
        from(configurations.named("runtimeClasspath").get().files.map { zipTree(it) })
        exclude("**/*.kotlin_metadata")
        exclude("**/*.kotlin_module")
        exclude("**/*.kotlin_builtins")
        exclude("**/module-info.class")
        exclude("META-INF/maven/**")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }
}

val binaryFile = layout.buildDirectory.file("compose-buddy").map { it.asFile }
val binaryJar = tasks.register("binaryJar") {
    dependsOn(fatJarProvider)

    inputs.files(fatJarProvider.get().outputs.files)
    outputs.file(binaryFile)

    doLast {
        val binary = binaryFile.get()
        val fatJar = fatJarProvider.get().archiveFile.get().asFile

        // Shell header that extracts the embedded jar to a cache on first run.
        // Needed because zip64 jars break when a shell stub is prepended (Java's
        // zip parser can't handle the shifted offsets).
        // Uses tail+c for fast binary extraction instead of byte-by-byte dd.
        val header = buildString {
            appendLine("#!/bin/sh")
            appendLine("# compose-buddy self-extracting binary")
            appendLine("HASH=\"${fatJar.length()}\"")
            appendLine("CACHE_DIR=\"\${HOME}/.cache/compose-buddy\"")
            appendLine("JAR=\"\${CACHE_DIR}/compose-buddy-\${HASH}.jar\"")
            appendLine("if [ ! -f \"\$JAR\" ]; then")
            appendLine("  mkdir -p \"\$CACHE_DIR\"")
            appendLine("  rm -f \"\${CACHE_DIR}\"/compose-buddy-*.jar 2>/dev/null")
            appendLine("  SKIP=@SKIP@")
            appendLine("  tail -c +\$((SKIP + 1)) \"\$0\" > \"\$JAR\"")
            appendLine("fi")
            appendLine("exec java \$JAVA_OPTS -jar \"\$JAR\" \"\$@\"")
        }
        // Replace @SKIP@ with actual header byte length
        val headerWithPlaceholder = header.toByteArray(Charsets.UTF_8)
        val skipValue = header.replace("@SKIP@", headerWithPlaceholder.size.toString()).toByteArray(Charsets.UTF_8).size
        val finalHeader = header.replace("@SKIP@", skipValue.toString())
        val finalHeaderBytes = finalHeader.toByteArray(Charsets.UTF_8)
        check(finalHeaderBytes.size == skipValue) {
            "Header size mismatch: expected $skipValue but got ${finalHeaderBytes.size}"
        }

        binary.parentFile.mkdirs()
        binary.outputStream().use { out ->
            out.write(finalHeaderBytes)
            fatJar.inputStream().use { it.copyTo(out) }
        }
        binary.setExecutable(true, false)
    }
}

tasks.named("assemble") {
    dependsOn(binaryJar)
}

artifacts {
    add("archives", binaryFile.get()) {
        name = "binary"
        type = "jar"
        builtBy(binaryJar)
        classifier = "binary"
    }
}
