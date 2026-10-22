package com.mrfilefixit.feature.image.work

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.image.BatchOutcome
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageBatchSpec
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.OutputSpec
import com.mrfilefixit.core.domain.image.ProcessedImage
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.result.AppError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Batch jobs handed to WorkManager are written as JSON into the job's private cache directory.
 * WorkManager's own input Data only carries the job id (Data is capped at 10 KB and is stored in a database).
 */
@Serializable
internal data class BatchJobDto(
    val sources: List<SourceDto>,
    val ops: List<OpDto>,
    val format: String,
    val quality: Int? = null,
    val minBytes: Long? = null,
    val maxBytes: Long? = null,
    val original: Boolean = false,
    val suffix: String,
)

@Serializable
internal data class SourceDto(val uri: String, val name: String, val mime: String?, val size: Long?)

@Serializable
internal data class OpDto(val type: String, val a: Int = 0, val b: Int = 0, val mode: String? = null)

@Serializable
internal data class BatchResultDto(val outputs: List<OutputDto>, val failures: Int)

@Serializable
internal data class OutputDto(
    val path: String,
    val name: String,
    val mime: String,
    val size: Long,
    val width: Int?,
    val height: Int?,
    val format: String,
)

internal object BatchSpecSerialization {
    val json = Json { ignoreUnknownKeys = false }

    fun encode(sources: List<FileRef>, spec: ImageBatchSpec): String = json.encodeToString(
        BatchJobDto.serializer(),
        BatchJobDto(
            sources = sources.map { SourceDto(it.uri, it.displayName, it.mimeType, it.sizeBytes) },
            ops = spec.operations.map { it.toDto() },
            format = spec.output.format.name,
            quality = (spec.output.target as? EncodeTarget.Quality)?.value,
            minBytes = (spec.output.target as? EncodeTarget.SizeRange)?.minBytes,
            maxBytes = (spec.output.target as? EncodeTarget.SizeRange)?.maxBytes,
            original = spec.output.target == EncodeTarget.Original,
            suffix = spec.suffix,
        ),
    )

    fun decode(text: String): Pair<List<FileRef>, ImageBatchSpec> {
        val dto = json.decodeFromString(BatchJobDto.serializer(), text)
        val target = when {
            dto.original -> EncodeTarget.Original
            dto.maxBytes != null -> EncodeTarget.SizeRange(dto.minBytes ?: 0, dto.maxBytes, allowDownscale = true)
            else -> EncodeTarget.Quality(dto.quality ?: OutputSpec.DEFAULT_QUALITY)
        }
        return dto.sources.map { FileRef(it.uri, it.name, it.mime, it.size) } to ImageBatchSpec(
            operations = dto.ops.map { it.toOp() },
            output = OutputSpec(ImageFormat.valueOf(dto.format), target),
            suffix = dto.suffix,
        )
    }

    fun encodeResult(outcome: BatchOutcome): String = json.encodeToString(
        BatchResultDto.serializer(),
        BatchResultDto(
            outputs = outcome.images.map {
                OutputDto(it.file.path, it.file.displayName, it.file.mimeType, it.file.sizeBytes, it.file.width, it.file.height, it.format.name)
            },
            failures = outcome.failures.size,
        ),
    )

    fun decodeResult(text: String): BatchOutcome {
        val dto = json.decodeFromString(BatchResultDto.serializer(), text)
        return BatchOutcome(
            images = dto.outputs.map {
                ProcessedImage(OutputFile(it.path, it.name, it.mime, it.size, it.width, it.height), ImageFormat.valueOf(it.format), null, false)
            },
            failures = List(dto.failures) { AppError.Unexpected("BatchItem") },
        )
    }

    private fun ImageOp.toDto(): OpDto = when (this) {
        is ImageOp.Rotate -> OpDto("rotate", degrees)
        is ImageOp.Resize -> OpDto("resize", width, height, mode.name)
        is ImageOp.Scale -> OpDto("scale", percent)
        is ImageOp.Crop -> error("Crop is single-image only and never batched")
    }

    private fun OpDto.toOp(): ImageOp = when (type) {
        "rotate" -> ImageOp.Rotate(a)
        "resize" -> ImageOp.Resize(a, b, ResizeMode.valueOf(mode ?: ResizeMode.FIT.name))
        "scale" -> ImageOp.Scale(a)
        else -> error("Unknown op $type")
    }
}
