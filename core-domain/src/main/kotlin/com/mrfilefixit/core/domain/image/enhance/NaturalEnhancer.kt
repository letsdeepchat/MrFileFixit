package com.mrfilefixit.core.domain.image.enhance

import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

/**
 * "Natural Enhance": clarity and pixel restoration that never changes colours or skin tone.
 *
 * Everything runs on the luminance (Y of BT.601 YCbCr) only. The final luminance change is added equally to R,
 * G and B, which leaves Cb and Cr (the colour of every pixel) exactly as they were, so brown and dark skin is
 * never lightened, whitened or tinted. Steps, all scaled by `strength` (0..1):
 *
 * 1. Edge-preserving denoise: a self-guided filter (He et al.) on Y removes sensor noise and JPEG speckle in
 *    flat areas while edges, eyelashes and text strokes stay put. Its strength follows the noise measured in
 *    the picture (Immerkaer's estimator), so a clean but soft photo is sharpened rather than smoothed. Part of
 *    the original is mixed back so skin keeps its pores and texture.
 * 2. Clarity: a two-scale unsharp mask with noise coring restores micro-detail (fine) and local contrast
 *    (medium). Sharpening is softer on skin so faces do not look harsh or "plastic".
 * 3. Gentle spot healing: inside skin-coloured areas (a chroma test that covers light to very dark skin), dark
 *    spots smaller than a few pixels are lifted part of the way toward their surroundings. A morphological
 *    closing finds them: it fills dark dots narrower than its window but keeps edges exactly, so eyes, brows,
 *    lips and hair are left alone, and non-skin areas such as text are excluded. Shallow pores stay.
 *
 * The per-pixel luminance change is capped, so there are no halos even at full strength.
 */
object NaturalEnhancer {
    /** Rows of context each output row needs above and below it; banded callers read this many extra rows. */
    const val HALO = 20

    private const val GUIDE_RADIUS = 2
    private const val MIN_SPOT_RADIUS = 2
    private const val MAX_SPOT_RADIUS = 6

    /** One pixel of spot radius per this many pixels of the long side, so blemishes scale with the photo. */
    private const val SPOT_RADIUS_STEP = 500
    private const val FINE_RADIUS = 1
    private const val MEDIUM_RADIUS = 3

    /** The guided filter smooths detail up to this many times the measured noise level, at full strength. */
    private const val NOISE_FACTOR = 2f
    private const val MAX_NOISE_SIGMA = 25f
    private const val DENOISE_MIX = 0.8f
    private const val SPOT_MIX = 0.75f
    private const val SPOT_THRESHOLD = 3f

    /** Dips within this many noise deviations are grain, not spots, so healing never brightens grain. */
    private const val SPOT_NOISE_FACTOR = 2.5f
    private const val SPOT_MAX_LIFT = 40f
    private const val FINE_AMOUNT = 1.1f
    private const val MEDIUM_AMOUNT = 0.35f
    private const val SKIN_SHARPEN_SHARE = 0.6f

    /** Detail smaller than this (luminance steps) is treated as noise and not sharpened. */
    private const val CORING = 3f
    private const val MAX_DELTA = 48f

