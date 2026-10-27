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
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

/** Per-pixel "person" confidence (0..1), row-major, usually smaller than the photo it was made from. */
internal class PersonMask(val width: Int, val height: Int, val values: FloatArray) {
    val hasPerson: Boolean get() = BackgroundMath.hasPerson(values)
}

/**
 * Remove background on this phone: ML Kit selfie segmentation (its model is bundled in the app, so nothing is
 * downloaded and nothing leaves the phone) finds the person, then the rest of the photo is replaced.
 *
 * The mask is made from a copy at most [MASK_SIDE] pixels on its long side and read back with bilinear
 * interpolation, so a large photo needs only a small mask and the outline stays smooth.
 */
internal class BackgroundRemoval @Inject constructor() {
    suspend fun mask(bitmap: Bitmap): PersonMask {
        val scale = min(1f, MASK_SIDE.toFloat() / max(bitmap.width, bitmap.height))
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
            PersonMask(mask.width, mask.height, values)
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
        mask: PersonMask,
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
        val back = if (backdrop != null) IntArray(width * rows) else null
        var top = 0
        while (top < height) {
            coroutineContext.ensureActive()
            val count = min(BAND, height - top)
            source.getPixels(front, 0, width, 0, top, width, count)
            back?.let { backdrop?.getPixels(it, 0, width, 0, top, width, count) }
            for (row in 0 until count) {
                val y = top + row
                for (x in 0 until width) {
                    val i = row * width + x
                    val confidence = BackgroundMath.sample(mask.values, mask.width, mask.height, width, height, x, y)
                    val alpha = BackgroundMath.alpha(confidence, replace.edge)
                    front[i] = when {
                        transparent -> BackgroundMath.cutOut(front[i], alpha)
                        back != null -> BackgroundMath.blend(front[i], back[i], alpha)
                        else -> BackgroundMath.blend(front[i], solid, alpha)
                    }
                }
            }
            out.setPixels(front, 0, width, 0, top, width, count)
            top += count
        }
        return out
    }

    private companion object {
        /** Long side of the copy that is segmented; ML Kit's model works at a few hundred pixels anyway. */
        const val MASK_SIDE = 1024
        const val BAND = 256
    }
}
