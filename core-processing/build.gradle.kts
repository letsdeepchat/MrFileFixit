plugins {
    alias(libs.plugins.mff.android.library)
    alias(libs.plugins.mff.android.hilt)
}

android {
    namespace = "com.mrfilefixit.core.processing"
}

// Processing engines. Network isolation is enforced by the convention plugin (verifyNetworkIsolation).
dependencies {
    implementation(projects.coreDomain)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.heifwriter)
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.devanagari)
}
