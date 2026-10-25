package com.mrfilefixit.core.domain.result

import java.io.FileNotFoundException
import java.io.IOException

/**
 * Every failure the app can show to the user.
 *
 * Errors carry numbers and enums only, never file names, paths or content, so an error can be
 * logged or reported without leaking user data.
 */
sealed interface AppError {
    /** Input validation (runs before any decoding). */
    data class FileTooLarge(val actualBytes: Long, val limitBytes: Long) : AppError

    data class DimensionsTooLarge(val width: Int, val height: Int, val maxPixels: Long) : AppError

    data class TooManyPages(val pageCount: Int, val limit: Int) : AppError

    data class TooManyFiles(val count: Int, val limit: Int) : AppError

    data class UnsupportedFormat(val mimeType: String?) : AppError

    data object CorruptFile : AppError

    data object EmptyFile : AppError

    /** PDFs. */
    data object PasswordRequired : AppError

    data object WrongPassword : AppError

    data class InvalidPageSelection(val pageCount: Int) : AppError

    /** Size targeting could not reach the requested range. */
    data class TargetSizeUnreachable(
        val achievedBytes: Long,
        val minBytes: Long,
        val maxBytes: Long,
    ) : AppError

    /** Storage. */
    data object FileNotFound : AppError

    data object PermissionDenied : AppError

    data object StorageFull : AppError

    /** Device and environment. */
    data object OutOfMemory : AppError

    data class FeatureUnavailable(val feature: Feature) : AppError

    /** Compression could not make the file any smaller, so the original is kept as it is. */
    data object AlreadyOptimized : AppError

    /** Office documents. A Word, Excel or PowerPoint file protected with a password cannot be opened. */
    data object DocumentProtected : AppError

    /** The document is valid but bigger or more deeply nested than the converter allows on a phone. */
    data object DocumentTooComplex : AppError

    /** A PDF with no selectable text (usually a scan) cannot become a spreadsheet or editable slides. */
    data object NoTextFound : AppError

    /** A file picked to re-open as a resume was not exported by the resume builder. */
    data object NotAResume : AppError

    /** Anything else. [type] is the exception class name, never its message. */
    data class Unexpected(val type: String) : AppError

    enum class Feature { HEIC_DECODE, HEIC_ENCODE, WEBP_ENCODE, DOCUMENT_SCANNER, OCR, BATCH_WORKER }

    companion object {
        fun fromThrowable(throwable: Throwable): AppError = when (throwable) {
            is OutOfMemoryError -> OutOfMemory
            is FileNotFoundException -> FileNotFound
            is SecurityException -> PermissionDenied
            is IOException -> if (throwable.isNoSpace()) StorageFull else Unexpected(throwable.javaClass.simpleName)
            else -> Unexpected(throwable.javaClass.simpleName)
        }

        private fun IOException.isNoSpace(): Boolean =
            message?.contains("ENOSPC") == true || message?.contains("No space left", ignoreCase = true) == true
    }
}
