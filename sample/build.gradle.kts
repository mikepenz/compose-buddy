plugins {
    alias(baseLibs.plugins.androidApplication) apply false
    alias(baseLibs.plugins.composeCompiler) apply false
    alias(baseLibs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
}
