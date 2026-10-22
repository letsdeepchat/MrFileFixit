package com.mrfilefixit.core.domain.image

import com.mrfilefixit.core.domain.file.FileRef

enum class ImageFormat(val mimeType: String, val extension: String, val supportsQuality: Boolean) {
    JPEG("image/jpeg", "jpg", supportsQuality = true),
    PNG("image/png", "png", supportsQuality = false),
    WEBP("image/webp", "webp", supportsQuality = true),
    HEIC("image/heic", "heic", supportsQuality = true),
    ;

    companion object {
        fun fromMimeType(mimeType: String?): ImageFormat? = when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg", "image/pjpeg" -> JPEG
            "image/png" -> PNG
            "image/webp" -> WEBP
            "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> HEIC
            else -> null
        }

        fun fromName(value: String): ImageFormat? = when (value.lowercase().trimStart('.')) {
            "jpg", "jpeg" -> JPEG
            "png" -> PNG
            "webp" -> WEBP
            "heic", "heif" -> HEIC
            else -> null
        }
    }
}

/** Header-level facts about an image, read without decoding pixels. */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val format: ImageFormat?,
    val sizeBytes: Long,
    /** EXIF rotation in degrees (0, 90, 180, 270). Width/height are already swapped for 90/270. */
    val rotationDegrees: Int = 0,
    val hasMetadata: Boolean = false,
)

/** Crop rectangle in normalised coordinates (0..1) of the upright image. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) { "Rect out of range" }
        require(right > left && bottom > top) { "Empty rect" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val FULL = NormalizedRect(0f, 0f, 1f, 1f)

        /** Largest centred rect with [aspect] (width / height) inside an image of [imageAspect]. */
        fun centeredWithAspect(aspect: Float, imageAspect: Float): NormalizedRect =
            if (imageAspect > aspect) {
                val w = aspect / imageAspect
                NormalizedRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
            } else {
                val h = imageAspect / aspect
                NormalizedRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
            }
    }
}

enum class ResizeMode {
    /** Scale to fit inside the box, keep aspect ratio. Output may be smaller than the box on one axis. */
    FIT,

    /** Scale and centre-crop to exactly fill the box. */
    FILL,

    /** Scale to fit, then pad to exactly the box size with the background colour. */
    PAD,

    /** Stretch to exactly the box size. */
    STRETCH,
}

sealed interface ImageOp {
    data class Crop(val rect: NormalizedRect) : ImageOp

    /** Clockwise rotation in multiples of 90. */
    data class Rotate(val degrees: Int) : ImageOp {
        init {
            require(degrees % 90 == 0) { "Rotation must be a multiple of 90" }
        }
    }

    data class Resize(val width: Int, val height: Int, val mode: ResizeMode = ResizeMode.FIT) : ImageOp {
        init {
            require(width > 0 && height > 0) { "Size must be positive" }
        }
    }

    /** Scale by a percentage, e.g. 50 halves both sides. */
    data class Scale(val percent: Int) : ImageOp {
        init {
            require(percent in 1..400) { "Scale must be 1..400%" }
        }
    }
}

/** How the encoder picks its quality. */
sealed interface EncodeTarget {
    data class Quality(val value: Int) : EncodeTarget {
        init {
            require(value in 1..100) { "Quality must be 1..100" }
        }
    }

    /**
     * Search for the highest quality whose output lies in [minBytes]..[maxBytes].
     * When the minimum cannot be reached at full quality and [allowPadding] is set, JPEG output is padded
     * with a comment segment (pixels are unchanged).
     */
    data class SizeRange(
        val minBytes: Long,
        val maxBytes: Long,
        val allowPadding: Boolean = false,
        /** Shrink pixel dimensions when even the lowest quality is too big. Off when dimensions are fixed. */
        val allowDownscale: Boolean = false,
    ) : EncodeTarget {
        init {
            require(minBytes in 0..maxBytes && maxBytes > 0) { "Invalid size range" }
        }
    }

    /**
     * Keep the encoded pixels as they are and only remove metadata (lossless EXIF strip).
     * Valid only with no operations and the source format as output. Engines fall back to a
     * high-quality re-encode when the source needs rotating to stay upright.
     */
    data object Original : EncodeTarget
}

data class OutputSpec(
    val format: ImageFormat,
    val target: EncodeTarget = EncodeTarget.Quality(DEFAULT_QUALITY),
    /** ARGB colour used for transparent pixels in formats without alpha, and for PAD. */
    val backgroundArgb: Int = WHITE,
    /** EXIF/XMP is stripped by default on export; only explicit "keep metadata" turns this off. */
    val stripMetadata: Boolean = true,
) {
    companion object {
        const val DEFAULT_QUALITY = 90
        const val WHITE = -0x1 // 0xFFFFFFFF
    }
}

data class ImageRequest(
    val source: FileRef,
    val outputDir: String,
    val outputBaseName: String,
    val operations: List<ImageOp>,
    val output: OutputSpec,
)

/** Photo and signature stacked into one image, as some exam portals require. */
data class CompositeRequest(
    val top: FileRef,
    val bottom: FileRef,
    val topCrop: NormalizedRect = NormalizedRect.FULL,
    val bottomCrop: NormalizedRect = NormalizedRect.FULL,
    /** Share of the output height used by the top image, 0..1. */
    val topFraction: Float,
    val width: Int,
    val height: Int,
    val outputDir: String,
    val outputBaseName: String,
    val output: OutputSpec,
)

/** Result of an image job with what was achieved, for the pass/fail screen. */
data class ProcessedImage(
    val file: com.mrfilefixit.core.domain.file.OutputFile,
    val format: ImageFormat,
    val quality: Int?,
    val padded: Boolean,
)
