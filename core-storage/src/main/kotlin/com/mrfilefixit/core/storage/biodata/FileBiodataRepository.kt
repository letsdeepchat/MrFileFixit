package com.mrfilefixit.core.storage.biodata

import android.content.Context
import com.mrfilefixit.core.domain.biodata.Biodata
import com.mrfilefixit.core.domain.biodata.BiodataJson
import com.mrfilefixit.core.domain.biodata.BiodataRepository
import com.mrfilefixit.core.domain.biodata.BiodataSummary
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Biodata drafts live in app-private `files/biodata/` as `<id>.json`, each photo next to it as
 * `<id>-<key>.jpg`. Like resumes they stay until the user deletes them, never leave the phone (backup is off
 * app-wide), and are written to a temporary file first and then renamed.
 */
@Singleton
internal class FileBiodataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : BiodataRepository {
    private val root: File get() = File(context.filesDir, DIR).apply { mkdirs() }
    private val lock = Mutex()

    override suspend fun list(): AppResult<List<BiodataSummary>> = io {
        val drafts = root.listFiles { f -> f.isFile && f.name.endsWith(JSON) }.orEmpty()
            .mapNotNull { file -> BiodataJson.decode(file.readText())?.let { it to file } }
            .filter { (biodata, file) -> file.name == biodata.id + JSON }
            .map { (b, _) -> BiodataSummary(b.id, b.displayName, b.templateId, b.photos.size, b.updatedAtMillis) }
            .sortedByDescending { it.updatedAtMillis }
        success(drafts)
    }

    override suspend fun load(id: String): AppResult<Biodata> = io {
        val file = draftFile(id) ?: return@io failure(AppError.FileNotFound)
        if (!file.isFile) return@io failure(AppError.FileNotFound)
        BiodataJson.decode(file.readText())?.let { success(it) } ?: failure(AppError.CorruptFile)
    }

    override suspend fun save(biodata: Biodata): AppResult<Unit> = io {
        val file = draftFile(biodata.id) ?: return@io failure(AppError.Unexpected("InvalidBiodataId"))
        writeAtomically(file) { it.writeText(BiodataJson.encode(biodata)) }
        // Photos removed from the draft are deleted with it, so no orphans pile up.
        val keep = biodata.photos.mapTo(HashSet()) { photoName(biodata.id, it.key) }
        photosOf(biodata.id).filter { it.name !in keep }.forEach(File::delete)
        success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> = io {
        draftFile(id)?.delete()
        photosOf(id).forEach(File::delete)
        success(Unit)
    }

    override suspend fun savePhoto(id: String, key: String, jpegPath: String): AppResult<Unit> = io {
        val target = photoFile(id, key) ?: return@io failure(AppError.Unexpected("InvalidBiodataId"))
        val source = File(jpegPath)
        // Only copies from this app's own cache (the cropped photo's job directory).
        if (!source.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator)) {
            return@io failure(AppError.PermissionDenied)
        }
        if (source.length() !in 1..MAX_PHOTO_BYTES) {
            return@io failure(AppError.FileTooLarge(source.length(), MAX_PHOTO_BYTES))
        }
        writeAtomically(target) { tmp ->
            source.inputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        }
        success(Unit)
    }

    override suspend fun photo(id: String, key: String): AppResult<ByteArray?> = io {
        val file = photoFile(id, key)
        success(if (file != null && file.isFile && file.length() <= MAX_PHOTO_BYTES) file.readBytes() else null)
    }

    override suspend fun deletePhoto(id: String, key: String): AppResult<Unit> = io {
        photoFile(id, key)?.delete()
        success(Unit)
    }

    private fun draftFile(id: String): File? = if (ID.matches(id)) File(root, id + JSON) else null

    private fun photoFile(id: String, key: String): File? =
        if (ID.matches(id) && ID.matches(key)) File(root, photoName(id, key)) else null

    private fun photosOf(id: String): List<File> {
        if (!ID.matches(id)) return emptyList()
        val prefix = "$id-"
        return root.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(JPG) }.orEmpty()
            .filter { ID.matches(it.name.removePrefix(prefix).removeSuffix(JPG)) }
    }

    private fun photoName(id: String, key: String) = "$id-$key$JPG"

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
        const val DIR = "biodata"
        const val JSON = ".json"
        const val JPG = ".jpg"
        const val TMP = ".tmp"
        const val MAX_PHOTO_BYTES = 4L * 1024 * 1024
        val ID = Regex("^[A-Za-z0-9]{1,64}$")
    }
}
