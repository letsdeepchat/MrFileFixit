package com.mrfilefixit.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.mrfilefixit.app"
private const val TIMEOUT_MS = 5_000L

/** Opens each top-level tab, so the profile covers every feature's first frame. No files are picked. */
internal fun MacrobenchmarkScope.visitTopLevelScreens() {
    device.wait(Until.hasObject(By.res("home")), TIMEOUT_MS)
    listOf("nav_exam", "nav_pdf", "nav_image", "nav_scan", "nav_resume", "nav_home").forEach { tag ->
        device.findObject(By.res(tag))?.click()
        device.waitForIdle()
    }
}
