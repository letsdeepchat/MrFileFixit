plugins {
    alias(libs.plugins.mff.android.feature)
}

android {
    namespace = "com.mrfilefixit.feature.scan"
}

dependencies {
    // The scanner UI runs inside Google Play services; images never leave the device.
    implementation(libs.mlkit.document.scanner)
    implementation(libs.kotlinx.coroutines.play.services)
}