    /**
     * Returns a new array; [pixels] (packed ARGB, row-major) is not modified. Alpha is kept. [noiseSigma] is the
     * picture's noise level and [spotRadius] the size of blemishes to heal; when null they are derived from
     * [pixels] itself (banded callers pass the whole picture's values).
     */
    @Suppress("LongParameterList")
    fun enhance(
        pixels: IntArray,
        width: Int,
        height: Int,
        strength: Float,
        noiseSigma: Float? = null,
        spotRadius: Int? = null,
    ): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixels must be width * height" }
        val s = strength.coerceIn(0f, 1f)
        if (s == 0f) return pixels.copyOf()
        val n = pixels.size
        val y = FloatArray(n)
        val skin = FloatArray(n)
        for (i in 0 until n) {
            val p = pixels[i]
            val r = (p shr RED_SHIFT) and CHANNEL
            val g = (p shr GREEN_SHIFT) and CHANNEL
            val b = p and CHANNEL
            y[i] = luma(r, g, b)
            skin[i] = skinWeight(r, g, b)
        }
        val sigma = noiseSigma ?: NoiseEstimate().apply { addRows(y, width, height, 0, height) }.sigma
        val denoised = denoise(y, width, height, s, sigma)
        val fine = Box.mean(denoised, width, height, FINE_RADIUS)
        val medium = Box.mean(denoised, width, height, MEDIUM_RADIUS)
        val sharpened = FloatArray(n) { i ->
            val d = denoised[i]
            val skinShare = 1f - SKIN_SHARPEN_SHARE * skin[i]
            d + s * skinShare * FINE_AMOUNT * core(d - fine[i]) + s * MEDIUM_AMOUNT * (d - medium[i])
        }
        val threshold = SPOT_THRESHOLD + SPOT_NOISE_FACTOR * sigma
        healSpots(sharpened, skin, Spots(width, height, s, spotRadius ?: spotRadiusFor(width, height), threshold))
        return IntArray(n) { i -> shiftLuma(pixels[i], (sharpened[i] - y[i]).coerceIn(-MAX_DELTA, MAX_DELTA)) }
    }

    /** Blemish size to heal for a picture of this size. */
    fun spotRadiusFor(width: Int, height: Int): Int =
        (max(width, height) / SPOT_RADIUS_STEP).coerceIn(MIN_SPOT_RADIUS, MAX_SPOT_RADIUS)

    /**
     * Enhances an image band by band so only one band (plus [HALO] rows on each side) is in memory. [read]
     * fills `into` from `offset` with `count` original rows starting at `top`; [write] stores enhanced rows.
     * Rows are written back in place, so the original rows a later band needs above it are kept aside first.
     * A first read-only pass measures the noise of the whole picture. The result is identical to [enhance] on
     * the whole image.
     */
    suspend fun enhanceInBands(
        width: Int,
        height: Int,
        strength: Float,
        bandRows: Int,
        read: (top: Int, count: Int, into: IntArray, offset: Int) -> Unit,
        write: (top: Int, count: Int, from: IntArray, offset: Int) -> Unit,
    ) {
        require(width > 0 && height > 0 && bandRows > 0) { "Sizes must be positive" }
        val sigma = measureNoise(width, height, bandRows, read)
        val spotRadius = spotRadiusFor(width, height)
        var top = 0
        var above: IntArray? = null
        while (top < height) {
            coroutineContext.ensureActive()
            val bottom = min(height, top + bandRows)
            val haloAbove = min(HALO, top)
            val readBottom = min(height, bottom + HALO)
            val rows = haloAbove + (readBottom - top)
            val buffer = IntArray(width * rows)
            above?.copyInto(buffer, 0)
            read(top, readBottom - top, buffer, haloAbove * width)
            val keep = min(HALO, bottom)
            val bandEnd = (haloAbove + bottom - top) * width
            above = buffer.copyOfRange(bandEnd - keep * width, bandEnd)
            val enhanced = enhance(buffer, width, rows, strength, sigma, spotRadius)
            write(top, bottom - top, enhanced, haloAbove * width)
            top = bottom
        }
    }

    /** Noise level of the whole picture, read band by band with one row of context on each side. */
    private suspend fun measureNoise(
        width: Int,
        height: Int,
        bandRows: Int,
        read: (top: Int, count: Int, into: IntArray, offset: Int) -> Unit,
    ): Float {
        val estimate = NoiseEstimate()
        var top = 0
        while (top < height) {
            coroutineContext.ensureActive()
            val bottom = min(height, top + bandRows)
            val readTop = max(0, top - 1)
            val readBottom = min(height, bottom + 1)
            val buffer = IntArray(width * (readBottom - readTop))
            read(readTop, readBottom - readTop, buffer, 0)
            val y = FloatArray(buffer.size) { i ->
                val p = buffer[i]
                luma((p shr RED_SHIFT) and CHANNEL, (p shr GREEN_SHIFT) and CHANNEL, p and CHANNEL)
            }
            // Only the band's own rows count, and only rows that are interior to the whole image.
            estimate.addRows(y, width, readBottom - readTop, max(1, top) - readTop, min(height - 1, bottom) - readTop)
            top = bottom
        }
        return estimate.sigma
    }

    /** Self-guided filter on luminance, then part of the original mixed back to keep texture. */
    private fun denoise(y: FloatArray, width: Int, height: Int, strength: Float, sigma: Float): FloatArray {
        val eps = (NOISE_FACTOR * sigma.coerceIn(0f, MAX_NOISE_SIGMA) * strength).let { it * it } + MIN_EPS
        val squares = FloatArray(y.size) { y[it] * y[it] }
        val mean = Box.mean(y, width, height, GUIDE_RADIUS)
        val meanSq = Box.mean(squares, width, height, GUIDE_RADIUS)
        val a = FloatArray(y.size)
        val b = FloatArray(y.size)
        for (i in y.indices) {
            val variance = max(0f, meanSq[i] - mean[i] * mean[i])
            a[i] = variance / (variance + eps)
            b[i] = mean[i] - a[i] * mean[i]
        }
        val meanA = Box.mean(a, width, height, GUIDE_RADIUS)
        val meanB = Box.mean(b, width, height, GUIDE_RADIUS)
        val mix = DENOISE_MIX * strength
        return FloatArray(y.size) { i ->
            val filtered = meanA[i] * y[i] + meanB[i]
            y[i] + (filtered - y[i]) * mix
        }
    }

    private class Spots(val width: Int, val height: Int, val strength: Float, val radius: Int, val threshold: Float)

    /** Lifts small dark spots on skin part of the way to their surroundings, in place. */
    private fun healSpots(y: FloatArray, skin: FloatArray, spots: Spots) {
        val closed = Morphology.close(y, spots.width, spots.height, spots.radius)
        for (i in y.indices) {
            if (skin[i] <= 0f) continue
            val depth = closed[i] - y[i] - spots.threshold
            if (depth > 0f) y[i] += skin[i] * spots.strength * min(SPOT_MIX * depth, SPOT_MAX_LIFT)
        }
    }

    /** Soft coring: detail below [CORING] fades to zero so flat noise is not amplified. */
    private fun core(detail: Float): Float {
        val magnitude = abs(detail)
        return if (magnitude >= CORING) detail else detail * magnitude / CORING
    }

    /** Adds [delta] to every colour channel, which changes Y by [delta] and leaves Cb and Cr untouched. */
    private fun shiftLuma(argb: Int, delta: Float): Int {
        val d = delta.roundToInt()
        if (d == 0) return argb
        val r = (((argb shr RED_SHIFT) and CHANNEL) + d).coerceIn(0, CHANNEL)
        val g = (((argb shr GREEN_SHIFT) and CHANNEL) + d).coerceIn(0, CHANNEL)
        val b = ((argb and CHANNEL) + d).coerceIn(0, CHANNEL)
        return (argb and ALPHA_MASK) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    }

    /** BT.601 luma. */
    fun luma(r: Int, g: Int, b: Int): Float = RED_WEIGHT * r + GREEN_WEIGHT * g + BLUE_WEIGHT * b

    fun chromaBlue(r: Int, g: Int, b: Int): Float = MID - CB_R * r - CB_G * g + HALF * b

    fun chromaRed(r: Int, g: Int, b: Int): Float = MID + HALF * r - CR_G * g - CR_B * b

    /**
     * 0..1 membership in the classic YCbCr skin region (Cb 77..127, Cr 133..173), which holds for light,
     * wheatish, brown and dark skin alike because it ignores brightness. Soft edges avoid visible seams.
     */
    fun skinWeight(r: Int, g: Int, b: Int): Float {
        if (luma(r, g, b) < MIN_SKIN_LUMA) return 0f
        val cb = chromaBlue(r, g, b)
        val cr = chromaRed(r, g, b)
        return ramp(cb, CB_MIN, CB_MAX) * ramp(cr, CR_MIN, CR_MAX)
    }

    private fun ramp(v: Float, low: Float, high: Float): Float = when {
        v < low - SKIN_SOFT || v > high + SKIN_SOFT -> 0f
        v < low -> (v - (low - SKIN_SOFT)) / SKIN_SOFT
        v > high -> ((high + SKIN_SOFT) - v) / SKIN_SOFT
        else -> 1f
    }

    private const val RED_WEIGHT = 0.299f
    private const val GREEN_WEIGHT = 0.587f
    private const val BLUE_WEIGHT = 0.114f
    private const val MID = 128f
    private const val HALF = 0.5f
    private const val CB_R = 0.168736f
    private const val CB_G = 0.331264f
    private const val CR_G = 0.418688f
    private const val CR_B = 0.081312f
    private const val CB_MIN = 77f
    private const val CB_MAX = 127f
    private const val CR_MIN = 133f
    private const val CR_MAX = 173f
    private const val SKIN_SOFT = 4f
    private const val MIN_SKIN_LUMA = 20f
    private const val MIN_EPS = 1e-3f
    private const val CHANNEL = 255
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ALPHA_MASK = -0x1000000
}

