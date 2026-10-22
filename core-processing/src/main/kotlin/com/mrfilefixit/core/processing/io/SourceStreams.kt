package com.mrfilefixit.core.processing.io

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Thrown when a stream turns out larger than allowed although its declared size was unknown or smaller. */
internal class InputTooLargeException(val limitBytes: Long) : IOException("Input exceeds limit")

/** Maps engine-specific exceptions to [AppError]; everything else goes through [AppError.fromThrowable]. */
internal fun mapEngineError(throwable: Throwable): AppError = when (throwable) {
    is InputTooLargeException -> AppError.FileTooLarge(throwable.limitBytes + 1, throwable.limitBytes)
    is UnsupportedImageException -> AppError.UnsupportedFormat(throwable.mimeType)
    is CorruptImageException -> AppError.CorruptFile
    is InputRejectedException -> throwable.error
    else -> AppError.fromThrowable(throwable)
}

internal class UnsupportedImageException(val mimeType: String?) : IOException("Unsupported image")

internal class CorruptImageException : IOException("Corrupt image")

/** Carries a validation [AppError] out of code that cannot return a Result (e.g. inside decoders). */
internal class InputRejectedException(val error: AppError) : IOException("Input rejected")

/** Stops reading after [limit] bytes so a lying content provider cannot feed us an unbounded stream. */
internal class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) advance(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) advance(n.toLong())
        return n
    }

    override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) advance(it) }

    private fun advance(n: Long) {
        count += n
        if (count > limit) throw InputTooLargeException(limit)
    }
}

// The stream is handed to the caller, who closes it with use {}.
@SuppressLint("Recycle")
internal fun ContentResolver.openLimited(uri: String, limitBytes: Long): InputStream {
    val stream = openInputStream(Uri.parse(uri)) ?: throw FileNotFoundException()
    return LimitedInputStream(stream.buffered(), limitBytes)
}

internal fun ContentResolver.readAllLimited(uri: String, limitBytes: Long): ByteArray =
    openLimited(uri, limitBytes).use { it.readBytes() }

/** For code paths that cannot return a Result (inside library callbacks): turns a validation failure into a throw. */
internal fun AppResult<Unit>.throwIfFailure() {
    if (this is Result.Failure) throw InputRejectedException(error)
}
