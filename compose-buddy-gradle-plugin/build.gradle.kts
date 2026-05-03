import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.GradlePublishPlugin

plugins {
    alias(baseLibs.plugins.kotlinJvm)
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.1.1"
    alias(baseLibs.plugins.mavenPublish)
}

val functionalTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[functionalTest.implementationConfigurationName].extendsFrom(
    configurations.implementation.get()
)

dependencies {
    implementation(projects.composeBuddyRenderer)
    implementation(projects.composeBuddyCore)
    implementation(libs.kotlinx.serialization.json)
    compileOnly("com.android.tools.build:gradle:8.9.1")

    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(libs.kotlin.test)
    "functionalTestImplementation"(libs.junit5)
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website = providers.gradleProperty("POM_URL").get()
    vcsUrl = providers.gradleProperty("POM_URL").get()

    plugins {
        create("composeBuddy") {
            id = "dev.mikepenz.composebuddy"
            implementationClass = "dev.mikepenz.composebuddy.gradle.ComposeBuddyPlugin"
            displayName = "Compose Buddy"
            description = "Renders Compose @Preview composables outside the IDE with hierarchy inspection and MCP server"
            tags = listOf("compose", "preview", "android", "jetpack-compose", "screenshot-testing")
        }
    }
    testSourceSets(functionalTest)
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.check {
    dependsOn(functionalTestTask)
}

// Fat JAR bundling plugin + compose-buddy-core + kotlinx-serialization for init-script injection.
val pluginFatJar by tasks.registering(Jar::class) {
    archiveClassifier = "fat"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    doFirst {
        from(configurations.runtimeClasspath.get()
            .filter {
                val name = it.name
                name.contains("compose-buddy-core") ||
                    name.contains("kotlinx-serialization") ||
                    name.contains("compose-buddy-renderer")
            }
            .map { zipTree(it) })
    }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

mavenPublishing {
    configure(GradlePublishPlugin())
    publishToMavenCentral(automaticRelease = false, validateDeployment = DeploymentValidation.NONE)
    signAllPublications()
}
