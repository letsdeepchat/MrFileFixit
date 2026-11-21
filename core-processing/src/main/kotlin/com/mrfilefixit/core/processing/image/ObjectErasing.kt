package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.mrfilefixit.core.domain.image.Inpainting
import com.mrfilefixit.core.domain.image.ObjectErase
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Erase object on a bitmap: only the painted area and a margin around it are worked on, scaled down when the
 * hole is large so it stays quick on a phone, then the filled pixels are scaled back and put into a copy of the
 * picture. Pixels outside the painted area are never touched.
 */
internal object ObjectErasing {
    /** Above this many hole pixels the work is done at a smaller size (the fill is smooth enough to scale up). */
    private const val MAX_WORK_HOLE = 50_000
    private const val MAX_WORK_SIDE = 900
    private const val MIN_MARGIN = 24
    private const val MARGIN_SHARE = 0.75f

    fun apply(source: Bitmap, erase: ObjectErase): Bitmap {
        val width = source.width
        val height = source.height
        val mask = erase.rasterize(width, height)
        val box = bounds(mask, width, height) ?: return source
        val margin = max(MIN_MARGIN, (max(box.width, box.height) * MARGIN_SHARE).roundToInt())
        val left = max(0, box.left - margin)
        val top = max(0, box.top - margin)
        val right = min(width, box.right + margin)
        val bottom = min(height, box.bottom + margin)
        val roiW = right - left
        val roiH = bottom - top
        val holeCount = mask.count { it }
        val scale = minOf(1f, sqrt(MAX_WORK_HOLE.toFloat() / holeCount), MAX_WORK_SIDE.toFloat() / max(roiW, roiH))
        val workW = max(1, (roiW * scale).roundToInt())
        val workH = max(1, (roiH * scale).roundToInt())

        val roi = Bitmap.createBitmap(source, left, top, roiW, roiH)
        val work = if (workW == roiW && workH == roiH) roi else roi.scale(workW, workH).also { roi.recycle() }
        val pixels = IntArray(workW * workH)
        work.getPixels(pixels, 0, workW, 0, 0, workW, workH)
        work.recycle()
        // A work pixel is part of the hole when any picture pixel it covers is, so no fringe survives the scaling.
        val workMask = BooleanArray(workW * workH)
        for (y in top until bottom) {
            for (x in left until right) {
                if (!mask[y * width + x]) continue
                val wx = ((x - left).toLong() * workW / roiW).toInt().coerceAtMost(workW - 1)
                val wy = ((y - top).toLong() * workH / roiH).toInt().coerceAtMost(workH - 1)
                workMask[wy * workW + wx] = true
            }
        }
        Inpainting.fill(pixels, workW, workH, workMask)

        val filledSmall = createBitmap(workW, workH).apply { setPixels(pixels, 0, workW, 0, 0, workW, workH) }
        val filled = if (workW == roiW && workH == roiH) {
            filledSmall
        } else {
            filledSmall.scale(roiW, roiH).also { filledSmall.recycle() }
        }
        val filledPixels = IntArray(roiW * roiH)
        filled.getPixels(filledPixels, 0, roiW, 0, 0, roiW, roiH)
        filled.recycle()

        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val outPixels = IntArray(roiW * roiH)
        out.getPixels(outPixels, 0, roiW, left, top, roiW, roiH)
        for (y in 0 until roiH) {
            for (x in 0 until roiW) {
                if (mask[(y + top) * width + x + left]) outPixels[y * roiW + x] = filledPixels[y * roiW + x]
            }
        }
        out.setPixels(outPixels, 0, roiW, left, top, roiW, roiH)
        return out
    }

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private fun bounds(mask: BooleanArray, width: Int, height: Int): Box? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!mask[y * width + x]) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) null else Box(left, top, right + 1, bottom + 1)
    }
}
