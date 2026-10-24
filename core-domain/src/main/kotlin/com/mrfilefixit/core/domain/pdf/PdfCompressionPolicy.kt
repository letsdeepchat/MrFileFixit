package com.mrfilefixit.core.domain.pdf

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The rules the PDF compressor follows, kept free of Android and PDF library types so they are unit-tested.
 *
 * Strategy: shrink only what is heavy (embedded images) and keep text and vector content as they are.
 * Every replacement must be smaller than what it replaces, and the finished file must be smaller than
 * the source. Rasterizing whole pages is a last resort that only [CompressionLevel.HIGH] may use.
 */
object PdfCompressionPolicy {
    private const val POINTS_PER_INCH = 72f

    /**
     * Pixel size for an image of [imageWidth] x [imageHeight] drawn on a page of [pageWidthPt] x
     * [pageHeightPt], so that it is no sharper than [dpi] at full-page size. Never upscales, never returns
     * less than 1 px.
     * The page bound is conservative: an image drawn smaller than the page keeps a higher effective dpi.
     */
    fun targetSize(
        imageWidth: Int,
        imageHeight: Int,
        pageWidthPt: Float,
        pageHeightPt: Float,
        dpi: Int,
    ): Pair<Int, Int> {
        if (imageWidth <= 0 || imageHeight <= 0) return imageWidth to imageHeight
        val maxW = pageWidthPt / POINTS_PER_INCH * dpi
        val maxH = pageHeightPt / POINTS_PER_INCH * dpi
        // Rotated pages can hold an image either way round, so compare against the larger page side too.
        val longSide = max(maxW, maxH)
        val shortSide = min(maxW, maxH)
        val imageLong = max(imageWidth, imageHeight).toFloat()
        val imageShort = min(imageWidth, imageHeight).toFloat()
        val scale = min(1f, min(longSide / imageLong, shortSide / imageShort))
        if (scale >= 1f) return imageWidth to imageHeight
        return max(1, (imageWidth * scale).roundToInt()) to max(1, (imageHeight * scale).roundToInt())
    }

    /** An image stream is replaced only when the new encoding is strictly smaller. */
    fun shouldReplace(originalBytes: Long, replacementBytes: Long): Boolean = replacementBytes < originalBytes

    /**
     * Picks the smallest candidate that is strictly smaller than the source, or null when none is.
     * Null means the source is already as small as these methods can make it.
     */
    fun <T> smallestBelow(sourceBytes: Long, candidates: List<Pair<T, Long>>): T? =
        candidates.filter { (_, size) -> size in 1 until sourceBytes }.minByOrNull { it.second }?.first

    /** Whole-page rasterization loses selectable text, so only the strongest level may fall back to it. */
    fun mayRasterize(level: CompressionLevel): Boolean = level == CompressionLevel.HIGH
}
