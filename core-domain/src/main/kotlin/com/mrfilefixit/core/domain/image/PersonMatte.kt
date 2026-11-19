package com.mrfilefixit.core.domain.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns the coarse person mask of a segmentation model into a clean cut-out, with two classic matting steps
 * that run in plain code, no extra model:
 *
 * 1. **Edge refinement (guided filter, He et al.).** The mask is filtered with the photo's own colours as the
 *    guide, so the outline snaps to the real edges of hair, ears and shoulders instead of the model's blurry,
 *    low-resolution blob, and see-through strands of hair get partial alpha. The filter's coefficients are
 *    computed on a small copy ([from]) and read back with bilinear interpolation at full size ("fast guided
 *    filter"), so [alpha] follows full-resolution edges at a few operations per pixel.
 * 2. **Colour clean-up (blur-fusion foreground estimation, Forte and Pitié 2020).** A pixel at the edge mixes
 *    the person with the old background, so pasting it onto a new background leaves a coloured fringe. The
 *    local person and background colours are estimated and the old background's share is removed
 *    ([foreground]), which is what keeps hair edges clean on white, blue or a picture.
 */
class PersonMatte private constructor(
    private val grid: Grid,
    private val coefficients: Array<FloatArray>,
    private val front: Array<FloatArray>,
    private val back: Array<FloatArray>,
    private val edge: Float,
    /** Share of the small copy that is clearly person; see [hasPerson]. */
    val personShare: Float,
) {
    private class Grid(val width: Int, val height: Int, val imageWidth: Int, val imageHeight: Int)

    val hasPerson: Boolean get() = personShare >= BackgroundMath.MIN_PERSON_SHARE

    /**
     * Cuts out [rows] full-width rows of the photo held in [pixels] from [offset], whose first row is row [top]
     * of the whole photo: each pixel is replaced in place by the person's own colour (opaque, see
     * [foreground]) and its opacity 0..1 is written to [alpha] at the same index.
     */
    fun cut(pixels: IntArray, offset: Int, top: Int, rows: Int, alpha: FloatArray) {
        val width = grid.imageWidth
        val sample = Sample()
        for (row in 0 until rows) for (x in 0 until width) {
            val i = offset + row * width + x
            locate(x, top + row, sample)
            val a = alphaAt(pixels[i], sample)
            alpha[i] = a
            pixels[i] = foregroundAt(pixels[i], a, sample)
        }
    }

    /** Opacity 0..1 of the full-size pixel at ([x], [y]) whose colour is [argb]. */
    fun alpha(argb: Int, x: Int, y: Int): Float = alphaAt(argb, Sample().also { locate(x, y, it) })

    /**
     * The colour of the person alone at ([x], [y]), for a pixel [argb] of opacity [alpha]: the old background's
     * share is taken out of see-through edge pixels. Opaque pixels come back unchanged. Alpha is set to 255.
     */
    fun foreground(argb: Int, alpha: Float, x: Int, y: Int): Int =
        foregroundAt(argb, alpha, Sample().also { locate(x, y, it) })

    private fun alphaAt(argb: Int, at: Sample): Float {
        val q = at.read(coefficients[R]) * channel(argb, RED_SHIFT) +
            at.read(coefficients[G]) * channel(argb, GREEN_SHIFT) +
            at.read(coefficients[B]) * channel(argb, 0) +
            at.read(coefficients[OFFSET])
        return BackgroundMath.alpha(q.coerceIn(0f, 1f), edge)
    }

    private fun foregroundAt(argb: Int, alpha: Float, at: Sample): Int {
        if (alpha >= OPAQUE) return argb or ALPHA_MASK
        var out = ALPHA_MASK
        for (c in 0 until CHANNELS) {
            val shift = SHIFTS[c]
            val f = at.read(front[c])
            val b = at.read(back[c])
            val value = f + alpha * (channel(argb, shift) - alpha * f - (1f - alpha) * b)
            out = out or ((value.coerceIn(0f, 1f) * CHANNEL).roundToInt() shl shift)
        }
        return out
    }

    /** A bilinear read position in the small grid, reused from pixel to pixel. */
    private class Sample {
        var i00 = 0
        var i01 = 0
        var i10 = 0
        var i11 = 0
        var fx = 0f
        var fy = 0f

        fun read(v: FloatArray): Float {
            val top = v[i00] + (v[i01] - v[i00]) * fx
            val bottom = v[i10] + (v[i11] - v[i10]) * fx
            return top + (bottom - top) * fy
        }
    }

    private fun locate(x: Int, y: Int, into: Sample) {
        val gx = ((x + HALF) * grid.width / grid.imageWidth - HALF).coerceIn(0f, (grid.width - 1).toFloat())
        val gy = ((y + HALF) * grid.height / grid.imageHeight - HALF).coerceIn(0f, (grid.height - 1).toFloat())
        val x0 = gx.toInt()
        val y0 = gy.toInt()
        val x1 = min(x0 + 1, grid.width - 1)
        val y1 = min(y0 + 1, grid.height - 1)
        val w = grid.width
        into.i00 = y0 * w + x0
        into.i01 = y0 * w + x1
        into.i10 = y1 * w + x0
        into.i11 = y1 * w + x1
        into.fx = gx - x0
        into.fy = gy - y0
    }

    companion object {
        /** Long side of the small copy [from] expects; the person mask is read at this size too. */
        const val GRID_SIDE = 640

        /** Guided filter window: radius per this many pixels of the small copy's long side. */
        private const val RADIUS_SHARE = 40
        private const val MIN_RADIUS = 2

        /** Regularisation: lower follows colour edges more closely, higher keeps more of the model's mask. */
        private const val EPS = 5e-4f

        /** Blur-fusion window radii (wide, then fine) as shares of the small copy's long side. */
        private const val WIDE_SHARE = 0.07f
        private const val FINE_RADIUS = 3
        private const val TINY = 1e-5f
        private const val OPAQUE = 0.995f
        private const val PERSON = 0.5f
        private const val R = 0
        private const val G = 1
        private const val B = 2
        private const val OFFSET = 3
        private const val CHANNELS = 3
        private const val HALF = 0.5f
        private const val CHANNEL = 255f
        private const val BYTE = 0xFF
        private const val RR = 0
        private const val RG = 1
        private const val RB = 2
        private const val GG = 3
        private const val GB = 4
        private const val BB = 5
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val ALPHA_MASK = -0x1000000
        private val SHIFTS = intArrayOf(RED_SHIFT, GREEN_SHIFT, 0)

        private fun channel(argb: Int, shift: Int): Float = ((argb shr shift) and BYTE) / CHANNEL

        /**
         * A matte for a [imageWidth] x [imageHeight] photo from [small], a scaled-down copy of it (packed ARGB,
         * [width] x [height], about [GRID_SIDE] on its long side), and [confidence], the segmentation model's
         * person confidence 0..1 for each pixel of that copy. [edge] 0..1 is the user's edge softness.
         */
        @Suppress("LongParameterList")
        fun from(
            small: IntArray,
            width: Int,
            height: Int,
            confidence: FloatArray,
            imageWidth: Int,
            imageHeight: Int,
            edge: Float,
        ): PersonMatte {
            val n = width * height
            require(small.size == n && confidence.size == n && n > 0) { "Copy and mask must be width * height" }
            val image = Array(CHANNELS) { c -> FloatArray(n) { channel(small[it], SHIFTS[c]) } }
            val radius = max(MIN_RADIUS, (max(width, height).toFloat() / RADIUS_SHARE).roundToInt())
            val coefficients = guidedFilter(image, confidence, width, height, radius)
            val alpha = FloatArray(n) { i ->
                var q = coefficients[OFFSET][i]
                for (c in 0 until CHANNELS) q += coefficients[c][i] * image[c][i]
                BackgroundMath.alpha(q.coerceIn(0f, 1f), edge)
            }
            val wide = max(FINE_RADIUS, (max(width, height) * WIDE_SHARE).roundToInt())
            val first = blurFusion(image, image, image, alpha, width, height, wide)
            val second = blurFusion(image, first.foreground, first.background, alpha, width, height, FINE_RADIUS)
            val share = alpha.count { it > PERSON }.toFloat() / n
            return PersonMatte(
                Grid(width, height, imageWidth, imageHeight),
                coefficients,
                second.blurredForeground,
                second.background,
                edge,
                share,
            )
        }

        /**
         * Colour guided filter of [p] with guide [image] (three channels): per window, the linear model
         * p = a . I + b with the least error, then the window average of a and b. Returns a (3) and b.
         */
        private fun guidedFilter(
            image: Array<FloatArray>,
            p: FloatArray,
            width: Int,
            height: Int,
            radius: Int,
        ): Array<FloatArray> {
            val n = p.size
            fun mean(v: FloatArray) = BoxSum.mean(v, width, height, radius)
            val meanI = Array(CHANNELS) { mean(image[it]) }
            val meanP = mean(p)
            val covIp = Array(CHANNELS) { c ->
                val m = mean(FloatArray(n) { image[c][it] * p[it] })
                FloatArray(n) { m[it] - meanI[c][it] * meanP[it] }
            }
            // Covariance of the guide colours: rr, rg, rb, gg, gb, bb.
            val pairs = arrayOf(R to R, R to G, R to B, G to G, G to B, B to B)
            val cov = Array(pairs.size) { k ->
                val (c1, c2) = pairs[k]
                val m = mean(FloatArray(n) { image[c1][it] * image[c2][it] })
                FloatArray(n) { m[it] - meanI[c1][it] * meanI[c2][it] }
            }
            val a = Array(CHANNELS) { FloatArray(n) }
            val b = FloatArray(n)
            for (i in 0 until n) {
                val rr = cov[RR][i] + EPS
                val rg = cov[RG][i]
                val rb = cov[RB][i]
                val gg = cov[GG][i] + EPS
                val gb = cov[GB][i]
                val bb = cov[BB][i] + EPS
                // Inverse of the symmetric 3 x 3 matrix by cofactors.
                val i00 = gg * bb - gb * gb
                val i01 = rb * gb - rg * bb
                val i02 = rg * gb - rb * gg
                val i11 = rr * bb - rb * rb
                val i12 = rb * rg - rr * gb
                val i22 = rr * gg - rg * rg
                val det = rr * i00 + rg * i01 + rb * i02
                val r = covIp[R][i]
                val g = covIp[G][i]
                val bl = covIp[B][i]
                val ar = (i00 * r + i01 * g + i02 * bl) / det
                val ag = (i01 * r + i11 * g + i12 * bl) / det
                val ab = (i02 * r + i12 * g + i22 * bl) / det
                a[R][i] = ar
                a[G][i] = ag
                a[B][i] = ab
                b[i] = meanP[i] - ar * meanI[R][i] - ag * meanI[G][i] - ab * meanI[B][i]
            }
            return arrayOf(mean(a[R]), mean(a[G]), mean(a[B]), mean(b))
        }

        private class Fusion(
            val foreground: Array<FloatArray>,
            val blurredForeground: Array<FloatArray>,
            val background: Array<FloatArray>,
        )

        /** One blur-fusion pass: smooth person and background colours, then this pass's foreground estimate. */
        @Suppress("LongParameterList")
        private fun blurFusion(
            image: Array<FloatArray>,
            front: Array<FloatArray>,
            back: Array<FloatArray>,
            alpha: FloatArray,
            width: Int,
            height: Int,
            radius: Int,
        ): Fusion {
            val n = alpha.size
            val blurredAlpha = BoxSum.mean(alpha, width, height, radius)
            val blurredFront = Array(CHANNELS) { c ->
                val m = BoxSum.mean(FloatArray(n) { front[c][it] * alpha[it] }, width, height, radius)
                FloatArray(n) { m[it] / (blurredAlpha[it] + TINY) }
            }
            val blurredBack = Array(CHANNELS) { c ->
                val m = BoxSum.mean(FloatArray(n) { back[c][it] * (1f - alpha[it]) }, width, height, radius)
                FloatArray(n) { m[it] / (1f - blurredAlpha[it] + TINY) }
            }
            val foreground = Array(CHANNELS) { c ->
                FloatArray(n) { i ->
                    val f = blurredFront[c][i]
                    val a = alpha[i]
                    (f + a * (image[c][i] - a * f - (1f - a) * blurredBack[c][i])).coerceIn(0f, 1f)
                }
            }
            return Fusion(foreground, blurredFront, blurredBack)
        }
    }
}

/** Box mean with running sums (constant cost per pixel at any radius); the window is clamped at the borders. */
internal object BoxSum {
    fun mean(src: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val horizontal = FloatArray(src.size)
        for (y in 0 until height) line(src, horizontal, y * width, 1, width, radius)
        val out = FloatArray(src.size)
        for (x in 0 until width) line(horizontal, out, x, width, height, radius)
        return out
    }

    /** One row or column: each output is the mean of the samples of its window that lie inside the line. */
    private fun line(src: FloatArray, dst: FloatArray, start: Int, step: Int, length: Int, radius: Int) {
        var sum = 0.0
        var lo = 0
        var hi = -1
        for (i in 0 until length) {
            val wantHi = min(length - 1, i + radius)
            while (hi < wantHi) sum += src[start + ++hi * step]
            val wantLo = max(0, i - radius)
            while (lo < wantLo) sum -= src[start + lo++ * step]
            dst[start + i * step] = (sum / (hi - lo + 1)).toFloat()
        }
    }
}
