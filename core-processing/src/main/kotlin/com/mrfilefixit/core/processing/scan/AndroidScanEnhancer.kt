package com.mrfilefixit.core.processing.scan

import android.graphics.Color
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImagePlan
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.image.OutputGeometry
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.scan.ScanEnhancer
import com.mrfilefixit.core.domain.scan.ScanFilter
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.image.ImageDecoding
import com.mrfilefixit.core.processing.image.ImageEncoding
import com.mrfilefixit.core.processing.image.ImageRendering
import com.mrfilefixit.core.processing.image.use
import com.mrfilefixit.core.processing.io.mapEngineError
import com.mrfilefixit.core.processing.io.newOutputFile
import com.mrfilefixit.core.processing.io.throwIfFailure
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.withContext

/**
 * Scan filters on Android bitmaps. The page is decoded at its own resolution (only halved for pages
 * larger than [MAX_SIDE], which no phone scanner produces), then filtered by [ScanFiltering].
 */
internal class AndroidScanEnhancer @Inject constructor(
    private val decoding: ImageDecoding,
    private val encoding: ImageEncoding,
    private val limits: InputLimits,
    private val dispatchers: DispatcherProvider,
) : ScanEnhancer {

    override suspend fun enhance(
        page: FileRef,
        filter: ScanFilter,
        outputDir: String,
        baseName: String,
    ): AppResult<OutputFile> = withContext(dispatchers.default) {
        runCatchingAppFlat(::mapEngineError) {
            limits.checkFileSize(page.sizeBytes).throwIfFailure()
            val raw = decoding.readRawInfo(page.uri)
            var sample = 1
            while (max(raw.width, raw.height) / sample > MAX_SIDE) sample *= 2
            val upright = decoding.decodeRegion(page.uri, raw, NormalizedRect.FULL, sample).use { region ->
                val turned = raw.exifRotation % HALF_TURN != 0
                val geometry = OutputGeometry.compute(
                    if (turned) region.height else region.width,
                    if (turned) region.width else region.height,
                    ImagePlan(0, NormalizedRect.FULL, null, null),
                )
                ImageRendering.render(region, raw.exifRotation, raw.flipped, 0, geometry, ImageFormat.JPEG, Color.WHITE)
            }
            val width = upright.width
            val height = upright.height
            val bytes = upright.use { source ->
                ScanFiltering.apply(source, filter).use {
                    encoding.encode(it, ImageFormat.JPEG, JPEG_QUALITY, File(outputDir))
                }
            }
            val file = newOutputFile(outputDir, baseName, ImageFormat.JPEG.extension)
            file.writeBytes(bytes)
            success(OutputFile(file.absolutePath, file.name, ImageFormat.JPEG.mimeType, file.length(), width, height))
        }
    }

    private companion object {
        /** Long side cap. 4096 px keeps an A4 page well above 300 dpi. */
        const val MAX_SIDE = 4096
        const val JPEG_QUALITY = 90
        const val HALF_TURN = 180
    }
}
