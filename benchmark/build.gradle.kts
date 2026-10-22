plugins {
    alias(libs.plugins.mff.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.mrfilefixit.benchmark"
    targetProjectPath = ":app"
    // Benchmarks run against a release-like build: minified, but signed with the debug key.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // Generate on a connected device or a Gradle Managed Device in CI.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
