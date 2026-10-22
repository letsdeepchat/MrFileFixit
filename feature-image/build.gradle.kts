plugins {
    alias(libs.plugins.mff.android.feature)
}

android {
    namespace = "com.mrfilefixit.feature.image"
}

dependencies {
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.androidx.core.ktx)
}
