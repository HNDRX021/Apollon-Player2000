// Top-level build file. AGP 9 has built-in Kotlin, so only the Compose compiler
// plugin, KSP and Hilt are declared here (versions live in gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
