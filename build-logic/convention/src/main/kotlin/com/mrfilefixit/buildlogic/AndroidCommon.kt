package com.mrfilefixit.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint

/**
 * Lint ids that are about the build environment rather than code quality.
 * They change every time Google ships a release, so they cannot be part of a zero-warning gate.
 * Dependabot keeps versions current instead.
 */
private val ENVIRONMENT_LINT_IDS = setOf(
    "GradleDependency",
    "NewerVersionAvailable",
    "AndroidGradlePluginVersion",
    "OldTargetApi",
)

internal fun Lint.configureZeroWarningLint() {
    warningsAsErrors = true
    abortOnError = true
    checkReleaseBuilds = true
    disable += ENVIRONMENT_LINT_IDS
    // Hardcoded text is forbidden: every user-visible string goes through resources (English + Hindi).
    error += setOf("HardcodedText", "MissingTranslation", "SetTextI18n", "RtlHardcoded", "RtlEnabled")
    xmlReport = true
    sarifReport = true
    // Print every finding to the build log, not just the first, so CI shows the whole list at once.
    textReport = true
    textOutput = java.io.File("stdout")
}

internal fun ApplicationExtension.configureAndroidApplication() {
    compileSdk = Sdk.COMPILE
    defaultConfig {
        minSdk = Sdk.MIN
        targetSdk = Sdk.TARGET
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    compileOptions {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    lint {
        configureZeroWarningLint()
        // The app run sees every module, so unused-resource checks are accurate only here.
        checkDependencies = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
    }
}

internal fun LibraryExtension.configureAndroidLibrary() {
    compileSdk = Sdk.COMPILE
    defaultConfig {
        minSdk = Sdk.MIN
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    lint {
        configureZeroWarningLint()
        targetSdk = Sdk.TARGET
        // A library cannot see which of its resources other modules use; :app checks this with dependencies.
        disable += "UnusedResources"
    }
    testOptions {
        targetSdk = Sdk.TARGET
        unitTests.isReturnDefaultValues = true
    }
}
