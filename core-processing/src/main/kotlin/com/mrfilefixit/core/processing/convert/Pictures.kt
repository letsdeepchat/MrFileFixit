package com.mrfilefixit.core.processing.convert

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

/**
 * Decodes embedded pictures just large enough for where they are drawn, so a 40-megapixel photo inside a
 * Word file costs a few megabytes of memory, not hundreds.
 */
internal object Pictures {
    /** Pixels per point at which pictures are kept: about 150 dpi on paper. */
    private const val PX_PER_PT = 2f
    private const val MAX_SOURCE_PIXELS = 100_000_000L
    private const val PLACEHOLDER = -0x1f1f20 // 0xFFE0E0E0

    fun bounds(bytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    fun decode(bytes: ByteArray, widthPt: Float, heightPt: Float): Bitmap? {
        val (w, h) = bounds(bytes) ?: return null
        if (w.toLong() * h > MAX_SOURCE_PIXELS) return null
        val targetW = max(1f, widthPt * PX_PER_PT)
        val targetH = max(1f, heightPt * PX_PER_PT)
        var sample = 1
        while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** Draws the picture into [frame], or a grey box when the format cannot be decoded (EMF, WMF, TIFF). */
    fun draw(canvas: Canvas, bytes: ByteArray, frame: RectF) {
        val bitmap = decode(bytes, frame.width(), frame.height())
        if (bitmap == null) {
            canvas.drawRect(frame, Paint().apply { color = PLACEHOLDER })
            return
        }
        try {
            canvas.drawBitmap(bitmap, null, frame, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } finally {
            bitmap.recycle()
        }
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray =
        java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    fun encodePng(bitmap: Bitmap): ByteArray =
        java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
