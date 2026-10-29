package com.mrfilefixit.core.domain.image

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Canonical form of an [ImageRequest]'s operations: rotate, then crop, then one resize or scale.
 * Engines execute a plan in a single decode + draw, which is what keeps memory bounded.
 */
data class ImagePlan(
    /** Clockwise user rotation, 0/90/180/270, applied after EXIF orientation. */
    val rotation: Int,
    /** Crop in the upright, user-rotated image. */
    val crop: NormalizedRect,
    val resize: ImageOp.Resize?,
    val scalePercent: Int?,
) {
    /**
     * This plan, with a fit-inside resize added when it keeps the full [width] x [height] (upright, after the crop)
     * and that is more than [maxPixels]. Effects (Edit, Enhance) hold several full-size copies of the picture, so a
     * 50 MP photo would need about 400 MB; capping keeps them safe on phones with 2 to 3 GB of RAM.
     */
    fun limitedTo(width: Int, height: Int, maxPixels: Long): ImagePlan {
        val pixels = width.toLong() * height
        if (resize != null || scalePercent != null || pixels <= maxPixels) return this
        val factor = sqrt(maxPixels.toDouble() / pixels)
        val w = floor(width * factor).toInt().coerceAtLeast(1)
        val h = floor(height * factor).toInt().coerceAtLeast(1)
        return copy(resize = ImageOp.Resize(w, h, ResizeMode.FIT))
    }

    companion object {
        private const val FULL_TURN = 360

        /** Largest picture Edit and Enhance work on: 24 MP, for example 6000 x 4000. */
        const val MAX_EFFECT_PIXELS = 24_000_000L

        /**
         * Builds a plan from [ops]. Accepts any number of rotations first, at most one crop, then at most
         * one resize or scale. Returns null for any other order, which the engine reports as a bug.
         */
        fun from(ops: List<ImageOp>): ImagePlan? {
            var rotation = 0
            var crop: NormalizedRect? = null
            var resize: ImageOp.Resize? = null
            var scale: Int? = null
            var stage = 0
            for (op in ops) {
                when (op) {
                    is ImageOp.Rotate -> {
                        if (stage > 0) return null
                        rotation += op.degrees
                    }
                    is ImageOp.Crop -> {
                        if (stage > 0) return null
                        crop = op.rect
                        stage = 1
                    }
                    is ImageOp.Resize -> {
                        if (stage > 1) return null
                        resize = op
                        stage = 2
                    }
                    is ImageOp.Scale -> {
                        if (stage > 1) return null
                        scale = op.percent
                        stage = 2
                    }
                }
            }
            return ImagePlan(
                ((rotation % FULL_TURN) + FULL_TURN) % FULL_TURN,
                crop ?: NormalizedRect.FULL,
                resize,
                scale,
            )
        }
    }
}

/** Geometry helpers for mapping between the stored (raw) image and what the user sees. */
object RectTransforms {
    /** Rect as seen after rotating its image 90 degrees clockwise. */
    fun rotateCw(r: NormalizedRect) = NormalizedRect(1f - r.bottom, r.left, 1f - r.top, r.right)

    /** Inverse of [rotateCw]. */
    fun rotateCcw(r: NormalizedRect) = NormalizedRect(r.top, 1f - r.right, r.bottom, 1f - r.left)

    fun mirrorX(r: NormalizedRect) = NormalizedRect(1f - r.right, r.top, 1f - r.left, r.bottom)

    /**
     * Maps a crop drawn on the displayed image back to raw pixel space.
     * Display = rotate(userRotation) ∘ mirror(if flipped) ∘ rotate(exifRotation) applied to raw.
     */
    fun displayToRaw(rect: NormalizedRect, exifRotation: Int, flipped: Boolean, userRotation: Int): NormalizedRect {
        var r = rect
        repeat(quarterTurns(userRotation)) { r = rotateCcw(r) }
        if (flipped) r = mirrorX(r)
        repeat(quarterTurns(exifRotation)) { r = rotateCcw(r) }
        return r.clamped()
    }

    fun quarterTurns(degrees: Int): Int = (((degrees / 90) % 4) + 4) % 4

    private fun NormalizedRect.clamped() = NormalizedRect(
        left.coerceIn(0f, 1f),
        top.coerceIn(0f, 1f),
        right.coerceIn(0f, 1f),
        bottom.coerceIn(0f, 1f),
    )
}

/** Pixel size of the output for a source region of [regionWidth] x [regionHeight] (already upright). */
data class OutputGeometry(
    val canvasWidth: Int,
    val canvasHeight: Int,
    /** Where the image is drawn inside the canvas (differs from the canvas only for PAD). */
    val drawLeft: Int,
    val drawTop: Int,
    val drawWidth: Int,
    val drawHeight: Int,
    /** Further crop of the region (normalised, upright) needed for FILL when aspects differ. */
    val fillCrop: NormalizedRect,
) {
    companion object {
        private const val PERCENT = 100f

        fun compute(regionWidth: Int, regionHeight: Int, plan: ImagePlan): OutputGeometry {
            val resize = plan.resize
            val scale = plan.scalePercent
            return when {
                resize != null -> forResize(regionWidth, regionHeight, resize)
                scale != null -> {
                    val w = (regionWidth * scale / PERCENT).toInt().coerceAtLeast(1)
                    val h = (regionHeight * scale / PERCENT).toInt().coerceAtLeast(1)
                    OutputGeometry(w, h, 0, 0, w, h, NormalizedRect.FULL)
                }
                else -> OutputGeometry(regionWidth, regionHeight, 0, 0, regionWidth, regionHeight, NormalizedRect.FULL)
            }
        }

        private fun forResize(rw: Int, rh: Int, resize: ImageOp.Resize): OutputGeometry {
            val boxW = resize.width
            val boxH = resize.height
            val regionAspect = rw.toFloat() / rh
            return when (resize.mode) {
                ResizeMode.STRETCH -> OutputGeometry(boxW, boxH, 0, 0, boxW, boxH, NormalizedRect.FULL)
                ResizeMode.FILL -> OutputGeometry(
                    boxW,
                    boxH,
                    0,
                    0,
                    boxW,
                    boxH,
                    NormalizedRect.centeredWithAspect(boxW.toFloat() / boxH, regionAspect),
                )
                ResizeMode.FIT -> {
                    val (w, h) = fit(regionAspect, boxW, boxH)
                    OutputGeometry(w, h, 0, 0, w, h, NormalizedRect.FULL)
                }
                ResizeMode.PAD -> {
                    val (w, h) = fit(regionAspect, boxW, boxH)
                    OutputGeometry(boxW, boxH, (boxW - w) / 2, (boxH - h) / 2, w, h, NormalizedRect.FULL)
                }
            }
        }

        private fun fit(aspect: Float, boxW: Int, boxH: Int): Pair<Int, Int> =
            if (aspect >= boxW.toFloat() / boxH) {
                boxW to (boxW / aspect).toInt().coerceAtLeast(1)
            } else {
                (boxH * aspect).toInt().coerceAtLeast(1) to boxH
            }
    }
}

/** Largest power-of-two sample size that still leaves at least [targetW] x [targetH] pixels. */
fun sampleSizeFor(sourceW: Int, sourceH: Int, targetW: Int, targetH: Int): Int {
    var sample = 1
    while (sourceW / (sample * 2) >= targetW && sourceH / (sample * 2) >= targetH) sample *= 2
    return sample
}