/**
 * Immerkaer's fast noise estimate: the mean absolute response to a Laplacian-difference mask that cancels
 * smooth image structure, scaled to a Gaussian standard deviation. Rows can be added in pieces.
 */
internal class NoiseEstimate {
    private var sum = 0.0
    private var count = 0L

    val sigma: Float
        get() = if (count == 0L) 0f else (sum * SQRT_HALF_PI / (MASK_WEIGHT * count)).toFloat()

    /** Adds rows [fromRow] until [toRow] of [y]; each needs its neighbours above and below inside [y]. */
    fun addRows(y: FloatArray, width: Int, height: Int, fromRow: Int, toRow: Int) {
        if (width < MIN_SIDE) return
        for (row in max(1, fromRow) until min(height - 1, toRow)) {
            val c = row * width
            val u = c - width
            val d = c + width
            for (x in 1 until width - 1) {
                // Mask [1 -2 1; -2 4 -2; 1 -2 1]: zero response to flat areas, ramps and straight edges.
                val v = y[u + x - 1] - 2 * y[u + x] + y[u + x + 1] -
                    2 * y[c + x - 1] + CENTER * y[c + x] - 2 * y[c + x + 1] +
                    y[d + x - 1] - 2 * y[d + x] + y[d + x + 1]
                sum += abs(v)
            }
            count += width - 2
        }
    }

