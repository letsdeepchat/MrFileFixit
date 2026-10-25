package com.mrfilefixit.core.domain.convert

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConvertDocumentUseCaseTest {
    private val output = OutputFile("/cache/job/report.pdf", "report.pdf", "application/pdf", 10)

    private class FakeConverter(private val result: AppResult<List<OutputFile>>) : DocumentConverter {
        var calls = 0

        override suspend fun convert(
            request: ConversionRequest,
            progress: ProgressReporter,
        ): AppResult<List<OutputFile>> {
            calls++
            return result
        }
    }

    private fun request(name: String, mime: String?, kind: ConversionKind, size: Long = 1_000) =
        ConversionRequest(FileRef("content://x/1", name, mime, size), kind, "/cache/job", "report")

    @Test
    fun `detects formats by MIME type, then by extension`() {
        assertEquals(DocumentFormat.DOCX, DocumentFormat.detect(DocumentFormat.DOCX.mimeType, "a.bin"))
        assertEquals(DocumentFormat.PPT, DocumentFormat.detect("application/octet-stream", "Deck.PPT"))
        assertEquals(DocumentFormat.CSV, DocumentFormat.detect("text/comma-separated-values", null))
        assertNull(DocumentFormat.detect("application/octet-stream", "photo.jpg"))
    }

    @Test
    fun `runs the converter for a matching file`() = runTest {
        val converter = FakeConverter(success(listOf(output)))
        val result = ConvertDocumentUseCase(converter, InputLimits())(
            request("Report.docx", "application/octet-stream", ConversionKind.WORD_TO_PDF),
            ProgressReporter.NONE,
        )
        assertEquals(success(listOf(output)), result)
        assertEquals(1, converter.calls)
    }

    @Test
    fun `rejects the wrong type and oversized files before converting`() = runTest {
        val converter = FakeConverter(success(listOf(output)))
        val useCase = ConvertDocumentUseCase(converter, InputLimits())
        val pdf = request("Report.pdf", "application/pdf", ConversionKind.WORD_TO_PDF)
        val wrongType = useCase(pdf, ProgressReporter.NONE)
        assertEquals(failure(AppError.UnsupportedFormat("application/pdf")), wrongType)
        val tooBig = useCase(
            request("Sheet.xlsx", DocumentFormat.XLSX.mimeType, ConversionKind.EXCEL_TO_PDF, size = 60L * 1024 * 1024),
            ProgressReporter.NONE,
        )
        assertEquals(AppError.FileTooLarge::class, (tooBig as Result.Failure).error::class)
        assertEquals(0, converter.calls)
    }
}
