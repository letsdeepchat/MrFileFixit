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
