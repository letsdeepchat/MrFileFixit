package com.mrfilefixit.core.domain.resume

import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.file.requireContent
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import javax.inject.Inject

/** Resume drafts and their photos, kept in app-private storage (never in the cache, never on shared storage). */
interface ResumeRepository {
    /** Newest first. */
    suspend fun list(): AppResult<List<ResumeSummary>>

    suspend fun load(id: String): AppResult<Resume>

    suspend fun save(resume: Resume): AppResult<Unit>

    /** Deletes the draft and its photo. */
    suspend fun delete(id: String): AppResult<Unit>

    /** Stores the already cropped and resized JPEG at [jpegPath] as the photo of draft [id]. */
    suspend fun savePhoto(id: String, jpegPath: String): AppResult<Unit>

    suspend fun photo(id: String): AppResult<ByteArray?>

    suspend fun deletePhoto(id: String): AppResult<Unit>
}

/** Writes a [RichDocument] as PDF or DOCX, and reads back the app data embedded in a DOCX. */
interface DocumentExporter {
    suspend fun export(
        document: RichDocument,
        format: DocumentFormat,
        outputDir: String,
        baseName: String,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<OutputFile>

    /** [RichDocument.appData] of a DOCX this app wrote, or null when it has none. */
    suspend fun readEmbeddedData(source: FileRef): AppResult<String?>
}

/** Renders a draft with its template and photo and writes it as PDF or DOCX into [outputDir]. */
class ExportResumeUseCase @Inject constructor(
    private val repository: ResumeRepository,
    private val exporter: DocumentExporter,
) {
    suspend operator fun invoke(
        resume: Resume,
        format: DocumentFormat,
        outputDir: String,
        progress: ProgressReporter,
    ): AppResult<OutputFile> {
        if (format != DocumentFormat.PDF && format != DocumentFormat.DOCX) {
            return failure(AppError.UnsupportedFormat(format.mimeType))
        }
        val photo = if (resume.hasPhoto) {
            when (val bytes = repository.photo(resume.id)) {
                is Result.Success -> bytes.value
                is Result.Failure -> null // A lost photo should not block the export.
            }
        } else {
            null
        }
        val document = ResumeRenderer.render(resume, ResumeTemplates.byId(resume.templateId), photo)
        return exporter.export(document, format, outputDir, resume.displayName + RESUME_SUFFIX, progress)
            .requireContent()
    }

    private companion object {
        const val RESUME_SUFFIX = " Resume"
    }
}

/**
 * Re-opens a DOCX exported by the resume builder: its embedded JSON becomes a new draft with a new id,
 * so importing twice never overwrites an existing draft. The photo is not restored.
 */
class ImportResumeUseCase @Inject constructor(
    private val exporter: DocumentExporter,
    private val repository: ResumeRepository,
    private val limits: InputLimits,
) {
    suspend operator fun invoke(source: FileRef, newId: String, nowMillis: Long): AppResult<Resume> =
        limits.checkFileSize(source.sizeBytes)
            .flatMap {
                val format = DocumentFormat.detect(source.mimeType, source.displayName)
                if (format == DocumentFormat.DOCX) {
                    success(
                        Unit,
                    )
                } else {
                    failure(AppError.UnsupportedFormat(source.mimeType))
                }
            }
            .flatMap { exporter.readEmbeddedData(source) }
            .flatMap { data ->
                val resume = data?.let(ResumeJson::decode) ?: return@flatMap failure(AppError.NotAResume)
                success(resume.copy(id = newId, hasPhoto = false, updatedAtMillis = nowMillis))
            }
            .flatMap { resume -> repository.save(resume).flatMap { success(resume) } }
}
