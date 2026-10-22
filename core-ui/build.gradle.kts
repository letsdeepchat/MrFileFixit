plugins {
    alias(libs.plugins.mff.android.library.compose)
}

android {
    namespace = "com.mrfilefixit.core.ui"
}

dependencies {
    api(projects.coreDomain)
    api(libs.androidx.compose.material3.navigation.suite)
    api(libs.bundles.compose.adaptive)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
