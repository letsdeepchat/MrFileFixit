plugins {
    alias(libs.plugins.mff.jvm.library)
}

// Pure Kotlin: no Android, no other project modules. Everything else depends on this module.
dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
