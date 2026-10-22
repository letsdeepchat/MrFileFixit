package com.mrfilefixit.core.processing.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.io.CorruptImageException
import com.mrfilefixit.core.processing.io.InputRejectedException
import com.mrfilefixit.core.processing.io.UnsupportedImageException
import com.mrfilefixit.core.processing.io.openLimited
import java.io.IOException
import javax.inject.Inject
import kotlin.math.roundToInt

/** Header facts in raw (stored) orientation. */
internal data class RawImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String?,
    val exifRotation: Int,
    val flipped: Boolean,
    val hasExif: Boolean,
) {
    val format: ImageFormat? get() = ImageFormat.fromMimeType(mimeType)
    val uprightWidth: Int get() = if (exifRotation % 180 == 0) width else height
    val uprightHeight: Int get() = if (exifRotation % 180 == 0) height else width
}

/**
 * Decodes only what is needed: headers first, then the cropped region at a power-of-two sample size.
 * Every read is bounded by [InputLimits.maxFileBytes].
 */
internal class ImageDecoding @Inject constructor(
    private val resolver: ContentResolver,
    private val limits: InputLimits,
) {
    /** Reads headers and enforces [InputLimits] before any pixel is decoded. */
    fun readRawInfo(uri: String): RawImageInfo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openLimited(uri, limits.maxFileBytes).use { BitmapFactory.decodeStream(it, null, options) }
        val mime = options.outMimeType ?: resolver.getType(Uri.parse(uri))
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            val format = ImageFormat.fromMimeType(mime)
            if (format == null || (format == ImageFormat.HEIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.P)) {
                throw UnsupportedImageException(mime)
            }
            throw CorruptImageException()
        }
        val dimensionCheck = limits.checkDimensions(options.outWidth, options.outHeight)
        if (dimensionCheck is Result.Failure) throw InputRejectedException(dimensionCheck.error)
        val exif = try {
            resolver.openLimited(uri, limits.maxFileBytes).use { ExifInterface(it) }
        } catch (_: IOException) {
            null
        }
        return RawImageInfo(
            width = options.outWidth,
            height = options.outHeight,
            mimeType = mime,
            exifRotation = exif?.rotationDegrees ?: 0,
            flipped = exif?.isFlipped ?: false,
            hasExif = exif?.hasAttributes() ?: false,
        )
    }

    /**
     * Decodes [rawRect] (normalised, raw orientation) at [sampleSize]. Uses [BitmapRegionDecoder] so only
     * the crop is decoded; falls back to a sampled full decode for formats the region decoder rejects.
     */
    fun decodeRegion(uri: String, info: RawImageInfo, rawRect: NormalizedRect, sampleSize: Int): Bitmap {
        val rect = Rect(
            (rawRect.left * info.width).roundToInt(),
            (rawRect.top * info.height).roundToInt(),
            (rawRect.right * info.width).roundToInt().coerceAtLeast((rawRect.left * info.width).roundToInt() + 1),
            (rawRect.bottom * info.height).roundToInt().coerceAtLeast((rawRect.top * info.height).roundToInt() + 1),
        )
        val sample = safeSampleSize(rect.width(), rect.height(), sampleSize)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        regionDecode(uri, rect, options)?.let { return it }
        return fullDecodeThenCrop(uri, info, rect, sampleSize)
    }

    private fun regionDecode(uri: String, rect: Rect, options: BitmapFactory.Options): Bitmap? = try {
        resolver.openLimited(uri, limits.maxFileBytes).use { stream ->
            val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(stream)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(stream, false)
            }
            try {
                decoder?.decodeRegion(rect, options)
            } finally {
                decoder?.recycle()
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun fullDecodeThenCrop(uri: String, info: RawImageInfo, rect: Rect, requestedSample: Int): Bitmap {
        val sample = safeSampleSize(info.width, info.height, requestedSample)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val full = resolver.openLimited(uri, limits.maxFileBytes).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw CorruptImageException()
        val scaled = Rect(
            rect.left / sample,
            rect.top / sample,
            (rect.right / sample).coerceIn(rect.left / sample + 1, full.width),
            (rect.bottom / sample).coerceIn(rect.top / sample + 1, full.height),
        )
        if (scaled.left == 0 && scaled.top == 0 && scaled.width() == full.width && scaled.height() == full.height) {
            return full
        }
        return full.use { Bitmap.createBitmap(it, scaled.left, scaled.top, scaled.width(), scaled.height()) }
    }

    /** Raises the sample size until the decoded bitmap fits in a quarter of the heap. */
    private fun safeSampleSize(width: Int, height: Int, requested: Int): Int {
        val budget = Runtime.getRuntime().maxMemory() / HEAP_FRACTION
        var sample = requested.coerceAtLeast(1)
        while ((width.toLong() / sample) * (height.toLong() / sample) * BYTES_PER_PIXEL > budget) sample *= 2
        return sample
    }

    private companion object {
        const val HEAP_FRACTION = 4
        const val BYTES_PER_PIXEL = 4L
    }
}
