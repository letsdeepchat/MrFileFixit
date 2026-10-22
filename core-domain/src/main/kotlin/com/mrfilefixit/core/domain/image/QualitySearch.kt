package com.mrfilefixit.core.domain.image

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Finds the highest encoder quality whose output size is within [minBytes]..[maxBytes].
 *
 * Strategy (at most [MAX_ITERATIONS] encodes):
 * 1. Encode at [maxQuality]. If that already fits under the maximum, it is the best possible result.
 * 2. Otherwise binary-search [minQuality]..[maxQuality - 1] for the largest quality that fits.
 *    Seven probes cover the 1..99 range exactly, so the total never exceeds eight encodes.
 *
 * Encoded size is close to monotonic in quality for JPEG and WebP. When it is not, the search still
 * returns the best fitting candidate it has seen.
 */
object QualitySearch {
    const val MAX_ITERATIONS = 8
    const val MIN_QUALITY = 1
    const val MAX_QUALITY = 100

    /** One encode attempt. [payload] is the encoded bytes (or a handle to them). */
    data class Attempt<out P>(val quality: Int, val sizeBytes: Long, val payload: P)

    sealed interface Outcome<out P> {
        val iterations: Int

        /** Best quality that lands inside the range. */
        data class Found<P>(val attempt: Attempt<P>, override val iterations: Int) : Outcome<P>

        /** Even [maxQuality] is below [minBytes]; [best] is the max-quality attempt. Caller may pad or upscale. */
        data class BelowMinimum<P>(val best: Attempt<P>, override val iterations: Int) : Outcome<P>

        /** Even the lowest probed quality exceeds [maxBytes]. Caller should downscale and retry. */
        data class AboveMaximum<P>(val smallest: Attempt<P>, override val iterations: Int) : Outcome<P>
    }

    suspend fun <P> search(
        minBytes: Long,
        maxBytes: Long,
        minQuality: Int = MIN_QUALITY,
        maxQuality: Int = MAX_QUALITY,
        maxIterations: Int = MAX_ITERATIONS,
        encode: suspend (quality: Int) -> Attempt<P>,
    ): Outcome<P> {
        require(minBytes in 0..maxBytes) { "minBytes must be in 0..maxBytes" }
        require(minQuality in MIN_QUALITY..maxQuality && maxQuality <= MAX_QUALITY) { "Invalid quality bounds" }
        require(maxIterations >= 1) { "Need at least one iteration" }

        var iterations = 0
        val top = encode(maxQuality).also { iterations++ }
        if (top.sizeBytes <= maxBytes) {
            return if (top.sizeBytes >=
                minBytes
            ) {
                Outcome.Found(top, iterations)
            } else {
                Outcome.BelowMinimum(top, iterations)
            }
        }

        var low = minQuality
        var high = maxQuality - 1
        var bestFit: Attempt<P>? = null
        var smallest: Attempt<P> = top
        while (low <= high && iterations < maxIterations) {
            coroutineContext.ensureActive()
            val mid = (low + high) ushr 1
            val attempt = encode(mid).also { iterations++ }
            if (attempt.sizeBytes < smallest.sizeBytes) smallest = attempt
            if (attempt.sizeBytes <= maxBytes) {
                if (bestFit == null || attempt.quality > bestFit.quality) bestFit = attempt
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return when {
            bestFit == null -> Outcome.AboveMaximum(smallest, iterations)
            bestFit.sizeBytes >= minBytes -> Outcome.Found(bestFit, iterations)
            // Size dropped below the minimum between two adjacent qualities: the range is narrower than
            // one quality step. Report the closest result below the maximum.
            else -> Outcome.BelowMinimum(bestFit, iterations)
        }
    }
}
