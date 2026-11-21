package com.mrfilefixit.core.domain.image

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.Serializable

/**
 * "Erase object": what the user painted over. Each stroke is a finger path in coordinates normalised to the
 * upright picture (0..1 across and down) with a brush [EraseStroke.radius] as a share of the picture's shorter
 * side, so the same strokes fit the preview and the full-size photo.
 */
@Serializable
data class ObjectErase(val strokes: List<EraseStroke>) {
    val isEmpty: Boolean get() = strokes.none { it.points.size >= 2 }

    /** The painted area as a [width] × [height] mask (row-major, true = erase), grown by [growPixels]. */
    fun rasterize(width: Int, height: Int, growPixels: Int = GROW_PIXELS): BooleanArray {
        val mask = BooleanArray(width * height)
        val side = min(width, height).toFloat()
        strokes.forEach { stroke ->
            val r = stroke.radius * side + growPixels
            val xs = stroke.points.filterIndexed { i, _ -> i % 2 == 0 }.map { it * width }
            val ys = stroke.points.filterIndexed { i, _ -> i % 2 == 1 }.map { it * height }
            val count = min(xs.size, ys.size)
            for (i in 0 until count) {
                val (x0, y0) = xs[i] to ys[i]
                val (x1, y1) = if (i + 1 < count) xs[i + 1] to ys[i + 1] else x0 to y0
                // Dabs every half radius along the segment, so fast swipes leave no gaps.
                val steps = max(1, ceil(hypot(x1 - x0, y1 - y0) / max(r / 2, 1f)).toInt())
                for (s in 0..steps) {
                    val t = s.toFloat() / steps
                    dab(mask, width, height, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r)
                }
            }
        }
        return mask
    }

    private fun dab(mask: BooleanArray, width: Int, height: Int, cx: Float, cy: Float, r: Float) {
        val left = max(0, (cx - r).toInt())
        val right = min(width - 1, (cx + r).toInt())
        val top = max(0, (cy - r).toInt())
        val bottom = min(height - 1, (cy + r).toInt())
        val r2 = r * r
        for (y in top..bottom) {
            val dy = y + HALF - cy
            for (x in left..right) {
                val dx = x + HALF - cx
                if (dx * dx + dy * dy <= r2) mask[y * width + x] = true
            }
        }
    }

    companion object {
        /** Grows the painted area a little so the object's soft edge and shadow fringe go too. */
        const val GROW_PIXELS = 2
        private const val HALF = 0.5f
    }
}

/** One finger path: x and y pairs (x0, y0, x1, y1, ...) normalised to the picture, and the brush radius. */
@Serializable
data class EraseStroke(val points: List<Float>, val radius: Float) {
    init {
        require(radius > 0f && radius <= MAX_RADIUS) { "Brush radius must be in (0, $MAX_RADIUS]" }
    }

    companion object {
        const val MAX_RADIUS = 0.25f
    }
}
