
plugins {
    alias(libs.plugins.mff.android.application)
    alias(libs.plugins.mff.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/** Secrets come from Gradle properties or environment variables, never from source control. */
fun secret(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

android {
    namespace = "com.mrfilefixit.app"

    defaultConfig {
        applicationId = "com.mrfilefixit.app"
        versionCode = 2
        versionName = "1.0.0"
        // The optional UPI / PayPal donation screen. Build with -Pmff.donations=false to leave it out, for
        // example for a store listing whose payments policy does not allow external donation links.
        val donations = providers.gradleProperty("mff.donations").orNull?.toBooleanStrict() ?: true
        buildConfigField("boolean", "DONATIONS_ENABLED", donations.toString())
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
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.coreDomain)
    implementation(projects.coreUi)
    implementation(projects.coreProcessing)
    implementation(projects.coreStorage)
    implementation(projects.featureExam)
    implementation(projects.featurePdf)
    implementation(projects.featureImage)
    implementation(projects.featureScan)
    implementation(projects.featureOcr)
    implementation(projects.featureResume)

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

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
