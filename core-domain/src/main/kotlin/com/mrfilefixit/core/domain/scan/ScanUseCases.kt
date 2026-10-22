package com.mrfilefixit.core.domain.scan

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.FileRepository
import com.mrfilefixit.core.domain.file.ImportFormat
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.ocr.OcrProcessor
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.collectAll
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.map
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import javax.inject.Inject

/** What the document scanner returned. URIs belong to the scanner and are only valid for a short time. */
data class ScanCapture(val pdfUri: String?, val pageUris: List<String>) {
    override fun toString(): String = "ScanCapture(pdf=${pdfUri != null}, pages=${pageUris.size})"
}

/** A scan copied into our own job directory, so it can be shared, saved and fed to OCR. */
data class ScanDocument(val pdf: OutputFile?, val pages: List<OutputFile>) {
    val all: List<OutputFile> get() = listOfNotNull(pdf) + pages
}

/** Copies the scanner's output into `cache/jobs/<id>/` (PDF first, then one JPEG per page). */
class ImportScanUseCase @Inject constructor(
    private val files: FileRepository,
    private val limits: InputLimits,
) {
    suspend operator fun invoke(
        capture: ScanCapture,
        jobDir: String,
        baseName: String,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<ScanDocument> {
        if (capture.pdfUri == null && capture.pageUris.isEmpty()) return failure(AppError.EmptyFile)
        return limits.checkFileCount(capture.pageUris.size).flatMap {
            val total = capture.pageUris.size + 1
            val pdf: AppResult<OutputFile?> = capture.pdfUri
                ?.let { files.importToJob(it, jobDir, baseName, ImportFormat.PDF, limits.maxFileBytes) }
                ?: success(null)
            progress.step(0, total)
            pdf.flatMap { pdfFile ->
                capture.pageUris.mapIndexed { index, uri ->
                    files.importToJob(uri, jobDir, "$baseName-${index + 1}", ImportFormat.JPEG, limits.maxFileBytes)
                        .also { progress.step(index + 1, total) }
                }.collectAll().map { pages -> ScanDocument(pdfFile, pages) }
            }
        }
    }
}

/** On-device OCR over picked files or scanned pages, after the usual input checks. */
class RecognizeTextUseCase @Inject constructor(
    private val ocr: OcrProcessor,
    private val limits: InputLimits,
) {
    suspend operator fun invoke(
        sources: List<FileRef>,
        script: OcrScript,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<OcrResult> {
        if (sources.isEmpty()) return failure(AppError.EmptyFile)
        return limits.checkFileCount(sources.size)
            .flatMap { sources.map { limits.checkFileSize(it.sizeBytes) }.collectAll() }
            .flatMap { ocr.recognize(sources, script, progress) }
    }
}

/** Turns a job output back into a readable source (OCR on scanned pages). */
fun OutputFile.asSource(): FileRef = FileRef("file://$path", displayName, mimeType, sizeBytes)
