package com.mrfilefixit.core.domain.image

import kotlinx.serialization.Serializable

/**
 * Pixel edits applied after the geometry of an [ImageRequest] (rotate, crop, resize), in this order:
 * [background] replacement, [enhance], then [filter] and [adjust], then the exam [stamp] bar, then the [texts].
 * Serializable so a batch handed to WorkManager carries them.
 */
@Serializable
data class ImageEffects(
    val adjust: ColorAdjust = ColorAdjust(),
    val filter: ImageFilter = ImageFilter.NONE,
    val stamp: PhotoStamp? = null,
    val texts: List<TextOverlay> = emptyList(),
    val enhance: NaturalEnhance? = null,
    val background: BackgroundReplace? = null,
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
    /** A soft outline in the opposite tone (dark round light text, light round dark text), readable anywhere. */
    val outline: Boolean = true,
    val font: OverlayFont = OverlayFont.SANS,
) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "Overlay position must be inside the picture" }
        require(sizeFraction in MIN_SIZE..MAX_SIZE) { "Overlay size must be $MIN_SIZE..$MAX_SIZE" }
    }

    /** Whether the text colour is light, so its outline is dark (and the other way round). */
    val isLight: Boolean get() = isLightColor(colorArgb)

    companion object {
        const val DEFAULT_SIZE = 0.06f
        const val MIN_SIZE = 0.02f
        const val MAX_SIZE = 0.3f
        const val WHITE = -0x1
        private const val LIGHT_LUMA = 140f
        private const val RED_WEIGHT = 0.299f
        private const val GREEN_WEIGHT = 0.587f
        private const val BLUE_WEIGHT = 0.114f
        private const val CHANNEL = 0xFF
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8

        fun isLightColor(argb: Int): Boolean {
            val r = (argb shr RED_SHIFT) and CHANNEL
            val g = (argb shr GREEN_SHIFT) and CHANNEL
            val b = argb and CHANNEL
            return RED_WEIGHT * r + GREEN_WEIGHT * g + BLUE_WEIGHT * b > LIGHT_LUMA
        }
    }
}

/** Font families for text on a picture; [family] is the Android system font family name (always installed). */
@Serializable
enum class OverlayFont(val family: String) {
    SANS("sans-serif"),
    SERIF("serif"),
    MONO("monospace"),
    CONDENSED("sans-serif-condensed"),
    HANDWRITING("cursive"),
    CASUAL("casual"),
}

/**
 * Natural Enhance: an optional [scale]x Lanczos upscale, then luminance-only denoise, gentle spot healing and
 * clarity at [strength] (0..1). Colours and skin tone are never changed (see NaturalEnhancer).
 */
@Serializable
data class NaturalEnhance(val strength: Float = DEFAULT_STRENGTH, val scale: Int = 1) {
    init {
        require(strength in 0f..1f) { "Strength must be 0..1" }
        require(scale in SCALES) { "Scale must be one of $SCALES" }
    }

    companion object {
        const val DEFAULT_STRENGTH = 0.6f
        val SCALES = listOf(1, 2, 4)

        /** Long side cap of an upscaled result, so a 4x upscale stays within a phone's memory. */
        const val MAX_SIDE = 3072
    }
}
