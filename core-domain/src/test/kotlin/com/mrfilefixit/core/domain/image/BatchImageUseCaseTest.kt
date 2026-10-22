package com.mrfilefixit.core.domain.image

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BatchImageUseCaseTest {
    private val processor = mockk<ImageProcessor>()
    private val useCase = BatchImageUseCase(processor, InputLimits.DEFAULT)
    private val spec = ImageBatchSpec(listOf(ImageOp.Scale(50)), OutputSpec(ImageFormat.JPEG), "-small")

    private fun file(name: String, size: Long = 1000) = FileRef("content://x/$name", name, "image/jpeg", size)

    private fun processed(name: String) =
        ProcessedImage(OutputFile("/c/$name", name, "image/jpeg", 10), ImageFormat.JPEG, 90, false)

    @Test
    fun `continues past failures and reports progress per file`() = runTest {
        every { processor.canEncode(any()) } returns true
        coEvery { processor.process(match { it.source.displayName == "bad.jpg" }, any()) } returns failure(AppError.CorruptFile)
        coEvery { processor.process(match { it.source.displayName != "bad.jpg" }, any()) } answers {
            success(processed(firstArg<ImageRequest>().outputBaseName))
        }
        val seen = mutableListOf<Float>()
        val result = useCase(
            listOf(file("a.jpg"), file("bad.jpg"), file("../c.jpg"), file("huge.jpg", 60L * 1024 * 1024)),
            spec,
            "/c",
            ProgressReporter { seen += it },
        )
        result as Result.Success
        assertEquals(listOf("a-small", "c-small"), result.value.images.map { it.file.displayName })
        assertEquals(2, result.value.failures.size)
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), seen)
    }

    @Test
    fun `fails when every file fails or the encoder is missing`() = runTest {
        every { processor.canEncode(any()) } returns true
        coEvery { processor.process(any(), any()) } returns failure(AppError.CorruptFile)
        assertEquals(failure(AppError.CorruptFile), useCase(listOf(file("a.jpg")), spec, "/c", ProgressReporter.NONE))

        every { processor.canEncode(ImageFormat.HEIC) } returns false
        val heic = spec.copy(output = OutputSpec(ImageFormat.HEIC))
        assertEquals(
            failure(AppError.FeatureUnavailable(AppError.Feature.HEIC_ENCODE)),
            useCase(listOf(file("a.jpg")), heic, "/c", ProgressReporter.NONE),
        )
    }
}
