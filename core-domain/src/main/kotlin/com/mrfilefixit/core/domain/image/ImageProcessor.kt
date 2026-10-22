package com.mrfilefixit.core.domain.image

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppResult

/**
 * Image engine. The Android implementation decodes at target size and never holds more than one
 * full-size bitmap per job; tests use fakes.
 */
interface ImageProcessor {
    /** Reads dimensions, format and EXIF orientation from the header only. */
    suspend fun readInfo(source: FileRef): AppResult<ImageInfo>

    suspend fun process(request: ImageRequest, progress: ProgressReporter = ProgressReporter.NONE): AppResult<ProcessedImage>

    suspend fun composite(request: CompositeRequest, progress: ProgressReporter = ProgressReporter.NONE): AppResult<ProcessedImage>

    /** Whether this device can encode [format] (HEIC needs a hardware HEVC encoder). */
    fun canEncode(format: ImageFormat): Boolean
}
