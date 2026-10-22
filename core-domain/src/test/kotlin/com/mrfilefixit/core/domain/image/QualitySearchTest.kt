package com.mrfilefixit.core.domain.image

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class QualitySearchTest {
    /** Monotonic fake encoder: size grows linearly with quality. */
    private fun linear(bytesPerQuality: Long, base: Long = 0): suspend (Int) -> QualitySearch.Attempt<Unit> =
        { q -> QualitySearch.Attempt(q, base + q * bytesPerQuality, Unit) }

    @Test
    fun `max quality that fits is returned in one iteration`() = runTest {
        val outcome = QualitySearch.search(minBytes = 10_000, maxBytes = 200_000, encode = linear(1_000))
        outcome as QualitySearch.Outcome.Found
        assertEquals(100, outcome.attempt.quality)
        assertEquals(1, outcome.iterations)
    }

    @ParameterizedTest(name = "range {0}..{1} with {2} B/q")
    @CsvSource(
        "20480, 50000, 1000",
        "10240, 20000, 300",
        "1, 5000, 999",
        "50000, 51000, 1000",
        "0, 99000, 1000",
    )
    fun `finds highest fitting quality within eight encodes`(min: Long, max: Long, perQ: Long) = runTest {
        var calls = 0
        val encode = linear(perQ)
        val outcome = QualitySearch.search(min, max) { q ->
            calls++
            encode(q)
        }
        outcome as QualitySearch.Outcome.Found
        val expected = (max / perQ).toInt().coerceAtMost(100)
        assertEquals(expected, outcome.attempt.quality)
        assertTrue(outcome.attempt.sizeBytes in min..max)
        assertTrue(calls <= QualitySearch.MAX_ITERATIONS, "used $calls encodes")
    }

    @Test
    fun `reports BelowMinimum when full quality is too small`() = runTest {
        val outcome = QualitySearch.search(minBytes = 50_000, maxBytes = 90_000, encode = linear(100))
        outcome as QualitySearch.Outcome.BelowMinimum
        assertEquals(100, outcome.best.quality)
    }

    @Test
    fun `reports AboveMaximum with the smallest attempt when nothing fits`() = runTest {
        val outcome = QualitySearch.search(minBytes = 0, maxBytes = 1_000, encode = linear(100, base = 5_000))
        outcome as QualitySearch.Outcome.AboveMaximum
        assertTrue(outcome.smallest.quality <= 2)
        assertTrue(outcome.iterations <= QualitySearch.MAX_ITERATIONS)
    }

    @Test
    fun `narrow range between two quality steps reports BelowMinimum with closest fit`() = runTest {
        // q=50 -> 50_000, q=51 -> 51_000; range 50_500..50_900 is unreachable.
        val outcome = QualitySearch.search(50_500, 50_900, encode = linear(1_000))
        outcome as QualitySearch.Outcome.BelowMinimum
        assertEquals(50, outcome.best.quality)
    }

    @Test
    fun `non monotonic encoder still returns a fitting result`() = runTest {
        val sizes = (0..100).associateWith { q -> q * 1_000L + if (q % 7 == 0) 3_000 else 0 }
        val outcome = QualitySearch.search(20_000, 60_000) { q -> QualitySearch.Attempt(q, sizes.getValue(q), Unit) }
        outcome as QualitySearch.Outcome.Found
        assertTrue(outcome.attempt.sizeBytes in 20_000L..60_000L)
    }
}
