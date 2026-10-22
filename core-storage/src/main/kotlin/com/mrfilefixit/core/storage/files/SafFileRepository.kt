package com.mrfilefixit.core.storage.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.DestinationUri
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.FileRepository
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingApp
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.FilenameSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SafFileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val dispatchers: DispatcherProvider,
) : FileRepository {

    override suspend fun describe(uri: String): AppResult<FileRef> = withContext(dispatchers.io) {
        runCatchingApp {
            val parsed = Uri.parse(uri)
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
                val stream = resolver.openOutputStream(Uri.parse(destination.value), "wt")
                    ?: return@runCatchingAppFlat failure(AppError.PermissionDenied)
                stream.use { out -> file.inputStream().use { it.copyTo(out) } }
                success(Unit)
            }
        }

    override suspend fun shareUri(output: OutputFile): AppResult<String> = withContext(dispatchers.io) {
        runCatchingAppFlat {
            val file = jobFile(output) ?: return@runCatchingAppFlat failure(AppError.FileNotFound)
            success(FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString())
        }
    }

    /** Resolves [output] only if it really lives in our job cache (defence against path tricks). */
    private fun jobFile(output: OutputFile): File? {
        val root = File(context.cacheDir, CacheTempFileStore.JOBS_DIR).canonicalFile
        val file = File(output.path).canonicalFile
        return file.takeIf { it.isFile && it.path.startsWith(root.path + File.separator) }
    }

    private companion object {
        val PROJECTION = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    }
}
