package com.mrfilefixit.core.domain.security

import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success

/**
 * Limits checked before any decoder sees a file. They block decompression bombs
 * (tiny files that decode to gigapixel bitmaps) and oversized or malformed PDFs.
 */
data class InputLimits(
    val maxFileBytes: Long = 50L * 1024 * 1024,
    val maxPixels: Long = 64_000_000L,
    val maxDimension: Int = 16_384,
    val maxPdfPages: Int = 1_000,
    val maxFilesPerJob: Int = 100,
) {
    fun checkFileSize(sizeBytes: Long?): AppResult<Unit> = when {
        sizeBytes == null -> success(Unit) // Unknown size: the stream reader enforces the limit while copying.
        sizeBytes <= 0L -> failure(AppError.EmptyFile)
        sizeBytes > maxFileBytes -> failure(AppError.FileTooLarge(sizeBytes, maxFileBytes))
        else -> success(Unit)
    }

    fun checkDimensions(width: Int, height: Int): AppResult<Unit> = when {
        width <= 0 || height <= 0 -> failure(AppError.CorruptFile)
        width > maxDimension || height > maxDimension ||
            width.toLong() * height.toLong() > maxPixels ->
            failure(AppError.DimensionsTooLarge(width, height, maxPixels))
        else -> success(Unit)
    }

    fun checkPageCount(pages: Int): AppResult<Unit> = when {
        pages <= 0 -> failure(AppError.CorruptFile)
        pages > maxPdfPages -> failure(AppError.TooManyPages(pages, maxPdfPages))
        else -> success(Unit)
    }

    fun checkFileCount(count: Int): AppResult<Unit> =
        if (count > maxFilesPerJob) failure(AppError.TooManyFiles(count, maxFilesPerJob)) else success(Unit)

    companion object {
        val DEFAULT = InputLimits()
    }
}
