package com.mrfilefixit.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run with `./gradlew :benchmark:connectedBenchmarkAndroidTest` on an API 28+ device, then copy the
 * generated `*-baseline-prof.txt` to `app/src/main/baseline-prof.txt`.
 * (The androidx.baselineprofile Gradle plugin is not used until a release supporting AGP 9 is pinned.)
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        visitTopLevelScreens()
    }
}
