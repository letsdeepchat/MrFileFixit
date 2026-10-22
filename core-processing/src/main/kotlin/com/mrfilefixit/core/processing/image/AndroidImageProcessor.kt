package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.image.CompositeRequest
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImagePlan
import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.image.ImageRequest
import com.mrfilefixit.core.domain.image.JpegSegments
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.image.OutputGeometry
import com.mrfilefixit.core.domain.image.OutputSpec
import com.mrfilefixit.core.domain.image.PngChunks
import com.mrfilefixit.core.domain.image.ProcessedImage
import com.mrfilefixit.core.domain.image.QualitySearch
import com.mrfilefixit.core.domain.image.RectTransforms
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.image.sampleSizeFor
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.logging.AppLogger
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.io.mapEngineError
import com.mrfilefixit.core.processing.io.newOutputFile
import com.mrfilefixit.core.processing.io.readAllLimited
import android.content.ContentResolver
import android.graphics.Canvas
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Android image engine.
 *
 * One job = one region decode at the smallest safe sample size, one draw into the output canvas,
 * then at most [QualitySearch.MAX_ITERATIONS] encodes for KB targeting. Bitmaps are recycled with `use {}`.
 */
internal class AndroidImageProcessor @Inject constructor(
    private val resolver: ContentResolver,
    private val decoding: ImageDecoding,
    private val encoding: ImageEncoding,
    private val limits: InputLimits,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
) : ImageProcessor {

    override fun canEncode(format: ImageFormat): Boolean = encoding.canEncode(format)

    override suspend fun readInfo(source: FileRef): AppResult<ImageInfo> = withContext(dispatchers.io) {
        runCatchingAppFlat(::mapEngineError) {
            val raw = decoding.readRawInfo(source.uri)
            success(
                ImageInfo(
                    width = raw.uprightWidth,
                    height = raw.uprightHeight,
                    format = raw.format,
                    sizeBytes = source.sizeBytes ?: 0L,
                    rotationDegrees = raw.exifRotation,
                    hasMetadata = raw.hasExif,
                ),
            )
        }
    }

    override suspend fun process(request: ImageRequest, progress: ProgressReporter): AppResult<ProcessedImage> =
        withContext(dispatchers.default) {
            runCatchingAppFlat(::mapEngineError) {
                val plan = ImagePlan.from(request.operations) ?: return@runCatchingAppFlat failure(
                    AppError.Unexpected("NonCanonicalImageOps"),
                )
                val output = request.output
                if (!encoding.canEncode(output.format)) {
                    return@runCatchingAppFlat failure(AppError.FeatureUnavailable(AppError.Feature.HEIC_ENCODE))
                }
                val raw = decoding.readRawInfo(request.source.uri)
                progress.report(PROGRESS_DECODED / 2)

                if (output.target == EncodeTarget.Original) {
                    losslessStrip(request, raw)?.let { return@runCatchingAppFlat success(it) }
                }

                val bitmap = renderPlan(request.source.uri, raw, plan, output)
                coroutineContext.ensureActive()
                progress.report(PROGRESS_DECODED)
                bitmap.use { encodeToFile(it, request.outputDir, request.outputBaseName, output, progress) }
            }
        }

    override suspend fun composite(request: CompositeRequest, progress: ProgressReporter): AppResult<ProcessedImage> =
        withContext(dispatchers.default) {
            runCatchingAppFlat(::mapEngineError) {
                val topHeight = (request.height * request.topFraction).toInt().coerceIn(1, request.height - 1)
                val bottomHeight = request.height - topHeight
                val top = renderPart(request.top, request.topCrop, request.width, topHeight, request.output)
                progress.report(PROGRESS_DECODED / 2)
                val canvasBitmap = top.use { topBitmap ->
                    val bottom = renderPart(request.bottom, request.bottomCrop, request.width, bottomHeight, request.output)
                    bottom.use { bottomBitmap ->
                        Bitmap.createBitmap(request.width, request.height, Bitmap.Config.ARGB_8888).also { out ->
                            Canvas(out).apply {
                                drawColor(request.output.backgroundArgb)
                                drawBitmap(topBitmap, 0f, 0f, null)
                                drawBitmap(bottomBitmap, 0f, topHeight.toFloat(), null)
                            }
                        }
                    }
                }
                progress.report(PROGRESS_DECODED)
                canvasBitmap.use { encodeToFile(it, request.outputDir, request.outputBaseName, request.output, progress) }
            }
        }

    private fun renderPart(source: FileRef, crop: NormalizedRect, width: Int, height: Int, output: OutputSpec): Bitmap {
        val raw = decoding.readRawInfo(source.uri)
        val plan = ImagePlan(0, crop, ImageOp.Resize(width, height, ResizeMode.FILL), null)
        return renderPlan(source.uri, raw, plan, output)
    }

    /** Decode the crop region at the right sample size and draw it into the output canvas. */
    private fun renderPlan(uri: String, raw: RawImageInfo, plan: ImagePlan, output: OutputSpec): Bitmap {
        val rawRect = RectTransforms.displayToRaw(plan.crop, raw.exifRotation, raw.flipped, plan.rotation)
        val regionW = (rawRect.width * raw.width).toInt().coerceAtLeast(1)
        val regionH = (rawRect.height * raw.height).toInt().coerceAtLeast(1)
        val quarterTurns = RectTransforms.quarterTurns(raw.exifRotation + plan.rotation)
        val uprightW = if (quarterTurns % 2 == 0) regionW else regionH
        val uprightH = if (quarterTurns % 2 == 0) regionH else regionW
        val geometry = OutputGeometry.compute(uprightW, uprightH, plan)
        val neededW = (geometry.drawWidth / geometry.fillCrop.width).toInt()
        val neededH = (geometry.drawHeight / geometry.fillCrop.height).toInt()
        val sample = if (quarterTurns % 2 == 0) {
            sampleSizeFor(regionW, regionH, neededW, neededH)
        } else {
            sampleSizeFor(regionW, regionH, neededH, neededW)
        }
        return decoding.decodeRegion(uri, raw, rawRect, sample).use { region ->
            ImageRendering.render(
                region = region,
                exifRotation = raw.exifRotation,
                flipped = raw.flipped,
                userRotation = plan.rotation,
                geometry = geometry,
                format = output.format,
                backgroundArgb = output.backgroundArgb,
            )
        }
    }

    private suspend fun encodeToFile(
        bitmap: Bitmap,
        outputDir: String,
        baseName: String,
        output: OutputSpec,
        progress: ProgressReporter,
    ): AppResult<ProcessedImage> {
        val scratch = File(outputDir).apply { mkdirs() }
        return when (val target = output.target) {
            is EncodeTarget.Quality, EncodeTarget.Original -> {
                val quality = (target as? EncodeTarget.Quality)?.value ?: ORIGINAL_REENCODE_QUALITY
                val bytes = encoding.encode(bitmap, output.format, quality, scratch)
                progress.report(1f)
                success(write(bytes, bitmap, outputDir, baseName, output.format, quality, padded = false))
            }
            is EncodeTarget.SizeRange -> encodeInRange(bitmap, target, outputDir, baseName, output.format, progress)
        }
    }

    /**
     * KB targeting: binary-search quality (max 8 encodes). If even the lowest quality is too large and
     * downscaling is allowed, shrink by the size ratio and search again (at most [MAX_DOWNSCALES] times).
     */
    @Suppress("LongParameterList")
    private suspend fun encodeInRange(
        source: Bitmap,
        target: EncodeTarget.SizeRange,
        outputDir: String,
        baseName: String,
        format: ImageFormat,
        progress: ProgressReporter,
    ): AppResult<ProcessedImage> {
        val scratch = File(outputDir)
        var bitmap = source
        var attempt = 0
        try {
            while (true) {
                coroutineContext.ensureActive()
                val current = bitmap
                val outcome = if (format.supportsQuality) {
                    QualitySearch.search(target.minBytes, target.maxBytes) { q ->
                        coroutineContext.ensureActive()
                        val bytes = encoding.encode(current, format, q, scratch)
                        QualitySearch.Attempt(q, bytes.size.toLong(), bytes)
                    }
                } else {
                    val bytes = encoding.encode(current, format, QualitySearch.MAX_QUALITY, scratch)
                    val single = QualitySearch.Attempt(QualitySearch.MAX_QUALITY, bytes.size.toLong(), bytes)
                    when {
                        single.sizeBytes > target.maxBytes -> QualitySearch.Outcome.AboveMaximum(single, 1)
                        single.sizeBytes < target.minBytes -> QualitySearch.Outcome.BelowMinimum(single, 1)
                        else -> QualitySearch.Outcome.Found(single, 1)
                    }
                }
                logger.debug(TAG) { "size search: ${outcome::class.simpleName} after ${outcome.iterations} encodes" }
                progress.report(PROGRESS_DECODED + (1f - PROGRESS_DECODED) * (attempt + 1) / (MAX_DOWNSCALES + 1))
                when (outcome) {
                    is QualitySearch.Outcome.Found ->
                        return success(write(outcome.attempt.payload, current, outputDir, baseName, format, outcome.attempt.quality, false))

                    is QualitySearch.Outcome.BelowMinimum -> {
                        val best = outcome.best
                        return if (target.allowPadding && format == ImageFormat.JPEG) {
                            val padTo = target.minBytes + minOf(PAD_HEADROOM_BYTES, (target.maxBytes - target.minBytes) / 2)
                            val padded = JpegSegments.padToSize(best.payload, padTo)
                            success(write(padded, current, outputDir, baseName, format, best.quality, padded = true))
                        } else {
                            failure(AppError.TargetSizeUnreachable(best.sizeBytes, target.minBytes, target.maxBytes))
                        }
                    }

                    is QualitySearch.Outcome.AboveMaximum -> {
                        if (!target.allowDownscale || attempt >= MAX_DOWNSCALES) {
                            return failure(
                                AppError.TargetSizeUnreachable(outcome.smallest.sizeBytes, target.minBytes, target.maxBytes),
                            )
                        }
                        val ratio = sqrt(target.maxBytes.toDouble() / outcome.smallest.sizeBytes) * DOWNSCALE_SAFETY
                        val w = (current.width * ratio).toInt().coerceAtLeast(1)
                        val h = (current.height * ratio).toInt().coerceAtLeast(1)
                        bitmap = Bitmap.createScaledBitmap(current, w, h, true)
                        if (current !== source) current.recycle()
                        attempt++
                    }
                }
            }
        } finally {
            if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Lossless metadata strip for JPEG/PNG that are already upright. Returns null to fall back to re-encode. */
    private fun losslessStrip(request: ImageRequest, raw: RawImageInfo): ProcessedImage? {
        if (request.operations.isNotEmpty() || raw.exifRotation != 0 || raw.flipped) return null
        if (raw.format != request.output.format) return null
        val bytes = resolver.readAllLimited(request.source.uri, limits.maxFileBytes)
        val stripped = when (raw.format) {
            ImageFormat.JPEG -> JpegSegments.stripMetadata(bytes)
            ImageFormat.PNG -> PngChunks.stripMetadata(bytes)
            else -> null
        } ?: return null
        val format = raw.format ?: return null
        val file = newOutputFile(request.outputDir, request.outputBaseName, format.extension)
        file.writeBytes(stripped)
        return ProcessedImage(
            OutputFile(file.absolutePath, file.name, format.mimeType, file.length(), raw.uprightWidth, raw.uprightHeight),
            format,
            quality = null,
            padded = false,
        )
    }

    @Suppress("LongParameterList")
    private fun write(
        bytes: ByteArray,
        bitmap: Bitmap,
        outputDir: String,
        baseName: String,
        format: ImageFormat,
        quality: Int?,
        padded: Boolean,
    ): ProcessedImage {
        val file = newOutputFile(outputDir, baseName, format.extension)
        file.writeBytes(bytes)
        return ProcessedImage(
            file = OutputFile(file.absolutePath, file.name, format.mimeType, file.length(), bitmap.width, bitmap.height),
            format = format,
            quality = quality.takeIf { format.supportsQuality },
            padded = padded,
        )
    }

    private companion object {
        const val TAG = "ImageEngine"
        const val PROGRESS_DECODED = 0.4f
        const val MAX_DOWNSCALES = 3
        const val DOWNSCALE_SAFETY = 0.95
        const val PAD_HEADROOM_BYTES = 512L
        const val ORIGINAL_REENCODE_QUALITY = 95
    }
}
