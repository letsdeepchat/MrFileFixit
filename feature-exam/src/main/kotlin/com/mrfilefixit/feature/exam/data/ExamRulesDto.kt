package com.mrfilefixit.feature.exam.data

import kotlinx.serialization.Serializable

/** Wire format of `assets/exam_rules.json`. See docs/exam-rules.schema.json. */
@Serializable
internal data class ExamRulesFileDto(
    val schemaVersion: Int,
    val profiles: List<ExamProfileDto>,
)

@Serializable
internal data class ExamProfileDto(
    val id: String,
    val name: String,
    val category: String,
    val conductingBody: String,
    val sourceUrl: String,
    val lastVerified: String?,
    val verification: String,
    val notes: String? = null,
    val documents: List<DocumentDto>,
    val composite: CompositeDto? = null,
)

@Serializable
internal data class DocumentDto(
    val kind: String,
    val formats: List<String>,
    val sizeKb: RangeDto,
    val widthPx: RangeDto,
    val heightPx: RangeDto,
    val targetPx: SizeDto,
    val kilobyteBytes: Int = 1024,
    val notes: String? = null,
)

@Serializable
internal data class CompositeDto(
    val document: DocumentDto,
    val photoFraction: Float,
)

@Serializable
internal data class RangeDto(val min: Int? = null, val max: Int? = null)

@Serializable
internal data class SizeDto(val width: Int, val height: Int)
