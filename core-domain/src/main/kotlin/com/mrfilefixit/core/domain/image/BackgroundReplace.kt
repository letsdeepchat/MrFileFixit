package com.mrfilefixit.core.domain.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What goes behind the person once the photo's own background is removed. */
@Serializable
sealed interface BackgroundFill {
    /** No background at all: a PNG with a transparent background. */
    @Serializable
    @SerialName("transparent")
    data object Transparent : BackgroundFill

    /** A plain colour, such as the white or light blue that exam forms ask for. */
    @Serializable
    @SerialName("color")
    data class Solid(val argb: Int) : BackgroundFill

    /** A picture of the user's choice, scaled to cover the whole photo. */
    @Serializable
    @SerialName("picture")
    data class Picture(val uri: String) : BackgroundFill
}

/**
 * Remove background: the person in front is kept and the rest is replaced by [fill]. [edge] (0..1) is how soft
 * the outline is, from crisp (0) to feathered (1), which hides small mistakes around hair.
 */
@Serializable
data class BackgroundReplace(val fill: BackgroundFill, val edge: Float = DEFAULT_EDGE) {
    init {
        require(edge in 0f..1f) { "Edge softness must be 0..1" }
    }

    /** Whether the result needs an alpha channel (PNG or WebP), because part of it is see-through. */
    val needsAlpha: Boolean get() = fill == BackgroundFill.Transparent

    companion object {
        const val DEFAULT_EDGE = 0.4f
        const val WHITE = -0x1

        /** The light blue most Indian exam and passport forms ask for. */
        const val EXAM_BLUE = -0x461b06
    }
}

/** The pixel maths of Remove background, on plain values so it is unit-tested without Android. */
object BackgroundMath {
    private const val MIN_HALF_WIDTH = 0.04f
    private const val MAX_HALF_WIDTH = 0.3f
    private const val MID = 0.5f
    private const val CHANNEL = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ALPHA_SHIFT = 24
    private const val SMOOTH_A = 3f
    private const val SMOOTH_B = 2f
    private const val RGB_MASK = 0xFFFFFF

    /**
     * How opaque the person is at a pixel, from the segmentation [confidence] (0..1): a smoothstep around 0.5
     * whose width follows [edge], so the outline is crisp or feathered but never a hard, jagged step.
     */
    fun alpha(confidence: Float, edge: Float): Float {
        val half = MIN_HALF_WIDTH + edge.coerceIn(0f, 1f) * (MAX_HALF_WIDTH - MIN_HALF_WIDTH)
        val t = ((confidence - (MID - half)) / (2 * half)).coerceIn(0f, 1f)
        return t * t * (SMOOTH_A - SMOOTH_B * t)
    }

    /**
     * The mask value at output pixel ([x], [y]) of a [outWidth] x [outHeight] picture, read from a smaller
     * [maskWidth] x [maskHeight] mask with bilinear interpolation, so the outline stays smooth when scaled up.
     */
    @Suppress("LongParameterList")
    fun sample(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        outWidth: Int,
        outHeight: Int,
        x: Int,
        y: Int,
    ): Float {
        val fx = ((x + MID) * maskWidth / outWidth - MID).coerceIn(0f, (maskWidth - 1).toFloat())
        val fy = ((y + MID) * maskHeight / outHeight - MID).coerceIn(0f, (maskHeight - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, maskWidth - 1)
        val y1 = minOf(y0 + 1, maskHeight - 1)
        val dx = fx - x0
        val dy = fy - y0
        val top = mask[y0 * maskWidth + x0] * (1 - dx) + mask[y0 * maskWidth + x1] * dx
        val bottom = mask[y1 * maskWidth + x0] * (1 - dx) + mask[y1 * maskWidth + x1] * dx
        return top * (1 - dy) + bottom * dy
    }

    /** [front] over [back] at opacity [alpha]; the result is opaque. */
    fun blend(front: Int, back: Int, alpha: Float): Int {
        fun mix(shift: Int): Int {
            val f = (front shr shift) and CHANNEL
            val b = (back shr shift) and CHANNEL
            return (b + (f - b) * alpha + MID).toInt().coerceIn(0, CHANNEL)
        }
        val red = mix(RED_SHIFT) shl RED_SHIFT
        val green = mix(GREEN_SHIFT) shl GREEN_SHIFT
        return (CHANNEL shl ALPHA_SHIFT) or red or green or mix(0)
    }

    /** [front] with its alpha multiplied by [alpha]: the person on a see-through background. */
    fun cutOut(front: Int, alpha: Float): Int {
        val a = (((front ushr ALPHA_SHIFT) and CHANNEL) * alpha + MID).toInt().coerceIn(0, CHANNEL)
        return (a shl ALPHA_SHIFT) or (front and RGB_MASK)
    }

    /** Whether a mask holds a person at all: enough of it is confidently "person". */
    fun hasPerson(mask: FloatArray, minShare: Float = MIN_PERSON_SHARE): Boolean {
        if (mask.isEmpty()) return false
        val person = mask.count { it >= MID }
        return person >= mask.size * minShare
    }

    /** Below this share of the picture, the "person" is noise (a hand, a poster face) and nothing is kept. */
    const val MIN_PERSON_SHARE = 0.01f
}
