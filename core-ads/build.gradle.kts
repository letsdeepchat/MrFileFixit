plugins {
    alias(libs.plugins.mff.android.library.compose)
    alias(libs.plugins.mff.android.hilt)
}

/** Ad unit IDs come from Gradle properties or environment variables, never from source control. */
fun secret(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

fun quoted(value: String) = "\"$value\""

android {
    namespace = "com.mrfilefixit.core.ads"
    buildFeatures { buildConfig = true }

    buildTypes {
        debug {
            // Google's published test ad units. Debug builds can never request a live ad.
            buildConfigField("String", "BANNER_UNIT_ID", quoted("ca-app-pub-3940256099942544/9214589741"))
            buildConfigField("String", "INTERSTITIAL_UNIT_ID", quoted("ca-app-pub-3940256099942544/1033173712"))
            buildConfigField("String", "REWARDED_UNIT_ID", quoted("ca-app-pub-3940256099942544/5224354917"))
        }
        release {
            // Empty IDs disable that ad format at runtime; :app's verifyReleaseSecrets fails the release build first.
            buildConfigField("String", "BANNER_UNIT_ID", quoted(secret("ADMOB_BANNER_ID").orEmpty()))
            buildConfigField("String", "INTERSTITIAL_UNIT_ID", quoted(secret("ADMOB_INTERSTITIAL_ID").orEmpty()))
            buildConfigField("String", "REWARDED_UNIT_ID", quoted(secret("ADMOB_REWARDED_ID").orEmpty()))
        }
    }
}

dependencies {
    implementation(projects.coreDomain)
    implementation(projects.coreUi)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