    private companion object {
        const val SQRT_HALF_PI = 1.2533141373155003
        const val CENTER = 4f
        const val MIN_SIDE = 3
        const val MASK_WEIGHT = 6.0
    }
}

/** Grey-level morphology with a square window, separable and clamped at the borders. */
internal object Morphology {
    /** Closing (max, then min): fills dark details narrower than the window and keeps edges where they are. */
    fun close(src: FloatArray, width: Int, height: Int, radius: Int): FloatArray =
        filter(filter(src, width, height, radius, max = true), width, height, radius, max = false)

    private fun filter(src: FloatArray, width: Int, height: Int, radius: Int, max: Boolean): FloatArray {
        val horizontal = FloatArray(src.size)
        for (y in 0 until height) line(src, horizontal, y * width, 1, width, radius, max)
        val out = FloatArray(src.size)
        for (x in 0 until width) line(horizontal, out, x, width, height, radius, max)
        return out
    }

    @Suppress("LongParameterList")
    private fun line(src: FloatArray, dst: FloatArray, start: Int, step: Int, length: Int, radius: Int, max: Boolean) {
        val last = length - 1
        for (i in 0 until length) {
            var v = src[start + i * step]
            for (k in i - radius..i + radius) {
                val s = src[start + k.coerceIn(0, last) * step]
                v = if (max) max(v, s) else min(v, s)
            }
            dst[start + i * step] = v
        }
    }
}

/**
 * Separable box mean with edge clamping. Each output sums its own window directly (the radii here are tiny),
 * so a pixel's value never depends on where a line starts, which keeps banded and whole-image results identical.
 */
internal object Box {
    fun mean(src: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val horizontal = FloatArray(src.size)
        for (y in 0 until height) line(src, horizontal, y * width, 1, width, radius)
        val out = FloatArray(src.size)
        for (x in 0 until width) line(horizontal, out, x, width, height, radius)
        return out
    }

    /** One row or column; samples outside the line repeat the edge value. */
    private fun line(src: FloatArray, dst: FloatArray, start: Int, step: Int, length: Int, radius: Int) {
        val last = length - 1
        val size = (2 * radius + 1).toFloat()
        for (i in 0 until length) {
            var sum = 0f
            for (k in i - radius..i + radius) sum += src[start + k.coerceIn(0, last) * step]
            dst[start + i * step] = sum / size
        }
    }
}
