package com.mrfilefixit.core.domain.scan

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.FileRepository
import com.mrfilefixit.core.domain.file.ImportFormat
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.ocr.OcrProcessor
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScanUseCasesTest {
    private val files = mockk<FileRepository>()
    private val ocr = mockk<OcrProcessor>()
    private val import = ImportScanUseCase(files, InputLimits.DEFAULT)
    private val recognize = RecognizeTextUseCase(ocr, InputLimits.DEFAULT)

    private fun stubImport() = coEvery { files.importToJob(any(), any(), any(), any(), any()) } answers {
        val name = thirdArg<String>()
        val format = arg<ImportFormat>(3)
        success(OutputFile("/jobs/1/$name.${format.extension}", "$name.${format.extension}", format.mimeType, 10))
    }

    @Test
    fun `imports pdf first then numbered pages and reaches full progress`() = runTest {
        stubImport()
        val seen = mutableListOf<Float>()
        val result = import(ScanCapture("content://s/pdf", listOf("content://s/1", "content://s/2")), "/jobs/1", "scan", ProgressReporter { seen += it })
        val doc = (result as Result.Success).value
        assertEquals("scan.pdf", doc.pdf?.displayName)
        assertEquals(listOf("scan-1.jpg", "scan-2.jpg"), doc.pages.map { it.displayName })
        assertEquals(1f, seen.last())
        assertTrue(seen.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `empty capture is rejected without touching storage`() = runTest {
        assertEquals(failure(AppError.EmptyFile), import(ScanCapture(null, emptyList()), "/jobs/1", "scan"))
        coVerify(exactly = 0) { files.importToJob(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed page import fails the whole import`() = runTest {
        stubImport()
        coEvery { files.importToJob("content://s/2", any(), any(), any(), any()) } returns failure(AppError.FileTooLarge(1, 0))
        val result = import(ScanCapture(null, listOf("content://s/1", "content://s/2")), "/jobs/1", "scan")
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `ocr rejects oversized inputs before running`() = runTest {
        val big = FileRef("content://x", "a.jpg", "image/jpeg", 60L * 1024 * 1024)
        val result = recognize(listOf(big), OcrScript.LATIN)
        assertTrue((result as Result.Failure).error is AppError.FileTooLarge)
        coVerify(exactly = 0) { ocr.recognize(any(), any(), any()) }
    }

    @Test
    fun `ocr passes valid inputs through`() = runTest {
        coEvery { ocr.recognize(any(), OcrScript.DEVANAGARI, any()) } returns success(OcrResult("नमस्ते", 1))
        val result = recognize(listOf(FileRef("content://x", "a.jpg", "image/jpeg", 100)), OcrScript.DEVANAGARI)
        assertEquals("नमस्ते", (result as Result.Success).value.text)
    }

    @Test
    fun `output files become file sources for ocr`() {
        val source = OutputFile("/data/cache/jobs/1/scan-1.jpg", "scan-1.jpg", "image/jpeg", 5).asSource()
        assertEquals("file:///data/cache/jobs/1/scan-1.jpg", source.uri)
    }
}
