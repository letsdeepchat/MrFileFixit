plugins {
    alias(libs.plugins.mff.jvm.library)
}

// Pure Kotlin office formats (OOXML, legacy binary Office, CSV) and PDF text layout analysis.
// No Android and no third-party libraries: everything here is unit-tested on the JVM.
dependencies {
    implementation(projects.coreDomain)
}
