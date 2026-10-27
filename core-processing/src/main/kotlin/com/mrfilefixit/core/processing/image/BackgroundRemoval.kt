package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.mrfilefixit.core.domain.image.BackgroundFill
import com.mrfilefixit.core.domain.image.BackgroundMath
import com.mrfilefixit.core.domain.image.BackgroundReplace
import com.mrfilefixit.core.domain.image.PersonMatte
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

/**
 * Remove background on this phone: ML Kit selfie segmentation (its model is bundled in the app, so nothing is
 * downloaded and nothing leaves the phone) finds the person on a copy about [PersonMatte.GRID_SIDE] pixels on
 * its long side, then [PersonMatte] snaps that coarse mask to the photo's real edges and cleans the old
 * background's colour out of hair and shoulder edges, and the rest of the photo is replaced at full size.
 */
internal class BackgroundRemoval @Inject constructor() {
    suspend fun matte(bitmap: Bitmap, edge: Float): PersonMatte {
        val scale = min(1f, PersonMatte.GRID_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        val input = if (scale < 1f) {
            bitmap.scale(
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            )
        } else {
            bitmap
        }
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        return try {
            val mask = segmenter.process(InputImage.fromBitmap(input, 0)).await()
            val values = FloatArray(mask.width * mask.height)
            mask.buffer.rewind()
            mask.buffer.asFloatBuffer().get(values)
            val w = input.width
            val h = input.height
            val pixels = IntArray(w * h)
            input.getPixels(pixels, 0, w, 0, 0, w, h)
            // The mask is normally the input's size; resample it when a model version returns another size.
            val confidence = if (mask.width == w && mask.height == h) {
                values
            } else {
                FloatArray(w * h) { BackgroundMath.sample(values, mask.width, mask.height, w, h, it % w, it / w) }
            }
            PersonMatte.from(pixels, w, h, confidence, bitmap.width, bitmap.height, edge)
        } finally {
            segmenter.close()
            if (input !== bitmap) input.recycle()
        }
    }

    /**
     * [source] with its background replaced: [backdrop] (the same size) for a picture, the colour for a solid
     * fill, or see-through when [keepAlpha] allows it (otherwise white). [source] is left as it is.
     */
    suspend fun composite(
        source: Bitmap,
        matte: PersonMatte,
        replace: BackgroundReplace,
        backdrop: Bitmap?,
        keepAlpha: Boolean,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val out = createBitmap(width, height)
        val solid = when (val fill = replace.fill) {
            is BackgroundFill.Solid -> fill.argb
            else -> BackgroundReplace.WHITE
        }
        val transparent = replace.needsAlpha && keepAlpha
        val rows = min(BAND, height)
        val front = IntArray(width * rows)
        val alpha = FloatArray(width * rows)
        val back = if (backdrop != null) IntArray(width * rows) else null
        var top = 0
        while (top < height) {
            coroutineContext.ensureActive()
            val count = min(BAND, height - top)
            source.getPixels(front, 0, width, 0, top, width, count)
            back?.let { backdrop?.getPixels(it, 0, width, 0, top, width, count) }
            matte.cut(front, 0, top, count, alpha)
            for (i in 0 until width * count) {
                front[i] = when {
                    transparent -> BackgroundMath.cutOut(front[i], alpha[i])
                    back != null -> BackgroundMath.blend(front[i], back[i], alpha[i])
                    else -> BackgroundMath.blend(front[i], solid, alpha[i])
                }
            }
            out.setPixels(front, 0, width, 0, top, width, count)
            top += count
        }
        return out
    }

    private companion object {
        const val BAND = 256
    }
}
