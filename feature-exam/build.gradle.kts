plugins {
    alias(libs.plugins.mff.android.feature)
}

android {
    namespace = "com.mrfilefixit.feature.exam"
}

// Golden tests read the bundled asset from the module directory.
tasks.withType<Test>().configureEach {
    systemProperty("mff.moduleDir", projectDir.path)
    providers.gradleProperty("golden.update").orNull?.let { systemProperty("golden.update", it) }
}
