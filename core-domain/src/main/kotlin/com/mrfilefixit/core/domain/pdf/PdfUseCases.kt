package com.mrfilefixit.core.domain.pdf

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import javax.inject.Inject

class GetPdfInfoUseCase @Inject constructor(
    private val processor: PdfProcessor,
    private val limits: InputLimits,
) {
    suspend operator fun invoke(source: FileRef): AppResult<PdfInfo> =
        limits.checkFileSize(source.sizeBytes).flatMap { processor.info(source) }
}

/**
 * Runs a PDF operation after validating its inputs, and always wipes passwords afterwards,
 * whatever the outcome.
 */
class RunPdfOperationUseCase @Inject constructor(
    private val processor: PdfProcessor,
    private val limits: InputLimits,
) {
    suspend operator fun invoke(
        operation: PdfOperation,
        progress: ProgressReporter,
    ): AppResult<List<OutputFile>> = try {
        validate(operation).flatMap { processor.run(operation, progress) }
    } finally {
        operation.wipeSecrets()
    }

    private fun validate(operation: PdfOperation): AppResult<Unit> = when (operation) {
        is PdfOperation.Merge ->
            if (operation.sources.size <
                2
            ) {
                failure(AppError.TooManyFiles(operation.sources.size, limits.maxFilesPerJob))
            } else {
                limits.checkFileCount(operation.sources.size)
            }
        is PdfOperation.ImagesToPdf -> limits.checkFileCount(operation.images.size)
        is PdfOperation.Protect ->
            if (operation.userPassword.isEmpty()) failure(AppError.WrongPassword) else success(Unit)
        is PdfOperation.Rotate ->
            if (operation.degrees % QUARTER_TURN != 0) failure(AppError.Unexpected("BadRotation")) else success(Unit)
        is PdfOperation.Watermark ->
            if (operation.text.isBlank()) failure(AppError.Unexpected("EmptyWatermark")) else success(Unit)
        else -> success(Unit)
    }

    private companion object {
        const val QUARTER_TURN = 90
    }
}
