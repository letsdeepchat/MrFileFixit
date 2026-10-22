package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.heifwriter.HeifWriter
import com.mrfilefixit.core.domain.image.ImageFormat
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/** Encodes bitmaps in memory (JPEG/PNG/WebP) or through a temp file (HEIC). */
internal class ImageEncoding @Inject constructor() {
    private val heicEncoderAvailable: Boolean by lazy { detectHeicEncoder() }

    fun canEncode(format: ImageFormat): Boolean = when (format) {
        ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP -> true
        ImageFormat.HEIC -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && heicEncoderAvailable
    }

    /** Encodes [bitmap]; [scratchDir] is only used for HEIC, which HeifWriter can only write to a file. */
    fun encode(bitmap: Bitmap, format: ImageFormat, quality: Int, scratchDir: File): ByteArray = when (format) {
        ImageFormat.HEIC -> encodeHeic(bitmap, quality, scratchDir)
        else -> ByteArrayOutputStream(bitmap.byteCount / ESTIMATED_RATIO).use { out ->
            check(bitmap.compress(compressFormat(format), quality, out)) { "Encoder refused bitmap" }
            out.toByteArray()
        }
    }

    private fun compressFormat(format: ImageFormat): Bitmap.CompressFormat = when (format) {
        ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ImageFormat.PNG -> Bitmap.CompressFormat.PNG
        ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        ImageFormat.HEIC -> error("HEIC is encoded with HeifWriter")
    }

    private fun encodeHeic(bitmap: Bitmap, quality: Int, scratchDir: File): ByteArray {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { "HEIC encoding needs API 28" }
        val temp = File.createTempFile("heic", ".tmp", scratchDir)
        try {
            HeifWriter.Builder(temp.absolutePath, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(quality)
                .setMaxImages(1)
                .build()
                .use { writer ->
                    writer.start()
                    writer.addBitmap(bitmap)
                    writer.stop(HEIC_TIMEOUT_MS)
                }
            return temp.readBytes()
        } finally {
            temp.delete()
        }
    }

    private fun detectHeicEncoder(): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info: MediaCodecInfo ->
            info.isEncoder && info.supportedTypes.any {
                it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) ||
                    it.equals(MIMETYPE_IMAGE_HEIC, ignoreCase = true)
            }
        }
    } catch (_: RuntimeException) {
        false
    }

    private companion object {
        const val ESTIMATED_RATIO = 8
        const val HEIC_TIMEOUT_MS = 10_000L
        const val MIMETYPE_IMAGE_HEIC = "image/vnd.android.heic"
    }
}
