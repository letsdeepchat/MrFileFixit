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
     */
    fun colorMatrix(adjust: ColorAdjust, grayscale: Boolean): FloatArray {
        val saturation = if (grayscale) 0f else 1f + adjust.saturation / PERCENT
        val contrast = if (adjust.contrast >= 0) {
            1f + CONTRAST_UP * adjust.contrast / PERCENT
        } else {
            1f + CONTRAST_DOWN * adjust.contrast / PERCENT
        }
        val offset = adjust.brightness / PERCENT * BRIGHTNESS_SPAN + MID_GREY * (1f - contrast)
        val inv = 1f - saturation
        val r = inv * RED_WEIGHT
        val g = inv * GREEN_WEIGHT
        val b = inv * BLUE_WEIGHT
        return floatArrayOf(
            contrast * (r + saturation), contrast * g, contrast * b, 0f, offset,
            contrast * r, contrast * (g + saturation), contrast * b, 0f, offset,
            contrast * r, contrast * g, contrast * (b + saturation), 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    }

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
