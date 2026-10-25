plugins {
    alias(libs.plugins.mff.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin: no Android, no other project modules. Everything else depends on this module.
dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
    // Resume drafts are stored as JSON (resume/ResumeJson.kt).
    implementation(libs.kotlinx.serialization.json)
}
