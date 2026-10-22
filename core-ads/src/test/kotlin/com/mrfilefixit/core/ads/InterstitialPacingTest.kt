package com.mrfilefixit.core.ads

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InterstitialPacingTest {
    private var clock = 1_000_000L
    private val pacing = InterstitialPacing(now = { clock })

    @Test
    fun `first interstitial is allowed`() {
        assertTrue(pacing.canShow())
    }

    @Test
    fun `blocks until 150 seconds have passed`() {
        pacing.recordShown()
        clock += 149_999
        assertFalse(pacing.canShow())
        clock += 1
        assertTrue(pacing.canShow())
    }

    @Test
    fun `caps at six per session however long the gap`() {
        repeat(6) {
            assertTrue(pacing.canShow())
            pacing.recordShown()
            clock += 10 * 60_000
        }
        assertFalse(pacing.canShow())
    }

    @Test
    fun `asking does not consume the budget`() {
        repeat(20) { pacing.canShow() }
        pacing.recordShown()
        clock += InterstitialPacing.MIN_INTERVAL_MILLIS
        assertTrue(pacing.canShow())
    }
}
