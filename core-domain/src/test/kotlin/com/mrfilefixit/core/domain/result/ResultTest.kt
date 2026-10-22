package com.mrfilefixit.core.domain.result

import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResultTest {
    @Test
    fun `map and flatMap transform success and short-circuit failure`() {
        val ok: AppResult<Int> = success(2)
        assertEquals(success(4), ok.map { it * 2 })
        assertEquals(success("2"), ok.flatMap { success(it.toString()) })

        val bad: AppResult<Int> = failure(AppError.CorruptFile)
        assertEquals(failure(AppError.CorruptFile), bad.map { it * 2 })
        assertNull(bad.getOrNull())
        assertEquals(AppError.CorruptFile, bad.errorOrNull())
    }

    @Test
    fun `runCatchingApp maps exceptions to AppError`() {
        assertEquals(failure(AppError.FileNotFound), runCatchingApp { throw FileNotFoundException("secret.jpg") })
        assertEquals(failure(AppError.StorageFull), runCatchingApp { throw IOException("write failed: ENOSPC") })
        assertEquals(failure(AppError.PermissionDenied), runCatchingApp { throw SecurityException() })
        assertEquals(failure(AppError.Unexpected("IllegalStateException")), runCatchingApp { error("x") })
    }

    @Test
    fun `runCatchingApp never swallows cancellation`() {
        assertThrows<CancellationException> { runCatchingApp { throw CancellationException("stop") } }
    }

    @Test
    fun `unexpected errors never carry exception messages`() {
        val error = AppError.fromThrowable(IllegalArgumentException("/sdcard/private/aadhaar.pdf"))
        assertEquals(AppError.Unexpected("IllegalArgumentException"), error)
    }

    @Test
    fun `collectAll returns first failure`() {
        val results: List<AppResult<Int>> =
            listOf(success(1), failure(AppError.CorruptFile), failure(AppError.EmptyFile))
        assertEquals(failure(AppError.CorruptFile), results.collectAll())
        assertEquals(success(listOf(1, 2)), listOf<AppResult<Int>>(success(1), success(2)).collectAll())
    }
}
