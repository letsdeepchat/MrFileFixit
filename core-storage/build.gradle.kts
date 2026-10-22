plugins {
    alias(libs.plugins.mff.android.library)
    alias(libs.plugins.mff.android.hilt)
}

android {
    namespace = "com.mrfilefixit.core.storage"
}

dependencies {
    implementation(projects.coreDomain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
}
