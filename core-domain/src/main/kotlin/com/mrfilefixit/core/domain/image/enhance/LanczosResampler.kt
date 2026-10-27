package com.mrfilefixit.core.domain.image.enhance

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lanczos-3 resampling of packed ARGB pixels, separable and in two passes so a caller can keep memory low:
 * [horizontal] runs once over the whole source and gives a `dstWidth x srcHeight` buffer, then [vertical]
 * produces any band of output rows from that buffer.
 *
 * Lanczos keeps edges and fine detail much crisper than bilinear scaling while staying free of the blocky
 * look of nearest neighbour. Each channel is filtered on its own and clamped, so ringing never wraps around.
 */
class LanczosResampler(
    private val srcWidth: Int,
    private val srcHeight: Int,
    val dstWidth: Int,
    val dstHeight: Int,
) {
    init {
        require(srcWidth > 0 && srcHeight > 0 && dstWidth > 0 && dstHeight > 0) { "Sizes must be positive" }
    }

    private val columns = Kernel.build(srcWidth, dstWidth)
    private val rows = Kernel.build(srcHeight, dstHeight)

    /** Resamples every source row to [dstWidth]. Returns `dstWidth * srcHeight` pixels. */
    fun horizontal(source: IntArray): IntArray {
        require(source.size == srcWidth * srcHeight) { "source must be srcWidth * srcHeight" }
        val out = IntArray(dstWidth * srcHeight)
        for (y in 0 until srcHeight) {
            val rowStart = y * srcWidth
            val outStart = y * dstWidth
            for (x in 0 until dstWidth) out[outStart + x] = columns.sample(source, rowStart, 1, x)
        }
        return out
    }

    /** Output rows [fromRow] until [toRow] from the result of [horizontal]. Returns `dstWidth * rows` pixels. */
    fun vertical(horizontal: IntArray, fromRow: Int, toRow: Int): IntArray {
        require(horizontal.size == dstWidth * srcHeight) { "horizontal must be dstWidth * srcHeight" }
        require(fromRow in 0..toRow && toRow <= dstHeight) { "Row range out of bounds" }
        val out = IntArray(dstWidth * (toRow - fromRow))
        for (y in fromRow until toRow) {
            val outStart = (y - fromRow) * dstWidth
            for (x in 0 until dstWidth) out[outStart + x] = rows.sample(horizontal, x, dstWidth, y)
        }
        return out
    }

    /** Both passes at once, for small images and tests. */
    fun resize(source: IntArray): IntArray = vertical(horizontal(source), 0, dstHeight)

    /** Precomputed, normalised filter taps for every output index along one axis. */
    private class Kernel(private val first: IntArray, private val counts: IntArray, private val weights: FloatArray) {
        private val stride = weights.size / counts.size

        fun sample(src: IntArray, base: Int, step: Int, index: Int): Int {
            var a = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            val start = first[index]
            val offset = index * stride
            for (k in 0 until counts[index]) {
                val w = weights[offset + k]
                val p = src[base + (start + k) * step]
                a += w * (p ushr ALPHA_SHIFT)
                r += w * ((p shr RED_SHIFT) and CHANNEL)
                g += w * ((p shr GREEN_SHIFT) and CHANNEL)
                b += w * (p and CHANNEL)
            }
            return (clamp(a) shl ALPHA_SHIFT) or (clamp(r) shl RED_SHIFT) or (clamp(g) shl GREEN_SHIFT) or clamp(b)
        }

        companion object {
            fun build(srcSize: Int, dstSize: Int): Kernel {
                val scale = srcSize.toDouble() / dstSize
                // Shrinking widens the filter so every source pixel still contributes (no aliasing).
                val filterScale = max(1.0, scale)
                val support = LOBES * filterScale
                val stride = ceil(support * 2).toInt() + 1
                val first = IntArray(dstSize)
                val counts = IntArray(dstSize)
                val weights = FloatArray(dstSize * stride)
                for (i in 0 until dstSize) {
                    val center = (i + 0.5) * scale
                    val left = max(0, floor(center - support).toInt())
                    val right = min(srcSize, ceil(center + support).toInt())
                    var sum = 0.0
                    var n = 0
                    for (j in left until right) {
                        if (n == stride) break
                        val w = lanczos((j + 0.5 - center) / filterScale)
                        weights[i * stride + n] = w.toFloat()
                        sum += w
                        n++
                    }
                    if (sum != 0.0) {
                        for (k in 0 until n) weights[i * stride + k] = (weights[i * stride + k] / sum).toFloat()
                    }
                    first[i] = left
                    counts[i] = n
                }
                return Kernel(first, counts, weights)
            }

            private fun lanczos(x: Double): Double {
                val ax = abs(x)
                if (ax < EPSILON) return 1.0
                if (ax >= LOBES) return 0.0
                val px = PI * x
                return LOBES * sin(px) * sin(px / LOBES) / (px * px)
            }
        }
    }

    private companion object {
        const val LOBES = 3.0
        const val EPSILON = 1e-9
        const val CHANNEL = 255
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8

        fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, CHANNEL)
    }
}
