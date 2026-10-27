package com.mrfilefixit.core.domain.convert

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.file.requireContents
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import javax.inject.Inject

/** File formats the converters read or write. */
enum class DocumentFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    DOC("application/msword", "doc"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
    PPT("application/vnd.ms-powerpoint", "ppt"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    XLS("application/vnd.ms-excel", "xls"),
    CSV("text/csv", "csv"),
    TXT("text/plain", "txt"),
    ;

    companion object {
        /** From a MIME type, falling back to the file extension (content providers often report octet-stream). */
        fun detect(mimeType: String?, fileName: String?): DocumentFormat? {
            val byMime = entries.firstOrNull { it.mimeType.equals(mimeType, ignoreCase = true) }
                ?: if (mimeType.equals("text/comma-separated-values", ignoreCase = true)) CSV else null
            if (byMime != null) return byMime
            val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return entries.firstOrNull { it.extension == ext }
        }
    }
}

/** One conversion offered in PDF tools → Convert. */
enum class ConversionKind(val inputs: Set<DocumentFormat>, val output: DocumentFormat) {
    WORD_TO_PDF(setOf(DocumentFormat.DOCX, DocumentFormat.DOC), DocumentFormat.PDF),
    PDF_TO_WORD(setOf(DocumentFormat.PDF), DocumentFormat.DOCX),
    PPT_TO_PDF(setOf(DocumentFormat.PPTX, DocumentFormat.PPT), DocumentFormat.PDF),
    PDF_TO_PPT(setOf(DocumentFormat.PDF), DocumentFormat.PPTX),
    EXCEL_TO_PDF(setOf(DocumentFormat.XLSX, DocumentFormat.XLS, DocumentFormat.CSV), DocumentFormat.PDF),
    PDF_TO_EXCEL(setOf(DocumentFormat.PDF), DocumentFormat.XLSX),
    ;

    /** MIME types for the system file picker. */
    val pickerMimeTypes: Array<String> get() = inputs.map { it.mimeType }.toTypedArray()
}

/** Spreadsheet output of [ConversionKind.PDF_TO_EXCEL]. */
enum class SheetOutput(val format: DocumentFormat) { XLSX(DocumentFormat.XLSX), CSV(DocumentFormat.CSV) }

data class ConversionOptions(
    /** Paper for Word and Excel to PDF when the source does not set its own. */
    val pageSize: PageSize = PageSize.A4,
    val sheetOutput: SheetOutput = SheetOutput.XLSX,
    /** 1-based pages of a PDF source to convert; empty means all. */
    val pages: List<Int> = emptyList(),
)

data class ConversionRequest(
    val source: FileRef,
    val kind: ConversionKind,
    val outputDir: String,
    val outputBaseName: String,
    val options: ConversionOptions = ConversionOptions(),
)

/**
 * Converts office documents to and from PDF, entirely on the device.
 *
 * Fidelity is best-effort and documented per direction in docs/CONVERSIONS.md: text, headings, lists,
 * tables and pictures carry over; complex layout features (floating shapes, charts, SmartArt, macros) do not.
 */
interface DocumentConverter {
    suspend fun convert(
        request: ConversionRequest,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<List<OutputFile>>
}

/** Validates the source's size and type against the chosen conversion before the engine opens it. */
class ConvertDocumentUseCase @Inject constructor(
    private val converter: DocumentConverter,
    private val limits: InputLimits,
) {
    suspend operator fun invoke(request: ConversionRequest, progress: ProgressReporter): AppResult<List<OutputFile>> =
        limits.checkFileSize(request.source.sizeBytes)
            .flatMap { checkFormat(request) }
            .flatMap { converter.convert(request, progress) }
            .requireContents()

    private fun checkFormat(request: ConversionRequest): AppResult<Unit> {
        val format = DocumentFormat.detect(request.source.mimeType, request.source.displayName)
        return if (format != null && format in request.kind.inputs) {
            success(Unit)
        } else {
            failure(AppError.UnsupportedFormat(request.source.mimeType))
        }
    }
}
