
plugins {
    alias(libs.plugins.mff.android.application)
    alias(libs.plugins.mff.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

/** Secrets come from Gradle properties or environment variables, never from source control. */
fun secret(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

// Google's published test IDs. Used only in debug builds.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val GOOGLE_TEST_PUBLISHER = "ca-app-pub-3940256099942544"

android {
    namespace = "com.mrfilefixit.app"

    defaultConfig {
        applicationId = "com.mrfilefixit.app"
        versionCode = 1
        versionName = "1.0.0"
    }

    androidResources {
        // Only ship the locales we translate, which also shrinks the APK.
        localeFilters += listOf("en", "hi")
    }

    signingConfigs {
        create("release") {
            val path = secret("MFF_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = secret("MFF_KEYSTORE_PASSWORD")
                keyAlias = secret("MFF_KEY_ALIAS")
                keyPassword = secret("MFF_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = testAdmobAppId
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = secret("ADMOB_APP_ID") ?: ""
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

// A release must never ship with test ad IDs or without an AdMob app ID.
val verifyReleaseSecrets by tasks.registering {
    val appId = secret("ADMOB_APP_ID")
    val unitIds = listOf("ADMOB_BANNER_ID", "ADMOB_INTERSTITIAL_ID", "ADMOB_REWARDED_ID").associateWith { secret(it) }
    // Locals, so the configuration cache does not capture the script object.
    val testAppId = testAdmobAppId
    val testPublisher = GOOGLE_TEST_PUBLISHER
    doLast {
        require(!appId.isNullOrBlank()) { "ADMOB_APP_ID is not set for the release build." }
        require(appId != testAppId) { "Release build is using the AdMob test app ID." }
        unitIds.forEach { (name, unit) ->
            require(!unit.isNullOrBlank()) { "$name is not set for the release build." }
            require(!unit.startsWith(testPublisher)) { "$name is a Google test ad unit." }
        }
    }
}
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseSecrets)
}

baselineProfile {
    // Profiles are generated on a device by :benchmark and checked in under src/main/generated.
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(projects.coreDomain)
    implementation(projects.coreUi)
    implementation(projects.coreProcessing)
    implementation(projects.coreStorage)
    implementation(projects.coreAds)
    implementation(projects.featureExam)
    implementation(projects.featurePdf)
    implementation(projects.featureImage)
    implementation(projects.featureScan)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.compose.adaptive)
    implementation(libs.androidx.compose.material3.navigation.suite)
    debugImplementation(libs.androidx.compose.ui.tooling)

    baselineProfile(projects.benchmark)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
