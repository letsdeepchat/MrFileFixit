package com.mrfilefixit.feature.image.ui

import com.mrfilefixit.core.domain.image.BackgroundFill
import com.mrfilefixit.core.domain.image.BackgroundReplace
import com.mrfilefixit.core.domain.image.ColorAdjust
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageEffects
import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.NaturalEnhance
import com.mrfilefixit.core.domain.image.OverlayFont
import com.mrfilefixit.core.domain.image.OverlayPosition
import com.mrfilefixit.core.domain.image.PhotoStamp
import com.mrfilefixit.core.domain.image.TextOverlay

/** The Edit tab: a look, three sliders, an optional line of styled text and the exam name and date bar. */
data class EditOptions(
    val filter: ImageFilter = ImageFilter.NONE,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val text: String = "",
    val textPosition: OverlayPosition = OverlayPosition.BOTTOM,
    /** Text colour as ARGB, from the swatches or the custom picker. */
    val textColor: Int = TextOverlay.WHITE,
    val textFont: OverlayFont = OverlayFont.SANS,
    /** Text height as a share of the picture height. */
    val textSize: Float = TextOverlay.DEFAULT_SIZE,
    val stamp: Boolean = false,
    val stampName: String = "",
    val stampDate: String = "",
    val stampDark: Boolean = false,
    /** Exam portals take the stamped photo as a 20 to 50 KB JPEG. */
    val stampKeepSize: Boolean = true,
) {
    val stampLines: List<String> get() = listOf(stampName, stampDate).map { it.trim() }.filter { it.isNotEmpty() }

    /** The stamp target applies only while there is a stamp to draw. */
    val usesStampSize: Boolean get() = stamp && stampKeepSize && stampLines.isNotEmpty()

    fun toEffects(): ImageEffects = ImageEffects(
        adjust = ColorAdjust(
            brightness.coerceIn(ColorAdjust.RANGE),
            contrast.coerceIn(ColorAdjust.RANGE),
            saturation.coerceIn(ColorAdjust.RANGE),
        ),
        filter = filter,
        stamp = if (stamp && stampLines.isNotEmpty()) PhotoStamp(stampLines, dark = stampDark) else null,
        texts = text.trim().takeIf { it.isNotEmpty() }?.let {
            // Every colour gets a soft outline in the opposite tone, so it reads on bright and dark photos alike.
            val size = textSize.coerceIn(TextOverlay.MIN_SIZE, MAX_TEXT_SIZE)
            val overlay = TextOverlay(
                it,
                y = textPosition.y,
                sizeFraction = size,
                colorArgb = opaque(textColor),
                font = textFont,
            )
            listOf(overlay)
        }.orEmpty(),
    )

    companion object {
        const val BLACK = -0x1000000

        /** The size slider stops at a fifth of the picture height; bigger text rarely fits a line. */
        const val MAX_TEXT_SIZE = 0.2f
        private const val OPAQUE = -0x1000000

        /** Text is always fully opaque, whatever a picker hands over. */
        fun opaque(argb: Int): Int = argb or OPAQUE

        /** The text colour swatches: white, black, then bright colours that read on most photos. */
        val TEXT_SWATCHES = listOf(
            TextOverlay.WHITE,
            BLACK,
            0xFFFFD600.toInt(), // yellow
            0xFFFF6D00.toInt(), // orange
            0xFFE53935.toInt(), // red
            0xFFD81B60.toInt(), // pink
            0xFF1E88E5.toInt(), // blue
            0xFF43A047.toInt(), // green
        )

        /** 20 KB and 50 KB, chosen so the file is inside the window whether a portal counts 1000 or 1024 bytes. */
        val STAMP_TARGET = EncodeTarget.SizeRange(
            minBytes = 20L * 1024,
            maxBytes = 50L * 1000,
            allowPadding = true,
            allowDownscale = true,
        )
    }
}

/** The Enhance tab: Natural Enhance at [strength] (0..1), optionally upscaled 2x or 4x first. */
data class EnhanceOptions(val strength: Float = NaturalEnhance.DEFAULT_STRENGTH, val scale: Int = 1) {
    fun toEffects(): ImageEffects = ImageEffects(
        enhance = NaturalEnhance(
            strength = strength.coerceIn(0f, 1f),
            scale = scale.takeIf { it in NaturalEnhance.SCALES } ?: 1,
        ),
    )
}

/** The background choices of Remove background, in the order the chips show them. */
enum class BackgroundChoice { WHITE, EXAM_BLUE, TRANSPARENT, COLOR, PICTURE }

/**
 * The Remove background tab: what goes behind the person ([choice], with [color] or [pictureUri] when those are
 * chosen) and how soft the outline is ([edge], 0..1).
 */
data class BackgroundOptions(
    val choice: BackgroundChoice = BackgroundChoice.WHITE,
    val color: Int = DEFAULT_COLOR,
    val pictureUri: String? = null,
    val edge: Float = BackgroundReplace.DEFAULT_EDGE,
) {
    val fill: BackgroundFill?
        get() = when (choice) {
            BackgroundChoice.WHITE -> BackgroundFill.Solid(BackgroundReplace.WHITE)
            BackgroundChoice.EXAM_BLUE -> BackgroundFill.Solid(BackgroundReplace.EXAM_BLUE)
            BackgroundChoice.TRANSPARENT -> BackgroundFill.Transparent
            BackgroundChoice.COLOR -> BackgroundFill.Solid(EditOptions.opaque(color))
            BackgroundChoice.PICTURE -> pictureUri?.let { BackgroundFill.Picture(it) }
        }

    /** False only while "Photo" is chosen and no photo has been picked yet. */
    val isReady: Boolean get() = fill != null

    fun toEffects(): ImageEffects =
        fill?.let { ImageEffects(background = BackgroundReplace(it, edge.coerceIn(0f, 1f))) } ?: ImageEffects.NONE

    private companion object {
        /** A studio grey, the usual first pick for a plain portrait background. */
        const val DEFAULT_COLOR = -0x2a2a2b
    }
}
