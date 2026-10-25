package com.mrfilefixit.core.processing.convert

import android.content.ContentResolver
import android.content.Context
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.resume.DocumentExporter
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.office.OfficeContainer
import com.mrfilefixit.core.office.OfficeEncryptedException
import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.docx.DocxReader
import com.mrfilefixit.core.office.docx.DocxWriter
import com.mrfilefixit.core.processing.io.InputRejectedException
import com.mrfilefixit.core.processing.io.mapEngineError
import com.mrfilefixit.core.processing.io.newOutputFile
import com.mrfilefixit.core.processing.io.openLimited
import com.mrfilefixit.core.processing.io.throwIfFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Writes generated documents (resumes) with the same engines as the office conversions: the PDF layout
 * engine and :core-office's DOCX writer, so both exports share one layout model.
 */
internal class AndroidDocumentExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val limits: InputLimits,
    private val dispatchers: DispatcherProvider,
) : DocumentExporter {

    override suspend fun export(
        document: RichDocument,
        format: DocumentFormat,
        outputDir: String,
        baseName: String,
        progress: ProgressReporter,
    ): AppResult<OutputFile> = withContext(dispatchers.default) {
        runCatchingAppFlat(::mapExportError) {
            when (format) {
                DocumentFormat.PDF -> {
                    val out = newOutputFile(outputDir, baseName, format.extension)
                    val pages = DocumentPdfWriter(MAX_PAGES).write(document, out) { progress.report(it) }
                    success(OutputFile(out.absolutePath, out.name, format.mimeType, out.length(), pageCount = pages))
                }
                DocumentFormat.DOCX -> {
                    val out = newOutputFile(outputDir, baseName, format.extension)
                    out.outputStream().use { DocxWriter().write(document, it) }
                    progress.report(1f)
                    success(OutputFile(out.absolutePath, out.name, format.mimeType, out.length()))
                }
                else -> failure(AppError.UnsupportedFormat(format.mimeType))
            }
        }
    }

    override suspend fun readEmbeddedData(source: FileRef): AppResult<String?> = withContext(dispatchers.io) {
        runCatchingAppFlat(::mapExportError) {
            limits.checkFileSize(source.sizeBytes).throwIfFailure()
            // Under cache/jobs/, so the start-up sweep removes it even if the process dies mid-read.
            val dir = File(context.cacheDir, JOBS_DIR).apply { mkdirs() }
            val copy = File.createTempFile("resume-import", ".tmp", dir)
            try {
                resolver.openLimited(source.uri, limits.maxFileBytes).use { input ->
                    copy.outputStream().use { input.copyTo(it) }
                }
                if (copy.length() == 0L) throw InputRejectedException(AppError.EmptyFile)
                // Anything that is not a ZIP package cannot be a DOCX, so it cannot hold a draft.
                if (OfficeContainer.sniff(copy) != OfficeContainer.ZIP) return@runCatchingAppFlat success(null)
                success(DocxReader().read(copy).appData)
            } finally {
                copy.delete()
            }
        }
    }

    private fun mapExportError(throwable: Throwable): AppError = when (throwable) {
        is OfficeEncryptedException -> AppError.DocumentProtected
        is OfficeLimitException -> AppError.DocumentTooComplex
        // Not a Word package at all: it cannot be one of our resumes.
        is OfficeFormatException -> AppError.NotAResume
        else -> mapEngineError(throwable)
    }

    private companion object {
        const val MAX_PAGES = 50
        const val JOBS_DIR = "jobs"
    }
}
