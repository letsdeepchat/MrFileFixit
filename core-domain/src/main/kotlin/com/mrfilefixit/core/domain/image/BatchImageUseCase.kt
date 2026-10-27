package com.mrfilefixit.core.domain.image

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.FilenameSanitizer
import com.mrfilefixit.core.domain.security.InputLimits
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Same edit applied to many images. */
data class ImageBatchSpec(
    val operations: List<ImageOp>,
    val output: OutputSpec,
    /** Appended to each source name, e.g. "-resized". */
    val suffix: String,
    val effects: ImageEffects = ImageEffects.NONE,
)

data class BatchOutcome(
    val images: List<ProcessedImage>,
    /** Per-file failures; the batch carries on past them. */
    val failures: List<AppError>,
)

/**
 * Processes images one at a time (bounded memory) and keeps going when a single file fails, so one
 * corrupt photo does not cost the user the other 49. Fails as a whole only if every file fails.
 */
class BatchImageUseCase @Inject constructor(
    private val processor: ImageProcessor,
    private val limits: InputLimits,
) {
    suspend operator fun invoke(
        sources: List<FileRef>,
        spec: ImageBatchSpec,
        outputDir: String,
        progress: ProgressReporter,
    ): AppResult<BatchOutcome> {
        if (sources.isEmpty()) return failure(AppError.EmptyFile)
        val count = limits.checkFileCount(sources.size)
        if (count is Result.Failure) return count
        if (!processor.canEncode(spec.output.format)) {
            return failure(AppError.FeatureUnavailable(AppError.Feature.HEIC_ENCODE))
        }
        val images = mutableListOf<ProcessedImage>()
        val failures = mutableListOf<AppError>()
        sources.forEachIndexed { index, source ->
            currentCoroutineContext().ensureActive()
            val request = ImageRequest(
                source = source,
                outputDir = outputDir,
                outputBaseName = FilenameSanitizer.sanitize(source.displayName.substringBeforeLast('.') + spec.suffix),
                operations = spec.operations,
                effects = spec.effects,
                // "Remove metadata" keeps each file's own format.
                output = if (spec.output.target == EncodeTarget.Original) {
                    spec.output.copy(format = ImageFormat.fromMimeType(source.mimeType) ?: spec.output.format)
                } else {
                    spec.output
                },
            )
            val sizeCheck = limits.checkFileSize(source.sizeBytes)
            val result = if (sizeCheck is Result.Failure) {
                sizeCheck
            } else {
                processor.process(request, ProgressReporter.NONE).flatMap { image ->
                    if (image.file.sizeBytes > 0) success(image) else failure(AppError.EmptyOutput)
                }
            }
            when (result) {
                is Result.Success -> images += result.value
                is Result.Failure -> failures += result.error
            }
            progress.step(index, sources.size)
        }
        return if (images.isEmpty()) failure(failures.first()) else success(BatchOutcome(images, failures))
    }
}
