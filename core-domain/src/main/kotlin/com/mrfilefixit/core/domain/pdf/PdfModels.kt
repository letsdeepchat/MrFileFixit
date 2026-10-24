package com.mrfilefixit.core.domain.pdf

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.ImageFormat

data class PdfInfo(val pageCount: Int, val encrypted: Boolean, val sizeBytes: Long)

enum class PageSize(val widthPt: Float, val heightPt: Float) {
    A4(595f, 842f),
    LETTER(612f, 792f),
    LEGAL(612f, 1008f),

    /** Each page takes the size of its image. */
    FIT_IMAGE(0f, 0f),
}

/** Page orientation for images placed on paper. [AUTO] turns each page to match its image. */
enum class PageOrientation { AUTO, PORTRAIT, LANDSCAPE }

/** Margin around images on paper, in PDF points (1 mm = 72 / 25.4 pt). */
enum class PageMargin(val points: Float) {
    NONE(0f),
    SMALL(14.17f), // 5 mm
    BIG(28.35f), // 10 mm
}

/** How Split PDF divides the document. */
enum class SplitMode { CUSTOM, EVERY_N, SINGLE_PAGES }

enum class Position { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

/**
 * Target resolution and JPEG quality for embedded images. Text and vector content are never rasterized,
 * except as [HIGH]'s last resort when nothing else makes the file smaller (see [PdfCompressionPolicy]).
 */
enum class CompressionLevel(val dpi: Int, val jpegQuality: Int) {
    LOW(dpi = 150, jpegQuality = 75),
    MEDIUM(dpi = 120, jpegQuality = 60),
    HIGH(dpi = 96, jpegQuality = 45),
}

/**
 * Every PDF tool, as data. One engine method handles them all, which keeps the engine swappable and
 * the ViewModels free of library types.
 *
 * Page numbers are 1-based, as users see them. Passwords are [CharArray] so callers can wipe them;
 * they are never stored.
 */
sealed interface PdfOperation {
    val outputDir: String
    val outputBaseName: String

    data class Merge(val sources: List<FileRef>, override val outputDir: String, override val outputBaseName: String) :
        PdfOperation

    /** One output file per range. */
    data class Split(
        val source: FileRef,
        val ranges: List<IntRange>,
        override val outputDir: String,
        override val outputBaseName: String,
    ) : PdfOperation

    /** Keeps [pages] in the given order. Covers extract, reorder and delete. */
    data class SelectPages(
        val source: FileRef,
        val pages: List<Int>,
        override val outputDir: String,
        override val outputBaseName: String,
    ) : PdfOperation

    data class Rotate(
        val source: FileRef,
        val pages: Set<Int>,
        val degrees: Int,
        override val outputDir: String,
        override val outputBaseName: String,
    ) : PdfOperation

    /**
     * Downsamples embedded images, recompresses uncompressed streams and drops metadata. The output is
     * always smaller than the source, or the job fails with `AppError.AlreadyOptimized` and nothing is
     * written. [grayscale] also converts images to grey, which saves more on colour scans.
     */
    data class Compress(
        val source: FileRef,
        val level: CompressionLevel,
        override val outputDir: String,
        override val outputBaseName: String,
        val grayscale: Boolean = false,
    ) : PdfOperation

    data class ImagesToPdf(
        val images: List<FileRef>,
        val pageSize: PageSize,
        val marginPt: Float,
        override val outputDir: String,
        override val outputBaseName: String,
        val orientation: PageOrientation = PageOrientation.AUTO,
    ) : PdfOperation

    data class ToImages(
        val source: FileRef,
        val pages: List<Int>,
        val dpi: Int,
        val format: ImageFormat,
        override val outputDir: String,
        override val outputBaseName: String,
    ) : PdfOperation

    data class Watermark(
        val source: FileRef,
        val text: String,
        val opacity: Float,
        val fontSizePt: Float,
        val diagonal: Boolean,
        override val outputDir: String,
        override val outputBaseName: String,
    ) : PdfOperation

    data class PageNumbers(
        val source: FileRef,
        val position: Position,
        val startAt: Int,
        /** Pattern with `{n}` and `{total}`, e.g. "Page {n} of {total}". Comes from string resources. */
        val pattern: String,
        override val outputDir: String,
        override val outputBaseName: String,
    ) : PdfOperation

    class Protect(
        val source: FileRef,
        val userPassword: CharArray,
        val ownerPassword: CharArray,
        override val outputDir: String,
        override val outputBaseName: String,
    ) : PdfOperation {
        override fun toString(): String = "Protect(redacted)"
    }

    class Unlock(
        val source: FileRef,
        val password: CharArray,
        override val outputDir: String,
        override val outputBaseName: String,
    ) : PdfOperation {
        override fun toString(): String = "Unlock(redacted)"
    }
}

/** Wipes any password held by [this] operation. Call when the job ends. */
fun PdfOperation.wipeSecrets() {
    when (this) {
        is PdfOperation.Protect -> {
            userPassword.fill('\u0000')
            ownerPassword.fill('\u0000')
        }
        is PdfOperation.Unlock -> password.fill('\u0000')
        else -> Unit
    }
}
