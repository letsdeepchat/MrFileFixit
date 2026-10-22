package com.mrfilefixit.core.domain.file

import com.mrfilefixit.core.domain.result.AppResult

/** Access to user files through the Storage Access Framework only. */
interface FileRepository {
    /** Reads name, MIME type and size of a picked file without opening its content. */
    suspend fun describe(uri: String): AppResult<FileRef>

    /** Copies a job output to a destination the user picked with the system "Save as" dialog. */
    suspend fun saveTo(output: OutputFile, destination: DestinationUri): AppResult<Unit>

    /** A `content://` URI for [output] served by our FileProvider, for the share sheet. */
    suspend fun shareUri(output: OutputFile): AppResult<String>

    /**
     * Copies a file handed to us by another component (for example the document scanner) into [jobDir].
     * Accepts `content://` URIs, and `file://` URIs only when they point inside this app's private storage.
     * The copy is capped at [maxBytes]; [baseName] is sanitized and made unique inside [jobDir].
     */
    suspend fun importToJob(
        uri: String,
        jobDir: String,
        baseName: String,
        format: ImportFormat,
        maxBytes: Long,
    ): AppResult<OutputFile>
}

enum class ImportFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    JPEG("image/jpeg", "jpg"),
}

/** App-private scratch space. Each job gets its own directory that is deleted when the job ends. */
interface TempFileStore {
    /** Creates `cache/jobs/<jobId>/` and returns its absolute path. */
    suspend fun createJobDir(jobId: String): AppResult<String>

    suspend fun deleteJobDir(jobId: String)

    /** Deletes job directories older than [maxAgeMillis]; called on app start. */
    suspend fun deleteStale(maxAgeMillis: Long)
}

/** Generates job ids. Injected so tests are deterministic. */
fun interface JobIdGenerator {
    fun next(): String
}
