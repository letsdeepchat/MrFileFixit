package com.mrfilefixit.core.domain.exam

import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.CompositeRequest
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.image.ImageRequest
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.image.OutputSpec
import com.mrfilefixit.core.domain.image.ProcessedImage
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.slice
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.map
import com.mrfilefixit.core.domain.security.FilenameSanitizer
import com.mrfilefixit.core.domain.security.InputLimits
import javax.inject.Inject
import kotlinx.coroutines.withContext

class GetExamProfilesUseCase @Inject constructor(
    private val repository: ExamRulesRepository,
) {
    suspend operator fun invoke(): AppResult<List<ExamProfile>> = repository.profiles()
}

class GetExamProfileUseCase @Inject constructor(
    private val repository: ExamRulesRepository,
) {
    suspend operator fun invoke(id: String): AppResult<ExamProfile> = repository.profile(id)
}

/** Checks a picked file as-is, so the user sees what is wrong before any processing. */
class CheckDocumentUseCase @Inject constructor(
    private val imageProcessor: ImageProcessor,
    private val limits: InputLimits,
) {
    suspend operator fun invoke(source: FileRef, spec: DocumentSpec): AppResult<Pair<ImageInfo, SpecReport>> =
        limits.checkFileSize(source.sizeBytes)
            .flatMap { imageProcessor.readInfo(source) }
            .flatMap { info -> limits.checkDimensions(info.width, info.height).map { info } }
            .map { info -> info to SpecChecker.check(spec, info) }
}

data class PreparedDocument(val image: ProcessedImage, val report: SpecReport)

/**
 * Turns a picked photo or signature into a file that meets [DocumentSpec]:
 * crop (user-chosen, or centred to the target aspect), resize to the target pixels, encode with
 * KB targeting, then verify the output with [SpecChecker].
 */
class PrepareExamDocumentUseCase @Inject constructor(
    private val imageProcessor: ImageProcessor,
    private val limits: InputLimits,
    private val dispatchers: DispatcherProvider,
) {
    @Suppress("LongParameterList")
    suspend operator fun invoke(
        source: FileRef,
        spec: DocumentSpec,
        crop: NormalizedRect?,
        rotationDegrees: Int,
        outputDir: String,
        baseName: String,
        progress: ProgressReporter,
    ): AppResult<PreparedDocument> = withContext(dispatchers.default) {
        limits.checkFileSize(source.sizeBytes)
            .flatMap { imageProcessor.readInfo(source) }
            .flatMap { info -> limits.checkDimensions(info.width, info.height).map { info } }
            .flatMap { info ->
                progress.report(PROGRESS_INFO_READ)
                val request = ImageRequest(
                    source = source,
                    outputDir = outputDir,
                    outputBaseName = FilenameSanitizer.sanitize(baseName),
                    operations = buildOperations(spec, info, crop, rotationDegrees),
                    output = outputSpecFor(spec),
                )
                imageProcessor.process(request, progress.slice(PROGRESS_INFO_READ, 1f))
            }
            .map { image -> PreparedDocument(image, SpecChecker.check(spec, image.toInfo())) }
    }

    private fun buildOperations(
        spec: DocumentSpec,
        info: ImageInfo,
        crop: NormalizedRect?,
        rotationDegrees: Int,
    ): List<ImageOp> = buildList {
        val normalizedRotation = ((rotationDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN
        if (normalizedRotation != 0) add(ImageOp.Rotate(normalizedRotation))
        val rotatedAspect = if (normalizedRotation % HALF_TURN == 0) {
            info.width.toFloat() / info.height
        } else {
            info.height.toFloat() / info.width
        }
        val targetAspect = spec.targetWidth.toFloat() / spec.targetHeight
        add(ImageOp.Crop(crop ?: NormalizedRect.centeredWithAspect(targetAspect, rotatedAspect)))
        add(ImageOp.Resize(spec.targetWidth, spec.targetHeight, ResizeMode.FILL))
    }

    companion object {
        private const val PROGRESS_INFO_READ = 0.05f
        private const val FULL_TURN = 360
        private const val HALF_TURN = 180

        fun outputSpecFor(spec: DocumentSpec): OutputSpec {
            val format = if (ImageFormat.JPEG in spec.formats) ImageFormat.JPEG else spec.formats.first()
            val range = spec.targetBytes
            return OutputSpec(
                format = format,
                target = EncodeTarget.SizeRange(range.first, range.last, allowPadding = format == ImageFormat.JPEG),
                stripMetadata = true,
            )
        }
    }
}

/** Builds the combined photo + signature image for portals that require one upload. */
class MergePhotoSignatureUseCase @Inject constructor(
    private val imageProcessor: ImageProcessor,
    private val dispatchers: DispatcherProvider,
) {
    @Suppress("LongParameterList")
    suspend operator fun invoke(
        photo: FileRef,
        signature: FileRef,
        composite: CompositeSpec,
        outputDir: String,
        baseName: String,
        progress: ProgressReporter,
    ): AppResult<PreparedDocument> = withContext(dispatchers.default) {
        val spec = composite.document
        val photoAspect = spec.targetWidth.toFloat() / (spec.targetHeight * composite.photoFraction)
        val signatureAspect = spec.targetWidth.toFloat() / (spec.targetHeight * (1f - composite.photoFraction))
        val photoInfo = imageProcessor.readInfo(photo)
        val signatureInfo = imageProcessor.readInfo(signature)
        photoInfo.flatMap { p ->
            signatureInfo.flatMap { s ->
                imageProcessor.composite(
                    CompositeRequest(
                        top = photo,
                        bottom = signature,
                        topCrop = NormalizedRect.centeredWithAspect(photoAspect, p.width.toFloat() / p.height),
                        bottomCrop = NormalizedRect.centeredWithAspect(signatureAspect, s.width.toFloat() / s.height),
                        topFraction = composite.photoFraction,
                        width = spec.targetWidth,
                        height = spec.targetHeight,
                        outputDir = outputDir,
                        outputBaseName = FilenameSanitizer.sanitize(baseName),
                        output = PrepareExamDocumentUseCase.outputSpecFor(spec),
                    ),
                    progress,
                )
            }
        }.map { image -> PreparedDocument(image, SpecChecker.check(spec, image.toInfo())) }
    }
}

internal fun ProcessedImage.toInfo(): ImageInfo = ImageInfo(
    width = file.width ?: 0,
    height = file.height ?: 0,
    format = format,
    sizeBytes = file.sizeBytes,
)
