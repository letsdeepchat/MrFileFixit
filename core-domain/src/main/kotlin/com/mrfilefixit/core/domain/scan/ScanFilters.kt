package com.mrfilefixit.core.domain.scan

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Post-scan look. [DOCUMENT_CLEAN] is the default: white paper, crisp dark text, no shadows. */
enum class ScanFilter { DOCUMENT_CLEAN, MAGIC_COLOR, GRAYSCALE, ORIGINAL }

/**
 * Pixel maths for the scan filters, on packed ARGB ints so it is unit-tested without Android.
 *
 * All three filters start from the same idea, background normalisation: estimate the local paper
 * brightness (the "illumination map") on a small copy of the page, then divide every full-resolution
 * pixel by it. Shadows, flash fall-off and yellowish paper all become flat white, while ink stays dark,
 * because ink is much darker than the paper around it. This is the adaptive, local equivalent of Otsu
 * or Sauvola thresholding, but it produces smooth anti-aliased edges instead of jagged 1-bit text.
 */
object ScanFilterMath {
    private const val MAX_CHANNEL = 255
    private const val RED_WEIGHT = 299
    private const val GREEN_WEIGHT = 587
    private const val BLUE_WEIGHT = 114
    private const val WEIGHT_TOTAL = 1000

    /** Below this paper brightness the map is clamped, so very dark corners do not blow up into noise. */
    private const val MIN_BACKGROUND = 24

    // Document clean: ratio (pixel / paper) at or above WHITE_AT becomes pure white; at or below INK_AT pure ink.
    private const val CLEAN_INK_AT = 0.55f
    private const val CLEAN_WHITE_AT = 0.88f

    // Magic colour: stretch each normalised channel and boost saturation.
    private const val MAGIC_BLACK = 0.12f
    private const val MAGIC_WHITE = 0.92f
    private const val MAGIC_SATURATION = 1.35f

    // Grayscale: gentler stretch that keeps grey tones such as stamps and photos.
    private const val GRAY_BLACK = 0.08f
    private const val GRAY_WHITE = 0.94f

    fun luminance(argb: Int): Int {
        val r = (argb shr RED_SHIFT) and MAX_CHANNEL
        val g = (argb shr GREEN_SHIFT) and MAX_CHANNEL
        val b = argb and MAX_CHANNEL
        return (r * RED_WEIGHT + g * GREEN_WEIGHT + b * BLUE_WEIGHT) / WEIGHT_TOTAL
    }

    /**
     * Paper brightness per pixel of a small luminance image ([width] x [height], values 0-255): a max filter
     * removes ink strokes thinner than [radius], then a box blur of the same radius smooths the result.
     * Both passes are separable, so the cost is O(width * height * radius).
     */
    fun illuminationMap(luma: IntArray, width: Int, height: Int, radius: Int): IntArray {
        require(luma.size == width * height) { "luma must be width * height" }
        val dilated = separable(luma, width, height, radius) { window -> window.max() }
        val blurred = separable(dilated, width, height, radius) { window -> window.average().roundToInt() }
        for (i in blurred.indices) blurred[i] = max(blurred[i], MIN_BACKGROUND)
        return blurred
    }

    /** Background at full-resolution position ([x], [y]) of a [fullWidth] x [fullHeight] page, bilinear. */
    fun sampleMap(
        map: IntArray,
        mapWidth: Int,
        mapHeight: Int,
        x: Int,
        y: Int,
        fullWidth: Int,
        fullHeight: Int,
    ): Int {
        val fx = ((x + HALF) * mapWidth / fullWidth - HALF).coerceIn(0f, (mapWidth - 1).toFloat())
        val fy = ((y + HALF) * mapHeight / fullHeight - HALF).coerceIn(0f, (mapHeight - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(x0 + 1, mapWidth - 1)
        val y1 = min(y0 + 1, mapHeight - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = map[y0 * mapWidth + x0] * (1 - tx) + map[y0 * mapWidth + x1] * tx
        val bottom = map[y1 * mapWidth + x0] * (1 - tx) + map[y1 * mapWidth + x1] * tx
        return (top * (1 - ty) + bottom * ty).roundToInt()
    }

    /** Applies [filter] to one pixel whose local paper brightness is [background] (0-255). Alpha becomes opaque. */
    fun apply(filter: ScanFilter, argb: Int, background: Int): Int = when (filter) {
        ScanFilter.ORIGINAL -> argb or OPAQUE
        ScanFilter.DOCUMENT_CLEAN -> gray(cleanLevel(luminance(argb), background))
        ScanFilter.GRAYSCALE -> gray(stretch(ratio(luminance(argb), background), GRAY_BLACK, GRAY_WHITE))
        ScanFilter.MAGIC_COLOR -> magic(argb, background)
    }

    private fun cleanLevel(luma: Int, background: Int): Int {
        val t = ((ratio(luma, background) - CLEAN_INK_AT) / (CLEAN_WHITE_AT - CLEAN_INK_AT)).coerceIn(0f, 1f)
        // Smoothstep: crisp text with a soft one-pixel edge instead of jaggies.
        return (t * t * (SMOOTH_A - 2 * t) * MAX_CHANNEL).roundToInt()
    }

    private fun magic(argb: Int, background: Int): Int {
        val r = stretch(ratio((argb shr RED_SHIFT) and MAX_CHANNEL, background), MAGIC_BLACK, MAGIC_WHITE)
        val g = stretch(ratio((argb shr GREEN_SHIFT) and MAX_CHANNEL, background), MAGIC_BLACK, MAGIC_WHITE)
        val b = stretch(ratio(argb and MAX_CHANNEL, background), MAGIC_BLACK, MAGIC_WHITE)
        val luma = (r * RED_WEIGHT + g * GREEN_WEIGHT + b * BLUE_WEIGHT) / WEIGHT_TOTAL
        fun saturate(c: Int) = (luma + (c - luma) * MAGIC_SATURATION).roundToInt().coerceIn(0, MAX_CHANNEL)
        return rgb(saturate(r), saturate(g), saturate(b))
    }

    private fun ratio(value: Int, background: Int): Float = value.toFloat() / max(background, MIN_BACKGROUND)

    private fun stretch(ratio: Float, black: Float, white: Float): Int =
        (((ratio - black) / (white - black)).coerceIn(0f, 1f) * MAX_CHANNEL).roundToInt()

    private fun gray(level: Int): Int = rgb(level, level, level)

    private fun rgb(r: Int, g: Int, b: Int): Int = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b

    /** Runs [reduce] over a sliding window of size 2 * radius + 1, first along rows, then along columns. */
    private inline fun separable(
        source: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        reduce: (IntArray) -> Int,
    ): IntArray {
        val horizontal = IntArray(source.size)
        val window = IntArray(2 * radius + 1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                for (k in -radius..radius) window[k + radius] = source[y * width + (x + k).coerceIn(0, width - 1)]
                horizontal[y * width + x] = reduce(window)
            }
        }
        val out = IntArray(source.size)
        for (x in 0 until width) {
            for (y in 0 until height) {
                for (k in -radius..radius) window[k + radius] = horizontal[(y + k).coerceIn(0, height - 1) * width + x]
                out[y * width + x] = reduce(window)
            }
        }
        return out
    }

    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val OPAQUE = -0x1000000 // 0xFF000000
    private const val HALF = 0.5f
    private const val SMOOTH_A = 3f
}
