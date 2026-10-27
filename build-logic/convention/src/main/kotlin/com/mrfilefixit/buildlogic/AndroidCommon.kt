package com.mrfilefixit.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import java.io.File

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

internal fun Lint.configureZeroWarningLint(config: File) {
    lintConfig = config
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
    textOutput = File("stdout")
    explainIssues = false
}

internal fun ApplicationExtension.configureAndroidApplication(lintConfig: File) {
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
        configureZeroWarningLint(lintConfig)
        // The app run sees every module, so unused-resource checks are accurate only here.
        checkDependencies = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
        // Data tables of Bouncy Castle's post-quantum ciphers (about 4.5 MB). PDFBox uses Bouncy Castle only
        // for standard PDF encryption and never loads these.
        resources.excludes += "/org/bouncycastle/pqc/**"
    }
}

internal fun LibraryExtension.configureAndroidLibrary(lintConfig: File) {
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
        configureZeroWarningLint(lintConfig)
        targetSdk = Sdk.TARGET
        // A library cannot see which of its resources other modules use; :app checks this with dependencies.
        disable += "UnusedResources"
    }
    testOptions {
        targetSdk = Sdk.TARGET
        unitTests.isReturnDefaultValues = true
    }
}
