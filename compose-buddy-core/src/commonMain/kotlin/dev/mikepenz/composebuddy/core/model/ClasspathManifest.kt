package dev.mikepenz.composebuddy.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ClasspathManifest(
    val compiledClassDirs: List<String>,
    val dependencyJars: List<String>,
    val androidJar: String? = null,
    val resourceDirs: List<String> = emptyList(),
    val assetDirs: List<String> = emptyList(),
    val composeVersion: String? = null,
    val kotlinVersion: String? = null,
    val platformType: String = "unknown",
    val namespace: String? = null,
    val compileSdk: Int? = null,
    val generatedAt: String = "",
)
