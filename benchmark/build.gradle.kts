plugins {
    alias(libs.plugins.mff.android.test)
}

android {
    namespace = "com.mrfilefixit.benchmark"
    targetProjectPath = ":app"
    // Lets the test APK instrument the app under test (needed by Macrobenchmark and BaselineProfileRule).
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
