package com.mrfilefixit.core.domain.pdf

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.job.ProgressReporter
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

class RunPdfOperationUseCaseTest {
    private val processor = mockk<PdfProcessor>()
    private val useCase = RunPdfOperationUseCase(processor, InputLimits.DEFAULT)
    private val pdf = FileRef("content://x/1", "a.pdf", "application/pdf", 1000)

    @Test
    fun `passwords are wiped after success and failure`() = runTest {
        coEvery { processor.run(any(), any()) } returns success(emptyList())
        val protect = PdfOperation.Protect(pdf, "secret".toCharArray(), "owner".toCharArray(), "/d", "out")
        useCase(protect, ProgressReporter.NONE)
        assertTrue(protect.userPassword.all { it == '\u0000' })
        assertTrue(protect.ownerPassword.all { it == '\u0000' })

        coEvery { processor.run(any(), any()) } returns failure(AppError.WrongPassword)
        val unlock = PdfOperation.Unlock(pdf, "guess".toCharArray(), "/d", "out")
        useCase(unlock, ProgressReporter.NONE)
        assertTrue(unlock.password.all { it == '\u0000' })
    }

    @Test
    fun `operations never print passwords`() {
        val protect = PdfOperation.Protect(pdf, "secret".toCharArray(), "owner".toCharArray(), "/d", "out")
        assertEquals("Protect(redacted)", protect.toString())
    }

    @Test
    fun `merge needs at least two files and empty passwords are rejected`() = runTest {
        val merge = PdfOperation.Merge(listOf(pdf), "/d", "out")
        assertTrue(useCase(merge, ProgressReporter.NONE) is Result.Failure)
        val protect = PdfOperation.Protect(pdf, CharArray(0), CharArray(0), "/d", "out")
        assertEquals(failure(AppError.WrongPassword), useCase(protect, ProgressReporter.NONE))
        coVerify(exactly = 0) { processor.run(any(), any()) }
    }
}
