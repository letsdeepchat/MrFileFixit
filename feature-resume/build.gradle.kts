plugins {
    alias(libs.plugins.mff.android.feature)
}

android {
    namespace = "com.mrfilefixit.feature.resume"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
