package com.mrfilefixit.core.storage.resume

import android.content.Context
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.resume.Resume
import com.mrfilefixit.core.domain.resume.ResumeJson
import com.mrfilefixit.core.domain.resume.ResumeRepository
import com.mrfilefixit.core.domain.resume.ResumeSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Drafts live in app-private `files/resumes/` as `<id>.json`, with the photo next to it as `<id>.jpg`.
 * Unlike job files they are kept until the user deletes them. Backup is disabled app-wide, so they never
 * leave the phone. Writes go to a temporary file first and are then renamed, so a crash cannot leave a
 * half-written draft.
 */
@Singleton
internal class FileResumeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ResumeRepository {
    private val root: File get() = File(context.filesDir, DIR).apply { mkdirs() }
    private val lock = Mutex()

    override suspend fun list(): AppResult<List<ResumeSummary>> = io {
        val drafts = root.listFiles { f -> f.isFile && f.name.endsWith(JSON) }.orEmpty()
            .mapNotNull { file -> ResumeJson.decode(file.readText())?.let { it to file } }
            .filter { (resume, file) -> file.name == resume.id + JSON }
            .map { (r, _) -> ResumeSummary(r.id, r.displayName, r.templateId, r.updatedAtMillis) }
            .sortedByDescending { it.updatedAtMillis }
        success(drafts)
    }

    override suspend fun load(id: String): AppResult<Resume> = io {
        val file = draftFile(id) ?: return@io failure(AppError.FileNotFound)
        if (!file.isFile) return@io failure(AppError.FileNotFound)
        ResumeJson.decode(file.readText())?.let { success(it) } ?: failure(AppError.CorruptFile)
    }

    override suspend fun save(resume: Resume): AppResult<Unit> = io {
        val file = draftFile(resume.id) ?: return@io failure(AppError.Unexpected("InvalidResumeId"))
        writeAtomically(file) { it.writeText(ResumeJson.encode(resume)) }
        success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> = io {
        draftFile(id)?.delete()
        photoFile(id)?.delete()
        success(Unit)
    }

    override suspend fun savePhoto(id: String, jpegPath: String): AppResult<Unit> = io {
        val target = photoFile(id) ?: return@io failure(AppError.Unexpected("InvalidResumeId"))
        val source = File(jpegPath)
        // Only copies from this app's own cache (the cropped photo's job directory).
        if (!source.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator)) {
            return@io failure(AppError.PermissionDenied)
        }
        if (source.length() !in 1..MAX_PHOTO_BYTES) {
            return@io failure(AppError.FileTooLarge(source.length(), MAX_PHOTO_BYTES))
        }
        writeAtomically(
            target,
        ) { tmp -> source.inputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } } }
        success(Unit)
    }

    override suspend fun photo(id: String): AppResult<ByteArray?> = io {
        val file = photoFile(id)
        success(if (file != null && file.isFile && file.length() <= MAX_PHOTO_BYTES) file.readBytes() else null)
    }

    override suspend fun deletePhoto(id: String): AppResult<Unit> = io {
        photoFile(id)?.delete()
        success(Unit)
    }

    private fun draftFile(id: String): File? = if (ID.matches(id)) File(root, id + JSON) else null

    private fun photoFile(id: String): File? = if (ID.matches(id)) File(root, id + JPG) else null

    private inline fun writeAtomically(target: File, write: (File) -> Unit) {
        val tmp = File(target.parentFile, target.name + TMP)
        write(tmp)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private suspend fun <T> io(block: suspend () -> AppResult<T>): AppResult<T> = withContext(dispatchers.io) {
        lock.withLock { runCatchingAppFlat { block() } }
    }

    private companion object {
        const val DIR = "resumes"
        const val JSON = ".json"
        const val JPG = ".jpg"
        const val TMP = ".tmp"
        const val MAX_PHOTO_BYTES = 2L * 1024 * 1024
        val ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
