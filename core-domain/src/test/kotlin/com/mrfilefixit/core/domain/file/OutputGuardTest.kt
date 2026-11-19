package com.mrfilefixit.core.domain.file

import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OutputGuardTest {
    private fun file(size: Long) = OutputFile("/c/a.pdf", "a.pdf", "application/pdf", size)

    @Test
    fun `files with content pass through`() {
        assertEquals(success(file(10)), success(file(10)).requireContent())
        assertEquals(success(listOf(file(1), file(2))), success(listOf(file(1), file(2))).requireContents())
    }

    @Test
    fun `a 0 B file or no file at all is a failure`() {
        assertEquals(Result.Failure(AppError.EmptyOutput), success(file(0)).requireContent())
        assertEquals(Result.Failure(AppError.EmptyOutput), success(listOf(file(5), file(0))).requireContents())
        assertEquals(Result.Failure(AppError.EmptyOutput), success(emptyList<OutputFile>()).requireContents())
    }

    @Test
    fun `earlier failures are kept`() {
        assertEquals(Result.Failure(AppError.CorruptFile), failure(AppError.CorruptFile).requireContent())
    }
}
