package com.mrfilefixit.core.domain.image.enhance

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The second half of Enhance: auto contrast, a gentle exposure fix and vibrance, so a dull or flat photo
 * visibly comes alive. Built from a [sample] of the picture's pixels, then applied to every pixel with
 * [apply], so a large photo can be processed band by band with the same curve.
 *
 * * **Auto contrast.** The luminance range between the 0.5th and 99.5th percentile is stretched toward the full
 *   range, by at most [MAX_GAIN]. The stretch pivots on the picture's own mean brightness, and skin takes only
 *   half of it, so faces are neither lightened nor darkened much.
 * * **Exposure.** Only a clearly dark or washed-out picture (mean outside [DARK]..[BRIGHT]) has its mean moved a
 *   little toward mid grey, at most [MAX_SHIFT] levels.
 * * **Vibrance.** Colour is boosted more where it is dull and less where it is already rich. Skin gets only a
 *   small share of it, so skin keeps its tone instead of turning orange.
 *
 * The luminance change is added equally to R, G and B (as [NaturalEnhancer] does), and vibrance scales each
 * pixel's colour difference from its own luminance, so the hue of every pixel stays the same.
 */
class ToneVibrance private constructor(private val lut: IntArray, private val vibrance: Float) {
    /** Applies the curve to [count] pixels of [pixels] starting at [offset], in place. Alpha is kept. */
    fun apply(pixels: IntArray, offset: Int = 0, count: Int = pixels.size - offset) {
        for (i in offset until offset + count) pixels[i] = applyPixel(pixels[i])
    }

    fun applyPixel(argb: Int): Int {
        val r = (argb shr RED_SHIFT) and CHANNEL
        val g = (argb shr GREEN_SHIFT) and CHANNEL
        val b = argb and CHANNEL
        val y = NaturalEnhancer.luma(r, g, b)
        val skin = NaturalEnhancer.skinWeight(r, g, b)
        // Skin takes only part of the contrast change, so faces are neither darkened nor lightened much.
        val newY = y + (lut[y.roundToInt().coerceIn(0, CHANNEL)] - y) * (1f - SKIN_TONE_PROTECT * skin)
        val factor = 1f + vibranceFor(r, g, b, y, skin)
        val nr = (newY + factor * (r - y)).roundToInt().coerceIn(0, CHANNEL)
        val ng = (newY + factor * (g - y)).roundToInt().coerceIn(0, CHANNEL)
        val nb = (newY + factor * (b - y)).roundToInt().coerceIn(0, CHANNEL)
        return (argb and ALPHA_MASK) or (nr shl RED_SHIFT) or (ng shl GREEN_SHIFT) or nb
    }

    /** Extra colour for this pixel: full [vibrance] on a grey-ish pixel, none on a fully saturated one. */
    private fun vibranceFor(r: Int, g: Int, b: Int, y: Float, skin: Float): Float {
        if (vibrance == 0f) return 0f
        val chroma = maxOf(abs(r - y), abs(g - y), abs(b - y)) / CHANNEL
        val dullness = (1f - chroma * DULL_SCALE).coerceIn(0f, 1f)
        return vibrance * dullness * (1f - SKIN_PROTECT * skin)
    }

    val isIdentity: Boolean get() = vibrance == 0f && lut.indices.all { lut[it] == it }

    companion object {
        /** The largest contrast stretch; more makes noise and JPEG blocks show. */
        const val MAX_GAIN = 1.4f
        const val MAX_SHIFT = 18f
        const val DARK = 85f
        const val BRIGHT = 175f

        /** Vibrance at full strength: dull colours get up to 30% more saturation. */
        const val MAX_VIBRANCE = 0.3f

        private const val LOW_PERCENTILE = 0.005f
        private const val HIGH_PERCENTILE = 0.995f
        private const val TARGET_RANGE = 240f
        private const val MID = 128f
        private const val EXPOSURE_SHARE = 0.5f
        private const val DULL_SCALE = 2.5f
        private const val SKIN_PROTECT = 0.75f
        private const val SKIN_TONE_PROTECT = 0.5f
        private const val CHANNEL = 255
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val ALPHA_MASK = -0x1000000

        /** The curve for a picture whose pixels (or an even sample of them) are [sample], at [strength] 0..1. */
        fun from(sample: IntArray, strength: Float): ToneVibrance {
            val s = strength.coerceIn(0f, 1f)
            if (s == 0f || sample.isEmpty()) return ToneVibrance(IntArray(CHANNEL + 1) { it }, 0f)
            val histogram = IntArray(CHANNEL + 1)
            var sum = 0.0
            sample.forEach { p ->
                val r = (p shr RED_SHIFT) and CHANNEL
                val g = (p shr GREEN_SHIFT) and CHANNEL
                val y = NaturalEnhancer.luma(r, g, p and CHANNEL)
                histogram[y.roundToInt().coerceIn(0, CHANNEL)]++
                sum += y
            }
            val mean = (sum / sample.size).toFloat()
            val low = percentile(histogram, sample.size, LOW_PERCENTILE)
            val high = percentile(histogram, sample.size, HIGH_PERCENTILE)
            val gain = (TARGET_RANGE / (high - low).coerceAtLeast(1)).coerceIn(1f, MAX_GAIN)
            val shift = if (mean < DARK || mean > BRIGHT) {
                ((MID - mean) * EXPOSURE_SHARE).coerceIn(-MAX_SHIFT, MAX_SHIFT)
            } else {
                0f
            }
            val lut = IntArray(CHANNEL + 1) { y ->
                val target = mean + shift + (y - mean) * gain
                (y + s * (target - y)).roundToInt().coerceIn(0, CHANNEL)
            }
            return ToneVibrance(lut, s * MAX_VIBRANCE)
        }

        private fun percentile(histogram: IntArray, total: Int, share: Float): Int {
            val wanted = (total * share).toInt()
            var seen = 0
            histogram.forEachIndexed { level, count ->
                seen += count
                if (seen > wanted) return level
            }
            return CHANNEL
        }
    }
}
