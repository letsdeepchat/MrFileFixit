package com.mrfilefixit.core.storage.files

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.DestinationUri
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.FileRepository
import com.mrfilefixit.core.domain.file.ImportFormat
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingApp
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.FilenameSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
internal class SafFileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val dispatchers: DispatcherProvider,
) : FileRepository {

    override suspend fun describe(uri: String): AppResult<FileRef> = withContext(dispatchers.io) {
        runCatchingApp {
            val parsed = uri.toUri()
            require(parsed.scheme == ContentResolver.SCHEME_CONTENT) { "Only content:// URIs are accepted" }
            var name: String? = null
            var size: Long? = null
            resolver.query(parsed, PROJECTION, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            } ?: throw FileNotFoundException()
            FileRef(
                uri = uri,
                displayName = FilenameSanitizer.sanitize(name.orEmpty()),
                mimeType = resolver.getType(parsed),
                sizeBytes = size,
            )
        }
    }

    override suspend fun saveTo(output: OutputFile, destination: DestinationUri): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatchingAppFlat {
                val file = jobFile(output) ?: return@runCatchingAppFlat failure(AppError.FileNotFound)
                // "wt" truncates: overwriting a longer file must not leave its tail behind.
                resolver.openOutputStream(destination.value.toUri(), "wt")
                    ?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: return@runCatchingAppFlat failure(AppError.PermissionDenied)
                success(Unit)
            }
        }

    override suspend fun shareUri(output: OutputFile): AppResult<String> = withContext(dispatchers.io) {
        runCatchingAppFlat {
            val file = jobFile(output) ?: return@runCatchingAppFlat failure(AppError.FileNotFound)
            success(FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString())
        }
    }

    override suspend fun importToJob(
        uri: String,
        jobDir: String,
        baseName: String,
        format: ImportFormat,
        maxBytes: Long,
    ): AppResult<OutputFile> = withContext(dispatchers.io) {
        runCatchingAppFlat {
            val parsed = uri.toUri()
            val allowed = when (parsed.scheme) {
                ContentResolver.SCHEME_CONTENT -> true
                ContentResolver.SCHEME_FILE -> parsed.path?.let { isInsideAppStorage(File(it)) } == true
                else -> false
            }
            if (!allowed) return@runCatchingAppFlat failure(AppError.PermissionDenied)
            val dir = File(jobDir).canonicalFile
            if (!isInsideJobs(dir)) return@runCatchingAppFlat failure(AppError.PermissionDenied)
            val target = uniqueFile(dir, FilenameSanitizer.sanitize(baseName).ifBlank { "scan" }, format.extension)
            var copied = 0L
            resolver.openInputStream(parsed)?.use { source ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > maxBytes) {
                            out.close()
                            target.delete()
                            return@runCatchingAppFlat failure(AppError.FileTooLarge(copied, maxBytes))
                        }
                        out.write(buffer, 0, read)
                    }
                }
            } ?: return@runCatchingAppFlat failure(AppError.FileNotFound)
            if (copied == 0L) {
                target.delete()
                return@runCatchingAppFlat failure(AppError.EmptyFile)
            }
            success(OutputFile(target.absolutePath, target.name, format.mimeType, copied))
        }
    }

    private fun uniqueFile(dir: File, base: String, extension: String): File {
        var candidate = File(dir, "$base.$extension")
        var n = 2
        while (candidate.exists()) candidate = File(dir, "$base-${n++}.$extension")
        return candidate
    }

    private fun isInsideAppStorage(file: File): Boolean {
        val canonical = file.canonicalFile.path
        return listOf(context.cacheDir, context.filesDir).any {
            canonical.startsWith(it.canonicalPath + File.separator)
        }
    }

    private fun isInsideJobs(dir: File): Boolean {
        val root = File(context.cacheDir, CacheTempFileStore.JOBS_DIR).canonicalFile
        return dir.isDirectory && dir.path.startsWith(root.path + File.separator)
    }

    /** Resolves [output] only if it really lives in our job cache (defence against path tricks). */
    private fun jobFile(output: OutputFile): File? {
        val root = File(context.cacheDir, CacheTempFileStore.JOBS_DIR).canonicalFile
        val file = File(output.path).canonicalFile
        return file.takeIf { it.isFile && it.path.startsWith(root.path + File.separator) }
    }

    private companion object {
        val PROJECTION = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        const val BUFFER_BYTES = 64 * 1024
    }
}
