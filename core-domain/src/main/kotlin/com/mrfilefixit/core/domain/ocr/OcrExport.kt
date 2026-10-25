package com.mrfilefixit.core.domain.ocr

import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.resume.DocumentExporter
import javax.inject.Inject

/** What recognized text can be saved as. */
enum class OcrExportFormat(val format: DocumentFormat) {
    /** The original pages with an invisible text layer, so the PDF can be searched and copied from. */
    SEARCHABLE_PDF(DocumentFormat.PDF),
    TXT(DocumentFormat.TXT),
    DOCX(DocumentFormat.DOCX),

    /** A new PDF with only the recognized text, one source page per PDF page. */
    TEXT_PDF(DocumentFormat.PDF),
}

/** Writes OCR output files that need the page images: plain text and searchable PDFs. */
interface OcrExporter {
    suspend fun writeText(text: String, outputDir: String, baseName: String): AppResult<OutputFile>

    /** Re-reads [sources] and lays [result]'s lines invisibly over each page. */
    suspend fun searchablePdf(
        sources: List<FileRef>,
        result: OcrResult,
        outputDir: String,
        baseName: String,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<OutputFile>
}

/** Saves an [OcrResult] in one of the [OcrExportFormat]s, into [outputDir]. */
class ExportOcrUseCase @Inject constructor(
    private val ocrExporter: OcrExporter,
    private val documentExporter: DocumentExporter,
) {
    suspend operator fun invoke(
        sources: List<FileRef>,
        result: OcrResult,
        format: OcrExportFormat,
        outputDir: String,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<OutputFile> {
        if (result.text.isBlank()) return failure(AppError.EmptyFile)
        val baseName = baseName(sources)
        return when (format) {
            OcrExportFormat.SEARCHABLE_PDF ->
                if (result.pages.isEmpty() || sources.isEmpty()) {
                    failure(AppError.EmptyFile)
                } else {
                    ocrExporter.searchablePdf(sources, result, outputDir, baseName, progress)
                }
            OcrExportFormat.TXT -> ocrExporter.writeText(result.text, outputDir, baseName)
            OcrExportFormat.DOCX, OcrExportFormat.TEXT_PDF -> {
                val document = OcrDocument.build(result, baseName)
                documentExporter.export(document, format.format, outputDir, baseName, progress)
            }
        }
    }

    private fun baseName(sources: List<FileRef>): String {
        val first = sources.firstOrNull()?.displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        return (first ?: DEFAULT_NAME) + TEXT_SUFFIX
    }

    private companion object {
        const val DEFAULT_NAME = "Scan"
        const val TEXT_SUFFIX = " text"
    }
}

/** Recognized text as a document: one paragraph per text line, and a page break between source pages. */
object OcrDocument {
    private const val LINE_SPACE_AFTER = 2f
    private const val BLOCK_SPACE_AFTER = 10f

    fun build(result: OcrResult, title: String): RichDocument {
        val pages = result.pages.map { it.text }.ifEmpty { listOf(result.text) }
        val blocks = mutableListOf<DocBlock>()
        pages.forEachIndexed { index, text ->
            if (index > 0) blocks += DocPageBreak
            blocks += paragraphs(text)
        }
        return RichDocument(blocks = blocks, title = title)
    }

    /** Text blocks are separated by a blank line; the last line of a block gets more space after it. */
    private fun paragraphs(text: String): List<DocParagraph> {
        val lines = text.trim().lines()
        return lines.mapIndexedNotNull { index, line ->
            if (line.isBlank()) return@mapIndexedNotNull null
            val endsBlock = index == lines.lastIndex || lines[index + 1].isBlank()
            val space = if (endsBlock) BLOCK_SPACE_AFTER else LINE_SPACE_AFTER
            DocParagraph.of(line.trimEnd(), ParagraphStyle(spaceAfterPt = space))
        }
    }
}
