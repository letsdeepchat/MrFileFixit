package com.mrfilefixit.feature.image.ui

import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.DocumentSpec
import com.mrfilefixit.core.domain.exam.ExamProfile
import kotlin.math.roundToInt

/**
 * A one-tap size for Resize. Exam presets carry the exam's [spec], so the output is also cropped to the
 * exact pixels and compressed into the exam's KB window; the passport preset only sets pixels.
 */
data class ResizePreset(
    val id: String,
    val width: Int,
    val height: Int,
    /** Exam short name, e.g. "SSC CGL"; null for the generic passport preset. */
    val examName: String? = null,
    val spec: DocumentSpec? = null,
) {
    companion object {
        /** 35 x 45 mm at 300 dpi, the common Indian passport photo size. */
        val PASSPORT = ResizePreset("passport", width = 413, height = 531)

        fun fromExam(profile: ExamProfile, spec: DocumentSpec) = ResizePreset(
            id = "${profile.id}:${spec.kind.name}",
            width = spec.targetWidth,
            height = spec.targetHeight,
            examName = profile.shortName,
            spec = spec,
        )
    }
}

/**
 * Presets offered on the Resize tab: the linked exam's uploads first (when the user came from an exam),
 * then the passport size and a few common exam uploads.
 */
internal object ResizePresets {
    private val defaults = listOf(
        "ssc-cgl" to DocumentKind.PHOTO,
        "upsc-cse" to DocumentKind.SIGNATURE,
        "ibps-po" to DocumentKind.PHOTO,
    )

    fun build(profiles: List<ExamProfile>, linkedExamId: String?): List<ResizePreset> {
        val linked = profiles.firstOrNull { it.id == linkedExamId }
            ?.let { profile -> profile.documents.map { ResizePreset.fromExam(profile, it) } }
            .orEmpty()
        val common = defaults.mapNotNull { (id, kind) ->
            profiles.firstOrNull { it.id == id }?.let { profile ->
                profile.document(kind)?.let { ResizePreset.fromExam(profile, it) }
            }
        }
        return (linked + ResizePreset.PASSPORT + common).distinctBy { it.id }
    }
}

/** Keeps width and height in the source image's ratio while the user types either one. */
internal object AspectRatioSync {
    fun heightFor(width: Int, sourceWidth: Int, sourceHeight: Int): Int =
        (width.toLong() * sourceHeight / sourceWidth.toDouble()).roundToInt().coerceAtLeast(1)

    fun widthFor(height: Int, sourceWidth: Int, sourceHeight: Int): Int =
        (height.toLong() * sourceWidth / sourceHeight.toDouble()).roundToInt().coerceAtLeast(1)
}
