package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.mrfilefixit.core.domain.image.ImageEffectMath
import com.mrfilefixit.core.domain.image.ImageEffects
import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.PhotoStamp
import com.mrfilefixit.core.domain.image.TextOverlay
import com.mrfilefixit.core.domain.image.Upscale
import com.mrfilefixit.core.domain.scan.ScanFilter
import com.mrfilefixit.core.processing.scan.ScanFiltering
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.ensureActive

/**
 * Applies [ImageEffects] to a rendered bitmap, in the documented order. Each step that needs a new bitmap
 * recycles the one before it, so at most two full-size bitmaps are alive at a time.
 */
internal object ImageEffectsRendering {
    /** Rows sharpened per pass; the blur needs only this band (plus its radius) in memory. */
    private const val SHARPEN_BAND = 256
    private const val SHARPEN_RADIUS = 1

    /** Stamp lines and overlays are shrunk to fit this share of the width. */
    private const val FIT_WIDTH = 0.92f
    private const val OUTLINE_SHARE = 0.12f

    /** Returns [source] itself when there is nothing to do, otherwise a new bitmap ([source] is recycled). */
    suspend fun apply(source: Bitmap, effects: ImageEffects): Bitmap {
        if (effects.isNone) return source
        var bitmap = source
        effects.upscale?.let { bitmap = replace(bitmap, upscale(bitmap, it)) }
        coroutineContext.ensureActive()
        if (effects.filter == ImageFilter.DOCUMENT) {
            bitmap = replace(bitmap, ScanFiltering.apply(bitmap, ScanFilter.DOCUMENT_CLEAN))
        }
        val grayscale = effects.filter == ImageFilter.GRAYSCALE
        if (grayscale || !effects.adjust.isNone) {
            bitmap = replace(bitmap, colorMatrix(bitmap, ImageEffectMath.colorMatrix(effects.adjust, grayscale)))
        }
        if (effects.stamp != null || effects.texts.isNotEmpty()) {
            if (!bitmap.isMutable) bitmap = replace(bitmap, mutableCopy(bitmap))
            val canvas = Canvas(bitmap)
            effects.stamp?.let { drawStamp(canvas, bitmap.width, bitmap.height, it) }
            effects.texts.forEach { drawOverlay(canvas, bitmap.width, bitmap.height, it) }
        }
        return bitmap
    }

    private fun replace(old: Bitmap, new: Bitmap): Bitmap {
        if (new !== old && !old.isRecycled) old.recycle()
        return new
    }

    /** Smooth upscaling in 2x steps (each bilinear step stays sharp), then an unsharp mask. */
    private suspend fun upscale(source: Bitmap, upscale: Upscale): Bitmap {
        val (targetW, targetH) = ImageEffectMath.upscaledSize(source.width, source.height, upscale)
        var bitmap = source
        while (bitmap.width * 2 < targetW) {
            coroutineContext.ensureActive()
            val next = bitmap.scale(bitmap.width * 2, bitmap.height * 2, filter = true)
            if (bitmap !== source) bitmap.recycle()
            bitmap = next
        }
        val sized = bitmap.scale(targetW, targetH, filter = true)
        if (bitmap !== source && bitmap !== sized) bitmap.recycle()
        val out = if (sized === source || !sized.isMutable) mutableCopy(sized) else sized
        if (out !== sized && sized !== source) sized.recycle()
        sharpen(out, upscale.sharpen)
        return out
    }

    /** Sharpens [bitmap] in place, band by band, reading [SHARPEN_RADIUS] extra rows around each band. */
    private suspend fun sharpen(bitmap: Bitmap, amount: Float) {
        if (amount <= 0f) return
        val width = bitmap.width
        val height = bitmap.height
        var top = 0
        while (top < height) {
            coroutineContext.ensureActive()
            val bottom = min(height, top + SHARPEN_BAND)
            val readTop = max(0, top - SHARPEN_RADIUS)
            val readBottom = min(height, bottom + SHARPEN_RADIUS)
            val rows = readBottom - readTop
            val pixels = IntArray(width * rows)
            bitmap.getPixels(pixels, 0, width, 0, readTop, width, rows)
            ImageEffectMath.sharpen(pixels, width, rows, amount, SHARPEN_RADIUS)
            bitmap.setPixels(pixels, (top - readTop) * width, width, 0, top, width, bottom - top)
            top = bottom
        }
    }

    /** A drawable copy; drawing it ourselves avoids [Bitmap.copy], which can return null when memory is short. */
    private fun mutableCopy(source: Bitmap): Bitmap = createBitmap(source.width, source.height).also {
        Canvas(it).drawBitmap(source, 0f, 0f, null)
    }

    private fun colorMatrix(source: Bitmap, values: FloatArray): Bitmap {
        val out = createBitmap(source.width, source.height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(values)) }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun drawStamp(canvas: Canvas, width: Int, height: Int, stamp: PhotoStamp) {
        val layout = ImageEffectMath.stampLayout(width, height, stamp)
        val band = Paint().apply { color = if (stamp.dark) Color.BLACK else Color.WHITE }
        canvas.drawRect(0f, layout.bandTop.toFloat(), width.toFloat(), height.toFloat(), band)
        val text = textPaint(if (stamp.dark) Color.WHITE else Color.BLACK, layout.textSizePx)
        stamp.lines.forEachIndexed { i, line ->
            fitWidth(text, line, width, layout.textSizePx)
            canvas.drawText(line, width / 2f, layout.baselines[i], text)
        }
    }

    private fun drawOverlay(canvas: Canvas, width: Int, height: Int, overlay: TextOverlay) {
        if (overlay.text.isBlank()) return
        val size = height * overlay.sizeFraction
        val fill = textPaint(overlay.colorArgb, size)
        fitWidth(fill, overlay.text, width, size)
        val x = width * overlay.x
        // Centre the text vertically on y: the baseline sits half the cap height below it.
        val metrics = fill.fontMetrics
        val baseline = (height * overlay.y - (metrics.ascent + metrics.descent) / 2)
            .coerceIn(-metrics.ascent, height - metrics.descent)
        if (overlay.outline) {
            val stroke = Paint(fill).apply {
                style = Paint.Style.STROKE
                strokeWidth = fill.textSize * OUTLINE_SHARE
                strokeJoin = Paint.Join.ROUND
                color = Color.BLACK
                alpha = OUTLINE_ALPHA
            }
            canvas.drawText(overlay.text, x, baseline, stroke)
        }
        canvas.drawText(overlay.text, x, baseline, fill)
    }

    private const val OUTLINE_ALPHA = 200

    private fun textPaint(color: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private fun fitWidth(paint: Paint, text: String, width: Int, size: Float) {
        paint.textSize = size
        val measured = paint.measureText(text)
        val room = width * FIT_WIDTH
        if (measured > room) paint.textSize = size * room / measured
    }
}
