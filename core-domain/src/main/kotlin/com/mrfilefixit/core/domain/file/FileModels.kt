package com.mrfilefixit.core.domain.file

/**
 * A user-selected file, addressed by its SAF `content://` URI.
 * [toString] is redacted so a file reference can never leak into a log line.
 */
data class FileRef(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
) {
    override fun toString(): String = "FileRef(mime=$mimeType, size=$sizeBytes)"
}

/** A file produced by a job, stored in app-private cache until it is shared or saved. */
data class OutputFile(
    val path: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val pageCount: Int? = null,
) {
    override fun toString(): String = "OutputFile(mime=$mimeType, size=$sizeBytes, ${width}x$height, pages=$pageCount)"
}

/** Destination chosen by the user with ACTION_CREATE_DOCUMENT. */
@JvmInline
value class DestinationUri(val value: String) {
    override fun toString(): String = "DestinationUri(redacted)"
}
