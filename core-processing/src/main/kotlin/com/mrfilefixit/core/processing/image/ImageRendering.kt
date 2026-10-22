package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.image.OutputGeometry

/**
 * Draws a decoded region (raw orientation) into the output canvas in one pass:
 * EXIF rotation, mirror, user rotation, FILL crop and scaling are a single [Matrix].
 */
internal object ImageRendering {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    fun render(
        region: Bitmap,
        exifRotation: Int,
        flipped: Boolean,
        userRotation: Int,
        geometry: OutputGeometry,
        format: ImageFormat,
        backgroundArgb: Int,
    ): Bitmap {
        val output = Bitmap.createBitmap(geometry.canvasWidth, geometry.canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        if (format == ImageFormat.JPEG || geometry.drawWidth != geometry.canvasWidth ||
            geometry.drawHeight != geometry.canvasHeight
        ) {
            canvas.drawColor(backgroundArgb) // JPEG has no alpha; PAD needs a background
        }
        val totalRotation = exifRotation + userRotation
        val uprightW = if (totalRotation % 180 == 0) region.width else region.height
        val uprightH = if (totalRotation % 180 == 0) region.height else region.width
        val fill = geometry.fillCrop
        val visibleW = uprightW * fill.width
        val visibleH = uprightH * fill.height

        val matrix = Matrix().apply {
            postTranslate(-region.width / 2f, -region.height / 2f)
            postRotate(exifRotation.toFloat())
            if (flipped) postScale(-1f, 1f)
            postRotate(userRotation.toFloat())
            // Now centred on the origin in upright space. Shift so the FILL crop is centred, then scale.
            postTranslate(uprightW * (0.5f - fill.centerX()), uprightH * (0.5f - fill.centerY()))
            postScale(geometry.drawWidth / visibleW, geometry.drawHeight / visibleH)
            postTranslate(geometry.drawLeft + geometry.drawWidth / 2f, geometry.drawTop + geometry.drawHeight / 2f)
        }
        canvas.save()
        canvas.clipRect(
            geometry.drawLeft,
            geometry.drawTop,
            geometry.drawLeft + geometry.drawWidth,
            geometry.drawTop + geometry.drawHeight,
        )
        canvas.drawBitmap(region, matrix, paint)
        canvas.restore()
        return output
    }

    private fun NormalizedRect.centerX() = (left + right) / 2f

    private fun NormalizedRect.centerY() = (top + bottom) / 2f
}
