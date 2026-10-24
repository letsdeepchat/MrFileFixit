package com.mrfilefixit.core.domain.scan

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.PdfOperation
import com.mrfilefixit.core.domain.pdf.PdfProcessor
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.collectAll
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.map
import com.mrfilefixit.core.domain.result.success
import javax.inject.Inject

/** Applies a [ScanFilter] to one scanned page at full resolution and writes it as a JPEG. */
interface ScanEnhancer {
    suspend fun enhance(page: FileRef, filter: ScanFilter, outputDir: String, baseName: String): AppResult<OutputFile>
}

/**
 * Re-renders every page of a scan with [ScanFilter] and rebuilds the PDF from the enhanced pages, so the
 * shared PDF and the page JPEGs always match what the user sees. [ScanFilter.ORIGINAL] returns the scan
 * as the scanner produced it.
 */
class EnhanceScanUseCase @Inject constructor(
    private val enhancer: ScanEnhancer,
    private val pdf: PdfProcessor,
) {
    suspend operator fun invoke(
        original: ScanDocument,
        filter: ScanFilter,
        jobDir: String,
        baseName: String,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<ScanDocument> {
        if (filter == ScanFilter.ORIGINAL || original.pages.isEmpty()) return success(original)
        val total = original.pages.size + 1
        val pages = original.pages.mapIndexed { index, page ->
            enhancer.enhance(page.asSource(), filter, jobDir, "$baseName-${index + 1}")
                .also { progress.step(index + 1, total) }
        }.collectAll()
        return pages.flatMap { enhanced ->
            val rebuild = PdfOperation.ImagesToPdf(
                images = enhanced.map { it.asSource() },
                pageSize = PageSize.FIT_IMAGE,
                marginPt = 0f,
                outputDir = jobDir,
                outputBaseName = baseName,
            )
            pdf.run(rebuild, ProgressReporter.NONE).map { outputs -> ScanDocument(outputs.firstOrNull(), enhanced) }
        }
    }
}
