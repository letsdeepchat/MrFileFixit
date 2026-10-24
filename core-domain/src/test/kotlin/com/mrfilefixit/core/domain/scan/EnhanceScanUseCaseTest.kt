package com.mrfilefixit.core.domain.scan

import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.PdfOperation
import com.mrfilefixit.core.domain.pdf.PdfProcessor
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class EnhanceScanUseCaseTest {
    private val enhancer = mockk<ScanEnhancer>()
    private val pdf = mockk<PdfProcessor>()
    private val enhance = EnhanceScanUseCase(enhancer, pdf)

    private fun page(name: String) = OutputFile("/jobs/1/$name.jpg", "$name.jpg", "image/jpeg", 100)
    private val original = ScanDocument(
        OutputFile("/jobs/1/scan.pdf", "scan.pdf", "application/pdf", 300),
        listOf(page("scan-1"), page("scan-2")),
    )

    @Test
    fun `original filter returns the scan untouched`() = runTest {
        assertSame(original, (enhance(original, ScanFilter.ORIGINAL, "/jobs/1", "scan") as Result.Success).value)
        coVerify(exactly = 0) { enhancer.enhance(any(), any(), any(), any()) }
    }

    @Test
    fun `every page is enhanced and the pdf is rebuilt from the enhanced pages`() = runTest {
        coEvery { enhancer.enhance(any(), ScanFilter.DOCUMENT_CLEAN, "/jobs/1", any()) } answers {
            success(page(arg<String>(3)))
        }
        val op = slot<PdfOperation>()
        coEvery { pdf.run(capture(op), any()) } returns
            success(listOf(OutputFile("/jobs/1/clean.pdf", "clean.pdf", "application/pdf", 200)))

        val result = enhance(original, ScanFilter.DOCUMENT_CLEAN, "/jobs/1", "clean")

        val doc = (result as Result.Success).value
        assertEquals(listOf("clean-1.jpg", "clean-2.jpg"), doc.pages.map { it.displayName })
        assertEquals("clean.pdf", doc.pdf?.displayName)
        val rebuild = op.captured as PdfOperation.ImagesToPdf
        assertEquals(PageSize.FIT_IMAGE, rebuild.pageSize)
        assertEquals(listOf("file:///jobs/1/clean-1.jpg", "file:///jobs/1/clean-2.jpg"), rebuild.images.map { it.uri })
    }

    @Test
    fun `a failing page fails the whole job and no pdf is built`() = runTest {
        coEvery { enhancer.enhance(any(), any(), any(), any()) } returns failure(AppError.OutOfMemory)
        assertEquals(failure(AppError.OutOfMemory), enhance(original, ScanFilter.GRAYSCALE, "/jobs/1", "gray"))
        coVerify(exactly = 0) { pdf.run(any(), any()) }
    }
}
