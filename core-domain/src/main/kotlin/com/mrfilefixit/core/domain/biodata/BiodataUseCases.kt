package com.mrfilefixit.core.domain.biodata

import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.file.requireContent
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.getOrNull
import com.mrfilefixit.core.domain.resume.DocumentExporter
import java.time.LocalDate
import javax.inject.Inject

/** Biodata drafts and their photos, in app-private storage (never the cache, never shared storage). */
interface BiodataRepository {
    /** Newest first. */
    suspend fun list(): AppResult<List<BiodataSummary>>

    suspend fun load(id: String): AppResult<Biodata>

    suspend fun save(biodata: Biodata): AppResult<Unit>

    /** Deletes the draft and all of its photos. */
    suspend fun delete(id: String): AppResult<Unit>

    /** Stores the already cropped JPEG at [jpegPath] as photo [key] of draft [id]. */
    suspend fun savePhoto(id: String, key: String, jpegPath: String): AppResult<Unit>

    suspend fun photo(id: String, key: String): AppResult<ByteArray?>

    suspend fun deletePhoto(id: String, key: String): AppResult<Unit>
}

/** Renders a draft with its template and photos and writes it as PDF (or Word) into [outputDir]. */
class ExportBiodataUseCase @Inject constructor(
    private val repository: BiodataRepository,
    private val exporter: DocumentExporter,
) {
    @Suppress("LongParameterList")
    suspend operator fun invoke(
        biodata: Biodata,
        labels: BiodataLabels,
        format: DocumentFormat,
        outputDir: String,
        today: LocalDate,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<OutputFile> {
        if (format != DocumentFormat.PDF && format != DocumentFormat.DOCX) {
            return failure(AppError.UnsupportedFormat(format.mimeType))
        }
        if (biodata.photos.size > Biodata.MAX_PHOTOS) {
            return failure(AppError.TooManyFiles(biodata.photos.size, Biodata.MAX_PHOTOS))
        }
        if (biodata.photos.size < Biodata.MIN_PHOTOS) return failure(AppError.PhotoRequired)
        val photos = biodata.photos.map { repository.photo(biodata.id, it.key).getOrNull() }
        if (photos.all { it == null }) return failure(AppError.PhotoRequired)
        val document = BiodataRenderer.render(biodata, BiodataTemplates.byId(biodata.templateId), photos, labels, today)
        return exporter.export(document, format, outputDir, "${biodata.displayName} $FILE_SUFFIX", progress)
            .requireContent()
    }

    private companion object {
        const val FILE_SUFFIX = "Biodata"
    }
}
