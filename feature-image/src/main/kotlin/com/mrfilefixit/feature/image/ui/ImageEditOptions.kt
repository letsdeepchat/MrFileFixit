package com.mrfilefixit.feature.image.ui

import com.mrfilefixit.core.domain.image.ColorAdjust
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageEffects
import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.OverlayPosition
import com.mrfilefixit.core.domain.image.PhotoStamp
import com.mrfilefixit.core.domain.image.TextOverlay
import com.mrfilefixit.core.domain.image.Upscale

/** The Edit tab: a look, three sliders, an optional line of text and the exam name and date bar. */
data class EditOptions(
    val filter: ImageFilter = ImageFilter.NONE,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val text: String = "",
    val textPosition: OverlayPosition = OverlayPosition.BOTTOM,
    val textDark: Boolean = false,
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
            // Dark text needs no outline; light text gets one so it reads on bright photos.
            listOf(
                TextOverlay(
                    it,
                    y = textPosition.y,
                    colorArgb = if (textDark) BLACK else TextOverlay.WHITE,
                    outline = !textDark,
                ),
            )
        }.orEmpty(),
    )

    companion object {
        const val BLACK = -0x1000000

        /** 20 KB and 50 KB, chosen so the file is inside the window whether a portal counts 1000 or 1024 bytes. */
        val STAMP_TARGET = EncodeTarget.SizeRange(
            minBytes = 20L * 1024,
            maxBytes = 50L * 1000,
            allowPadding = true,
            allowDownscale = true,
        )
    }
}

/** The Enhance tab: a 2x or 4x upscale with sharpening. */
data class EnhanceOptions(val factor: Int = 2, val sharpen: Float = Upscale.DEFAULT_SHARPEN) {
    fun toEffects(): ImageEffects = ImageEffects(upscale = Upscale(factor, sharpen.coerceIn(0f, 1f)))
}
