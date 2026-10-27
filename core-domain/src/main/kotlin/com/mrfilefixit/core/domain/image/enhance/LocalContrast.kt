package com.mrfilefixit.core.domain.image.enhance

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The "clarity" step of Enhance: large-scale local contrast that makes a flat or hazy photo look crisp and
 * three-dimensional, the way the clarity and dehaze sliders of photo editors do.
 *
 * The picture's luminance is split into a smooth base (an edge-preserving guided filter, He et al.) and the
 * detail on top of it, and the detail is amplified. The base is computed on a small copy of the picture
 * ([from]) and its guided-filter coefficients are read back with bilinear interpolation at full size ("fast
 * guided filter"), so the base follows the full-resolution edges, needs no halo between bands and costs only a
 * few operations per pixel in [apply].
 *
 * Like the rest of Enhance it changes luminance only (added equally to R, G and B), so colours and skin tone
 * keep their hue. Skin, deep shadows and bright highlights get less of it, so faces stay soft and nothing clips.
 */
class LocalContrast private constructor(
    private val grid: Grid,
    private val a: FloatArray,
    private val b: FloatArray,
    private val amount: Float,
) {
    /** Grid coefficients and the full-size picture they map onto. */
    private class Grid(val width: Int, val height: Int, val imageWidth: Int, val imageHeight: Int)

    val isIdentity: Boolean get() = amount == 0f

    /**
     * Applies clarity in place to [rows] full-width rows of the picture held in [pixels] from [offset], whose
     * first row is row [top] of the whole picture.
     */
    fun apply(pixels: IntArray, offset: Int, top: Int, rows: Int) {
        if (isIdentity) return
        val width = grid.imageWidth
        val sx = grid.width.toFloat() / grid.imageWidth
        val sy = grid.height.toFloat() / grid.imageHeight
        for (row in 0 until rows) {
            val gy = ((top + row + HALF) * sy - HALF).coerceIn(0f, (grid.height - 1).toFloat())
            val y0 = gy.toInt()
            val y1 = min(y0 + 1, grid.height - 1)
            val fy = gy - y0
            for (x in 0 until width) {
                val gx = ((x + HALF) * sx - HALF).coerceIn(0f, (grid.width - 1).toFloat())
                val x0 = gx.toInt()
                val x1 = min(x0 + 1, grid.width - 1)
                val fx = gx - x0
                val i = offset + row * width + x
                val p = pixels[i]
                val r = (p shr RED_SHIFT) and CHANNEL
                val g = (p shr GREEN_SHIFT) and CHANNEL
                val bl = p and CHANNEL
                val y = NaturalEnhancer.luma(r, g, bl)
                val base = bilinear(a, x0, x1, y0, y1, fx, fy) * y + bilinear(b, x0, x1, y0, y1, fx, fy)
                val tone = (y - MID) / MID
                val skin = NaturalEnhancer.skinWeight(r, g, bl)
                val weight = (1f - TONE_FALLOFF * tone * tone) * (1f - SKIN_SHARE * skin)
                val delta = (amount * weight * (y - base)).coerceIn(-MAX_DELTA, MAX_DELTA)
                pixels[i] = shiftLuma(p, delta)
            }
        }
    }

    private fun bilinear(v: FloatArray, x0: Int, x1: Int, y0: Int, y1: Int, fx: Float, fy: Float): Float {
        val w = grid.width
        val top = v[y0 * w + x0] + (v[y0 * w + x1] - v[y0 * w + x0]) * fx
        val bottom = v[y1 * w + x0] + (v[y1 * w + x1] - v[y1 * w + x0]) * fx
        return top + (bottom - top) * fy
    }

    companion object {
        /** Detail is amplified by up to this factor at full strength (1 = doubled). */
        const val MAX_AMOUNT = 1.4f

        /** Long side of the small copy [from] expects; callers scale the picture down to about this size. */
        const val GRID_SIDE = 256

        /** The base is smooth over about 1/32 of the picture's long side. */
        private const val RADIUS_SHARE = 32
        private const val MIN_RADIUS = 2

        /** Edges steeper than about this many luminance levels are kept in the base, so they get no halo. */
        private const val EDGE_LEVEL = 14f
        private const val TONE_FALLOFF = 0.7f
        private const val SKIN_SHARE = 0.55f
        private const val MAX_DELTA = 40f
        private const val HALF = 0.5f
        private const val MID = 128f
        private const val CHANNEL = 255
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val ALPHA_MASK = -0x1000000

        /**
         * Clarity for a [imageWidth] x [imageHeight] picture, from [small], a scaled-down copy of it (packed ARGB,
         * [smallWidth] x [smallHeight], ideally about [GRID_SIDE] on its long side), at [strength] 0..1.
         */
        @Suppress("LongParameterList")
        fun from(
            small: IntArray,
            smallWidth: Int,
            smallHeight: Int,
            imageWidth: Int,
            imageHeight: Int,
            strength: Float,
        ): LocalContrast {
            require(small.size == smallWidth * smallHeight && smallWidth > 0 && smallHeight > 0) { "Bad small copy" }
            val grid = Grid(smallWidth, smallHeight, imageWidth, imageHeight)
            val s = strength.coerceIn(0f, 1f)
            val n = small.size
            val y = FloatArray(n) { i ->
                val p = small[i]
                NaturalEnhancer.luma((p shr RED_SHIFT) and CHANNEL, (p shr GREEN_SHIFT) and CHANNEL, p and CHANNEL)
            }
            val radius = max(MIN_RADIUS, (max(smallWidth, smallHeight).toFloat() / RADIUS_SHARE).roundToInt())
            val mean = Box.mean(y, smallWidth, smallHeight, radius)
            val meanSq = Box.mean(FloatArray(n) { y[it] * y[it] }, smallWidth, smallHeight, radius)
            val eps = EDGE_LEVEL * EDGE_LEVEL
            val a = FloatArray(n)
            val b = FloatArray(n)
            for (i in 0 until n) {
                val variance = max(0f, meanSq[i] - mean[i] * mean[i])
                a[i] = variance / (variance + eps)
                b[i] = mean[i] - a[i] * mean[i]
            }
            return LocalContrast(
                grid,
                Box.mean(a, smallWidth, smallHeight, radius),
                Box.mean(b, smallWidth, smallHeight, radius),
                s * MAX_AMOUNT,
            )
        }

        private fun shiftLuma(argb: Int, delta: Float): Int {
            val d = delta.roundToInt()
            if (d == 0) return argb
            val r = (((argb shr RED_SHIFT) and CHANNEL) + d).coerceIn(0, CHANNEL)
            val g = (((argb shr GREEN_SHIFT) and CHANNEL) + d).coerceIn(0, CHANNEL)
            val b = ((argb and CHANNEL) + d).coerceIn(0, CHANNEL)
            return (argb and ALPHA_MASK) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
        }
    }
}
