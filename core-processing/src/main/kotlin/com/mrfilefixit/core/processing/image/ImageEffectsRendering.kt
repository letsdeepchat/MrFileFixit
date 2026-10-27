package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.mrfilefixit.core.domain.image.ImageEffectMath
import com.mrfilefixit.core.domain.image.ImageEffects
import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.NaturalEnhance
import com.mrfilefixit.core.domain.image.PhotoStamp
import com.mrfilefixit.core.domain.image.TextOverlay
import com.mrfilefixit.core.domain.image.enhance.LanczosResampler
import com.mrfilefixit.core.domain.image.enhance.NaturalEnhancer
import com.mrfilefixit.core.domain.scan.ScanFilter
import com.mrfilefixit.core.processing.scan.ScanFiltering
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlinx.coroutines.ensureActive

/**
 * Applies [ImageEffects] to a rendered bitmap, in the documented order. Each step that needs a new bitmap
 * recycles the one before it, so at most two full-size bitmaps are alive at a time.
 */
internal object ImageEffectsRendering {
    /** Rows resampled or enhanced per pass; only this band (plus the enhancer's halo) is held as pixels. */
    private const val BAND = 256

    /** Stamp lines and overlays are shrunk to fit this share of the width. */
    private const val FIT_WIDTH = 0.92f
    private const val OUTLINE_SHARE = 0.12f

    /** Returns [source] itself when there is nothing to do, otherwise a new bitmap ([source] is recycled). */
    suspend fun apply(source: Bitmap, effects: ImageEffects): Bitmap {
        if (effects.isNone) return source
        var bitmap = source
        effects.enhance?.let { bitmap = replace(bitmap, enhance(bitmap, it)) }
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

    /**
     * Natural Enhance: a Lanczos upscale when asked for, then the luminance-only clarity pipeline, band by band
     * on the (mutable) result so a large photo is never copied as a whole pixel array twice.
     */
    private suspend fun enhance(source: Bitmap, enhance: NaturalEnhance): Bitmap {
        val (targetW, targetH) = ImageEffectMath.enhancedSize(source.width, source.height, enhance)
        val out = when {
            targetW != source.width || targetH != source.height -> lanczos(source, targetW, targetH)
            source.isMutable -> source
            else -> mutableCopy(source)
        }
        if (enhance.strength > 0f) {
            val width = out.width
            NaturalEnhancer.enhanceInBands(
                width = width,
                height = out.height,
                strength = enhance.strength,
                bandRows = BAND,
                read = { top, count, into, offset -> out.getPixels(into, offset, width, 0, top, width, count) },
                write = { top, count, from, offset -> out.setPixels(from, offset, width, 0, top, width, count) },
            )
        }
        return out
    }

    /** Lanczos-3 resize: one horizontal pass over the source, then the output written band by band. */
    private suspend fun lanczos(source: Bitmap, width: Int, height: Int): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val resampler = LanczosResampler(source.width, source.height, width, height)
        val horizontal = resampler.horizontal(pixels)
        val out = createBitmap(width, height)
        var top = 0
        while (top < height) {
            coroutineContext.ensureActive()
            val bottom = min(height, top + BAND)
            out.setPixels(resampler.vertical(horizontal, top, bottom), 0, width, 0, top, width, bottom - top)
            top = bottom
        }
        return out
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
