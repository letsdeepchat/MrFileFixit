package com.mrfilefixit.core.domain.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pixel maths behind [ImageEffects], on plain arrays so it is unit-tested without Android. */
object ImageEffectMath {
    private const val CHANNEL_MAX = 255
    private const val RED_WEIGHT = 0.299f
    private const val GREEN_WEIGHT = 0.587f
    private const val BLUE_WEIGHT = 0.114f
    private const val PERCENT = 100f

    /** Full brightness slider (100) shifts every channel by this much. */
    private const val BRIGHTNESS_SPAN = 100f

    /** Full contrast slider (100) doubles contrast; -100 flattens it to a quarter. */
    private const val CONTRAST_UP = 1f
    private const val CONTRAST_DOWN = 0.75f
    private const val MID_GREY = 128f

    /**
     * A 4x5 colour matrix (row-major, the layout Android's ColorMatrix uses) that applies [adjust] and, with
     * [grayscale], drops the colour first. Saturation is applied around each pixel's luminance.
     *
     * Black & white is a real monochrome print: with [levels] (from [grayLevels]) the picture's own darkest and
     * brightest tones are stretched to black and white, with a little extra punch ([BW_PUNCH]), so the result is
     * clearly different from the colour photo even when the photo was dull.
     */
    fun colorMatrix(adjust: ColorAdjust, grayscale: Boolean, levels: GrayLevels? = null): FloatArray {
        val saturation = if (grayscale) 0f else 1f + adjust.saturation / PERCENT
        val contrast = if (adjust.contrast >= 0) {
            1f + CONTRAST_UP * adjust.contrast / PERCENT
        } else {
            1f + CONTRAST_DOWN * adjust.contrast / PERCENT
        }
        // The black & white stretch: luma' = gain * (luma - pivot) + mid grey, folded into the same matrix.
        val gain = if (grayscale && levels != null) levels.gain else 1f
        val pivot = if (grayscale && levels != null) levels.pivot else MID_GREY
        val offset = adjust.brightness / PERCENT * BRIGHTNESS_SPAN + MID_GREY - contrast * gain * pivot
        val k = contrast * gain
        val inv = 1f - saturation
        val r = inv * RED_WEIGHT
        val g = inv * GREEN_WEIGHT
        val b = inv * BLUE_WEIGHT
        return floatArrayOf(
            k * (r + saturation), k * g, k * b, 0f, offset,
            k * r, k * (g + saturation), k * b, 0f, offset,
            k * r, k * g, k * (b + saturation), 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** How black & white stretches a picture's tones: luma' = [gain] * (luma - [pivot]) + 128. */
    data class GrayLevels(val gain: Float, val pivot: Float)

    /**
     * The black & white stretch for a picture whose pixels (or an even sample of them) are [sample]: the 1st and
     * 99th luminance percentiles go to black and white, times [BW_PUNCH], with the gain capped at [MAX_BW_GAIN]
     * so a nearly flat picture does not turn into noise.
     */
    fun grayLevels(sample: IntArray): GrayLevels {
        if (sample.isEmpty()) return GrayLevels(BW_PUNCH, MID_GREY)
        val histogram = IntArray(CHANNEL_MAX + 1)
        sample.forEach { p ->
            val y = RED_WEIGHT * ((p shr RED_SHIFT) and CHANNEL_MAX) +
                GREEN_WEIGHT * ((p shr GREEN_SHIFT) and CHANNEL_MAX) + BLUE_WEIGHT * (p and CHANNEL_MAX)
            histogram[y.roundToInt().coerceIn(0, CHANNEL_MAX)]++
        }
        val low = percentile(histogram, sample.size, BW_LOW)
        val high = percentile(histogram, sample.size, BW_HIGH)
        val gain = (BW_PUNCH * CHANNEL_MAX / max(1, high - low)).coerceIn(BW_PUNCH, MAX_BW_GAIN)
        return GrayLevels(gain, (low + high) / 2f)
    }

    private fun percentile(histogram: IntArray, total: Int, share: Float): Int {
        val wanted = (total * share).toInt()
        var seen = 0
        histogram.forEachIndexed { level, count ->
            seen += count
            if (seen > wanted) return level
        }
        return CHANNEL_MAX
    }

    /** Black & white is a little harder than a plain luminance copy, like a monochrome print. */
    const val BW_PUNCH = 1.15f
    private const val MAX_BW_GAIN = 3f
    private const val BW_LOW = 0.01f
    private const val BW_HIGH = 0.99f

    /** Applies a [colorMatrix] to one ARGB pixel, as the platform's ColorMatrixColorFilter does. */
    fun applyMatrix(matrix: FloatArray, argb: Int): Int {
        val a = argb ushr ALPHA_SHIFT
        val r = (argb shr RED_SHIFT) and CHANNEL_MAX
        val g = (argb shr GREEN_SHIFT) and CHANNEL_MAX
        val b = argb and CHANNEL_MAX
        fun row(i: Int): Int {
            val v = matrix[i] * r + matrix[i + G] * g + matrix[i + B] * b + matrix[i + A] * a + matrix[i + OFFSET]
            return v.roundToInt().coerceIn(0, CHANNEL_MAX)
        }
        return (a shl ALPHA_SHIFT) or (row(0) shl RED_SHIFT) or (row(ROW) shl GREEN_SHIFT) or row(2 * ROW)
    }

    /**
     * Output size of a [NaturalEnhance]: [NaturalEnhance.scale] times bigger, scaled back so the long side fits
     * [NaturalEnhance.MAX_SIDE]. An image already at or above the cap keeps its size (never shrinks).
     */
    fun enhancedSize(width: Int, height: Int, enhance: NaturalEnhance): Pair<Int, Int> {
        if (enhance.scale == 1) return width to height
        val wanted = max(width, height) * enhance.scale
        val scale = max(1f, enhance.scale * min(1f, NaturalEnhance.MAX_SIDE.toFloat() / wanted))
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** Where the [PhotoStamp] band and its lines go on a [width] x [height] photo. */
    data class StampLayout(val bandTop: Int, val bandHeight: Int, val textSizePx: Float, val baselines: List<Float>)

    private const val LINE_GAP = 1.2f
    private const val TEXT_SHARE = 0.8f
    private const val BASELINE_SHARE = 0.78f

    /** Lines share the band equally, sized so the widest of them fits [width] at an average glyph width. */
    fun stampLayout(width: Int, height: Int, stamp: PhotoStamp): StampLayout {
        val bandHeight = max(1, (height * stamp.heightFraction).roundToInt())
        val bandTop = height - bandHeight
        val slot = bandHeight.toFloat() / stamp.lines.size
        val longest = stamp.lines.maxOf { it.length }.coerceAtLeast(1)
        val byWidth = width * TEXT_SHARE / (longest * AVERAGE_GLYPH_WIDTH)
        val textSize = min(slot / LINE_GAP, byWidth).coerceAtLeast(1f)
        val baselines = stamp.lines.indices.map { i -> bandTop + slot * i + slot * BASELINE_SHARE }
        return StampLayout(bandTop, bandHeight, textSize, baselines)
    }

    /** A typical sans-serif glyph is about this share of the text size wide. */
    private const val AVERAGE_GLYPH_WIDTH = 0.55f

    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ROW = 5
    private const val G = 1
    private const val B = 2
    private const val A = 3
    private const val OFFSET = 4
}
