package com.mrfilefixit.core.domain.exam

import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.image.ImageRequest
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.image.ProcessedImage
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrepareExamDocumentUseCaseTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }
    private val processor = mockk<ImageProcessor>()
    private val useCase = PrepareExamDocumentUseCase(processor, InputLimits.DEFAULT, dispatchers)

    private val source = FileRef("content://picked/1", "IMG_0001.jpg", "image/jpeg", 3_000_000)
    private val spec = DocumentSpec(
        kind = DocumentKind.PHOTO,
        formats = setOf(ImageFormat.JPEG),
        minKb = 20,
        maxKb = 50,
        width = IntBounds.exactly(200),
        height = IntBounds.exactly(230),
        targetWidth = 200,
        targetHeight = 230,
    )

    @Test
    fun `builds centre crop, exact resize and KB target, then checks output`() = TestScope(dispatcher).runTest {
        coEvery { processor.readInfo(source) } returns success(ImageInfo(4000, 3000, ImageFormat.JPEG, 3_000_000))
        val request = slot<ImageRequest>()
        coEvery { processor.process(capture(request), any()) } returns success(
            ProcessedImage(
                OutputFile("/cache/jobs/1/photo.jpg", "photo.jpg", "image/jpeg", 35_000, 200, 230),
                ImageFormat.JPEG,
                92,
                false,
            ),
        )

        val result =
            useCase(source, spec, crop = null, rotationDegrees = 0, "/cache/jobs/1", "../photo", ProgressReporter.NONE)

        result as Result.Success
        assertTrue(result.value.report.passed)
        val ops = request.captured.operations
        val crop = ops[0] as ImageOp.Crop
        assertEquals(200f / 230f, crop.rect.width * 4000 / (crop.rect.height * 3000), 0.001f)
        assertEquals(ImageOp.Resize(200, 230, ResizeMode.FILL), ops[1])
        val target = request.captured.output.target as EncodeTarget.SizeRange
        assertEquals(spec.targetBytes.first, target.minBytes)
        assertEquals(spec.targetBytes.last, target.maxBytes)
        assertTrue(request.captured.output.stripMetadata)
        assertEquals("photo", request.captured.outputBaseName)
    }

    @Test
    fun `rotation is applied before crop and uses rotated aspect`() = TestScope(dispatcher).runTest {
        coEvery { processor.readInfo(source) } returns success(ImageInfo(4000, 3000, ImageFormat.JPEG, 3_000_000))
        val request = slot<ImageRequest>()
        coEvery { processor.process(capture(request), any()) } returns failure(AppError.CorruptFile)

        useCase(source, spec, crop = NormalizedRect.FULL, rotationDegrees = -90, "/d", "p", ProgressReporter.NONE)

        assertEquals(ImageOp.Rotate(270), request.captured.operations.first())
        assertEquals(ImageOp.Crop(NormalizedRect.FULL), request.captured.operations[1])
    }

    @Test
    fun `oversized input is rejected before decoding`() = TestScope(dispatcher).runTest {
        val huge = source.copy(sizeBytes = 60L * 1024 * 1024)
        val result = useCase(huge, spec, null, 0, "/d", "p", ProgressReporter.NONE)
        assertEquals(AppError.FileTooLarge(60L * 1024 * 1024, 50L * 1024 * 1024), (result as Result.Failure).error)
        coVerify(exactly = 0) { processor.readInfo(any()) }
    }

    @Test
    fun `decompression bomb dimensions are rejected before processing`() = TestScope(dispatcher).runTest {
        coEvery { processor.readInfo(source) } returns success(ImageInfo(30_000, 30_000, ImageFormat.PNG, 1_000))
        val result = useCase(source, spec, null, 0, "/d", "p", ProgressReporter.NONE)
        assertTrue((result as Result.Failure).error is AppError.DimensionsTooLarge)
        coVerify(exactly = 0) { processor.process(any(), any()) }
    }
}
