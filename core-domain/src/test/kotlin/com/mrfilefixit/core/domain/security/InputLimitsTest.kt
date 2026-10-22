package com.mrfilefixit.core.domain.security

import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputLimitsTest {
    private val limits = InputLimits.DEFAULT

    @Test
    fun `file size limit is 50 MB`() {
        assertEquals(success(Unit), limits.checkFileSize(50L * 1024 * 1024))
        assertEquals(
            failure(AppError.FileTooLarge(50L * 1024 * 1024 + 1, 50L * 1024 * 1024)),
            limits.checkFileSize(50L * 1024 * 1024 + 1),
        )
        assertEquals(failure(AppError.EmptyFile), limits.checkFileSize(0))
        assertEquals(success(Unit), limits.checkFileSize(null))
    }

    @Test
    fun `decompression bombs are rejected by pixel count and side length`() {
        assertEquals(success(Unit), limits.checkDimensions(8000, 8000))
        assertEquals(failure(AppError.DimensionsTooLarge(9000, 9000, 64_000_000)), limits.checkDimensions(9000, 9000))
        assertEquals(failure(AppError.DimensionsTooLarge(20_000, 10, 64_000_000)), limits.checkDimensions(20_000, 10))
        assertEquals(failure(AppError.CorruptFile), limits.checkDimensions(0, 100))
    }

    @Test
    fun `page and file counts are limited`() {
        assertEquals(success(Unit), limits.checkPageCount(1000))
        assertEquals(failure(AppError.TooManyPages(1001, 1000)), limits.checkPageCount(1001))
        assertEquals(failure(AppError.TooManyFiles(101, 100)), limits.checkFileCount(101))
    }
}
