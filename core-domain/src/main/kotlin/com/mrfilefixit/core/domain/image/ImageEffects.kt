package com.mrfilefixit.core.domain.image

import kotlinx.serialization.Serializable

/**
 * Pixel edits applied after the geometry of an [ImageRequest] (rotate, crop, resize), in this order:
 * [upscale], then [filter] and [adjust], then the exam [stamp] bar, then the [texts]. Serializable so a batch
 * handed to WorkManager carries them.
 */
@Serializable
data class ImageEffects(
    val adjust: ColorAdjust = ColorAdjust(),
    val filter: ImageFilter = ImageFilter.NONE,
    val stamp: PhotoStamp? = null,
    val texts: List<TextOverlay> = emptyList(),
    val upscale: Upscale? = null,
) {
    val isNone: Boolean get() = this == NONE

    companion object {
        val NONE = ImageEffects()
    }
}

@Serializable
enum class ImageFilter {
    NONE,
    GRAYSCALE,

    /** White paper and dark text, the Scan tool's "document" look. */
    DOCUMENT,
}

/** Each value is -100..100; 0 leaves the picture unchanged. */
@Serializable
data class ColorAdjust(val brightness: Int = 0, val contrast: Int = 0, val saturation: Int = 0) {
    init {
        require(brightness in RANGE && contrast in RANGE && saturation in RANGE) { "Adjustments must be -100..100" }
    }

    val isNone: Boolean get() = brightness == 0 && contrast == 0 && saturation == 0

    companion object {
        val RANGE = -100..100
    }
}

/**
 * The name and date bar exam portals ask for: a band across the bottom of the photo with up to three
 * centred lines of text. [dark] is a black band with white text; otherwise white with black text.
 */
@Serializable
data class PhotoStamp(
    val lines: List<String>,
    val dark: Boolean = false,
    /** Band height as a share of the photo height. */
    val heightFraction: Float = DEFAULT_HEIGHT,
) {
    init {
        require(lines.size in 1..MAX_LINES) { "A stamp has 1..$MAX_LINES lines" }
        require(heightFraction in MIN_HEIGHT..MAX_HEIGHT) { "Stamp height must be $MIN_HEIGHT..$MAX_HEIGHT" }
    }

    companion object {
        const val MAX_LINES = 3
        const val DEFAULT_HEIGHT = 0.2f
        const val MIN_HEIGHT = 0.08f
        const val MAX_HEIGHT = 0.4f
    }
}

enum class OverlayPosition(val y: Float) { TOP(0.08f), CENTER(0.5f), BOTTOM(0.92f) }

/**
 * A line of text drawn on the picture. [x] and [y] are the centre of the text as a share of the width and
 * height; [sizeFraction] is the text height as a share of the picture height.
 */
@Serializable
data class TextOverlay(
    val text: String,
    val x: Float = 0.5f,
    val y: Float = OverlayPosition.BOTTOM.y,
    val sizeFraction: Float = DEFAULT_SIZE,
    val colorArgb: Int = WHITE,
    /** A soft dark outline that keeps light text readable on any background. */
    val outline: Boolean = true,
) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "Overlay position must be inside the picture" }
        require(sizeFraction in MIN_SIZE..MAX_SIZE) { "Overlay size must be $MIN_SIZE..$MAX_SIZE" }
    }

    companion object {
        const val DEFAULT_SIZE = 0.06f
        const val MIN_SIZE = 0.02f
        const val MAX_SIZE = 0.3f
        const val WHITE = -0x1
    }
}

/** Upscaling by [factor] (2 or 4) with smooth interpolation, then sharpening of [sharpen] (0..1). */
@Serializable
data class Upscale(val factor: Int, val sharpen: Float = DEFAULT_SHARPEN) {
    init {
        require(factor == 2 || factor == 4) { "Upscale factor must be 2 or 4" }
        require(sharpen in 0f..1f) { "Sharpen must be 0..1" }
    }

    companion object {
        const val DEFAULT_SHARPEN = 0.6f

        /** Long side cap of the result, so a 4x upscale stays within a phone's memory. */
        const val MAX_SIDE = 3072
    }
}
